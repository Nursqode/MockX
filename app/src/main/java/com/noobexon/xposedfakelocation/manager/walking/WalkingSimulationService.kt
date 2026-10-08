package com.noobexon.xposedfakelocation.manager.walking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.noobexon.xposedfakelocation.data.WALKING_MAX_CATCHUP_SECONDS
import com.noobexon.xposedfakelocation.data.WALKING_TICK_INTERVAL_MS
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.notification.WalkingNotificationBuilder
import com.noobexon.xposedfakelocation.manager.notification.WalkingNotificationState
import com.noobexon.xposedfakelocation.manager.route.PositionSnapshot
import com.noobexon.xposedfakelocation.manager.route.RouteProgressEngine
import com.noobexon.xposedfakelocation.manager.route.WalkEngine
import com.noobexon.xposedfakelocation.manager.route.WalkingErrorCode
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import com.noobexon.xposedfakelocation.manager.route.WalkingRoute
import com.noobexon.xposedfakelocation.manager.route.WalkingRouteCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Foreground service that advances the simulated position along a prepared walking route.
 *
 * Division of responsibility (see 规划.md §9): the service never plans routes and never talks
 * to the Amap API — it only consumes the route the manager app has already stored in the
 * remote preferences, advances it on a fixed tick, and publishes the dynamic WGS-84 position
 * for the Xposed hooks to read.
 *
 * Commands are idempotent: START while walking, PAUSE while paused and STOP while stopped are
 * all no-ops. If the system kills and recreates the service ([onStartCommand] with a null
 * intent), the session is restored from the shared state: WALKING sessions resume ticking,
 * PAUSED sessions re-enter the foreground paused, anything else stops the service.
 */
class WalkingSimulationService : Service() {

    private lateinit var repository: PreferencesRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Serial queue for every shared-state write (phase transitions, tick position publishes,
     * terminal cleanup). Single-threaded FIFO dispatch guarantees that near-simultaneous
     * commands land in the order they were processed, so UI, notification, remote preferences
     * and the Xposed hooks always agree on the current phase (追踪.md P2-001).
     */
    private val stateWriteDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** Set once when the terminal stop/fail cleanup starts; blocks duplicate finalizers.
     * Re-armed by [startSession] when a new session generation opens on this same instance. */
    private val finalizing = AtomicBoolean(false)

    /**
     * Session-generation id this instance is currently serving (MockX完整功能规划.md §5.1).
     * Captured from the shared state on START/adopt/restore and by every queued state write;
     * a write whose generation no longer matches the shared state is dropped, so delayed
     * ticks and a late terminal cleanup can never clobber a newer session.
     */
    private var sessionId: String? = null

    private var tickJob: Job? = null
    private var engine: WalkEngine? = null
    private var distanceTravelledMeters = 0.0
    private var lastTickElapsedRealtime = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    /** Unified notification assembly (通知体验升级规划.md §6): state in, one notification out. */
    private val notificationBuilder by lazy { WalkingNotificationBuilder(this) }

    /** Monotonic notification-update counter backing [WalkingNotificationState.sequence]. */
    private val notificationSequence = AtomicLong(0)

    /** Serialises throttle/sequence bookkeeping; tick updates race with command-path updates. */
    private val notificationLock = Any()
    private var lastSentSequence = 0L
    private var lastSentPhase: WalkingPhase? = null
    private var lastNotifyElapsedRealtime = 0L

    override fun onCreate() {
        super.onCreate()
        repository = PreferencesRepository(this)
        notificationBuilder.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WALKING -> startSession()
            ACTION_PAUSE_WALKING -> pauseSession()
            ACTION_RESUME_WALKING -> resumeSession()
            ACTION_STOP_WALKING -> stopSession()
            // null intent ⇒ the system recreated the service; restore from shared state.
            else -> restoreAfterRecreation()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tickJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    // region Session control

    /**
     * Loads the walk engine and progress cursor from shared state, unless already loaded.
     *
     * A home-walk session builds an open-ended [HomeWalkEngine] around its stored anchor; a route
     * session restores the planned polyline. Anything else (missing route, no anchor) is not a
     * runnable session and fails the caller.
     */
    private fun ensureLoaded(): Boolean {
        if (engine != null) return true
        return when (repository.getWalkingMode()) {
            WalkingMode.HOME -> {
                val anchor = repository.getWalkingHomeAnchor() ?: return false
                engine = HomeWalkEngine(anchor)
                distanceTravelledMeters = repository.getWalkingDistanceTravelled().coerceAtLeast(0.0)
                true
            }

            WalkingMode.ROUTE -> {
                val restored = WalkingRouteCodec.decode(repository.getWalkingRouteJson())
                if (restored == null || restored.points.size < 2) return false
                engine = RouteProgressEngine(restored.points)
                distanceTravelledMeters = repository.getWalkingDistanceTravelled().coerceIn(0.0, restored.totalDistanceMeters)
                true
            }
        }
    }

    private fun startSession() {
        if (tickJob?.isActive == true) return // Already walking; START is a no-op.
        // A START after a terminal cleanup began on this same instance opens a NEW generation:
        // re-arm the finalizer and adopt the fresh session id (§5.1 generation isolation).
        finalizing.set(false)
        sessionId = repository.getWalkingSessionId()
        if (!enterForegroundWithRoute()) return
        launchSessionWrite { repository.resumeWalkingSession() }
        startTicking()
    }

    private fun pauseSession() {
        if (tickJob?.isActive != true) {
            // No ticker running. Adopt a stale WALKING session (process was restarted) by
            // writing PAUSED and holding position in the foreground; every other phase is an
            // idempotent no-op — ARRIVED/IDLE/FAILED must never be flipped by a late pause
            // (追踪.md P2-001).
            val targetPhase = WalkingCommandPolicy.phaseAfterPause(repository.getWalkingPhase())
            if (targetPhase != null && ensureLoaded() && startInForeground(WalkingPhase.PAUSED)) {
                sessionId = repository.getWalkingSessionId()
                launchSessionWrite { repository.pauseWalkingSession() }
            }
            return
        }
        tickJob?.cancel()
        tickJob = null
        launchSessionWrite { repository.pauseWalkingSession() }
        updateNotification(WalkingPhase.PAUSED)
    }

    private fun resumeSession() {
        if (tickJob?.isActive == true) return
        val phase = repository.getWalkingPhase()
        if (!WalkingCommandPolicy.canResume(phase)) {
            // No live session to resume. ARRIVED keeps its foreground view; a definitively
            // dead session (IDLE/FAILED) means this started service has nothing left to do.
            if (phase == WalkingPhase.IDLE || phase == WalkingPhase.FAILED) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }
        sessionId = repository.getWalkingSessionId()
        if (!enterForegroundWithRoute()) return
        launchSessionWrite { repository.resumeWalkingSession() }
        startTicking()
    }

    private fun stopSession() {
        tickJob?.cancel()
        tickJob = null
        finalizeSession { repository.stopWalkingSession() }
    }

    /**
     * Shared prologue for START/RESUME: (re)load the route, publish the foreground notification
     * and hold a wake lock. Returns `false` (after failing the session) when either step is
     * impossible.
     */
    private fun enterForegroundWithRoute(): Boolean {
        if (!ensureLoaded()) {
            fail(WalkingErrorCode.SERVICE_START_FAILED, "No valid route in shared state")
            return false
        }
        lastTickElapsedRealtime = SystemClock.elapsedRealtime()
        if (!startInForeground(WalkingPhase.WALKING)) {
            fail(WalkingErrorCode.SERVICE_START_FAILED, "startForeground rejected")
            return false
        }
        acquireWakeLock()
        return true
    }

    private fun restoreAfterRecreation() {
        sessionId = repository.getWalkingSessionId()
        if (!ensureLoaded()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        when (repository.getWalkingPhase()) {
            WalkingPhase.WALKING -> {
                if (tickJob?.isActive == true) return
                if (!enterForegroundWithRoute()) return
                startTicking()
            }
            WalkingPhase.PAUSED -> {
                // Stay alive and paused in the foreground so the user can resume.
                startInForeground(WalkingPhase.PAUSED)
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun fail(errorCode: WalkingErrorCode, detail: String) {
        Log.e(TAG, "Walking session failed: $detail")
        tickJob?.cancel()
        tickJob = null
        finalizeSession { repository.markWalkingFailed(errorCode.name) }
    }

    /**
     * Terminal stop/fail path (追踪.md P1-002): the shared-state cleanup runs to completion on
     * a dedicated coroutine before the foreground notification is removed and the service stops.
     *
     * The finalizer deliberately does NOT run on [serviceScope] — `onDestroy` cancels that
     * scope, which is exactly the race that used to leave `walking_enabled`/`is_playing` stale
     * after a stop. This scope survives `onDestroy`; it only dies if the whole process does.
     * [stateWriteDispatcher] keeps the terminal write ordered behind any phase write already
     * queued. A timeout guards against a hung remote-preferences commit blocking shutdown.
     *
     * Generation isolation (MockX完整功能规划.md §5.1): the cleanup carries the session id it
     * belongs to. If a newer session took over the shared state meanwhile, the stale finalizer
     * skips both the state write and the shutdown — the notification, wake lock and service
     * lifetime now belong to the new generation.
     */
    private fun finalizeSession(cleanup: suspend () -> Unit) {
        val writerSessionId = sessionId
        if (!finalizing.compareAndSet(false, true)) return
        CoroutineScope(SupervisorJob() + stateWriteDispatcher).launch {
            var superseded = false
            try {
                withTimeout(CLEANUP_TIMEOUT_MS) {
                    superseded = WalkingSessionPolicy.isSuperseded(
                        writerSessionId,
                        repository.getWalkingSessionId(),
                    )
                    if (!superseded) cleanup()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Terminal walking-state write failed: ${e.message}")
            }
            if (superseded) return@launch
            stopForeground(STOP_FOREGROUND_REMOVE)
            releaseWakeLock()
            stopSelf()
        }
    }

    /**
     * Enqueues a session-scoped shared-state write on the serial [stateWriteDispatcher]. The
     * write carries the session id captured at enqueue time and is dropped when the shared
     * state has moved to a different generation in the meantime (§5.1).
     */
    private fun launchSessionWrite(block: suspend () -> Unit): Job {
        val writerSessionId = sessionId
        return serviceScope.launch(stateWriteDispatcher) {
            if (!WalkingSessionPolicy.mayWrite(writerSessionId, repository.getWalkingSessionId())) {
                Log.w(TAG, "Dropped a walking-state write from a superseded session generation")
                return@launch
            }
            block()
        }
    }

    // endregion

    // region Tick loop

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            val activeEngine = engine ?: return@launch
            while (isActive) {
                delay(WALKING_TICK_INTERVAL_MS)

                val now = SystemClock.elapsedRealtime()
                val elapsedSeconds = (now - lastTickElapsedRealtime) / 1000.0
                lastTickElapsedRealtime = now

                val speed = repository.getWalkingSpeed()
                val snapshot = advance(activeEngine, elapsedSeconds, speed)

                launchSessionWrite {
                    try {
                        repository.updateWalkingTick(
                            latitude = snapshot.coordinate.latitude,
                            longitude = snapshot.coordinate.longitude,
                            speedMetersPerSecond = speed,
                            bearingDegrees = snapshot.bearingDegrees,
                            distanceTravelledMeters = distanceTravelledMeters,
                            updatedAtEpochMillis = System.currentTimeMillis(),
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to publish walking position: ${e.message}")
                    }
                }
                updateNotification(WalkingPhase.WALKING)

                if (snapshot.arrived) {
                    // Queued after the final position write, so the ARRIVED phase always lands
                    // on top of the last published coordinates.
                    launchSessionWrite {
                        repository.markWalkingArrived(snapshot.coordinate.latitude, snapshot.coordinate.longitude)
                        updateNotification(WalkingPhase.ARRIVED)
                    }
                    releaseWakeLock()
                    break
                }
            }
        }
    }

    /**
     * Moves the travelled-distance cursor forward by [elapsedSeconds] of walking and resolves
     * the resulting position. A single delayed tick (deep sleep, scheduler starvation) advances
     * by at most [WALKING_MAX_CATCHUP_SECONDS] worth of distance so the marker never teleports.
     * Exposed for unit tests.
     */
    internal fun advance(engine: WalkEngine, elapsedSeconds: Double, speedMetersPerSecond: Float): PositionSnapshot {
        if (elapsedSeconds > 0) {
            val effective = min(elapsedSeconds, WALKING_MAX_CATCHUP_SECONDS)
            distanceTravelledMeters = min(
                distanceTravelledMeters + effective * speedMetersPerSecond,
                engine.totalDistanceMeters,
            )
        }
        return engine.positionAt(distanceTravelledMeters)
    }

    // endregion

    // region Notification & foreground

    /**
     * Publishes the foreground notification for the phase the service itself knows it is in
     * ([phase] is passed explicitly because the shared preference can lag the in-flight command
     * by one serialised write).
     */
    private fun startInForeground(phase: WalkingPhase): Boolean {
        val state = buildState(phase)
        val notification = notificationBuilder.build(state)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, notification)
            }
            rememberSent(state)
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            false
        }
    }

    /**
     * Assembles the shared notification state. All backends (standard bar, Android 16 Live
     * Update, HyperOS island) consume exactly this state — none of them recompute progress
     * (通知体验升级规划.md §5.2).
     *
     * A home-walk session has no destination and no total distance, so it reports the anchor as
     * the destination, a zero total (no progress bar) and no ETA.
     */
    private fun buildState(phase: WalkingPhase): WalkingNotificationState {
        val mode = repository.getWalkingMode()
        val route = if (mode == WalkingMode.ROUTE) WalkingRouteCodec.decode(repository.getWalkingRouteJson()) else null
        return WalkingNotificationState.fromSession(
            phase = phase,
            travelledMeters = distanceTravelledMeters,
            totalMeters = if (mode == WalkingMode.HOME) 0.0 else repository.getWalkingTotalDistance(),
            speedMetersPerSecond = repository.getWalkingSpeed(),
            sequence = notificationSequence.incrementAndGet(),
            origin = route?.origin,
            destination = if (mode == WalkingMode.HOME) repository.getWalkingHomeAnchor() else route?.destination,
            mode = mode,
        )
    }

    private fun rememberSent(state: WalkingNotificationState) {
        synchronized(notificationLock) {
            lastSentSequence = state.sequence
            lastSentPhase = state.phase
            lastNotifyElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Refreshes the walking notification for [phase]. Tick-driven WALKING refreshes are
     * throttled to [NOTIFICATION_UPDATE_INTERVAL_MS]; every phase transition is immediate
     * (通知体验升级规划.md §9.6). [WalkingNotificationState.sequence] drops out-of-order updates
     * when a slow build races a newer state. A failed refresh never stops the simulation.
     */
    private fun updateNotification(phase: WalkingPhase) {
        try {
            synchronized(notificationLock) {
                val now = SystemClock.elapsedRealtime()
                if (phase == lastSentPhase &&
                    now - lastNotifyElapsedRealtime < NOTIFICATION_UPDATE_INTERVAL_MS
                ) {
                    return
                }
                val state = buildState(phase)
                if (state.sequence <= lastSentSequence) return
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notificationBuilder.build(state))
                lastSentSequence = state.sequence
                lastSentPhase = phase
                lastNotifyElapsedRealtime = now
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification update failed: ${e.message}")
        }
    }

    // endregion

    // region Wake lock

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MockX:WalkingSimulation").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    // endregion

    companion object {
        private const val TAG = "WalkingSimulation"

        const val ACTION_START_WALKING = "io.github.souitou.mockx.action.START_WALKING"
        const val ACTION_PAUSE_WALKING = "io.github.souitou.mockx.action.PAUSE_WALKING"
        const val ACTION_RESUME_WALKING = "io.github.souitou.mockx.action.RESUME_WALKING"
        const val ACTION_STOP_WALKING = "io.github.souitou.mockx.action.STOP_WALKING"

        const val CHANNEL_ID = "walking_simulation"
        const val NOTIFICATION_ID = 4201

        /** Tick-driven notification refreshes are throttled to this interval (通知体验升级规划.md §9.6). */
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2_000L

        /** Upper bound for the wake lock (2 hours); the session renews it on resume. */
        private const val WAKE_LOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L

        /** Max time the terminal stop/fail cleanup may hold the service open. */
        private const val CLEANUP_TIMEOUT_MS = 5_000L

        /** Explicit command [Intent] for [WalkingSimulationService]. */
        fun commandIntent(context: Context, action: String): Intent =
            Intent(context, WalkingSimulationService::class.java).setAction(action)
    }
}

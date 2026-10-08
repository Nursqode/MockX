package com.noobexon.xposedfakelocation.manager.notification

import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase

/**
 * Everything the walking notification backends render, assembled once per refresh by
 * [com.noobexon.xposedfakelocation.manager.walking.WalkingSimulationService] and consumed
 * unchanged by the standard builder, the Android 16 Live Update style and the Xiaomi HyperOS
 * island payload (通知体验升级规划.md §5). All backends must share this one progress source —
 * none of them recompute progress from preferences.
 *
 * Carries no coordinates, no API key and no route geometry. [sequence] is a monotonically
 * increasing counter the service uses to drop out-of-order notification updates.
 */
data class WalkingNotificationState(
    val phase: WalkingPhase,
    /** Progress on the shared 0..[PROGRESS_MAX] per-mille scale, identical for every backend. */
    val progressPerMille: Int,
    val travelledMeters: Double,
    val totalMeters: Double,
    /** Estimated seconds to arrival; null when unknown or not meaningful (paused, arrived). */
    val remainingSeconds: Long?,
    val sequence: Long,
    val origin: Coordinate? = null,
    val destination: Coordinate? = null,
    /** Route walk (travelled / total) or home walk (endless, around [destination]). */
    val mode: WalkingMode = WalkingMode.ROUTE,
) {
    /** Progress on the 0..100 percent scale used by the HyperOS progress bar. */
    val progressPercent: Int get() = (progressPerMille / 10).coerceIn(0, 100)

    /** Phases that keep a movement-notification foreground surface alive. */
    val isActiveSession: Boolean
        get() = phase == WalkingPhase.WALKING || phase == WalkingPhase.PAUSED || phase == WalkingPhase.ARRIVED

    /** Actions offered on the notification for the current phase (通知体验升级规划.md §5.3). */
    fun actions(): List<Action> = when (phase) {
        WalkingPhase.WALKING -> listOf(Action.PAUSE, Action.STOP)
        WalkingPhase.PAUSED -> listOf(Action.RESUME, Action.STOP)
        WalkingPhase.ARRIVED -> listOf(Action.STOP)
        else -> emptyList()
    }

    enum class Action { PAUSE, RESUME, STOP }

    companion object {
        const val PROGRESS_MAX = 1000

        /**
         * Shared progress rule (通知体验升级规划.md §5.2): travelled/total on a 0..1000 scale.
         * A non-finite or non-positive total — and non-finite travelled — collapse to 0 instead
         * of dividing by zero or poisoning the bar.
         */
        fun progressPerMille(travelledMeters: Double, totalMeters: Double): Int {
            if (!travelledMeters.isFinite() || !totalMeters.isFinite() || totalMeters <= 0.0) return 0
            return ((travelledMeters / totalMeters) * PROGRESS_MAX).toInt().coerceIn(0, PROGRESS_MAX)
        }

        /**
         * Assembles the state from one service tick with the phase the service itself knows it is
         * in (the shared preference may lag the in-flight command by one serialised write).
         * ARRIVED pins progress to the full bar (通知体验升级规划.md §7.4); only WALKING gets an ETA.
         *
         * A home walk has no destination distance to count down, so its progress stays at zero
         * (the notification renders an indeterminate bar) and it carries no ETA.
         */
        fun fromSession(
            phase: WalkingPhase,
            travelledMeters: Double,
            totalMeters: Double,
            speedMetersPerSecond: Float,
            sequence: Long,
            origin: Coordinate? = null,
            destination: Coordinate? = null,
            mode: WalkingMode = WalkingMode.ROUTE,
        ): WalkingNotificationState {
            val progress = when {
                phase == WalkingPhase.ARRIVED -> PROGRESS_MAX
                mode == WalkingMode.HOME -> 0
                else -> progressPerMille(travelledMeters, totalMeters)
            }
            val remainingSeconds = if (phase == WalkingPhase.WALKING &&
                mode == WalkingMode.ROUTE && speedMetersPerSecond > 0f
            ) {
                (((totalMeters - travelledMeters) / speedMetersPerSecond).toLong()).coerceAtLeast(0)
            } else {
                null
            }
            return WalkingNotificationState(
                phase = phase,
                progressPerMille = progress,
                travelledMeters = travelledMeters,
                totalMeters = totalMeters,
                origin = origin,
                destination = destination,
                remainingSeconds = remainingSeconds,
                sequence = sequence,
                mode = mode,
            )
        }
    }
}

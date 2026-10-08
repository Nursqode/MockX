package com.noobexon.xposedfakelocation.manager.notification

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase

/**
 * Pure description of the Android 16 progress style, derived from the shared notification state.
 * Derivation is unit-testable; only [AndroidLiveUpdateAdapter.apply] touches platform classes.
 */
data class LiveUpdateStyleSpec(
    val progressPerMille: Int,
    val trackerMarker: TrackerMarker,
) {
    enum class TrackerMarker { WALKING, PAUSED, ARRIVED }
}

/**
 * Maps the shared walking state onto the Android 16 `NotificationCompat.ProgressStyle` that
 * powers Live Updates (通知体验升级规划.md §7). The spec is derived purely so tests can verify
 * Live Updates and the standard bar share one progress source; [apply] is only ever invoked
 * behind a `SDK_INT >= 36` guard so the API 36 classes are never loaded below that (§7.5).
 */
object AndroidLiveUpdateAdapter {

    /**
     * Null for phases without a movement surface (IDLE/PLANNING/READY/STOPPING/FAILED) and for a
     * home walk, whose endless progress has no fraction to drive a promoted Live Update bar.
     */
    fun styleSpec(state: WalkingNotificationState): LiveUpdateStyleSpec? {
        if (!state.isActiveSession || state.mode == WalkingMode.HOME) return null
        val marker = when (state.phase) {
            WalkingPhase.PAUSED -> LiveUpdateStyleSpec.TrackerMarker.PAUSED
            WalkingPhase.ARRIVED -> LiveUpdateStyleSpec.TrackerMarker.ARRIVED
            else -> LiveUpdateStyleSpec.TrackerMarker.WALKING
        }
        return LiveUpdateStyleSpec(state.progressPerMille, marker)
    }

    /**
     * One segment spanning the whole 0..1000 scale with progress = travelled per-mille, so the
     * styled bar and the legacy `setProgress` show the same fraction (§15.1). `styledByProgress`
     * colours travelled vs remaining distance differently; the tracker icon rides the bar.
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    fun apply(builder: NotificationCompat.Builder, spec: LiveUpdateStyleSpec, trackerIcon: IconCompat) {
        val style = NotificationCompat.ProgressStyle()
            .addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(WalkingNotificationState.PROGRESS_MAX),
            )
            .setProgress(spec.progressPerMille)
            .setStyledByProgress(true)
            .setProgressTrackerIcon(trackerIcon)
        builder.setStyle(style)
        builder.setRequestPromotedOngoing(true)
    }
}

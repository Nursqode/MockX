package com.noobexon.xposedfakelocation.manager.notification

import android.content.Context
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.HOME_WALK_MAX_RADIUS_METERS
import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import java.util.Locale

/** The localized strings the notification copy is assembled from; JVM tests construct this directly. */
data class WalkingNotificationTexts(
    /** Template with two `%s` placeholders: travelled and total distance. */
    val walkingTemplate: String,
    /** Home-walk template with one `%s` placeholder: the travelled distance. */
    val homeWalkingTemplate: String,
    val paused: String,
    val arrived: String,
    val statusWalking: String,
    val statusPaused: String,
    val statusArrived: String,
)

/**
 * Resolves notification copy from the shared [WalkingNotificationState]. The resolution rules
 * live in the pure [NotificationTextResolver] so they stay unit-testable; this class only reads
 * resources. It never touches preferences or raw coordinates (通知体验升级规划.md §6.2).
 */
class NotificationContentFormatter(private val context: Context) {

    private val texts = WalkingNotificationTexts(
        walkingTemplate = context.getString(R.string.walk_notification_text),
        homeWalkingTemplate = context.getString(R.string.walk_notification_home_text),
        paused = context.getString(R.string.walk_notification_paused_text),
        arrived = context.getString(R.string.walk_notification_arrived_text),
        statusWalking = context.getString(R.string.walk_status_walking),
        statusPaused = context.getString(R.string.walk_status_paused),
        statusArrived = context.getString(R.string.walk_status_arrived),
    )

    fun contentTitle(): String = context.getString(R.string.walk_notification_title)

    fun contentText(state: WalkingNotificationState): String =
        NotificationTextResolver.contentText(state, texts) +
            etaText(state)?.let { " · $it" }.orEmpty()

    fun routeSummary(state: WalkingNotificationState): String =
        if (state.mode == WalkingMode.HOME) {
            homeSpotText(state).orEmpty()
        } else {
            listOfNotNull(
                state.origin?.let { "${context.getString(R.string.walk_origin)} ${formatCoordinate(it)}" },
                state.destination?.let { "${context.getString(R.string.walk_destination)} ${formatCoordinate(it)}" },
            ).joinToString(" → ")
        }

    /** One line per endpoint (起点 / 终点), so the expanded island can stack them instead of
     * truncating a single long "起点 … → 终点 …" line. A home walk has only its anchor line. */
    fun routeLines(state: WalkingNotificationState): List<String> =
        if (state.mode == WalkingMode.HOME) {
            listOfNotNull(homeSpotText(state))
        } else {
            listOfNotNull(
                state.origin?.let { "${context.getString(R.string.walk_origin)} ${formatCoordinate(it)}" },
                state.destination?.let { "${context.getString(R.string.walk_destination)} ${formatCoordinate(it)}" },
            )
        }

    /** [routeLines] shortened to 3-decimal coordinates so both endpoints fit on one island
     * content line without truncation; full 5-decimal precision stays in the notification centre. */
    fun routeLinesShort(state: WalkingNotificationState): List<String> =
        if (state.mode == WalkingMode.HOME) {
            listOfNotNull(homeSpotText(state, short = true))
        } else {
            listOfNotNull(
                state.origin?.let { "${context.getString(R.string.walk_origin)} ${formatCoordinateShort(it)}" },
                state.destination?.let { "${context.getString(R.string.walk_destination)} ${formatCoordinateShort(it)}" },
            )
        }

    /** "Within 20 m of 12.345, 67.890" — describes a home walk, whose anchor is not a destination. */
    private fun homeSpotText(state: WalkingNotificationState, short: Boolean = false): String? =
        state.destination?.let { anchor ->
            context.getString(
                R.string.walk_home_spot_format,
                HOME_WALK_MAX_RADIUS_METERS.toInt(),
                if (short) formatCoordinateShort(anchor) else formatCoordinate(anchor),
            )
        }

    fun compactMode(): String = context.getString(R.string.walk_compact_mode)

    /** Short status label shared by the HyperOS ticker, AOD line and island summary. */
    fun statusLabel(state: WalkingNotificationState): String =
        NotificationTextResolver.statusLabel(state, texts)

    /** Localized ETA line, or null when the estimate is missing or under a minute. */
    fun etaText(state: WalkingNotificationState): String? {
        val minutes = state.remainingSeconds?.takeIf { it >= 60 }?.let { it / 60 } ?: return null
        return context.getString(R.string.walk_eta_format, minutes.toInt())
    }

    /** "Travelled / total" for a route walk, or just the travelled distance for a home walk. */
    fun distanceSummary(state: WalkingNotificationState): String =
        NotificationTextResolver.distanceSummary(state)

    companion object {
        fun formatCoordinate(coordinate: Coordinate): String =
            String.format(Locale.US, "%.5f, %.5f", coordinate.latitude, coordinate.longitude)

        /** 3-decimal compact form for space-constrained island lines. */
        fun formatCoordinateShort(coordinate: Coordinate): String =
            String.format(Locale.US, "%.3f, %.3f", coordinate.latitude, coordinate.longitude)

        /** Mirrors the map screen's distance formatting: "850 m" below 1 km, "1.24 km" above. */
        fun formatDistance(meters: Double): String = when {
            !meters.isFinite() || meters < 0.0 -> String.format(Locale.US, "%.0f m", 0.0)
            meters >= 1000.0 -> String.format(Locale.US, "%.2f km", meters / 1000.0)
            else -> String.format(Locale.US, "%.0f m", meters)
        }
    }
}

/** Pure text rules; no Android types so JVM tests can exercise every phase. */
object NotificationTextResolver {

    fun contentText(state: WalkingNotificationState, texts: WalkingNotificationTexts): String =
        when {
            state.phase == WalkingPhase.PAUSED -> texts.paused
            state.phase == WalkingPhase.ARRIVED -> texts.arrived
            state.mode == WalkingMode.HOME -> String.format(
                Locale.US,
                texts.homeWalkingTemplate,
                NotificationContentFormatter.formatDistance(state.travelledMeters),
            )
            else -> String.format(
                Locale.US,
                texts.walkingTemplate,
                NotificationContentFormatter.formatDistance(state.travelledMeters),
                NotificationContentFormatter.formatDistance(state.totalMeters),
            )
        }

    fun statusLabel(state: WalkingNotificationState, texts: WalkingNotificationTexts): String =
        when (state.phase) {
            WalkingPhase.PAUSED -> texts.statusPaused
            WalkingPhase.ARRIVED -> texts.statusArrived
            else -> texts.statusWalking
        }

    /** "Travelled / total" for a route walk, or the travelled distance alone for a home walk. */
    fun distanceSummary(state: WalkingNotificationState): String =
        if (state.mode == WalkingMode.HOME) {
            NotificationContentFormatter.formatDistance(state.travelledMeters)
        } else {
            distanceSummary(state.travelledMeters, state.totalMeters)
        }

    fun distanceSummary(travelledMeters: Double, totalMeters: Double): String =
        "${NotificationContentFormatter.formatDistance(travelledMeters)} / " +
            NotificationContentFormatter.formatDistance(totalMeters)
}

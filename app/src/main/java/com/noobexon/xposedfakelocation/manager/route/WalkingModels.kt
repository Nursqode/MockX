package com.noobexon.xposedfakelocation.manager.route

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R

/**
 * Lifecycle of a walking-simulation session.
 *
 * [IDLE]/[PLANNING]/[READY]/[FAILED] are manager-app-only states; [WALKING]/[PAUSED]/[ARRIVED]
 * are the "dynamic position" states the Xposed hooks treat as active. The value's [name] is what
 * gets persisted to the shared remote preference [com.noobexon.xposedfakelocation.data.KEY_WALKING_PHASE].
 */
enum class WalkingPhase {
    IDLE,
    PLANNING,
    READY,
    WALKING,
    PAUSED,
    ARRIVED,
    STOPPING,
    FAILED;

    companion object {
        /** Phases during which the hooks must return the dynamic (moving) position. */
        val SPOOFING_PHASES: Set<WalkingPhase> = setOf(WALKING, PAUSED, ARRIVED)

        fun fromName(name: String?): WalkingPhase =
            entries.firstOrNull { it.name == name } ?: IDLE
    }
}

/**
 * A plain WGS-84 coordinate. Used for route data and location injection so the coordinate
 * system is unambiguous — UI map points use osmdroid [org.osmdroid.util.GeoPoint] and are
 * converted to/from GCJ-02 only at the map-display boundary.
 */
data class Coordinate(val latitude: Double, val longitude: Double) {
    fun isValid(): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * How the simulated walker generates its positions.
 *
 * [ROUTE] follows a planned Amap walking route from the real position to the pinned
 * destination; [HOME] walks around the pinned coordinate itself, staying
 * [com.noobexon.xposedfakelocation.data.HOME_WALK_MIN_RADIUS_METERS]…
 * [com.noobexon.xposedfakelocation.data.HOME_WALK_MAX_RADIUS_METERS] metres away from it and
 * needing no route planning, network access or API key. The value's [name] is what gets
 * persisted to [com.noobexon.xposedfakelocation.data.KEY_WALKING_MODE].
 */
enum class WalkingMode {
    ROUTE,
    HOME;

    companion object {
        /** Unknown or missing values fall back to [ROUTE], the pre-existing behaviour. */
        fun fromName(name: String?): WalkingMode =
            entries.firstOrNull { it.name == name } ?: ROUTE
    }
}

/**
 * A parsed walking route. All [points] are WGS-84, ordered from origin to destination, with
 * at least two entries and no consecutive duplicates.
 */
data class WalkingRoute(
    val origin: Coordinate,
    val destination: Coordinate,
    val points: List<Coordinate>,
    /** Total path length computed from the parsed trajectory points (metres). */
    val totalDistanceMeters: Double,
    /** Distance reported by the route provider; kept for validation only. */
    val apiReportedDistanceMeters: Double?,
    /** Duration reported by the route provider (seconds), or `null` when not returned. */
    val expectedDurationSeconds: Int?,
    val provider: String = "amap",
)

/** Walking-speed presets offered in the map screen before starting a simulation. */
enum class WalkingSpeedPreset(val metersPerSecond: Float, @StringRes val labelRes: Int) {
    RELAXED(1.0f, R.string.walk_speed_relaxed),
    NORMAL(1.4f, R.string.walk_speed_normal),
    BRISK(1.8f, R.string.walk_speed_brisk),
}

/**
 * Stable failure codes for the walking feature. UI maps these to localized messages; logs
 * record only the code, never API keys, URLs or precise coordinates.
 */
enum class WalkingErrorCode {
    MISSING_API_KEY,
    LOCATION_UNAVAILABLE,
    INVALID_DESTINATION,
    NETWORK_TIMEOUT,
    NETWORK_UNAVAILABLE,
    API_AUTH_FAILED,
    API_QUOTA_EXCEEDED,
    NO_ROUTE,
    INVALID_ROUTE_DATA,
    PREFERENCE_UNAVAILABLE,
    SERVICE_START_FAILED,
    LOCATION_STATE_STALE,
}

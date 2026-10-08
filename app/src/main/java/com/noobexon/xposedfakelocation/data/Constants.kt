//Constants.kt
package com.noobexon.xposedfakelocation.data

// APP
const val MANAGER_APP_PACKAGE_NAME = "io.github.souitou.mockx"
const val SHARED_PREFS_FILE = "xposed_shared_prefs"
const val REMOTE_PREFS_GROUP = "settings"

// KEYS
const val KEY_IS_PLAYING = "is_playing"
/** Epoch millis when the fixed virtual-location session started; drives the "elapsed" display. */
const val KEY_FIXED_LOCATION_STARTED_AT = "fixed_location_started_at"

const val KEY_LAST_CLICKED_LOCATION = "last_clicked_location"

const val KEY_USE_ACCURACY = "use_accuracy"
const val KEY_ACCURACY  = "accuracy"

const val KEY_USE_ALTITUDE = "use_altitude"
const val KEY_ALTITUDE  = "altitude"

const val KEY_USE_RANDOMIZE  = "use_randomize"
const val KEY_RANDOMIZE_RADIUS = "randomize_radius"

const val KEY_USE_VERTICAL_ACCURACY = "use_vertical_accuracy"
const val KEY_VERTICAL_ACCURACY = "vertical_accuracy"

const val KEY_USE_MEAN_SEA_LEVEL = "use_mean_sea_level"
const val KEY_MEAN_SEA_LEVEL = "mean_sea_level"

const val KEY_USE_MEAN_SEA_LEVEL_ACCURACY = "use_mean_sea_level_accuracy"
const val KEY_MEAN_SEA_LEVEL_ACCURACY = "mean_sea_level_accuracy"

const val KEY_USE_SPEED = "use_speed"
const val KEY_SPEED = "speed"

const val KEY_USE_SPEED_ACCURACY = "use_speed_accuracy"
const val KEY_SPEED_ACCURACY = "speed_accuracy"

const val KEY_FAVORITES = "favorites"

const val KEY_TARGET_APPS = "target_apps"

const val KEY_HIDE_FAKE_LOCATION_TOAST = "hide_fake_location_toast"

const val KEY_ENABLE_BROADCAST_CONTROL = "enable_broadcast_control"
const val KEY_LANGUAGE_TAG = "language_tag"

const val KEY_ENABLE_SYSTEM_HOOKS = "enable_system_hooks"
const val KEY_ENABLE_WIFI_IDENTITY = "enable_wifi_identity"

const val KEY_THEME_OPTION = "theme_option"

const val KEY_THEME_MODE = "theme_mode"
const val KEY_MONET_COLOR = "monet_color"

const val KEY_PREDICTIVE_BACK_ENABLED = "predictive_back_enabled"

const val KEY_WIFI_SSID = "wifi_ssid"
const val KEY_WIFI_BSSID = "wifi_bssid"
const val KEY_WIFI_RSSI = "wifi_rssi"

// WALKING SIMULATION (remote; written by the manager, read by the Xposed hooks)
const val KEY_WALKING_ENABLED = "walking_enabled"
const val KEY_WALKING_PHASE = "walking_phase"
const val KEY_WALKING_ROUTE_JSON = "walking_route_json"
const val KEY_WALKING_CURRENT_LATITUDE = "walking_current_latitude"
const val KEY_WALKING_CURRENT_LONGITUDE = "walking_current_longitude"
const val KEY_WALKING_DISTANCE_TRAVELLED = "walking_distance_travelled"
const val KEY_WALKING_TOTAL_DISTANCE = "walking_total_distance"
const val KEY_WALKING_SPEED = "walking_speed"
const val KEY_WALKING_BEARING = "walking_bearing"
const val KEY_WALKING_STARTED_AT = "walking_started_at"
const val KEY_WALKING_UPDATED_AT = "walking_updated_at"
const val KEY_WALKING_ERROR_CODE = "walking_error_code"
/**
 * Session-generation id (UUID). Written when a session starts, cleared when it reaches a
 * terminal state; every session-scoped write validates it before mutating shared state so a
 * delayed stop/fail/tick from a previous generation can never clobber a newer session
 * (MockX完整功能规划.md §5.1).
 */
const val KEY_WALKING_SESSION_ID = "walking_session_id"

// Packages added/removed from module scope when system-level hooks are toggled.
// Modern libxposed uses `system` as the virtual package name for system_server.
val SYSTEM_HOOK_PACKAGES = listOf("system", "com.android.phone")

 // DEFAULT VALUES
const val DEFAULT_USE_ACCURACY = false
const val DEFAULT_ACCURACY = 0.0

const val DEFAULT_USE_ALTITUDE = false
const val DEFAULT_ALTITUDE = 0.0

const val DEFAULT_USE_RANDOMIZE = false
const val DEFAULT_RANDOMIZE_RADIUS = 0.0

const val DEFAULT_USE_VERTICAL_ACCURACY = false
const val DEFAULT_VERTICAL_ACCURACY = 0.0f

const val DEFAULT_USE_MEAN_SEA_LEVEL = false
const val DEFAULT_MEAN_SEA_LEVEL = 0.0

const val DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY = false
const val DEFAULT_MEAN_SEA_LEVEL_ACCURACY = 0.0f

const val DEFAULT_USE_SPEED = false
const val DEFAULT_SPEED = 0.0f

const val DEFAULT_USE_SPEED_ACCURACY = false
const val DEFAULT_SPEED_ACCURACY = 0.0f

const val DEFAULT_HIDE_FAKE_LOCATION_TOAST = false

const val DEFAULT_PREDICTIVE_BACK_ENABLED = true

const val DEFAULT_ENABLE_BROADCAST_CONTROL = false
const val DEFAULT_LANGUAGE_TAG = ""

const val DEFAULT_ENABLE_SYSTEM_HOOKS = false
const val DEFAULT_ENABLE_WIFI_IDENTITY = false

const val DEFAULT_THEME_MODE = 0
const val DEFAULT_MONET_COLOR = 0

const val DEFAULT_WIFI_SSID = "AndroidAP"
const val DEFAULT_WIFI_BSSID = "02:00:00:00:00:00"
const val DEFAULT_WIFI_RSSI = -60
const val MAX_WIFI_SSID_BYTES = 32
const val MIN_WIFI_RSSI = -127
const val MAX_WIFI_RSSI = 0
val MAC_ADDRESS_REGEX = Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")

// AMAP WEB SERVICE (local; manager-only, never shared with the hook side)
const val KEY_AMAP_WEB_SERVICE_KEY = "amap_web_service_key"
const val DEFAULT_AMAP_WEB_SERVICE_KEY = ""

// NOTIFICATION STYLE (local; manager-only — decides whether active sessions render as the
// Xiaomi Super Island or the Android 16 Google Live Update, or fall back to the device default)
const val KEY_ISLAND_STYLE = "island_style"
const val DEFAULT_ISLAND_STYLE = "auto"

// WALKING SIMULATION DEFAULTS
const val DEFAULT_WALKING_ENABLED = false
const val DEFAULT_WALKING_SPEED = 1.4f
/** Hard cap on route points kept in memory and in the shared-preferences JSON blob. */
const val MAX_ROUTE_POINTS = 5000
/** Distance (metres) between two coordinates treated as "the same point" when de-duplicating. */
const val ROUTE_POINT_DEDUP_EPSILON_METERS = 0.05

fun normalizeWifiSsid(rawSsid: String?): String {
    val trimmed = rawSsid?.trim().orEmpty()
    val utf8 = trimmed.toByteArray(Charsets.UTF_8)
    val isValidUtf8 = utf8.toString(Charsets.UTF_8) == trimmed
    return if (trimmed.isNotEmpty() && isValidUtf8 && utf8.size <= MAX_WIFI_SSID_BYTES) {
        trimmed
    } else {
        DEFAULT_WIFI_SSID
    }
}

// MATH & PHYS
const val PI = 3.14159265359
const val RADIUS_EARTH = 6378137.0 // Approximately Earth's radius in meters

// MAP SETTINGS
const val KEY_MAP_SOURCE = "map_source"
const val DEFAULT_MAP_SOURCE = "amap_vector"
const val KEY_TIANDITU_TOKEN = "tianditu_token"
const val DEFAULT_TIANDITU_TOKEN = ""
const val KEY_MAP_ZOOM = "map_zoom"
const val DEFAULT_MAP_ZOOM = 18.0
const val WORLD_MAP_ZOOM = 2.0
const val LOCATION_DETECTION_MAX_ATTEMPTS = 80
const val LOCATION_DETECTION_DELAY_MS = 100L

// WALKING SIMULATION TIMING
/** Interval between simulated-position ticks in the foreground walking service. */
const val WALKING_TICK_INTERVAL_MS = 1000L
/** Upper bound for compensating a single delayed tick; beyond this the advance is clamped. */
const val WALKING_MAX_CATCHUP_SECONDS = 30.0
/** A WALKING state whose updated_at is older than this is considered stale by the hooks. */
const val WALKING_STATE_STALE_MS = 15_000L

// WALKING SIMULATION MODES
/** Remote key holding [WALKING_MODE_ROUTE] or [WALKING_MODE_HOME]. */
const val KEY_WALKING_MODE = "walking_mode"
/** Follow a planned route from the real position to the pinned destination (Amap). */
const val WALKING_MODE_ROUTE = "ROUTE"
/** Move around the pinned coordinate itself, without planning a route (home walk). */
const val WALKING_MODE_HOME = "HOME"
/** Remote keys holding the home-walk anchor (the pinned coordinate) in WGS-84. */
const val KEY_WALKING_HOME_LATITUDE = "walking_home_latitude"
const val KEY_WALKING_HOME_LONGITUDE = "walking_home_longitude"
/** Lower bound (metres) of the home-walk radius around the anchor. */
const val HOME_WALK_MIN_RADIUS_METERS = 1.0
/** Upper bound (metres) of the home-walk radius around the anchor. */
const val HOME_WALK_MAX_RADIUS_METERS = 20.0

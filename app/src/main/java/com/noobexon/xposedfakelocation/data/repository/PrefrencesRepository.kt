// PreferencesRepository.kt
package com.noobexon.xposedfakelocation.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_AMAP_WEB_SERVICE_KEY
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_BROADCAST_CONTROL
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_WIFI_IDENTITY
import com.noobexon.xposedfakelocation.data.DEFAULT_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.DEFAULT_ISLAND_STYLE
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_SOURCE
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_MONET_COLOR
import com.noobexon.xposedfakelocation.data.DEFAULT_PREDICTIVE_BACK_ENABLED
import com.noobexon.xposedfakelocation.data.DEFAULT_THEME_MODE
import com.noobexon.xposedfakelocation.data.DEFAULT_TIANDITU_TOKEN
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_RANDOMIZE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_WALKING_ENABLED
import com.noobexon.xposedfakelocation.data.DEFAULT_WALKING_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_BSSID
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_SSID
import com.noobexon.xposedfakelocation.data.KEY_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_ALTITUDE
import com.noobexon.xposedfakelocation.data.KEY_AMAP_WEB_SERVICE_KEY
import com.noobexon.xposedfakelocation.data.KEY_ENABLE_BROADCAST_CONTROL
import com.noobexon.xposedfakelocation.data.KEY_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.KEY_ENABLE_WIFI_IDENTITY
import com.noobexon.xposedfakelocation.data.KEY_FAVORITES
import com.noobexon.xposedfakelocation.data.KEY_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
import com.noobexon.xposedfakelocation.data.KEY_FIXED_LOCATION_STARTED_AT
import com.noobexon.xposedfakelocation.data.KEY_ISLAND_STYLE
import com.noobexon.xposedfakelocation.data.KEY_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.KEY_LAST_CLICKED_LOCATION
import com.noobexon.xposedfakelocation.data.KEY_MAP_SOURCE
import com.noobexon.xposedfakelocation.data.KEY_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.KEY_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.KEY_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.KEY_SPEED
import com.noobexon.xposedfakelocation.data.KEY_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_TARGET_APPS
import com.noobexon.xposedfakelocation.data.KEY_THEME_MODE
import com.noobexon.xposedfakelocation.data.KEY_THEME_OPTION
import com.noobexon.xposedfakelocation.data.KEY_MONET_COLOR
import com.noobexon.xposedfakelocation.data.KEY_PREDICTIVE_BACK_ENABLED
import com.noobexon.xposedfakelocation.data.KEY_TIANDITU_TOKEN
import com.noobexon.xposedfakelocation.data.KEY_USE_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_USE_ALTITUDE
import com.noobexon.xposedfakelocation.data.KEY_USE_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.KEY_USE_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_USE_RANDOMIZE
import com.noobexon.xposedfakelocation.data.KEY_USE_SPEED
import com.noobexon.xposedfakelocation.data.KEY_USE_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_USE_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_WIFI_BSSID
import com.noobexon.xposedfakelocation.data.KEY_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.KEY_WIFI_SSID
import com.noobexon.xposedfakelocation.data.KEY_WALKING_BEARING
import com.noobexon.xposedfakelocation.data.KEY_WALKING_CURRENT_LATITUDE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_CURRENT_LONGITUDE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_DISTANCE_TRAVELLED
import com.noobexon.xposedfakelocation.data.KEY_WALKING_ENABLED
import com.noobexon.xposedfakelocation.data.KEY_WALKING_ERROR_CODE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_HOME_LATITUDE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_HOME_LONGITUDE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_MODE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_PHASE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_ROUTE_JSON
import com.noobexon.xposedfakelocation.data.KEY_WALKING_SESSION_ID
import com.noobexon.xposedfakelocation.data.KEY_WALKING_SPEED
import com.noobexon.xposedfakelocation.data.KEY_WALKING_STARTED_AT
import com.noobexon.xposedfakelocation.data.KEY_WALKING_TOTAL_DISTANCE
import com.noobexon.xposedfakelocation.data.KEY_WALKING_UPDATED_AT
import com.noobexon.xposedfakelocation.data.MAC_ADDRESS_REGEX
import com.noobexon.xposedfakelocation.data.MAX_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.MIN_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.REMOTE_PREFS_GROUP
import com.noobexon.xposedfakelocation.data.SHARED_PREFS_FILE
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.LastClickedLocation
import com.noobexon.xposedfakelocation.data.normalizeWifiSsid
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import com.noobexon.xposedfakelocation.manager.route.WalkingRoute
import com.noobexon.xposedfakelocation.manager.route.WalkingRouteCodec
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/**
 * Single-source-of-truth preferences store.
 *
 * Each setting lives in exactly one place:
 *  - Hook-shared settings (read by the Xposed module) live in the LSPosed remote
 *    preferences exposed through [App.service]. They are only available while the
 *    XposedService is bound; when it isn't, reads fall back to defaults and writes
 *    are dropped (the UI is gated behind a bound service anyway).
 *  - Manager-only settings (language, favorites, broadcast control) live in a local
 *    [SharedPreferences] file so they are always available, including at startup and
 *    when the module is disabled.
 *
 * Doubles are encoded as raw long bits because [SharedPreferences] has no putDouble,
 * keeping read/write symmetry with the hook-side PreferencesUtil.
 */
class PreferencesRepository(context: Context) {
    private val tag = "PreferencesRepository"

    private val gson = Gson()

    private val localPrefs: SharedPreferences =
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)

    private fun remotePrefs(): SharedPreferences? =
        App.service?.getRemotePreferences(REMOTE_PREFS_GROUP)

    // region Flow helpers

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> remoteFlow(key: String, default: T, read: (SharedPreferences) -> T): Flow<T> =
        App.serviceState.flatMapLatest { service ->
            val prefs = service?.getRemotePreferences(REMOTE_PREFS_GROUP)
            if (prefs == null) {
                flowOf(default)
            } else {
                prefsChangeFlow(prefs, key) { read(prefs) }
            }
        }

    private fun <T> localFlow(key: String, read: (SharedPreferences) -> T): Flow<T> =
        prefsChangeFlow(localPrefs, key) { read(localPrefs) }

    private fun <T> prefsChangeFlow(
        prefs: SharedPreferences,
        key: String,
        read: () -> T
    ): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null || changedKey == key) trySend(read())
        }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // endregion

    // region Write helpers

    private inline fun editRemote(action: SharedPreferences.Editor.() -> Unit) {
        val prefs = remotePrefs()
        if (prefs == null) {
            Log.w(tag, "Remote preferences unavailable (service not bound); write skipped")
            return
        }
        prefs.edit(commit = true, action = action)
    }

    private inline fun editLocal(action: SharedPreferences.Editor.() -> Unit) {
        localPrefs.edit(action = action)
    }

    private fun readRemoteDouble(key: String, default: Double): Double {
        val prefs = remotePrefs() ?: return default
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    // endregion

    // region Is Playing (remote)
    fun getIsPlayingFlow(): Flow<Boolean> = remoteFlow(KEY_IS_PLAYING, false) { it.getBoolean(KEY_IS_PLAYING, false) }
    suspend fun saveIsPlaying(isPlaying: Boolean) = editRemote { putBoolean(KEY_IS_PLAYING, isPlaying) }
    fun getIsPlaying(): Boolean = remotePrefs()?.getBoolean(KEY_IS_PLAYING, false) ?: false
    // endregion

    // region Fixed Location Started At (remote)
    fun getFixedLocationStartedAt(): Long = remotePrefs()?.getLong(KEY_FIXED_LOCATION_STARTED_AT, 0L) ?: 0L
    suspend fun saveFixedLocationStartedAt(epochMillis: Long) =
        editRemote { putLong(KEY_FIXED_LOCATION_STARTED_AT, epochMillis) }
    // endregion

    // region Last Clicked Location (remote)
    fun getLastClickedLocationFlow(): Flow<LastClickedLocation?> =
        remoteFlow<LastClickedLocation?>(KEY_LAST_CLICKED_LOCATION, null) {
            parseLastClickedLocation(it.getString(KEY_LAST_CLICKED_LOCATION, null))
        }

    suspend fun saveLastClickedLocation(latitude: Double, longitude: Double) {
        val json = gson.toJson(LastClickedLocation(latitude, longitude))
        editRemote { putString(KEY_LAST_CLICKED_LOCATION, json) }
    }

    fun getLastClickedLocation(): LastClickedLocation? =
        parseLastClickedLocation(remotePrefs()?.getString(KEY_LAST_CLICKED_LOCATION, null))

    suspend fun clearLastClickedLocation() {
        editRemote { remove(KEY_LAST_CLICKED_LOCATION) }
        saveIsPlaying(false)
        Log.d(tag, "Cleared 'LastClickedLocation' and set 'IsPlaying' to false")
    }

    private fun parseLastClickedLocation(json: String?): LastClickedLocation? {
        if (json == null) return null
        return try {
            gson.fromJson(json, LastClickedLocation::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "Error parsing LastClickedLocation: ${e.message}")
            null
        }
    }
    // endregion

    // region Use Accuracy / Accuracy (remote)
    fun getUseAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) { it.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) }
    suspend fun saveUseAccuracy(useAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_ACCURACY, useAccuracy) }
    fun getUseAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) ?: DEFAULT_USE_ACCURACY

    fun getAccuracyFlow(): Flow<Double> = remoteFlow(KEY_ACCURACY, DEFAULT_ACCURACY) { readRemoteDouble(KEY_ACCURACY, DEFAULT_ACCURACY) }
    suspend fun saveAccuracy(accuracy: Double) = editRemote { putLong(KEY_ACCURACY, java.lang.Double.doubleToRawLongBits(accuracy)) }
    fun getAccuracy(): Double = readRemoteDouble(KEY_ACCURACY, DEFAULT_ACCURACY)
    // endregion

    // region Use Altitude / Altitude (remote)
    fun getUseAltitudeFlow(): Flow<Boolean> = remoteFlow(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) { it.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) }
    suspend fun saveUseAltitude(useAltitude: Boolean) = editRemote { putBoolean(KEY_USE_ALTITUDE, useAltitude) }
    fun getUseAltitude(): Boolean = remotePrefs()?.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) ?: DEFAULT_USE_ALTITUDE

    fun getAltitudeFlow(): Flow<Double> = remoteFlow(KEY_ALTITUDE, DEFAULT_ALTITUDE) { readRemoteDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE) }
    suspend fun saveAltitude(altitude: Double) = editRemote { putLong(KEY_ALTITUDE, java.lang.Double.doubleToRawLongBits(altitude)) }
    fun getAltitude(): Double = readRemoteDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE)
    // endregion

    // region Use Randomize / Randomize Radius (remote)
    fun getUseRandomizeFlow(): Flow<Boolean> = remoteFlow(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) { it.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) }
    suspend fun saveUseRandomize(randomize: Boolean) = editRemote { putBoolean(KEY_USE_RANDOMIZE, randomize) }
    fun getUseRandomize(): Boolean = remotePrefs()?.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) ?: DEFAULT_USE_RANDOMIZE

    fun getRandomizeRadiusFlow(): Flow<Double> = remoteFlow(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) { readRemoteDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) }
    suspend fun saveRandomizeRadius(radius: Double) = editRemote { putLong(KEY_RANDOMIZE_RADIUS, java.lang.Double.doubleToRawLongBits(radius)) }
    fun getRandomizeRadius(): Double = readRemoteDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS)
    // endregion

    // region Vertical Accuracy (remote)
    fun getUseVerticalAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) { it.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) }
    suspend fun saveUseVerticalAccuracy(useVerticalAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_VERTICAL_ACCURACY, useVerticalAccuracy) }
    fun getUseVerticalAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) ?: DEFAULT_USE_VERTICAL_ACCURACY

    fun getVerticalAccuracyFlow(): Flow<Float> = remoteFlow(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) { it.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) }
    suspend fun saveVerticalAccuracy(verticalAccuracy: Float) = editRemote { putFloat(KEY_VERTICAL_ACCURACY, verticalAccuracy) }
    fun getVerticalAccuracy(): Float = remotePrefs()?.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) ?: DEFAULT_VERTICAL_ACCURACY
    // endregion

    // region Mean Sea Level (remote)
    fun getUseMeanSeaLevelFlow(): Flow<Boolean> = remoteFlow(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) }
    suspend fun saveUseMeanSeaLevel(useMeanSeaLevel: Boolean) = editRemote { putBoolean(KEY_USE_MEAN_SEA_LEVEL, useMeanSeaLevel) }
    fun getUseMeanSeaLevel(): Boolean = remotePrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) ?: DEFAULT_USE_MEAN_SEA_LEVEL

    fun getMeanSeaLevelFlow(): Flow<Double> = remoteFlow(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) { readRemoteDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) }
    suspend fun saveMeanSeaLevel(meanSeaLevel: Double) = editRemote { putLong(KEY_MEAN_SEA_LEVEL, java.lang.Double.doubleToRawLongBits(meanSeaLevel)) }
    fun getMeanSeaLevel(): Double = readRemoteDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL)

    fun getUseMeanSeaLevelAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveUseMeanSeaLevelAccuracy(useMeanSeaLevelAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, useMeanSeaLevelAccuracy) }
    fun getUseMeanSeaLevelAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY

    fun getMeanSeaLevelAccuracyFlow(): Flow<Float> = remoteFlow(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) { it.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveMeanSeaLevelAccuracy(meanSeaLevelAccuracy: Float) = editRemote { putFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, meanSeaLevelAccuracy) }
    fun getMeanSeaLevelAccuracy(): Float = remotePrefs()?.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
    // endregion

    // region Speed (remote)
    fun getUseSpeedFlow(): Flow<Boolean> = remoteFlow(KEY_USE_SPEED, DEFAULT_USE_SPEED) { it.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) }
    suspend fun saveUseSpeed(useSpeed: Boolean) = editRemote { putBoolean(KEY_USE_SPEED, useSpeed) }
    fun getUseSpeed(): Boolean = remotePrefs()?.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) ?: DEFAULT_USE_SPEED

    fun getSpeedFlow(): Flow<Float> = remoteFlow(KEY_SPEED, DEFAULT_SPEED) { it.getFloat(KEY_SPEED, DEFAULT_SPEED) }
    suspend fun saveSpeed(speed: Float) = editRemote { putFloat(KEY_SPEED, speed) }
    fun getSpeed(): Float = remotePrefs()?.getFloat(KEY_SPEED, DEFAULT_SPEED) ?: DEFAULT_SPEED

    fun getUseSpeedAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) { it.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) }
    suspend fun saveUseSpeedAccuracy(useSpeedAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_SPEED_ACCURACY, useSpeedAccuracy) }
    fun getUseSpeedAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) ?: DEFAULT_USE_SPEED_ACCURACY

    fun getSpeedAccuracyFlow(): Flow<Float> = remoteFlow(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) { it.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) }
    suspend fun saveSpeedAccuracy(speedAccuracy: Float) = editRemote { putFloat(KEY_SPEED_ACCURACY, speedAccuracy) }
    fun getSpeedAccuracy(): Float = remotePrefs()?.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) ?: DEFAULT_SPEED_ACCURACY
    // endregion

    // region Enable System-Level Hooks (remote)
    fun getEnableSystemHooksFlow(): Flow<Boolean> = remoteFlow(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) { it.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) }
    suspend fun saveEnableSystemHooks(enabled: Boolean) = editRemote { putBoolean(KEY_ENABLE_SYSTEM_HOOKS, enabled) }
    fun getEnableSystemHooks(): Boolean = remotePrefs()?.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) ?: DEFAULT_ENABLE_SYSTEM_HOOKS
    // endregion

    // region Enable Wi-Fi Identity Hooks (remote)
    fun getEnableWifiIdentityFlow(): Flow<Boolean> =
        remoteFlow(KEY_ENABLE_WIFI_IDENTITY, DEFAULT_ENABLE_WIFI_IDENTITY) {
            it.getBoolean(KEY_ENABLE_WIFI_IDENTITY, DEFAULT_ENABLE_WIFI_IDENTITY)
        }

    suspend fun saveEnableWifiIdentity(enabled: Boolean) =
        editRemote { putBoolean(KEY_ENABLE_WIFI_IDENTITY, enabled) }

    fun getEnableWifiIdentity(): Boolean =
        remotePrefs()?.getBoolean(KEY_ENABLE_WIFI_IDENTITY, DEFAULT_ENABLE_WIFI_IDENTITY)
            ?: DEFAULT_ENABLE_WIFI_IDENTITY
    // endregion

    // region Hide Fake Location Toast (remote)
    fun getHideFakeLocationToastFlow(): Flow<Boolean> = remoteFlow(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) { it.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) }
    suspend fun saveHideFakeLocationToast(hideFakeLocationToast: Boolean) = editRemote { putBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, hideFakeLocationToast) }
    fun getHideFakeLocationToast(): Boolean = remotePrefs()?.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) ?: DEFAULT_HIDE_FAKE_LOCATION_TOAST
    // endregion

    // region Wi-Fi identity (remote)
    fun getWifiSsidFlow(): Flow<String> =
        remoteFlow(KEY_WIFI_SSID, DEFAULT_WIFI_SSID) {
            normalizeWifiSsid(it.getString(KEY_WIFI_SSID, DEFAULT_WIFI_SSID))
        }

    suspend fun saveWifiSsid(ssid: String) =
        editRemote { putString(KEY_WIFI_SSID, normalizeWifiSsid(ssid)) }

    fun getWifiBssidFlow(): Flow<String> =
        remoteFlow(KEY_WIFI_BSSID, DEFAULT_WIFI_BSSID) {
            normalizeWifiBssid(it.getString(KEY_WIFI_BSSID, DEFAULT_WIFI_BSSID).orEmpty())
        }

    suspend fun saveWifiBssid(bssid: String) =
        editRemote { putString(KEY_WIFI_BSSID, normalizeWifiBssid(bssid)) }

    fun getWifiRssiFlow(): Flow<Int> =
        remoteFlow(KEY_WIFI_RSSI, DEFAULT_WIFI_RSSI) {
            it.getInt(KEY_WIFI_RSSI, DEFAULT_WIFI_RSSI).coerceIn(MIN_WIFI_RSSI, MAX_WIFI_RSSI)
        }

    suspend fun saveWifiRssi(rssi: Int) =
        editRemote { putInt(KEY_WIFI_RSSI, rssi.coerceIn(MIN_WIFI_RSSI, MAX_WIFI_RSSI)) }

    private fun normalizeWifiBssid(bssid: String): String {
        val trimmed = bssid.trim()
        return if (MAC_ADDRESS_REGEX.matches(trimmed)) {
            trimmed.uppercase(Locale.US)
        } else {
            DEFAULT_WIFI_BSSID
        }
    }
    // endregion

    // region Target Apps (remote)
    fun getTargetAppsFlow(): Flow<Set<String>> =
        remoteFlow(KEY_TARGET_APPS, emptySet()) { parseTargetApps(it.getString(KEY_TARGET_APPS, null)) }

    suspend fun saveTargetApps(packageNames: Set<String>) {
        val normalized = packageNames
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val json = gson.toJson(normalized)
        editRemote { putString(KEY_TARGET_APPS, json) }
    }

    private fun parseTargetApps(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type).toSet()
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "Error parsing target apps: ${e.message}")
            emptySet()
        }
    }
    // endregion

    // region Favorites (local)
    fun getFavoritesFlow(): Flow<List<FavoriteLocation>> =
        localFlow(KEY_FAVORITES) { parseFavorites(it.getString(KEY_FAVORITES, null)) }

    suspend fun addFavorite(favorite: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply { add(favorite) }
        saveFavorites(updated)
        Log.d(tag, "Added Favorite: $favorite")
    }

    suspend fun removeFavorite(favorite: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply { remove(favorite) }
        saveFavorites(updated)
        Log.d(tag, "Removed Favorite: $favorite")
    }

    suspend fun updateFavorite(old: FavoriteLocation, new: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply {
            val index = indexOf(old)
            if (index != -1) set(index, new)
        }
        saveFavorites(updated)
        Log.d(tag, "Updated Favorite: $old -> $new")
    }

    fun getFavorites(): List<FavoriteLocation> = parseFavorites(localPrefs.getString(KEY_FAVORITES, null))

    private fun saveFavorites(favorites: List<FavoriteLocation>) {
        val json = gson.toJson(favorites)
        editLocal { putString(KEY_FAVORITES, json) }
    }

    private fun parseFavorites(json: String?): List<FavoriteLocation> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<FavoriteLocation>>() {}.type
            gson.fromJson(json, type)
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "Error parsing Favorites: ${e.message}")
            emptyList()
        }
    }
    // endregion

    // region Map Zoom (local)
    fun getMapZoom(): Double {
        val bits = localPrefs.getLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(DEFAULT_MAP_ZOOM))
        return java.lang.Double.longBitsToDouble(bits)
    }

    fun saveMapZoom(zoom: Double) =
        editLocal { putLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(zoom)) }
    // endregion

    // region Broadcast Control (local)
    fun getEnableBroadcastControlFlow(): Flow<Boolean> = localFlow(KEY_ENABLE_BROADCAST_CONTROL) { it.getBoolean(KEY_ENABLE_BROADCAST_CONTROL, DEFAULT_ENABLE_BROADCAST_CONTROL) }
    suspend fun saveEnableBroadcastControl(enable: Boolean) = editLocal { putBoolean(KEY_ENABLE_BROADCAST_CONTROL, enable) }
    // endregion

    // region Language (local; shared with LocaleController)
    fun getLanguageTagFlow(): Flow<String> = localFlow(KEY_LANGUAGE_TAG) { it.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG }
    suspend fun saveLanguageTag(languageTag: String) = editLocal { putString(KEY_LANGUAGE_TAG, languageTag) }
    // endregion

    // region Theme (local)
    fun getThemeModeFlow(): Flow<Int> = localFlow(KEY_THEME_MODE) { prefs ->
        if (prefs.contains(KEY_THEME_MODE)) {
            prefs.getInt(KEY_THEME_MODE, DEFAULT_THEME_MODE)
        } else {
            // Migrate the pre-miuix string tag on first read after upgrading.
            when (prefs.getString(KEY_THEME_OPTION, null)) {
                "light" -> 1
                "dark" -> 2
                else -> 0
            }
        }
    }
    suspend fun saveThemeMode(modeId: Int) = editLocal { putInt(KEY_THEME_MODE, modeId) }

    fun getMonetColorFlow(): Flow<Int> = localFlow(KEY_MONET_COLOR) { it.getInt(KEY_MONET_COLOR, DEFAULT_MONET_COLOR) }
    suspend fun saveMonetColor(colorId: Int) = editLocal { putInt(KEY_MONET_COLOR, colorId) }

    fun getPredictiveBackFlow(): Flow<Boolean> = localFlow(KEY_PREDICTIVE_BACK_ENABLED) { it.getBoolean(KEY_PREDICTIVE_BACK_ENABLED, DEFAULT_PREDICTIVE_BACK_ENABLED) }
    suspend fun savePredictiveBack(enabled: Boolean) = editLocal { putBoolean(KEY_PREDICTIVE_BACK_ENABLED, enabled) }
    // endregion

    // region Map Source (local)
    fun getMapSourceOptionFlow(): Flow<String> = localFlow(KEY_MAP_SOURCE) { it.getString(KEY_MAP_SOURCE, DEFAULT_MAP_SOURCE) ?: DEFAULT_MAP_SOURCE }
    suspend fun saveMapSourceOption(sourceTag: String) = editLocal { putString(KEY_MAP_SOURCE, sourceTag) }
    fun getMapSourceOption(): String = localPrefs.getString(KEY_MAP_SOURCE, DEFAULT_MAP_SOURCE) ?: DEFAULT_MAP_SOURCE
    // endregion

    // region TianDiTu Token (local)
    fun getTianDiTuTokenFlow(): Flow<String> = localFlow(KEY_TIANDITU_TOKEN) { it.getString(KEY_TIANDITU_TOKEN, DEFAULT_TIANDITU_TOKEN) ?: DEFAULT_TIANDITU_TOKEN }
    suspend fun saveTianDiTuToken(token: String) = editLocal { putString(KEY_TIANDITU_TOKEN, token.trim()) }
    fun getTianDiTuToken(): String = localPrefs.getString(KEY_TIANDITU_TOKEN, DEFAULT_TIANDITU_TOKEN) ?: DEFAULT_TIANDITU_TOKEN
    // endregion

    // region Amap Web Service Key (local; manager-only, never read by the hooks)
    fun getAmapWebServiceKeyFlow(): Flow<String> = localFlow(KEY_AMAP_WEB_SERVICE_KEY) { it.getString(KEY_AMAP_WEB_SERVICE_KEY, DEFAULT_AMAP_WEB_SERVICE_KEY) ?: DEFAULT_AMAP_WEB_SERVICE_KEY }
    suspend fun saveAmapWebServiceKey(value: String) = editLocal { putString(KEY_AMAP_WEB_SERVICE_KEY, value.trim()) }
    fun getAmapWebServiceKey(): String = localPrefs.getString(KEY_AMAP_WEB_SERVICE_KEY, DEFAULT_AMAP_WEB_SERVICE_KEY) ?: DEFAULT_AMAP_WEB_SERVICE_KEY

    fun isAmapWebServiceKeyConfigured(): Boolean = getAmapWebServiceKey().isNotBlank()
    // endregion

    // region Island style (local; manager-only — rendering choice for active-session notifications)
    fun getIslandStyleFlow(): Flow<String> =
        localFlow(KEY_ISLAND_STYLE) { it.getString(KEY_ISLAND_STYLE, DEFAULT_ISLAND_STYLE) ?: DEFAULT_ISLAND_STYLE }

    suspend fun saveIslandStyle(tag: String) = editLocal { putString(KEY_ISLAND_STYLE, tag) }

    /** Synchronous read used by the notification builders when a session renders. */
    fun getIslandStyle(): String =
        localPrefs.getString(KEY_ISLAND_STYLE, DEFAULT_ISLAND_STYLE) ?: DEFAULT_ISLAND_STYLE
    // endregion

    // region Walking simulation (remote; hooks read the dynamic position from these keys)
    fun isRemotePreferencesAvailable(): Boolean = remotePrefs() != null

    fun getWalkingEnabledFlow(): Flow<Boolean> = remoteFlow(KEY_WALKING_ENABLED, DEFAULT_WALKING_ENABLED) { it.getBoolean(KEY_WALKING_ENABLED, DEFAULT_WALKING_ENABLED) }
    fun getWalkingEnabled(): Boolean = remotePrefs()?.getBoolean(KEY_WALKING_ENABLED, DEFAULT_WALKING_ENABLED) ?: DEFAULT_WALKING_ENABLED

    fun getWalkingPhaseFlow(): Flow<WalkingPhase> = remoteFlow(KEY_WALKING_PHASE, WalkingPhase.IDLE) { WalkingPhase.fromName(it.getString(KEY_WALKING_PHASE, null)) }
    fun getWalkingPhase(): WalkingPhase = WalkingPhase.fromName(remotePrefs()?.getString(KEY_WALKING_PHASE, null))

    /** Mode of the current (or last prepared) session: planned route or walk-around-the-pin. */
    fun getWalkingModeFlow(): Flow<WalkingMode> = remoteFlow(KEY_WALKING_MODE, WalkingMode.ROUTE) { WalkingMode.fromName(it.getString(KEY_WALKING_MODE, null)) }
    fun getWalkingMode(): WalkingMode = WalkingMode.fromName(remotePrefs()?.getString(KEY_WALKING_MODE, null))

    /**
     * Anchor of the home-walk session (the pinned coordinate), or `null` when no home session is
     * prepared. Missing or non-finite entries collapse to `null` so a torn-down session can never
     * resurrect a bogus anchor.
     */
    fun getWalkingHomeAnchor(): Coordinate? = Coordinate(
        readRemoteDouble(KEY_WALKING_HOME_LATITUDE, Double.NaN),
        readRemoteDouble(KEY_WALKING_HOME_LONGITUDE, Double.NaN),
    ).takeIf { it.isValid() }

    fun getWalkingRouteJsonFlow(): Flow<String?> = remoteFlow<String?>(KEY_WALKING_ROUTE_JSON, null) { it.getString(KEY_WALKING_ROUTE_JSON, null) }
    fun getWalkingRouteJson(): String? = remotePrefs()?.getString(KEY_WALKING_ROUTE_JSON, null)

    fun getWalkingDistanceTravelledFlow(): Flow<Double> = remoteFlow(KEY_WALKING_DISTANCE_TRAVELLED, 0.0) { readRemoteDouble(KEY_WALKING_DISTANCE_TRAVELLED, 0.0) }
    fun getWalkingTotalDistanceFlow(): Flow<Double> = remoteFlow(KEY_WALKING_TOTAL_DISTANCE, 0.0) { readRemoteDouble(KEY_WALKING_TOTAL_DISTANCE, 0.0) }

    /** Synchronous reads used by the walking service when restoring a session. */
    fun getWalkingDistanceTravelled(): Double = readRemoteDouble(KEY_WALKING_DISTANCE_TRAVELLED, 0.0)
    fun getWalkingTotalDistance(): Double = readRemoteDouble(KEY_WALKING_TOTAL_DISTANCE, 0.0)

    fun getWalkingCurrentLatitudeFlow(): Flow<Double> = remoteFlow(KEY_WALKING_CURRENT_LATITUDE, 0.0) { readRemoteDouble(KEY_WALKING_CURRENT_LATITUDE, 0.0) }
    fun getWalkingCurrentLongitudeFlow(): Flow<Double> = remoteFlow(KEY_WALKING_CURRENT_LONGITUDE, 0.0) { readRemoteDouble(KEY_WALKING_CURRENT_LONGITUDE, 0.0) }

    fun getWalkingErrorCodeFlow(): Flow<String> = remoteFlow(KEY_WALKING_ERROR_CODE, "") { it.getString(KEY_WALKING_ERROR_CODE, "") ?: "" }

    /**
     * Session-generation id of the live walking session, or `null` when no session is active
     * (terminal states clear it). Used by the walking service to drop writes from a stale
     * generation (MockX完整功能规划.md §5.1).
     */
    fun getWalkingSessionId(): String? = remotePrefs()?.getString(KEY_WALKING_SESSION_ID, null)

    /**
     * Current walking speed in m/s. Read by the service on every tick so the user can change
     * the pace mid-walk without restarting the session.
     */
    fun getWalkingSpeed(): Float = remotePrefs()?.getFloat(KEY_WALKING_SPEED, DEFAULT_WALKING_SPEED) ?: DEFAULT_WALKING_SPEED

    suspend fun saveWalkingSpeed(speed: Float) = editRemote { putFloat(KEY_WALKING_SPEED, speed) }

    /**
     * Writes the whole prepared-session snapshot: route, totals, and the WALKING phase. Also
     * turns the fixed-location spoof on ([KEY_IS_PLAYING] = true) so the hooks actually apply
     * the dynamic coordinates. One [commit] keeps the hook-visible state consistent.
     */
    suspend fun startWalkingSession(route: WalkingRoute, speedMetersPerSecond: Float) {
        val now = System.currentTimeMillis()
        editRemote {
            putString(KEY_WALKING_ROUTE_JSON, WalkingRouteCodec.encode(route))
            putString(KEY_WALKING_MODE, WalkingMode.ROUTE.name)
            remove(KEY_WALKING_HOME_LATITUDE)
            remove(KEY_WALKING_HOME_LONGITUDE)
            putString(KEY_WALKING_SESSION_ID, UUID.randomUUID().toString())
            putBoolean(KEY_WALKING_ENABLED, true)
            putString(KEY_WALKING_PHASE, WalkingPhase.WALKING.name)
            putDoubleBits(KEY_WALKING_CURRENT_LATITUDE, route.origin.latitude)
            putDoubleBits(KEY_WALKING_CURRENT_LONGITUDE, route.origin.longitude)
            putDoubleBits(KEY_WALKING_DISTANCE_TRAVELLED, 0.0)
            putDoubleBits(KEY_WALKING_TOTAL_DISTANCE, route.totalDistanceMeters)
            putFloat(KEY_WALKING_SPEED, speedMetersPerSecond)
            putBoolean(KEY_IS_PLAYING, true)
            putLong(KEY_WALKING_STARTED_AT, now)
            putLong(KEY_WALKING_UPDATED_AT, now)
            putString(KEY_WALKING_ERROR_CODE, "")
        }
    }

    /**
     * Starts a home-walk session: the service walks around [anchor] itself instead of following a
     * planned route, so no route JSON and no total distance are written. One [commit] keeps the
     * hook-visible state consistent.
     */
    suspend fun startHomeWalkingSession(anchor: Coordinate, speedMetersPerSecond: Float) {
        val now = System.currentTimeMillis()
        editRemote {
            remove(KEY_WALKING_ROUTE_JSON)
            putString(KEY_WALKING_MODE, WalkingMode.HOME.name)
            putDoubleBits(KEY_WALKING_HOME_LATITUDE, anchor.latitude)
            putDoubleBits(KEY_WALKING_HOME_LONGITUDE, anchor.longitude)
            putString(KEY_WALKING_SESSION_ID, UUID.randomUUID().toString())
            putBoolean(KEY_WALKING_ENABLED, true)
            putString(KEY_WALKING_PHASE, WalkingPhase.WALKING.name)
            putDoubleBits(KEY_WALKING_CURRENT_LATITUDE, anchor.latitude)
            putDoubleBits(KEY_WALKING_CURRENT_LONGITUDE, anchor.longitude)
            putDoubleBits(KEY_WALKING_DISTANCE_TRAVELLED, 0.0)
            remove(KEY_WALKING_TOTAL_DISTANCE)
            putFloat(KEY_WALKING_SPEED, speedMetersPerSecond)
            putBoolean(KEY_IS_PLAYING, true)
            putLong(KEY_WALKING_STARTED_AT, now)
            putLong(KEY_WALKING_UPDATED_AT, now)
            putString(KEY_WALKING_ERROR_CODE, "")
        }
    }

    /** Per-tick dynamic-position write. High-frequency; must stay cheap and atomic. */
    suspend fun updateWalkingTick(
        latitude: Double,
        longitude: Double,
        speedMetersPerSecond: Float,
        bearingDegrees: Float,
        distanceTravelledMeters: Double,
        updatedAtEpochMillis: Long,
    ) {
        editRemote {
            putDoubleBits(KEY_WALKING_CURRENT_LATITUDE, latitude)
            putDoubleBits(KEY_WALKING_CURRENT_LONGITUDE, longitude)
            putFloat(KEY_WALKING_SPEED, speedMetersPerSecond)
            putFloat(KEY_WALKING_BEARING, bearingDegrees)
            putDoubleBits(KEY_WALKING_DISTANCE_TRAVELLED, distanceTravelledMeters)
            putLong(KEY_WALKING_UPDATED_AT, updatedAtEpochMillis)
        }
    }

    suspend fun pauseWalkingSession() {
        editRemote {
            putString(KEY_WALKING_PHASE, WalkingPhase.PAUSED.name)
            putLong(KEY_WALKING_UPDATED_AT, System.currentTimeMillis())
        }
    }

    suspend fun resumeWalkingSession() {
        editRemote {
            putString(KEY_WALKING_PHASE, WalkingPhase.WALKING.name)
            putLong(KEY_WALKING_UPDATED_AT, System.currentTimeMillis())
        }
    }

    suspend fun markWalkingArrived(latitude: Double, longitude: Double) {
        editRemote {
            putString(KEY_WALKING_PHASE, WalkingPhase.ARRIVED.name)
            putDoubleBits(KEY_WALKING_CURRENT_LATITUDE, latitude)
            putDoubleBits(KEY_WALKING_CURRENT_LONGITUDE, longitude)
            putLong(KEY_WALKING_UPDATED_AT, System.currentTimeMillis())
        }
    }

    /**
     * Tears the session down the same way the fixed-location flow does: dynamic state cleared,
     * spoof switched off. The destination marker (last_clicked_location) is intentionally kept
     * so the user can re-plan from the same place.
     */
    suspend fun stopWalkingSession() {
        editRemote {
            putBoolean(KEY_WALKING_ENABLED, false)
            putString(KEY_WALKING_PHASE, WalkingPhase.IDLE.name)
            putString(KEY_WALKING_ERROR_CODE, "")
            remove(KEY_WALKING_MODE)
            remove(KEY_WALKING_SESSION_ID)
            remove(KEY_WALKING_ROUTE_JSON)
            remove(KEY_WALKING_HOME_LATITUDE)
            remove(KEY_WALKING_HOME_LONGITUDE)
            remove(KEY_WALKING_CURRENT_LATITUDE)
            remove(KEY_WALKING_CURRENT_LONGITUDE)
            remove(KEY_WALKING_DISTANCE_TRAVELLED)
            remove(KEY_WALKING_TOTAL_DISTANCE)
            remove(KEY_WALKING_SPEED)
            remove(KEY_WALKING_BEARING)
            remove(KEY_WALKING_STARTED_AT)
            remove(KEY_WALKING_UPDATED_AT)
            putBoolean(KEY_IS_PLAYING, false)
        }
    }

    suspend fun markWalkingFailed(errorCode: String) {
        editRemote {
            putBoolean(KEY_WALKING_ENABLED, false)
            putString(KEY_WALKING_PHASE, WalkingPhase.FAILED.name)
            putString(KEY_WALKING_ERROR_CODE, errorCode)
            remove(KEY_WALKING_MODE)
            remove(KEY_WALKING_SESSION_ID)
            remove(KEY_WALKING_HOME_LATITUDE)
            remove(KEY_WALKING_HOME_LONGITUDE)
            putBoolean(KEY_IS_PLAYING, false)
        }
    }

    /** Preview-only write: stores the planned route and moves the shared session to READY. */
    suspend fun savePreparedWalkingRoute(route: WalkingRoute) {
        editRemote {
            putString(KEY_WALKING_ROUTE_JSON, WalkingRouteCodec.encode(route))
            putDoubleBits(KEY_WALKING_TOTAL_DISTANCE, route.totalDistanceMeters)
            // READY is a preview state with no live session; drop any stale generation id so
            // late writes from a previous session cannot sneak past the session guard.
            remove(KEY_WALKING_SESSION_ID)
            putString(KEY_WALKING_PHASE, WalkingPhase.READY.name)
        }
    }

    suspend fun clearWalkingRoute() {
        editRemote {
            remove(KEY_WALKING_ROUTE_JSON)
            remove(KEY_WALKING_SESSION_ID)
            putString(KEY_WALKING_PHASE, WalkingPhase.IDLE.name)
        }
    }
    // endregion

    private fun SharedPreferences.Editor.putDoubleBits(key: String, value: Double) {
        putLong(key, java.lang.Double.doubleToRawLongBits(value))
    }

}

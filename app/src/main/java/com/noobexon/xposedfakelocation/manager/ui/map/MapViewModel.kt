package com.noobexon.xposedfakelocation.manager.ui.map

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.route.AmapWalkingRouteClient
import com.noobexon.xposedfakelocation.manager.route.Coordinate
import com.noobexon.xposedfakelocation.manager.route.RealLocationProvider
import com.noobexon.xposedfakelocation.manager.route.WalkingErrorCode
import com.noobexon.xposedfakelocation.manager.route.WalkingMode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import com.noobexon.xposedfakelocation.manager.route.WalkingRoute
import com.noobexon.xposedfakelocation.manager.route.WalkingRouteClient
import com.noobexon.xposedfakelocation.manager.route.WalkingRouteCodec
import com.noobexon.xposedfakelocation.manager.route.WalkingSpeedPreset
import com.noobexon.xposedfakelocation.manager.walking.WalkingSimulationService
import com.noobexon.xposedfakelocation.manager.notification.FixedLocationNotificationService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Valid latitude values accepted by the "Go to point" and "Add to favorites" dialogs. */
private val LATITUDE_RANGE = -90.0..90.0

/** Valid longitude values accepted by the "Go to point" and "Add to favorites" dialogs. */
private val LONGITUDE_RANGE = -180.0..180.0

/** One-shot walking-simulation messages surfaced to the UI as toasts/dialogs. */
sealed interface WalkingEvent {
    /** A failure the user can act on; [errorCode] maps to a localized message. */
    data class WalkFailed(val errorCode: WalkingErrorCode) : WalkingEvent

    /** The planned route is ready and can be reviewed before starting. */
    data object RouteReady : WalkingEvent

    /** A one-button home walk started around the pinned coordinate. */
    data object HomeWalkStarted : WalkingEvent
}

/**
 * ViewModel for the Map screen.
 *
 * Owns all Map-screen state via a single [uiState] [StateFlow], exposes one-shot camera events as
 * [Channel]-backed [Flow]s, and provides typed mutation functions so the UI never writes directly to
 * [_uiState].
 *
 * **Persistence**: [MapUiState.isPlaying], [MapUiState.lastClickedLocation], and [MapUiState.mapZoom]
 * are kept in sync with [PreferencesRepository] — all three are read on init and written back
 * whenever they change, so they survive the app being fully closed and reopened.
 *
 * **Dialog lifecycle**: each dialog has a paired `show*` / `hide*` function that guards setup and
 * teardown. The corresponding `confirm*` function validates input; on success it acts (emits event
 * or persists) and dismisses; on failure it writes error strings into the state so the dialog can
 * render inline validation messages.
 *
 * **Drawer re-open**: a simple boolean flag ([reopenDrawerRequested]) stores the intent to re-open
 * the navigation drawer when the map screen is returned to after a drawer-triggered navigation.
 * The flag is consumed exactly once via [consumeReopenDrawerRequest].
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)
    private val routeClient: WalkingRouteClient = AmapWalkingRouteClient()

    private companion object {
        const val TAG = "MapViewModel"

        /** Free IP geolocation endpoint; `ip-api.com` serves plain HTTP only. */
        const val IP_LOCATION_ENDPOINT = "http://ip-api.com/json"

        /** Connect/read timeout for the IP lookup. */
        const val IP_LOOKUP_TIMEOUT_MS = 10_000

        /** Numeric IPv4/IPv6 literal shape; host names are rejected before any network call. */
        val IP_LITERAL_REGEX = Regex("^[0-9A-Fa-f:.]+$")
    }

    private val _uiState = MutableStateFlow(
        MapUiState(mapZoom = preferencesRepository.getMapZoom())
    )

    /** Snapshot of the full Map-screen UI state, updated atomically. */
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /**
     * One-shot event that tells [MapViewEffects.HandleGoToPointEvent] to animate the camera to a
     * specific coordinate and place the spoof marker there.
     */
    private val _goToPointEvent = Channel<GeoPoint>(Channel.BUFFERED)
    val goToPointEvent: Flow<GeoPoint> = _goToPointEvent.receiveAsFlow()

    /**
     * One-shot event that tells [MapViewEffects.HandleCenterMapEvent] to animate the camera back
     * to the user's real location.
     */
    private val _centerMapEvent = Channel<Unit>(Channel.BUFFERED)
    val centerMapEvent: Flow<Unit> = _centerMapEvent.receiveAsFlow()

    /**
     * One-shot event emitted after a favorite is successfully saved. [MapScreen] collects this
     * and navigates the user to the Favorites screen so they can immediately see the new entry.
     */
    private val _navigateToFavoritesEvent = Channel<Unit>(Channel.BUFFERED)
    val navigateToFavoritesEvent: Flow<Unit> = _navigateToFavoritesEvent.receiveAsFlow()

    /** Stream of one-shot [WalkingEvent]s for toasts/snackbars. */
    private val _walkingEvents = Channel<WalkingEvent>(Channel.BUFFERED)
    val walkingEvents: Flow<WalkingEvent> = _walkingEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.getIsPlayingFlow().collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getLastClickedLocationFlow().collect { location ->
                val geoPoint = location?.let { GeoPoint(it.latitude, it.longitude) }
                _uiState.update { it.copy(lastClickedLocation = geoPoint) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getMapSourceOptionFlow().collect { tag ->
                _uiState.update { it.copy(mapSource = MapSourceOption.fromTag(tag)) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getTianDiTuTokenFlow().collect { token ->
                _uiState.update { it.copy(tiandituToken = token) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getWalkingPhaseFlow().collect { phase ->
                _uiState.update {
                    it.copy(
                        walkingPhase = phase,
                        // Route/preview data is meaningless once the session ends.
                        walkingRoute = if (phase == WalkingPhase.IDLE && !it.isWalkingActive) null else it.walkingRoute,
                        walkingErrorCode = if (phase == WalkingPhase.IDLE) null else it.walkingErrorCode,
                    )
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getWalkingRouteJsonFlow().collect { json ->
                val route = WalkingRouteCodec.decode(json)
                _uiState.update { it.copy(walkingRoute = route) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getWalkingModeFlow().collect { mode ->
                _uiState.update { it.copy(walkingMode = mode) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getWalkingDistanceTravelledFlow().collect { travelled ->
                _uiState.update { it.copy(walkingDistanceTravelled = travelled) }
            }
        }

        viewModelScope.launch {
            combineWalkingCoordinates().collect { position ->
                _uiState.update { it.copy(walkingCurrentPosition = position) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getWalkingErrorCodeFlow().collect { code ->
                _uiState.update { it.copy(walkingErrorCode = code.takeIf { c -> c.isNotBlank() }?.let { c -> WalkingErrorCode.valueOf(c) }) }
            }
        }
    }

    private fun combineWalkingCoordinates(): Flow<GeoPoint?> =
        kotlinx.coroutines.flow.combine(
            preferencesRepository.getWalkingCurrentLatitudeFlow(),
            preferencesRepository.getWalkingCurrentLongitudeFlow(),
        ) { lat, lon ->
            if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 &&
                !(lat == 0.0 && lon == 0.0)
            ) {
                GeoPoint(lat, lon)
            } else {
                null
            }
        }

    fun setMapSource(option: MapSourceOption) {
        _uiState.update { it.copy(mapSource = option) }
        viewModelScope.launch {
            preferencesRepository.saveMapSourceOption(option.tag)
        }
    }

    fun showMapSourceDialog() {
        _uiState.update { it.copy(isMapSourceDialogVisible = true) }
    }

    fun hideMapSourceDialog() {
        _uiState.update { it.copy(isMapSourceDialogVisible = false) }
    }

    /**
     * Toggles location-spoofing on/off and persists the new value.
     *
     * The optimistic local update ensures the FAB reflects the new state immediately, while the
     * coroutine write to [PreferencesRepository] happens asynchronously. Ignored while a walking
     * session owns the spoofing pipeline — stopping is done through the walking controls.
     */
    fun togglePlaying() {
        if (_uiState.value.isWalkingActive) return

        val currentIsPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = currentIsPlaying) }

        viewModelScope.launch {
            preferencesRepository.saveIsPlaying(currentIsPlaying)
            val action = if (currentIsPlaying) FixedLocationNotificationService.ACTION_START
            else FixedLocationNotificationService.ACTION_STOP
            if (currentIsPlaying) {
                ContextCompat.startForegroundService(
                    getApplication(), Intent(getApplication(), FixedLocationNotificationService::class.java).setAction(action)
                )
            } else {
                getApplication<Application>().startService(
                    Intent(getApplication(), FixedLocationNotificationService::class.java).setAction(action)
                )
            }
        }
    }

    /**
     * Updates the cached real-device location that is used for the "center on me" action and for
     * restoring the camera on re-entry when no spoof marker exists.
     *
     * @param location The most-recent device location reported by the location overlay.
     */
    fun updateUserLocation(location: GeoPoint) {
        _uiState.update { it.copy(userLocation = location) }
    }

    /**
     * Sets or clears the spoof-target marker and persists the change.
     *
     * Passing `null` removes the marker and clears the persisted location so that reopening the
     * app starts with a clean slate.
     *
     * Moving the pin invalidates any prepared walking route (its destination no longer matches),
     * so the preview is cleared as well — but never while a session is live.
     *
     * @param geoPoint The new spoof target, or `null` to clear it.
     */
    fun updateClickedLocation(geoPoint: GeoPoint?) {
        _uiState.update { it.copy(lastClickedLocation = geoPoint) }

        val phase = _uiState.value.walkingPhase
        if (phase == WalkingPhase.READY || phase == WalkingPhase.FAILED) {
            _uiState.update { it.copy(walkingPhase = WalkingPhase.IDLE, walkingRoute = null, walkingErrorCode = null) }
            viewModelScope.launch { preferencesRepository.clearWalkingRoute() }
        }

        viewModelScope.launch {
            geoPoint?.let {
                preferencesRepository.saveLastClickedLocation(it.latitude, it.longitude)
            } ?: preferencesRepository.clearLastClickedLocation()
        }
    }

    /**
     * Stores the map's current zoom level so it can be restored when the screen is re-entered or
     * the app is reopened after being fully closed. Called from [MapViewEffects.ManageMapViewLifecycle]
     * on dispose, capturing the zoom at the exact moment the map is torn down.
     *
     * @param zoom The zoom level to persist.
     */
    fun updateMapZoom(zoom: Double) {
        _uiState.update { it.copy(mapZoom = zoom) }
        preferencesRepository.saveMapZoom(zoom)
    }

    /**
     * Clears the loading state once [MapViewEffects.CenterMapOnUserLocation] has finished
     * determining the initial camera position. After this call [MapUiState.isLoading] is `false`
     * and the map view becomes visible.
     */
    fun setLoadingFinished() {
        _uiState.update { it.copy(isLoading = false) }
    }

    /** Marks that the one-time initial camera positioning has completed. */
    fun markInitialLocationResolved() {
        _uiState.update { it.copy(hasResolvedInitialLocation = true) }
    }

    /**
     * One-shot flag that signals the map screen should reopen the navigation drawer when it becomes
     * active again. Set when the user navigates away via the drawer; consumed once on re-entry.
     * Survives the map composable being destroyed because it lives in the ViewModel.
     */
    private var reopenDrawerRequested = false

    /** Records that the drawer should be reopened the next time the map screen is shown. */
    fun requestReopenDrawer() {
        reopenDrawerRequested = true
    }

    /** Returns whether a drawer-reopen was requested, consuming the one-shot flag. */
    fun consumeReopenDrawerRequest(): Boolean {
        val requested = reopenDrawerRequested
        reopenDrawerRequested = false
        return requested
    }

    /**
     * Enqueues a [centerMapEvent] to animate the camera back to the user's real location.
     * Called when the user taps the "My Location" icon in the top bar.
     */
    fun triggerCenterMapEvent() {
        _centerMapEvent.trySend(Unit)
    }

    // ---- Go to point dialog ----

    /** Makes the "Go to point" dialog visible. */
    fun showGoToPointDialog() {
        _uiState.update { it.copy(isGoToPointDialogVisible = true) }
    }

    /**
     * Dismisses the "Go to point" dialog and resets its input state so it is clean the next time
     * it is opened.
     */
    fun hideGoToPointDialog() {
        _uiState.update {
            it.copy(isGoToPointDialogVisible = false, goToPointState = GoToPointInputState())
        }
    }

    /**
     * Updates the combined coordinates-or-IP field of the "Go to point" dialog without running
     * validation; the previous error is cleared as soon as the user edits the field.
     *
     * @param value The raw string typed by the user.
     */
    fun onGoToPointQueryChange(value: String) {
        _uiState.update {
            it.copy(
                goToPointState = it.goToPointState.copy(
                    query = it.goToPointState.query.copy(value = value, errorMessageRes = null)
                )
            )
        }
    }

    /**
     * Validates the "Go to point" input: plain coordinates move the pin immediately, an IP
     * address is resolved to its approximate location first, and anything else is rejected with
     * an inline error while the dialog stays open.
     */
    fun confirmGoToPoint() {
        val query = _uiState.value.goToPointState.query.value.trim()
        if (query.isEmpty()) {
            setGoToPointError(R.string.validation_location_query)
            return
        }

        parseCoordinates(query)?.let { coordinates ->
            goToCoordinates(coordinates.latitude, coordinates.longitude)
            return
        }

        if (!isIpAddress(query)) {
            setGoToPointError(R.string.validation_location_query)
            return
        }

        lookupIpLocation(query)
    }

    private fun goToCoordinates(latitude: Double, longitude: Double) {
        _goToPointEvent.trySend(GeoPoint(latitude, longitude))
        hideGoToPointDialog()
    }

    private fun setGoToPointError(@StringRes errorRes: Int) {
        _uiState.update {
            it.copy(
                goToPointState = it.goToPointState.copy(
                    query = it.goToPointState.query.copy(errorMessageRes = errorRes)
                )
            )
        }
    }

    /**
     * Splits [query] on commas, semicolons or whitespace and parses the two parts as
     * `latitude, longitude`.
     *
     * @return The parsed pair, or `null` when the input is not exactly two in-range numbers.
     */
    private fun parseCoordinates(query: String): ParsedCoordinates? {
        val parts = query
            .replace(';', ',')
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
        if (parts.size != 2) return null

        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        if (latitude !in LATITUDE_RANGE || longitude !in LONGITUDE_RANGE) return null

        return ParsedCoordinates(latitude, longitude)
    }

    /**
     * Whether [value] looks like a literal IPv4/IPv6 address.
     *
     * Deliberately a shape check rather than DNS resolution: host names and city names must not
     * be sent to the lookup service, and the field is documented as coordinates-or-IP.
     */
    private fun isIpAddress(value: String): Boolean {
        val input = value.trim()
        if (input.isEmpty()) return false
        if (!input.matches(IP_LITERAL_REGEX)) return false
        return input.contains('.') || input.contains(':')
    }

    /** Resolves [ip] to its approximate location and moves the pin there. */
    private fun lookupIpLocation(ip: String) {
        viewModelScope.launch {
            val location = withContext(Dispatchers.IO) { requestIpLocation(ip) }
            if (location == null) {
                setGoToPointError(R.string.validation_ip_lookup_failed)
            } else {
                goToCoordinates(location.latitude, location.longitude)
            }
        }
    }

    /**
     * Queries the IP geolocation endpoint for [ip]. Never throws: transport, HTTP, JSON and
     * range failures all collapse to `null`, which the caller renders as a validation error.
     */
    private fun requestIpLocation(ip: String): IpLocation? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(
                "$IP_LOCATION_ENDPOINT/${URLEncoder.encode(ip, Charsets.UTF_8.name())}" +
                    "?fields=status,lat,lon,query"
            ).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = IP_LOOKUP_TIMEOUT_MS
                readTimeout = IP_LOOKUP_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optString("status") != "success") return null

            val latitude = json.optDouble("lat", Double.NaN)
            val longitude = json.optDouble("lon", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in LATITUDE_RANGE || longitude !in LONGITUDE_RANGE
            ) {
                return null
            }

            IpLocation(latitude, longitude)
        } catch (e: Exception) {
            Log.w(TAG, "IP geolocation lookup failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Coordinates parsed out of the combined "Go to point" field. */
    private data class ParsedCoordinates(val latitude: Double, val longitude: Double)

    /** Approximate location resolved for an IP address. */
    private data class IpLocation(val latitude: Double, val longitude: Double)

    // ---- Add to favorites dialog ----

    /**
     * Makes the "Add to favorites" dialog visible, pre-filling the latitude and longitude fields
     * from the currently placed spoof marker (if any) so the user only needs to supply a name.
     */
    fun showAddToFavoritesDialog() {
        val marker = _uiState.value.lastClickedLocation
        _uiState.update {
            it.copy(
                isAddToFavoritesDialogVisible = true,
                addToFavoritesState = if (marker != null) {
                    it.addToFavoritesState.copy(
                        latitude = InputFieldState(value = marker.latitude.toString()),
                        longitude = InputFieldState(value = marker.longitude.toString())
                    )
                } else {
                    it.addToFavoritesState
                }
            )
        }
    }

    /**
     * Dismisses the "Add to favorites" dialog and resets its input state so it is clean the next
     * time it is opened.
     */
    fun hideAddToFavoritesDialog() {
        _uiState.update {
            it.copy(isAddToFavoritesDialogVisible = false, addToFavoritesState = FavoritesInputState())
        }
    }

    /**
     * Updates the name field of the "Add to favorites" dialog with inline live validation — the
     * field is marked as an error immediately if the value is blank.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteNameChange(value: String) {
        val error = if (value.isBlank()) R.string.validation_name_required else null
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    name = it.addToFavoritesState.name.copy(value = value, errorMessageRes = error)
                )
            )
        }
    }

    /**
     * Updates the optional description field of the "Add to favorites" dialog. No validation is
     * applied — description is always optional and may be left blank.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteDescriptionChange(value: String) {
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    description = it.addToFavoritesState.description.copy(value = value)
                )
            )
        }
    }

    /**
     * Updates the latitude field of the "Add to favorites" dialog with inline live validation.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteLatitudeChange(value: String) {
        val error = validateInput(value, LATITUDE_RANGE, R.string.validation_latitude_range)
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    latitude = it.addToFavoritesState.latitude.copy(value = value, errorMessageRes = error)
                )
            )
        }
    }

    /**
     * Updates the longitude field of the "Add to favorites" dialog with inline live validation.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteLongitudeChange(value: String) {
        val error = validateInput(value, LONGITUDE_RANGE, R.string.validation_longitude_range)
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    longitude = it.addToFavoritesState.longitude.copy(value = value, errorMessageRes = error)
                )
            )
        }
    }

    /**
     * Validates the "Add to favorites" inputs. On success, persists the favorite and dismisses the
     * dialog; on failure, updates the input fields with validation errors and keeps the dialog open.
     */
    fun confirmAddFavorite() {
        val state = _uiState.value.addToFavoritesState
        val nameError = if (state.name.value.isBlank()) R.string.validation_name_required else null
        val latitudeError = validateInput(state.latitude.value, LATITUDE_RANGE, R.string.validation_latitude_range)
        val longitudeError = validateInput(state.longitude.value, LONGITUDE_RANGE, R.string.validation_longitude_range)

        if (nameError == null && latitudeError == null && longitudeError == null) {
            val favorite = FavoriteLocation(
                name = state.name.value,
                latitude = state.latitude.value.toDouble(),
                longitude = state.longitude.value.toDouble(),
                description = state.description.value.trim(),
            )
            viewModelScope.launch {
                preferencesRepository.addFavorite(favorite)
            }
            hideAddToFavoritesDialog()
            _navigateToFavoritesEvent.trySend(Unit)
        } else {
            _uiState.update {
                it.copy(
                    addToFavoritesState = it.addToFavoritesState.copy(
                        name = it.addToFavoritesState.name.copy(errorMessageRes = nameError),
                        latitude = it.addToFavoritesState.latitude.copy(errorMessageRes = latitudeError),
                        longitude = it.addToFavoritesState.longitude.copy(errorMessageRes = longitudeError)
                    )
                )
            }
        }
    }

    // ---- Walking simulation ----

    /** Selects the pace used for the next (or the running) walking session. */
    fun setWalkingSpeedPreset(preset: WalkingSpeedPreset) {
        _uiState.update { it.copy(walkingSpeedPreset = preset) }
        if (_uiState.value.isWalkingActive) {
            viewModelScope.launch { preferencesRepository.saveWalkingSpeed(preset.metersPerSecond) }
        }
    }

    /**
     * Plans a walking route from the device's *real* current position to the selected map pin.
     *
     * Preconditions (规划.md §11.3): an Amap Web-Service key must be configured and a
     * destination must be placed. The real location is read fresh at planning time — never the
     * current virtual position — so repeated planning cannot compound offsets.
     */
    fun planWalkingRoute() {
        val state = _uiState.value
        if (state.isWalkingActive || state.walkingPhase == WalkingPhase.PLANNING) return

        if (!preferencesRepository.isAmapWebServiceKeyConfigured()) {
            viewModelScope.launch { _walkingEvents.trySend(WalkingEvent.WalkFailed(WalkingErrorCode.MISSING_API_KEY)) }
            return
        }
        val destination = state.lastClickedLocation
        if (destination == null || !destination.latitude.isFinite() || !destination.longitude.isFinite() ||
            destination.latitude !in -90.0..90.0 || destination.longitude !in -180.0..180.0
        ) {
            viewModelScope.launch { _walkingEvents.trySend(WalkingEvent.WalkFailed(WalkingErrorCode.INVALID_DESTINATION)) }
            return
        }

        _uiState.update { it.copy(walkingPhase = WalkingPhase.PLANNING, walkingErrorCode = null) }

        viewModelScope.launch {
            val origin = RealLocationProvider.getLastKnown(getApplication())
            if (origin == null) {
                _uiState.update { it.copy(walkingPhase = WalkingPhase.IDLE) }
                _walkingEvents.trySend(WalkingEvent.WalkFailed(WalkingErrorCode.LOCATION_UNAVAILABLE))
                return@launch
            }

            val destinationCoordinate = Coordinate(destination.latitude, destination.longitude)
            val result = routeClient.planWalkingRoute(
                apiKey = preferencesRepository.getAmapWebServiceKey(),
                origin = origin,
                destination = destinationCoordinate,
            )

            result.fold(
                onSuccess = { route ->
                    if (!preferencesRepository.isRemotePreferencesAvailable()) {
                        _uiState.update { it.copy(walkingPhase = WalkingPhase.FAILED, walkingErrorCode = WalkingErrorCode.PREFERENCE_UNAVAILABLE) }
                        _walkingEvents.trySend(WalkingEvent.WalkFailed(WalkingErrorCode.PREFERENCE_UNAVAILABLE))
                        return@launch
                    }
                    preferencesRepository.savePreparedWalkingRoute(route)
                    _uiState.update { it.copy(walkingPhase = WalkingPhase.READY, walkingRoute = route, walkingErrorCode = null) }
                    _walkingEvents.trySend(WalkingEvent.RouteReady)
                },
                onFailure = { error ->
                    val errorCode = (error as? com.noobexon.xposedfakelocation.manager.route.WalkingException)?.errorCode
                        ?: WalkingErrorCode.INVALID_ROUTE_DATA
                    Log.w(TAG, "Walking route planning failed: ${error.message}")
                    _uiState.update { it.copy(walkingPhase = WalkingPhase.FAILED, walkingErrorCode = errorCode) }
                    _walkingEvents.trySend(WalkingEvent.WalkFailed(errorCode))
                },
            )
        }
    }

    /**
     * One-button home walk (the map screen's "Home walk" chip): stops the running session when it
     * is a home walk, otherwise starts moving around the pinned coordinate.
     *
     * Unlike [planWalkingRoute] it needs no Amap key, no network round-trip and no destination
     * search — the pin itself is the anchor and the walker always stays
     * [com.noobexon.xposedfakelocation.data.HOME_WALK_MIN_RADIUS_METERS]…
     * [com.noobexon.xposedfakelocation.data.HOME_WALK_MAX_RADIUS_METERS] metres away from it.
     */
    fun toggleHomeWalking() {
        val state = _uiState.value
        if (state.isWalkingActive) {
            if (state.walkingMode == WalkingMode.HOME) stopWalking()
            return
        }
        startHomeWalking()
    }

    private fun startHomeWalking() {
        val state = _uiState.value
        val anchor = state.lastClickedLocation
        if (anchor == null || !anchor.latitude.isFinite() || !anchor.longitude.isFinite()) {
            viewModelScope.launch { _walkingEvents.trySend(WalkingEvent.WalkFailed(WalkingErrorCode.INVALID_DESTINATION)) }
            return
        }
        if (!preferencesRepository.isRemotePreferencesAvailable()) {
            viewModelScope.launch { _walkingEvents.trySend(WalkingEvent.WalkFailed(WalkingErrorCode.PREFERENCE_UNAVAILABLE)) }
            return
        }

        viewModelScope.launch {
            preferencesRepository.startHomeWalkingSession(
                anchor = Coordinate(anchor.latitude, anchor.longitude),
                speedMetersPerSecond = state.walkingSpeedPreset.metersPerSecond,
            )
            _uiState.update {
                it.copy(
                    walkingPhase = WalkingPhase.WALKING,
                    walkingMode = WalkingMode.HOME,
                    walkingRoute = null,
                    walkingErrorCode = null,
                )
            }
            // Drop the fixed-position surface first: the walking service owns the spoofing
            // pipeline from this point on, exactly like the route flow does.
            getApplication<Application>().startService(
                Intent(getApplication(), FixedLocationNotificationService::class.java)
                    .setAction(FixedLocationNotificationService.ACTION_STOP),
            )
            ContextCompat.startForegroundService(
                getApplication(),
                WalkingSimulationService.commandIntent(getApplication(), WalkingSimulationService.ACTION_START_WALKING),
            )
            _walkingEvents.trySend(WalkingEvent.HomeWalkStarted)
        }
    }

    /**
     * Starts the prepared walking session. If a fixed-position spoof is currently active the
     * caller is asked to confirm the replacement first ([isReplaceSessionDialogVisible]).
     */
    fun startWalking() {
        val state = _uiState.value
        if (state.walkingPhase != WalkingPhase.READY || state.walkingRoute == null) return

        if (state.isPlaying) {
            _uiState.update { it.copy(isReplaceSessionDialogVisible = true) }
            return
        }
        beginWalkingSession()
    }

    /** Confirmed replacement of a fixed-position session by a walking session. */
    fun confirmStartWalking() {
        _uiState.update { it.copy(isReplaceSessionDialogVisible = false) }
        beginWalkingSession()
    }

    fun dismissReplaceSessionDialog() {
        _uiState.update { it.copy(isReplaceSessionDialogVisible = false) }
    }

    private fun beginWalkingSession() {
        val state = _uiState.value
        val route = state.walkingRoute ?: return
        if (state.isWalkingActive) return
        if (!preferencesRepository.isRemotePreferencesAvailable()) {
            viewModelScope.launch { _walkingEvents.trySend(WalkingEvent.WalkFailed(WalkingErrorCode.PREFERENCE_UNAVAILABLE)) }
            return
        }

        viewModelScope.launch {
            preferencesRepository.startWalkingSession(route, state.walkingSpeedPreset.metersPerSecond)
            _uiState.update { it.copy(walkingPhase = WalkingPhase.WALKING, walkingErrorCode = null) }
            ContextCompat.startForegroundService(
                getApplication(),
                WalkingSimulationService.commandIntent(getApplication(), WalkingSimulationService.ACTION_START_WALKING),
            )
        }
    }

    /** Pauses the running session; the service publishes the authoritative PAUSED state. */
    fun pauseWalking() {
        if (_uiState.value.walkingPhase != WalkingPhase.WALKING) return
        _uiState.update { it.copy(walkingPhase = WalkingPhase.PAUSED) }
        getApplication<Application>().startService(
            WalkingSimulationService.commandIntent(getApplication(), WalkingSimulationService.ACTION_PAUSE_WALKING),
        )
    }

    /** Resumes a paused session; the service publishes the authoritative WALKING state. */
    fun resumeWalking() {
        if (_uiState.value.walkingPhase != WalkingPhase.PAUSED) return
        _uiState.update { it.copy(walkingPhase = WalkingPhase.WALKING) }
        getApplication<Application>().startService(
            WalkingSimulationService.commandIntent(getApplication(), WalkingSimulationService.ACTION_RESUME_WALKING),
        )
    }

    /**
     * Stops the session and restores the pre-walking behavior (fixed-point spoof off, dynamic
     * state cleared). The destination marker is kept so the route can be planned again.
     */
    fun stopWalking() {
        val phase = _uiState.value.walkingPhase
        if (phase != WalkingPhase.WALKING && phase != WalkingPhase.PAUSED && phase != WalkingPhase.ARRIVED && phase != WalkingPhase.STOPPING) return

        _uiState.update {
            it.copy(
                walkingPhase = WalkingPhase.IDLE,
                walkingRoute = null,
                walkingErrorCode = null,
                isPlaying = false,
            )
        }
        getApplication<Application>().startService(
            WalkingSimulationService.commandIntent(getApplication(), WalkingSimulationService.ACTION_STOP_WALKING),
        )
    }

    /**
     * Parses [input] as a `Double` and checks whether it falls within [range].
     *
     * @param input Raw text from a dialog field.
     * @param range The valid coordinate range (e.g. `−90..90` for latitude).
     * @param errorMessageRes String resource to return when the value is invalid.
     * @return `null` when the input is valid, or [errorMessageRes] when it is not.
     */
    private fun validateInput(
        input: String, range: ClosedRange<Double>, @StringRes errorMessageRes: Int
    ): Int? {
        val value = input.toDoubleOrNull()
        return if (value == null || value !in range) errorMessageRes else null
    }
}

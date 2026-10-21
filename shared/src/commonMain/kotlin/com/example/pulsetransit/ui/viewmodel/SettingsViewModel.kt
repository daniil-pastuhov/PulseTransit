package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.CameraSensitivity
import com.example.pulsetransit.data.DestinationSchedule
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.RoutePreference
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val enturRepository: EnturRepository,
    private val settingsRepository: SettingsRepository,
    private val sensorManager: SystemSensorManager,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    /** Where the location lookup stands. Each platform turns this into its own localized text. */
    sealed interface LocationStatus {
        data object NotRequested : LocationStatus

        data object RequestingPrecise : LocationStatus

        /** The precise fix failed and the approximate fallback is running. */
        data object RequestingApproximate : LocationStatus

        data class Found(
            val accuracy: LocationAccuracy,
        ) : LocationStatus

        data object Failed : LocationStatus

        data object Denied : LocationStatus

        data class Error(
            val message: String,
        ) : LocationStatus

        data object Manual : LocationStatus

        data object InvalidManualFormat : LocationStatus
    }

    data class UiState(
        val appMode: AppMode = AppMode.BOARD,
        /** Route alternatives per destination in on-spot mode; [SettingsRepository.TRIP_OPTIONS_AUTO] fits the screen. */
        val tripOptionsCount: Int = SettingsRepository.TRIP_OPTIONS_AUTO,
        val routePreference: RoutePreference = RoutePreference.FASTEST,
        val excludedRouteModes: Set<RouteTransportMode> = emptySet(),
        val walkingSpeed: WalkingSpeed = WalkingSpeed.NORMAL,
        val query: String = "",
        val searchResults: List<StopPlace> = emptyList(),
        val isSearching: Boolean = false,
        val nearbyStops: List<StopPlace> = emptyList(),
        val isSearchingNearby: Boolean = false,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val locationStatus: LocationStatus = LocationStatus.NotRequested,
        val showManualCoordinatesPrompt: Boolean = false,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val burnInLevel: BurnInProtectionLevel = BurnInProtectionLevel.MEDIUM,
        val refreshIntervalSeconds: Int = 60,
        val autoDimSeconds: Int = 30,
        val favorites: List<StopPlace> = emptyList(),
        val favoriteStopsWithQuays: Map<String, StopPlace> = emptyMap(),
        val selectedQuays: Map<String, Set<String>> = emptyMap(),
        val showDelays: Boolean = true,
        val showMessages: Boolean = true,
        val showCancelledLines: Boolean = false,
        val showWeather: Boolean = true,
        val autoThemeDarkLux: Float = 10f,
        val autoThemeLightLux: Float = 40f,
        val currentLux: Float? = null,
        val cameraMotionWake: Boolean = false,
        val cameraSensitivity: CameraSensitivity = CameraSensitivity.MEDIUM,
        val dynamicRefresh: Boolean = true,
        val isFullScreenMode: Boolean = false,
        val isAlwaysOnEnabled: Boolean = false,
        val filteredLines: Set<String> = emptySet(),
        val destinationStations: List<StopPlace> = emptyList(),
        /** When each destination is shown, by id; one without a schedule always is. */
        val destinationSchedules: Map<String, DestinationSchedule> = emptyMap(),
        val destinationQuery: String = "",
        val destinationResults: List<StopPlace> = emptyList(),
        val isSearchingDestination: Boolean = false,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private var destinationSearchJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            sensorManager.getLightFlow().collect { lux ->
                _uiState.update { it.copy(currentLux = lux) }
            }
        }
    }

    fun loadSettings() {
        val favs = settingsRepository.getFavorites()
        val quaysMap = favs.associateBy({ it.id }) { settingsRepository.getSelectedQuays(it.id) ?: emptySet() }

        _uiState.update {
            it.copy(
                appMode = settingsRepository.getAppMode() ?: AppMode.BOARD,
                tripOptionsCount = settingsRepository.getTripOptionsCount(),
                routePreference = settingsRepository.getRoutePreference(),
                excludedRouteModes = settingsRepository.getExcludedRouteModes(),
                walkingSpeed = settingsRepository.getWalkingSpeed(),
                themeMode = settingsRepository.getThemeMode(),
                burnInLevel = settingsRepository.getBurnInProtectionLevel(),
                refreshIntervalSeconds = settingsRepository.getRefreshIntervalSeconds(),
                autoDimSeconds = settingsRepository.getAutoDimSeconds(),
                latitude = settingsRepository.getSavedLatitude(),
                longitude = settingsRepository.getSavedLongitude(),
                favorites = favs,
                selectedQuays = quaysMap,
                showDelays = settingsRepository.isShowDelays(),
                showMessages = settingsRepository.isShowMessages(),
                showCancelledLines = settingsRepository.isShowCancelledLines(),
                showWeather = settingsRepository.isShowWeather(),
                autoThemeDarkLux = settingsRepository.getAutoThemeDarkLux(),
                autoThemeLightLux = settingsRepository.getAutoThemeLightLux(),
                cameraMotionWake = settingsRepository.isCameraMotionWakeEnabled(),
                cameraSensitivity = settingsRepository.getCameraSensitivity(),
                dynamicRefresh = settingsRepository.isDynamicRefreshEnabled(),
                isFullScreenMode = settingsRepository.isFullScreenMode(),
                isAlwaysOnEnabled = settingsRepository.isAlwaysOnEnabled(),
                destinationStations = settingsRepository.getDestinationStations(),
                destinationSchedules = settingsRepository.getDestinationSchedules(),
                filteredLines = settingsRepository.getFilteredLines(),
            )
        }

        val lat = settingsRepository.getSavedLatitude()
        val lon = settingsRepository.getSavedLongitude()
        if ((lat != null) && (lon != null)) {
            fetchNearbyStops(lat, lon)
        }

        // Fetch quays for all favorites
        favs.forEach { stop ->
            fetchQuaysForStop(stop.id)
        }
    }

    private fun fetchQuaysForStop(stopId: String) {
        viewModelScope.launch {
            val stopWithQuays = enturRepository.getStopPlaceWithQuays(stopId)
            if (stopWithQuays != null) {
                _uiState.update {
                    it.copy(favoriteStopsWithQuays = it.favoriteStopsWithQuays + (stopId to stopWithQuays))
                }
            }
        }
    }

    fun toggleQuay(
        stopId: String,
        quayId: String,
    ) {
        val allQuayIds =
            _uiState.value.favoriteStopsWithQuays[stopId]
                ?.quays
                ?.asSequence()
                ?.map { it.id }
                ?.toSet() ?: emptySet()
        // A selection can hold a quay the picker no longer lists (it stopped serving any line), and
        // it could never be unticked; drop it so it doesn't skew the "everything selected" check.
        val saved = _uiState.value.selectedQuays[stopId] ?: emptySet()
        val current = if (allQuayIds.isEmpty()) saved else saved intersect allQuayIds

        val next =
            // Keyed on the saved set, as the rows' ticks are: a selection of only unlisted quays
            // shows every row unticked, so a tap there must tick one, not untick it.
            if (saved.isEmpty() && allQuayIds.isNotEmpty()) {
                // When none are explicitly selected, they are all active by default.
                // Clicking one should deselect it, meaning we select all others.
                allQuayIds - quayId
            } else if (current.contains(quayId)) {
                current - quayId
            } else {
                current + quayId
            }

        // Normalize: if everything or nothing is selected, treat it as "All" (empty set in UI, null in DB)
        val isAll = next.isEmpty() || (allQuayIds.isNotEmpty() && next.size == allQuayIds.size)
        val finalNext = if (isAll) emptySet() else next

        settingsRepository.setSelectedQuays(stopId, finalNext.ifEmpty { null })
        _uiState.update {
            it.copy(selectedQuays = it.selectedQuays + (stopId to finalNext))
        }
    }

    fun updateAppMode(mode: AppMode) {
        settingsRepository.setAppMode(mode)
        _uiState.update { it.copy(appMode = mode) }
    }

    fun updateTripOptionsCount(count: Int) {
        settingsRepository.setTripOptionsCount(count)
        _uiState.update { it.copy(tripOptionsCount = settingsRepository.getTripOptionsCount()) }
    }

    fun updateRoutePreference(preference: RoutePreference) {
        settingsRepository.setRoutePreference(preference)
        _uiState.update { it.copy(routePreference = preference) }
    }

    fun updateWalkingSpeed(speed: WalkingSpeed) {
        settingsRepository.setWalkingSpeed(speed)
        _uiState.update { it.copy(walkingSpeed = speed) }
    }

    /**
     * Allows or excludes [mode] for routes. The last allowed kind cannot be excluded: with no
     * transit at all there would be no route to show.
     */
    fun toggleRouteMode(mode: RouteTransportMode) {
        val current = _uiState.value.excludedRouteModes
        val next = if (mode in current) current - mode else current + mode
        if (next.size >= RouteTransportMode.entries.size) return
        settingsRepository.setExcludedRouteModes(next)
        _uiState.update { it.copy(excludedRouteModes = next) }
    }

    fun updateShowDelays(show: Boolean) {
        settingsRepository.setShowDelays(show)
        _uiState.update { it.copy(showDelays = show) }
    }

    fun updateShowMessages(show: Boolean) {
        settingsRepository.setShowMessages(show)
        _uiState.update { it.copy(showMessages = show) }
    }

    fun updateShowCancelledLines(show: Boolean) {
        settingsRepository.setShowCancelledLines(show)
        _uiState.update { it.copy(showCancelledLines = show) }
    }

    fun updateCameraMotionWake(enabled: Boolean) {
        settingsRepository.setCameraMotionWakeEnabled(enabled)
        _uiState.update { it.copy(cameraMotionWake = enabled) }
    }

    fun updateCameraSensitivity(sensitivity: CameraSensitivity) {
        settingsRepository.setCameraSensitivity(sensitivity)
        _uiState.update { it.copy(cameraSensitivity = sensitivity) }
    }

    fun updateShowWeather(show: Boolean) {
        settingsRepository.setShowWeather(show)
        _uiState.update { it.copy(showWeather = show) }
    }

    fun updateDynamicRefresh(enabled: Boolean) {
        settingsRepository.setDynamicRefreshEnabled(enabled)
        _uiState.update { it.copy(dynamicRefresh = enabled) }
    }

    fun updateFullScreenMode(enabled: Boolean) {
        settingsRepository.setFullScreenMode(enabled)
        _uiState.update { it.copy(isFullScreenMode = enabled) }
    }

    fun updateAlwaysOnMode(enabled: Boolean) {
        settingsRepository.setAlwaysOnEnabled(enabled)
        _uiState.update { it.copy(isAlwaysOnEnabled = enabled) }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchStops(newQuery)
    }

    private fun searchStops(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val results = enturRepository.searchStopPlaces(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    // The location provider and the Entur lookup can fail in a dozen unrelated ways; whatever
    // goes wrong, the screen falls back to asking for coordinates by hand.
    @Suppress("TooGenericExceptionCaught")
    fun requestLocationAndNearbyStops() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingNearby = true,
                    locationStatus = LocationStatus.RequestingPrecise,
                    showManualCoordinatesPrompt = false,
                )
            }

            try {
                // Precise first; if it has nothing, fall back to approximate.
                var accuracy = LocationAccuracy.PRECISE
                var result = locationProvider.currentLocation(accuracy)
                if (result == LocationResult.Unavailable) {
                    _uiState.update { it.copy(locationStatus = LocationStatus.RequestingApproximate) }
                    accuracy = LocationAccuracy.APPROXIMATE
                    result = locationProvider.currentLocation(accuracy)
                }

                when (result) {
                    is LocationResult.Fix -> handleLocationSuccess(result, accuracy)
                    LocationResult.Unavailable -> handleLocationFailure(LocationStatus.Failed)
                    LocationResult.PermissionDenied -> handleLocationFailure(LocationStatus.Denied)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleLocationFailure(LocationStatus.Error(e.message ?: e.toString()))
            }
        }
    }

    private fun handleLocationSuccess(
        fix: LocationResult.Fix,
        accuracy: LocationAccuracy,
    ) {
        val lat = fix.latitude
        val lon = fix.longitude
        settingsRepository.setSavedLocation(lat, lon)
        _uiState.update {
            it.copy(
                latitude = lat,
                longitude = lon,
                locationStatus = LocationStatus.Found(accuracy),
                isSearchingNearby = false,
                showManualCoordinatesPrompt = false,
            )
        }
        fetchNearbyStops(lat, lon)
    }

    private fun handleLocationFailure(status: LocationStatus) {
        _uiState.update {
            it.copy(
                locationStatus = status,
                isSearchingNearby = false,
                showManualCoordinatesPrompt = true,
            )
        }
    }

    fun setManualCoordinates(
        latStr: String,
        lonStr: String,
    ) {
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        if (lat != null && lon != null) {
            settingsRepository.setSavedLocation(lat, lon)
            _uiState.update {
                it.copy(
                    latitude = lat,
                    longitude = lon,
                    locationStatus = LocationStatus.Manual,
                    showManualCoordinatesPrompt = false,
                )
            }
            fetchNearbyStops(lat, lon)
        } else {
            _uiState.update {
                it.copy(locationStatus = LocationStatus.InvalidManualFormat)
            }
        }
    }

    private fun fetchNearbyStops(
        lat: Double,
        lon: Double,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingNearby = true) }
            val stops = enturRepository.getNearbyStopPlaces(lat, lon, radiusKm = 1.0)
            _uiState.update { it.copy(nearbyStops = stops, isSearchingNearby = false) }
        }
    }

    fun addFavorite(stopPlace: StopPlace) {
        settingsRepository.addFavorite(stopPlace)
        val currentFavs = settingsRepository.getFavorites()
        _uiState.update { it.copy(favorites = currentFavs) }
        if (currentFavs.any { it.id == stopPlace.id }) fetchQuaysForStop(stopPlace.id)
    }

    fun removeFavorite(stopId: String) {
        settingsRepository.removeFavorite(stopId)
        _uiState.update {
            it.copy(
                favorites = it.favorites.filter { f -> f.id != stopId },
                favoriteStopsWithQuays = it.favoriteStopsWithQuays - stopId,
                selectedQuays = it.selectedQuays - stopId,
            )
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun updateAutoThemeDarkLux(lux: Float) {
        settingsRepository.setAutoThemeDarkLux(lux)
        _uiState.update { it.copy(autoThemeDarkLux = lux) }
    }

    fun updateAutoThemeLightLux(lux: Float) {
        settingsRepository.setAutoThemeLightLux(lux)
        _uiState.update { it.copy(autoThemeLightLux = lux) }
    }

    fun updateBurnInLevel(level: BurnInProtectionLevel) {
        settingsRepository.setBurnInProtectionLevel(level)
        _uiState.update { it.copy(burnInLevel = level) }
    }

    fun updateRefreshInterval(seconds: Int) {
        settingsRepository.setRefreshIntervalSeconds(seconds)
        _uiState.update { it.copy(refreshIntervalSeconds = seconds) }
    }

    fun updateAutoDimSeconds(seconds: Int) {
        settingsRepository.setAutoDimSeconds(seconds)
        _uiState.update { it.copy(autoDimSeconds = seconds) }
    }

    fun addDestinationStation(stopPlace: StopPlace) {
        destinationSearchJob?.cancel()
        settingsRepository.addDestinationStation(stopPlace)
        _uiState.update {
            it.copy(
                destinationStations = settingsRepository.getDestinationStations(),
                destinationQuery = "",
                destinationResults = emptyList(),
                isSearchingDestination = false,
            )
        }
    }

    fun removeDestinationStation(stopId: String) {
        settingsRepository.removeDestinationStation(stopId)
        _uiState.update {
            it.copy(
                destinationStations = settingsRepository.getDestinationStations(),
                destinationSchedules = settingsRepository.getDestinationSchedules(),
            )
        }
    }

    /** Sets when [destinationId]'s route is shown; null shows it always. */
    fun setDestinationSchedule(
        destinationId: String,
        schedule: DestinationSchedule?,
    ) {
        settingsRepository.setDestinationSchedule(destinationId, schedule)
        _uiState.update { it.copy(destinationSchedules = settingsRepository.getDestinationSchedules()) }
    }

    fun onDestinationQueryChanged(newQuery: String) {
        _uiState.update { it.copy(destinationQuery = newQuery) }
        // Each keystroke supersedes the last search, so a slow early response can't land after
        // (and overwrite) the results for what the user has typed since.
        destinationSearchJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(destinationResults = emptyList(), isSearchingDestination = false) }
            return
        }
        destinationSearchJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isSearchingDestination = true) }
                delay(SEARCH_DEBOUNCE_MS)
                val state = _uiState.value
                val results = enturRepository.searchPlaces(newQuery, state.latitude, state.longitude)
                _uiState.update { it.copy(destinationResults = results, isSearchingDestination = false) }
            }
    }

    fun removeFilteredLine(line: String) {
        settingsRepository.removeFilteredLine(line)
        _uiState.update { it.copy(filteredLines = settingsRepository.getFilteredLines()) }
    }
}

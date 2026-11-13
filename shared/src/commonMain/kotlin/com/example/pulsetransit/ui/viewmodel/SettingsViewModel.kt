package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.CameraSensitivity
import com.example.pulsetransit.data.DestinationSchedule
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.RouteModeGroup
import com.example.pulsetransit.data.RoutePreference
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TypedCoordinates
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.distanceMeters
import com.example.pulsetransit.data.journey.JourneyAlertKind
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.data.parseTypedCoordinates
import com.example.pulsetransit.platform.JourneyPermissions
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
import kotlin.math.roundToInt

class SettingsViewModel(
    private val enturRepository: EnturRepository,
    private val settingsRepository: SettingsRepository,
    private val sensorManager: SystemSensorManager,
    private val locationProvider: LocationProvider,
    private val journeyStore: JourneyStore,
    private val journeyPermissions: JourneyPermissions,
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
    }

    data class UiState(
        val appMode: AppMode = AppMode.BOARD,
        /** Route alternatives per destination in on-spot mode; [SettingsRepository.TRIP_OPTIONS_AUTO] fits the screen. */
        val tripOptionsCount: Int = SettingsRepository.TRIP_OPTIONS_AUTO,
        val routePreference: RoutePreference = RoutePreference.FASTEST,
        val excludedRouteModes: Set<RouteTransportMode> = emptySet(),
        val walkingSpeed: WalkingSpeed = WalkingSpeed.NORMAL,
        /** Whether this platform has journey alerts at all, and so their setting. */
        val journeyAlertsSupported: Boolean = false,
        /** How "Notify me about this journey" follows a journey; null until first chosen. */
        val journeyMode: JourneyTrackingMode? = null,
        /** The kinds of journey alert turned off; empty when all are on, the default. */
        val disabledJourneyAlerts: Set<JourneyAlertKind> = emptySet(),
        val query: String = "",
        val searchResults: List<StopPlace> = emptyList(),
        val isSearching: Boolean = false,
        /** Stops within 1 km of the saved location, nearest first. */
        val nearbyStops: List<NearbyStop> = emptyList(),
        val isSearchingNearby: Boolean = false,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val locationStatus: LocationStatus = LocationStatus.NotRequested,
        val showManualCoordinatesPrompt: Boolean = false,
        /** What is wrong with the coordinates last typed, field by field; null once they are fine. */
        val coordinatesError: TypedCoordinates.Invalid? = null,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val burnInLevel: BurnInProtectionLevel = BurnInProtectionLevel.MEDIUM,
        val refreshIntervalSeconds: Int = 60,
        val autoDimSeconds: Int = 30,
        val favorites: List<StopPlace> = emptyList(),
        val favoriteStopsWithQuays: Map<String, StopPlace> = emptyMap(),
        /** Stops whose platforms Entur did not return, for Directions to say so and retry. */
        val quaysFailed: Set<String> = emptySet(),
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
        /** The last change that can still be taken back, shown with Undo; null when there is none. */
        val pendingUndo: SettingsUndo? = null,
    )

    /** A change made at once, without asking, that [undo] reverses. [serial] tells repeats apart. */
    sealed interface SettingsUndo {
        val serial: Long

        /** [stop] was removed from position [index] on the board. */
        data class StopRemoved(
            val stop: StopPlace,
            val index: Int,
            override val serial: Long,
        ) : SettingsUndo

        data class StopAdded(
            val stop: StopPlace,
            override val serial: Long,
        ) : SettingsUndo
    }

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
                journeyAlertsSupported = journeyPermissions.isSupported,
                journeyMode = journeyStore.preferredMode(),
                disabledJourneyAlerts = journeyStore.disabledAlerts(),
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
        _uiState.update { it.copy(quaysFailed = it.quaysFailed - stopId) }
        viewModelScope.launch {
            // The repository answers null when Entur fails, rather than throwing.
            val stopWithQuays = enturRepository.getStopPlaceWithQuays(stopId)
            _uiState.update {
                if (stopWithQuays != null) {
                    it.copy(favoriteStopsWithQuays = it.favoriteStopsWithQuays + (stopId to stopWithQuays))
                } else {
                    it.copy(quaysFailed = it.quaysFailed + stopId)
                }
            }
        }
    }

    /** Asks Entur again for a stop's platforms, after [UiState.quaysFailed] said it failed. */
    fun retryQuays(stopId: String) = fetchQuaysForStop(stopId)

    /** Clears a stop's platform choice, so the board shows every direction again. */
    fun showAllQuays(stopId: String) {
        settingsRepository.setSelectedQuays(stopId, null)
        _uiState.update { it.copy(selectedQuays = it.selectedQuays + (stopId to emptySet())) }
    }

    /** Moves the stop at [fromIndex] to [toIndex]; the board shows its stops in this order. */
    fun moveFavorite(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val ids =
            _uiState.value.favorites
                .map { it.id }
                .toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return
        ids.add(toIndex, ids.removeAt(fromIndex))
        settingsRepository.reorderFavorites(ids)
        _uiState.update { it.copy(favorites = settingsRepository.getFavorites()) }
    }

    /**
     * Ticks or unticks one of a stop's platforms. None ticked means every direction is shown, so
     * the first tick chooses just that platform, and unticking the last goes back to all.
     */
    fun toggleQuay(
        stopId: String,
        quayId: String,
    ) {
        val listed =
            _uiState.value.favoriteStopsWithQuays[stopId]
                ?.quays
                ?.map { it.id }
                ?.toSet()
        // A selection can hold a quay the picker no longer lists (it stopped serving any line), and
        // it could never be unticked; drop it so it doesn't linger in the choice.
        val saved = _uiState.value.selectedQuays[stopId] ?: emptySet()
        val current = if (listed.isNullOrEmpty()) saved else saved intersect listed
        val next = if (quayId in current) current - quayId else current + quayId

        settingsRepository.setSelectedQuays(stopId, next.ifEmpty { null })
        _uiState.update {
            it.copy(selectedQuays = it.selectedQuays + (stopId to next))
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

    /**
     * The mode the next followed journey uses. A journey followed now keeps its own; location mode
     * still asks for the location, through the rationale, the first time it is used.
     */
    fun updateJourneyMode(mode: JourneyTrackingMode) {
        journeyStore.setPreferredMode(mode)
        _uiState.update { it.copy(journeyMode = mode) }
    }

    /** Turns one kind of journey alert on or off, also for the journey followed now. */
    fun updateJourneyAlert(
        kind: JourneyAlertKind,
        enabled: Boolean,
    ) {
        journeyStore.setAlertEnabled(kind, enabled)
        _uiState.update { it.copy(disabledJourneyAlerts = journeyStore.disabledAlerts()) }
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

    /**
     * Travel by's group box: allows the whole group when any of it is off, else excludes it all.
     * Excluding it all is refused when it would leave no kind allowed anywhere.
     */
    fun toggleRouteModeGroup(group: RouteModeGroup) {
        val modes = RouteTransportMode.entries.filter { it.group == group }.toSet()
        val current = _uiState.value.excludedRouteModes
        val next = if ((current intersect modes).isEmpty()) current + modes else current - modes
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

    /** Saves typed coordinates, or says per field what is wrong with them ([UiState.coordinatesError]). */
    fun setManualCoordinates(
        latStr: String,
        lonStr: String,
    ) {
        when (val typed = parseTypedCoordinates(latStr, lonStr)) {
            is TypedCoordinates.Valid -> {
                settingsRepository.setSavedLocation(typed.latitude, typed.longitude)
                _uiState.update {
                    it.copy(
                        latitude = typed.latitude,
                        longitude = typed.longitude,
                        locationStatus = LocationStatus.Manual,
                        showManualCoordinatesPrompt = false,
                        coordinatesError = null,
                    )
                }
                fetchNearbyStops(typed.latitude, typed.longitude)
            }
            is TypedCoordinates.Invalid -> _uiState.update { it.copy(coordinatesError = typed) }
        }
    }

    /** The user is correcting the coordinates, so the old errors no longer apply. */
    fun clearCoordinatesError() {
        _uiState.update { it.copy(coordinatesError = null) }
    }

    private fun fetchNearbyStops(
        lat: Double,
        lon: Double,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingNearby = true) }
            val stops =
                enturRepository
                    .getNearbyStopPlaces(lat, lon, radiusKm = 1.0)
                    .map { stop ->
                        val distance =
                            if (stop.latitude != null && stop.longitude != null) {
                                distanceMeters(lat, lon, stop.latitude, stop.longitude).roundToInt()
                            } else {
                                null
                            }
                        NearbyStop(stop, distance)
                    }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
            _uiState.update { it.copy(nearbyStops = stops, isSearchingNearby = false) }
        }
    }

    fun addFavorite(stopPlace: StopPlace) {
        val wasThere = settingsRepository.isFavorite(stopPlace.id)
        settingsRepository.addFavorite(stopPlace)
        val currentFavs = settingsRepository.getFavorites()
        val added = !wasThere && currentFavs.any { it.id == stopPlace.id }
        _uiState.update {
            it.copy(
                favorites = currentFavs,
                pendingUndo = if (added) SettingsUndo.StopAdded(stopPlace, nextUndoSerial()) else it.pendingUndo,
            )
        }
        if (added) fetchQuaysForStop(stopPlace.id)
    }

    /**
     * Removes the stop at once; [undo] puts it back in its place. Its chosen directions stay saved
     * (only the board's list forgets the stop), so Undo restores those too.
     */
    fun removeFavorite(stopId: String) {
        val favorites = settingsRepository.getFavorites()
        val index = favorites.indexOfFirst { it.id == stopId }
        settingsRepository.removeFavorite(stopId)
        _uiState.update {
            it.copy(
                favorites = it.favorites.filter { f -> f.id != stopId },
                selectedQuays = it.selectedQuays - stopId,
                pendingUndo =
                    if (index >= 0) SettingsUndo.StopRemoved(favorites[index], index, nextUndoSerial()) else it.pendingUndo,
            )
        }
    }

    /** Reverses [UiState.pendingUndo], if any. */
    fun undo() {
        when (val pending = _uiState.value.pendingUndo) {
            is SettingsUndo.StopRemoved -> restoreFavorite(pending)
            is SettingsUndo.StopAdded -> {
                settingsRepository.removeFavorite(pending.stop.id)
                _uiState.update {
                    it.copy(
                        favorites = it.favorites.filter { f -> f.id != pending.stop.id },
                        favoriteStopsWithQuays = it.favoriteStopsWithQuays - pending.stop.id,
                        selectedQuays = it.selectedQuays - pending.stop.id,
                    )
                }
            }
            null -> return
        }
        _uiState.update { it.copy(pendingUndo = null) }
    }

    /** The Undo offer timed out or was closed. */
    fun dismissUndo() {
        _uiState.update { it.copy(pendingUndo = null) }
    }

    private fun restoreFavorite(removed: SettingsUndo.StopRemoved) {
        settingsRepository.addFavorite(removed.stop)
        val ids =
            settingsRepository
                .getFavorites()
                .map { it.id }
                .filter { it != removed.stop.id }
                .toMutableList()
        ids.add(removed.index.coerceAtMost(ids.size), removed.stop.id)
        settingsRepository.reorderFavorites(ids)
        val favorites = settingsRepository.getFavorites()
        _uiState.update {
            it.copy(
                favorites = favorites,
                selectedQuays = it.selectedQuays + (removed.stop.id to (settingsRepository.getSelectedQuays(removed.stop.id) ?: emptySet())),
            )
        }
        if (removed.stop.id !in _uiState.value.favoriteStopsWithQuays) fetchQuaysForStop(removed.stop.id)
    }

    private var undoSerial = 0L

    private fun nextUndoSerial() = ++undoSerial

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

    /**
     * Moves the destination at [fromIndex] to [toIndex], as Settings' list is dragged; the board
     * shows their routes in this order. Out-of-range indices are ignored.
     */
    fun moveDestinationStation(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val current = _uiState.value.destinationStations.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        current.add(toIndex, current.removeAt(fromIndex))
        settingsRepository.reorderDestinationStations(current.map { it.id })
        _uiState.update { it.copy(destinationStations = settingsRepository.getDestinationStations()) }
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

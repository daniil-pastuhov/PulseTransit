package com.example.pulsetransit.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.R
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.CameraSensitivity
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SettingsViewModel(
    private val enturRepository: EnturRepository,
    private val settingsRepository: SettingsRepository,
    context: Context,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)

    data class LocationStatus(
        val resId: Int,
        val arg: String? = null,
    )

    data class UiState(
        val query: String = "",
        val searchResults: List<StopPlace> = emptyList(),
        val isSearching: Boolean = false,
        val nearbyStops: List<StopPlace> = emptyList(),
        val isSearchingNearby: Boolean = false,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val locationStatus: LocationStatus = LocationStatus(R.string.loc_not_requested),
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
        val autoThemeDarkLux: Float = 10f,
        val autoThemeLightLux: Float = 40f,
        val cameraMotionWake: Boolean = false,
        val cameraSensitivity: CameraSensitivity = CameraSensitivity.MEDIUM,
        val dynamicRefresh: Boolean = true,
        val isFullScreenMode: Boolean = false,
        val isAlwaysOnEnabled: Boolean = false,
        val filteredLines: Set<String> = emptySet(),
        val destinationStations: List<StopPlace> = emptyList(),
        val destinationQuery: String = "",
        val destinationResults: List<StopPlace> = emptyList(),
        val isSearchingDestination: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val favs = settingsRepository.getFavorites()
        val quaysMap = favs.associateBy({ it.id }) { settingsRepository.getSelectedQuays(it.id) ?: emptySet() }

        _uiState.update {
            it.copy(
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
                autoThemeDarkLux = settingsRepository.getAutoThemeDarkLux(),
                autoThemeLightLux = settingsRepository.getAutoThemeLightLux(),
                cameraMotionWake = settingsRepository.isCameraMotionWakeEnabled(),
                cameraSensitivity = settingsRepository.getCameraSensitivity(),
                dynamicRefresh = settingsRepository.isDynamicRefreshEnabled(),
                isFullScreenMode = settingsRepository.isFullScreenMode(),
                isAlwaysOnEnabled = settingsRepository.isAlwaysOnEnabled(),
                destinationStations = settingsRepository.getDestinationStations(),
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
        val current = _uiState.value.selectedQuays[stopId] ?: emptySet()
        val allQuayIds =
            _uiState.value.favoriteStopsWithQuays[stopId]
                ?.quays
                ?.asSequence()
                ?.map { it.id }
                ?.toSet() ?: emptySet()

        val next =
            if (current.isEmpty() && allQuayIds.isNotEmpty()) {
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

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocationWithPriority(priority: Int): Location? =
        suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            fusedLocationClient
                .getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
        }

    // Play Services and the Entur lookup can fail in a dozen unrelated ways; whatever
    // goes wrong, the screen falls back to asking for coordinates by hand.
    @Suppress("TooGenericExceptionCaught")
    @SuppressLint("MissingPermission")
    fun requestLocationAndNearbyStops() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearchingNearby = true,
                    locationStatus = LocationStatus(R.string.loc_requesting_gps),
                    showManualCoordinatesPrompt = false,
                )
            }

            try {
                // 1. Try GPS (Precise)
                val gpsLocation = fetchCurrentLocationWithPriority(Priority.PRIORITY_HIGH_ACCURACY)

                if (gpsLocation != null) {
                    handleLocationSuccess(gpsLocation, R.string.gps_source)
                } else {
                    // 2. GPS failed/unavailable, try Network (Approximate)
                    _uiState.update { it.copy(locationStatus = LocationStatus(R.string.loc_requesting_network)) }
                    val networkLocation = fetchCurrentLocationWithPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)

                    if (networkLocation != null) {
                        handleLocationSuccess(networkLocation, R.string.network_source)
                    } else {
                        // 3. Both failed, fallback to manual prompt
                        handleLocationFailure(LocationStatus(R.string.loc_failed))
                    }
                }
            } catch (_: SecurityException) {
                handleLocationFailure(LocationStatus(R.string.loc_denied))
            } catch (e: Exception) {
                handleLocationFailure(LocationStatus(R.string.loc_error, e.message))
            }
        }
    }

    private fun handleLocationSuccess(
        location: Location,
        sourceResId: Int,
    ) {
        val lat = location.latitude
        val lon = location.longitude
        settingsRepository.setSavedLocation(lat, lon)
        _uiState.update {
            it.copy(
                latitude = lat,
                longitude = lon,
                locationStatus = LocationStatus(R.string.loc_success, appContext.getString(sourceResId)),
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
                    locationStatus = LocationStatus(R.string.loc_manual),
                    showManualCoordinatesPrompt = false,
                )
            }
            fetchNearbyStops(lat, lon)
        } else {
            _uiState.update {
                it.copy(locationStatus = LocationStatus(R.string.loc_invalid_format))
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
        fetchQuaysForStop(stopPlace.id)
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
        settingsRepository.addDestinationStation(stopPlace)
        _uiState.update {
            it.copy(
                destinationStations = settingsRepository.getDestinationStations(),
                destinationQuery = "",
                destinationResults = emptyList(),
            )
        }
    }

    fun removeDestinationStation(stopId: String) {
        settingsRepository.removeDestinationStation(stopId)
        _uiState.update {
            it.copy(destinationStations = settingsRepository.getDestinationStations())
        }
    }

    fun onDestinationQueryChanged(newQuery: String) {
        _uiState.update { it.copy(destinationQuery = newQuery) }
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(destinationResults = emptyList(), isSearchingDestination = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingDestination = true) }
            val results = enturRepository.searchStopPlaces(newQuery)
            _uiState.update { it.copy(destinationResults = results, isSearchingDestination = false) }
        }
    }

    fun removeFilteredLine(line: String) {
        settingsRepository.removeFilteredLine(line)
        _uiState.update { it.copy(filteredLines = settingsRepository.getFilteredLines()) }
    }
}

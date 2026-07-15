package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.*
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StopBoardViewModel(
    private val enturRepository: EnturRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val sensorManager: SystemSensorManager,
    private val cameraMotionDetector: CameraMotionDetector,
) : ViewModel() {

    private companion object {
        // Auto theme hysteresis thresholds (lux). At/below DARK -> dark, at/above LIGHT -> light,
        // and the gap in between is a dead zone that holds the current theme to avoid flapping.
        const val AUTO_THEME_DARK_LUX = 10f
        const val AUTO_THEME_LIGHT_LUX = 40f
    }

    data class UiState(
        val favorites: List<StopPlace> = emptyList(),
        val departures: List<StopDepartures> = emptyList(),
        val weather: List<WeatherInfo> = emptyList(),
        val isLoading: Boolean = false,
        val lastUpdated: String = "",
        val currentTime: String = "",
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val burnInLevel: BurnInProtectionLevel = BurnInProtectionLevel.MEDIUM,
        val refreshIntervalSeconds: Int = 60,
        val isScreenDimmed: Boolean = false,
        val showDelays: Boolean = true,
        val showMessages: Boolean = true,
        val showCancelledLines: Boolean = false,
        val activeTheme: ThemeMode = ThemeMode.SYSTEM,
        val fastestTrips: List<TripInfo> = emptyList(),
        val destinationStations: List<StopPlace> = emptyList(),
        val isFullScreenMode: Boolean = false,
        val isAlwaysOnEnabled: Boolean = false,
        val autoDimSeconds: Int = 30,
        val cameraMotionWake: Boolean = false,
        val filteredLines: Set<String> = emptySet(),
        /**
         * When the transit board is showing cached data because Entur could not be reached, this
         * is when that data was actually fetched; null while the board is live. Weather is
         * excluded on purpose: it is cache-first by MET's rules, so a cache hit there is normal.
         */
        val staleSinceMillis: Long? = null,
        /** [staleSinceMillis] as whole minutes, recomputed on each clock tick. */
        val staleAgeMinutes: Int? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var lastWeatherUpdateTime: Long = 0
    private val weatherRefreshIntervalMs = 30 * 60 * 1000L // 30 minutes

    private var rawDepartures: Map<String, List<Departure>> = emptyMap()

    init {
        loadSettings()
        startClock()
        startAutoRefresh()
        setupSensors()
        observeSettings()
    }

    private fun observeSettings() {
        settingsRepository.getFullScreenModeFlow()
            .onEach { isFullScreen ->
                _uiState.update { it.copy(isFullScreenMode = isFullScreen) }
            }
            .launchIn(viewModelScope)

        settingsRepository.getAlwaysOnEnabledFlow()
            .onEach { alwaysOn ->
                _uiState.update { it.copy(isAlwaysOnEnabled = alwaysOn) }
            }
            .launchIn(viewModelScope)

        settingsRepository.getShowCancelledLinesFlow()
            .onEach { showCancelled ->
                _uiState.update { it.copy(showCancelledLines = showCancelled) }
                updateGroupedDepartures()
            }
            .launchIn(viewModelScope)

        settingsRepository.getShowDelaysFlow()
            .onEach { showDelays ->
                _uiState.update { it.copy(showDelays = showDelays) }
            }
            .launchIn(viewModelScope)

        settingsRepository.getShowMessagesFlow()
            .onEach { showMessages ->
                _uiState.update { it.copy(showMessages = showMessages) }
            }
            .launchIn(viewModelScope)

        settingsRepository.getFilteredLinesFlow()
            .onEach { filteredLines ->
                _uiState.update { it.copy(filteredLines = filteredLines) }
                refreshDepartures()
            }
            .launchIn(viewModelScope)

        settingsRepository.getAutoDimSecondsFlow()
            .onEach { seconds ->
                _uiState.update { it.copy(autoDimSeconds = seconds) }
                // Re-trigger dim timer if currently active
                if (!uiState.value.isScreenDimmed && uiState.value.cameraMotionWake && seconds > 0) {
                    wakeScreen()
                }
            }
            .launchIn(viewModelScope)

        settingsRepository.getCameraMotionWakeFlow()
            .onEach { enabled ->
                _uiState.update { it.copy(cameraMotionWake = enabled) }
                if (enabled && !uiState.value.isScreenDimmed) {
                    wakeScreen()
                } else if (!enabled) {
                    dimJob?.cancel()
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadSettings() {
        val favs = settingsRepository.getFavorites()
        val theme = settingsRepository.getThemeMode()
        val burnIn = settingsRepository.getBurnInProtectionLevel()
        val interval = settingsRepository.getRefreshIntervalSeconds()
        val showDelays = settingsRepository.isShowDelays()
        val showMessages = settingsRepository.isShowMessages()
        val showCancelledLines = settingsRepository.isShowCancelledLines()
        val destinations = settingsRepository.getDestinationStations()
        val isFullScreen = settingsRepository.isFullScreenMode()
        val alwaysOn = settingsRepository.isAlwaysOnEnabled()
        val autoDim = settingsRepository.getAutoDimSeconds()
        val cameraWake = settingsRepository.isCameraMotionWakeEnabled()
        val filtered = settingsRepository.getFilteredLines()

        _uiState.update {
            it.copy(
                favorites = favs,
                themeMode = theme,
                activeTheme = theme,
                burnInLevel = burnIn,
                refreshIntervalSeconds = interval,
                showDelays = showDelays,
                showMessages = showMessages,
                showCancelledLines = showCancelledLines,
                destinationStations = destinations,
                fastestTrips = if (destinations.isEmpty()) emptyList() else it.fastestTrips,
                isFullScreenMode = isFullScreen,
                isAlwaysOnEnabled = alwaysOn,
                autoDimSeconds = autoDim,
                cameraMotionWake = cameraWake,
                filteredLines = filtered
            )
        }
    }

    private fun setupSensors() {
        if (_uiState.value.themeMode == ThemeMode.AUTO) {
            sensorManager.getLightFlow()
                .onEach { lux ->
                    // Hysteresis: switch to light only when clearly bright, and to dark only
                    // when clearly dim. The dead zone between the two thresholds prevents the
                    // theme from flapping when ambient light hovers near a single edge.
                    val current = _uiState.value.activeTheme
                    val autoTheme = when {
                        lux <= AUTO_THEME_DARK_LUX -> ThemeMode.DARK
                        lux >= AUTO_THEME_LIGHT_LUX -> ThemeMode.LIGHT
                        else -> current // within dead zone: keep the current theme
                    }
                    if (autoTheme != current) {
                        _uiState.update { it.copy(activeTheme = autoTheme) }
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private var dimJob: Job? = null

    fun wakeScreen() {
        dimJob?.cancel()
        _uiState.update { it.copy(isScreenDimmed = false) }
        
        // Auto-dim after configured seconds of no motion if camera motion detection is enabled
        if (uiState.value.cameraMotionWake) {
            val autoDimSeconds = uiState.value.autoDimSeconds
            if (autoDimSeconds > 0) {
                dimJob = viewModelScope.launch {
                    delay(autoDimSeconds.seconds)
                    dimScreen()
                }
            }
        }
    }

    fun dimScreen() {
        _uiState.update { it.copy(isScreenDimmed = true) }
    }

    fun isCameraMotionEnabled(): Boolean = settingsRepository.isCameraMotionWakeEnabled()

    fun bindCameraMotion(lifecycleOwner: LifecycleOwner) {
        if (settingsRepository.isCameraMotionWakeEnabled()) {
            cameraMotionDetector.getMotionFlow(lifecycleOwner)
                .onEach { motionDetected ->
                    if (motionDetected) {
                        wakeScreen()
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (true) {
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        currentTime = sdf.format(Date(now)),
                        // Derived here so the age keeps climbing while the board stays offline.
                        staleAgeMinutes = it.staleSinceMillis?.let { since ->
                            ((now - since) / 60_000L).toInt()
                        }
                    )
                }
                delay(1000.milliseconds)
            }
        }
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                refreshData()
                val seconds = calculateNextRefreshInterval()
                if (seconds <= 0) break
                delay((seconds * 1000L).milliseconds)
            }
        }
    }

    private fun calculateNextRefreshInterval(): Int {
        val base = _uiState.value.refreshIntervalSeconds
        if (!settingsRepository.isDynamicRefreshEnabled()) return base

        val calendar = Calendar.getInstance()
        val hour = calendar[Calendar.HOUR_OF_DAY]
        
        var multiplier = 1.0
        
        // Night mode (00:00 - 05:00)
        if (hour in (0..5)) multiplier *= 5.0
        
        // Unoccupied mode (if dimmed)
        if (_uiState.value.isScreenDimmed) multiplier *= 2.0
        
        return (base * multiplier).toInt()
    }

    fun refreshData() {
        refreshDepartures()
        refreshFastestTrip()
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastWeatherUpdateTime >= weatherRefreshIntervalMs) {
            refreshWeather()
            lastWeatherUpdateTime = currentTime
        }
    }

    fun refreshDepartures() {
        val favs = _uiState.value.favorites
        if (favs.isEmpty()) {
            rawDepartures = emptyMap()
            _uiState.update { it.copy(departures = emptyList(), lastUpdated = "No favorites") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val newRawDepartures = mutableMapOf<String, List<Departure>>()
            // The board is only as fresh as its stalest stop.
            var oldestCacheFetch: Long? = null

            for (stop in favs) {
                val quayIds = settingsRepository.getSelectedQuays(stop.id)
                val result = enturRepository.getDepartures(stop.id, limit = 15, quayIds = quayIds)
                result.fetchedAtMillis?.let { fetchedAt ->
                    oldestCacheFetch = minOf(oldestCacheFetch ?: fetchedAt, fetchedAt)
                }
                newRawDepartures[stop.id] = result.data.filter { it.line !in _uiState.value.filteredLines }
            }

            rawDepartures = newRawDepartures
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            // Report when the data was actually fetched, not when we last tried.
            val updateTime = timeFormat.format(Date(oldestCacheFetch ?: System.currentTimeMillis()))

            _uiState.update {
                it.copy(
                    isLoading = false,
                    lastUpdated = updateTime,
                    staleSinceMillis = oldestCacheFetch,
                    staleAgeMinutes = oldestCacheFetch?.let { since ->
                        ((System.currentTimeMillis() - since) / 60_000L).toInt()
                    }
                )
            }
            updateGroupedDepartures()
        }
    }

    private fun updateGroupedDepartures() {
        val showCancelled = _uiState.value.showCancelledLines
        val grouped = rawDepartures.map { (stopId, deps) ->
            StopDepartures(stopId, groupDepartures(deps, showCancelled))
        }
        _uiState.update { it.copy(departures = grouped) }
    }

    private fun groupDepartures(departures: List<Departure>, showCancelled: Boolean): List<DepartureGroup> {
        val filtered = if (showCancelled) departures else departures.filter { !it.isCancelled }
        val result = mutableListOf<DepartureGroup>()
        if (filtered.isNotEmpty()) {
            var currentList = mutableListOf(filtered[0])
            for (i in 1 until filtered.size) {
                val dep = filtered[i]
                val prev = filtered[i - 1]
                if (dep.line == prev.line && dep.destination == prev.destination) {
                    currentList.add(dep)
                } else {
                    result.add(DepartureGroup(currentList))
                    currentList = mutableListOf(dep)
                }
            }
            result.add(DepartureGroup(currentList))
        }
        return result
    }

    private fun refreshFastestTrip() {
        val destinations = _uiState.value.destinationStations
        val favorites = _uiState.value.favorites
        if (destinations.isEmpty() || favorites.isEmpty()) return

        viewModelScope.launch {
            val fromIds = favorites.map { it.id }
            val trips = destinations.mapNotNull { destination ->
                val result = enturRepository.getFastestTrip(fromIds = fromIds, toId = destination.id)
                result.data?.copy(destinationName = destination.name)
            }
            _uiState.update { it.copy(fastestTrips = trips) }
        }
    }

    private fun refreshWeather() {
        val lat = settingsRepository.getSavedLatitude()
        val lon = settingsRepository.getSavedLongitude()
        if (lat == null || lon == null) return

        viewModelScope.launch {
            // Cached or live, the forecast is hour-stamped and past hours are dropped, so a stale
            // one can only show fewer hours — never wrong ones. It does not raise the offline
            // banner, which tracks the transit board.
            val weather = weatherRepository.getWeather(lat, lon)
            _uiState.update { it.copy(weather = weather.data) }
        }
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.favorites.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        
        _uiState.update { it.copy(favorites = currentList) }
        settingsRepository.reorderFavorites(currentList.map { it.id })
    }

    fun filterOutLine(line: String) {
        settingsRepository.addFilteredLine(line)
    }
}

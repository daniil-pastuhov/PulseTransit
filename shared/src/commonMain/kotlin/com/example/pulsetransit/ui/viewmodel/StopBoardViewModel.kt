package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureGroup
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopDepartures
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// The constructor is the DI seam: every collaborator is injected so the board runs on the JVM in
// tests. The function count is the board's public surface plus its refresh/clock loops.
@Suppress("LongParameterList", "TooManyFunctions")
class StopBoardViewModel(
    private val enturRepository: EnturRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val sensorManager: SystemSensorManager,
    private val cameraMotionDetector: CameraMotionDetector,
    private val clockFormatter: ClockFormatter,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private companion object {
        // Auto theme hysteresis thresholds (lux). At/below DARK -> dark, at/above LIGHT -> light,
        // and the gap in between is a dead zone that holds the current theme to avoid flapping.

        // Dynamic refresh: the base interval is stretched overnight and while the
        // screen is dimmed, since nobody is reading the board in either case.
        val NIGHT_HOURS = 0..5
        const val NIGHT_REFRESH_MULTIPLIER = 5.0
        const val DIMMED_REFRESH_MULTIPLIER = 2.0

        const val MILLIS_PER_SECOND = 1000L
    }

    data class UiState(
        val favorites: List<StopPlace> = emptyList(),
        val departures: List<StopDepartures> = emptyList(),
        val weather: List<WeatherInfo> = emptyList(),
        /** Favorites whose departures are being fetched right now. */
        val loadingStopIds: Set<String> = emptySet(),
        /** Destination stations whose fastest trip is being fetched right now. */
        val loadingTripDestinationIds: Set<String> = emptySet(),
        val lastUpdated: String = "",
        val currentTime: String = "",
        val currentDayOfMonth: String = "",
        val currentWeekday: String = "",
        val currentMonth: String = "",
        val currentWeekdayLong: String = "",
        val currentMonthLong: String = "",
        val dateStyle: DateDisplayStyle = DateDisplayStyle.BESIDE,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val burnInLevel: BurnInProtectionLevel = BurnInProtectionLevel.MEDIUM,
        val refreshIntervalSeconds: Int = 60,
        val isScreenDimmed: Boolean = false,
        val showDelays: Boolean = true,
        val showMessages: Boolean = true,
        val showCancelledLines: Boolean = false,
        val autoThemeDarkLux: Float = 10f,
        val autoThemeLightLux: Float = 40f,
        val activeTheme: ThemeMode = ThemeMode.SYSTEM,
        /** Fastest trip per destination station id; a destination with no route is absent. */
        val fastestTrips: Map<String, TripInfo> = emptyMap(),
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
        val staleAgeMinutes: Int? = null,
        /**
         * Stops the user asked to search further ahead for, because nothing departs within the
         * default window. Kept for the session so the next auto-refresh doesn't empty them again.
         */
        val extendedStopIds: Set<String> = emptySet(),
    ) {
        val isLoading: Boolean get() = loadingStopIds.isNotEmpty() || loadingTripDestinationIds.isNotEmpty()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var lightSensorJob: Job? = null
    private var cameraMotionJob: Job? = null
    private var lastWeatherUpdateTime: Long = 0
    private val weatherRefreshIntervalMs = 30 * 60 * 1000L // 30 minutes

    private var rawDepartures: Map<String, List<Departure>> = emptyMap()

    /** When each stop's departures were fetched if they came from the cache; null when live. */
    private var stopFetchedAt: Map<String, Long?> = emptyMap()

    // A new refresh supersedes one still in flight instead of racing it to the UI state.
    private var departuresJob: Job? = null
    private var tripsJob: Job? = null

    init {
        loadSettings() // also starts the light sensor when the theme is AUTO
        startClock()
        startAutoRefresh()
        observeSettings()
    }

    private fun observeSettings() {
        settingsRepository
            .getFullScreenModeFlow()
            .onEach { isFullScreen -> _uiState.update { it.copy(isFullScreenMode = isFullScreen) } }
            .launchIn(viewModelScope)

        settingsRepository
            .getAlwaysOnEnabledFlow()
            .onEach { alwaysOn -> _uiState.update { it.copy(isAlwaysOnEnabled = alwaysOn) } }
            .launchIn(viewModelScope)

        settingsRepository
            .getShowDelaysFlow()
            .onEach { showDelays -> _uiState.update { it.copy(showDelays = showDelays) } }
            .launchIn(viewModelScope)

        settingsRepository
            .getShowMessagesFlow()
            .onEach { showMessages -> _uiState.update { it.copy(showMessages = showMessages) } }
            .launchIn(viewModelScope)

        settingsRepository
            .getShowCancelledLinesFlow()
            .onEach { showCancelled ->
                _uiState.update { it.copy(showCancelledLines = showCancelled) }
                updateGroupedDepartures()
            }.launchIn(viewModelScope)

        settingsRepository
            .getFilteredLinesFlow()
            .onEach { filteredLines ->
                _uiState.update { it.copy(filteredLines = filteredLines) }
                refreshDepartures()
            }.launchIn(viewModelScope)

        settingsRepository
            .getAutoDimSecondsFlow()
            .onEach { seconds ->
                _uiState.update { it.copy(autoDimSeconds = seconds) }
                if (!uiState.value.isScreenDimmed && uiState.value.cameraMotionWake && seconds > 0) {
                    wakeScreen()
                }
            }.launchIn(viewModelScope)

        settingsRepository
            .getAutoThemeDarkLuxFlow()
            .onEach { lux -> _uiState.update { it.copy(autoThemeDarkLux = lux) } }
            .launchIn(viewModelScope)

        settingsRepository
            .getAutoThemeLightLuxFlow()
            .onEach { lux -> _uiState.update { it.copy(autoThemeLightLux = lux) } }
            .launchIn(viewModelScope)

        settingsRepository
            .getCameraMotionWakeFlow()
            .onEach { enabled ->
                _uiState.update { it.copy(cameraMotionWake = enabled) }
                if (enabled && !uiState.value.isScreenDimmed) {
                    wakeScreen()
                } else if (!enabled) {
                    dimJob?.cancel()
                }
            }.launchIn(viewModelScope)
    }

    fun loadSettings() {
        val favs = settingsRepository.getFavorites()
        val theme = settingsRepository.getThemeMode()
        val burnIn = settingsRepository.getBurnInProtectionLevel()
        val dateStyle = settingsRepository.getDateDisplayStyle()
        val interval = settingsRepository.getRefreshIntervalSeconds()
        val showDelays = settingsRepository.isShowDelays()
        val showMessages = settingsRepository.isShowMessages()
        val showCancelledLines = settingsRepository.isShowCancelledLines()
        val destinations = settingsRepository.getDestinationStations()
        val isFullScreen = settingsRepository.isFullScreenMode()
        val alwaysOn = settingsRepository.isAlwaysOnEnabled()
        val autoDim = settingsRepository.getAutoDimSeconds()
        val autoDark = settingsRepository.getAutoThemeDarkLux()
        val autoLight = settingsRepository.getAutoThemeLightLux()
        val cameraWake = settingsRepository.isCameraMotionWakeEnabled()
        val filtered = settingsRepository.getFilteredLines()

        _uiState.update {
            it.copy(
                favorites = favs,
                themeMode = theme,
                activeTheme = theme,
                burnInLevel = burnIn,
                dateStyle = dateStyle,
                refreshIntervalSeconds = interval,
                showDelays = showDelays,
                showMessages = showMessages,
                showCancelledLines = showCancelledLines,
                destinationStations = destinations,
                fastestTrips = if (destinations.isEmpty()) emptyMap() else it.fastestTrips,
                isFullScreenMode = isFullScreen,
                isAlwaysOnEnabled = alwaysOn,
                autoDimSeconds = autoDim,
                autoThemeDarkLux = autoDark,
                autoThemeLightLux = autoLight,
                cameraMotionWake = cameraWake,
                filteredLines = filtered,
            )
        }

        setupSensors()
    }

    /**
     * Starts the ambient-light collector while the theme is `AUTO` and stops it otherwise. Called
     * on every [loadSettings], so leaving `AUTO` tears the collector down instead of letting it
     * keep overwriting [UiState.activeTheme], and returning to `AUTO` brings it back.
     */
    private fun setupSensors() {
        if (_uiState.value.themeMode != ThemeMode.AUTO) {
            lightSensorJob?.cancel()
            lightSensorJob = null
            return
        }
        if (lightSensorJob?.isActive == true) return

        lightSensorJob =
            sensorManager
                .getLightFlow()
                .onEach { lux ->
                    // Hysteresis: switch to light only when clearly bright, and to dark only
                    // when clearly dim. The dead zone between the two thresholds prevents the
                    // theme from flapping when ambient light hovers near a single edge.
                    val current = _uiState.value.activeTheme
                    val state = _uiState.value
                    val autoTheme =
                        when {
                            lux <= state.autoThemeDarkLux -> ThemeMode.DARK
                            lux >= state.autoThemeLightLux -> ThemeMode.LIGHT
                            else -> current // within dead zone: keep the current theme
                        }
                    if (autoTheme != current) {
                        _uiState.update { it.copy(activeTheme = autoTheme) }
                    }
                }.launchIn(viewModelScope)
    }

    private var dimJob: Job? = null

    fun wakeScreen() {
        dimJob?.cancel()
        _uiState.update { it.copy(isScreenDimmed = false) }

        // Auto-dim after configured seconds of no motion if camera motion detection is enabled
        if (uiState.value.cameraMotionWake) {
            val autoDimSeconds = uiState.value.autoDimSeconds
            if (autoDimSeconds > 0) {
                dimJob =
                    viewModelScope.launch {
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

    /**
     * Starts watching the camera for motion, if the setting is on. The camera stays open until
     * [stopCameraMotion], so the screen calls the pair from its start/stop lifecycle events.
     */
    fun startCameraMotion() {
        cameraMotionJob?.cancel()
        cameraMotionJob = null
        if (!settingsRepository.isCameraMotionWakeEnabled()) return

        cameraMotionJob =
            cameraMotionDetector
                .motionEvents()
                .onEach { wakeScreen() }
                .launchIn(viewModelScope)
    }

    fun stopCameraMotion() {
        cameraMotionJob?.cancel()
        cameraMotionJob = null
    }

    /** Advances the header date to the next [DateDisplayStyle] and remembers the choice. */
    fun cycleDateStyle() {
        val next = _uiState.value.dateStyle.next()
        settingsRepository.setDateDisplayStyle(next)
        _uiState.update { it.copy(dateStyle = next) }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                val now = clock.now().toEpochMilliseconds()
                val text = clockFormatter.clockText(now)
                _uiState.update {
                    it.copy(
                        currentTime = text.time,
                        currentDayOfMonth = text.dayOfMonth,
                        currentWeekday = text.weekdayShort,
                        currentMonth = text.monthShort,
                        currentWeekdayLong = text.weekdayLong,
                        currentMonthLong = text.monthLong,
                        // Derived here so the age keeps climbing while the board stays offline.
                        staleAgeMinutes =
                            it.staleSinceMillis?.let { since ->
                                ((now - since) / MILLIS_PER_MINUTE).toInt()
                            },
                    )
                }
                delay(1000.milliseconds)
            }
        }
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                while (true) {
                    refreshData()
                    val seconds = calculateNextRefreshInterval()
                    if (seconds <= 0) break
                    delay((seconds * MILLIS_PER_SECOND).milliseconds)
                }
            }
    }

    private fun calculateNextRefreshInterval(): Int {
        val base = _uiState.value.refreshIntervalSeconds
        if (!settingsRepository.isDynamicRefreshEnabled()) return base

        val hour = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour

        var multiplier = 1.0

        // Night mode (00:00 - 05:00)
        if (hour in NIGHT_HOURS) multiplier *= NIGHT_REFRESH_MULTIPLIER

        // Unoccupied mode (if dimmed)
        if (_uiState.value.isScreenDimmed) multiplier *= DIMMED_REFRESH_MULTIPLIER

        return (base * multiplier).toInt()
    }

    fun refreshData() {
        refreshDepartures()
        refreshFastestTrip()

        val currentTime = clock.now().toEpochMilliseconds()
        // Only a fetch that actually started counts: with no location yet, the first one is due
        // as soon as a location is saved, not 30 minutes later.
        if (currentTime - lastWeatherUpdateTime >= weatherRefreshIntervalMs && refreshWeather()) {
            lastWeatherUpdateTime = currentTime
        }
    }

    /**
     * Fetches every favorite's departures concurrently and publishes each stop the moment it
     * arrives, so the board fills in panel by panel instead of waiting for the slowest stop.
     */
    fun refreshDepartures() {
        departuresJob?.cancel()
        val favs = _uiState.value.favorites
        if (favs.isEmpty()) {
            rawDepartures = emptyMap()
            stopFetchedAt = emptyMap()
            _uiState.update {
                it.copy(departures = emptyList(), loadingStopIds = emptySet(), lastUpdated = "No favorites")
            }
            return
        }

        val favIds = favs.map { it.id }.toSet()
        // Drop stops that are no longer favorites; the rest keep showing until replaced.
        rawDepartures = rawDepartures.filterKeys { it in favIds }
        stopFetchedAt = stopFetchedAt.filterKeys { it in favIds }
        _uiState.update { it.copy(loadingStopIds = favIds) }
        updateGroupedDepartures()

        departuresJob =
            viewModelScope.launch {
                for (stop in favs) {
                    launch {
                        val quayIds = settingsRepository.getSelectedQuays(stop.id)
                        val timeRange =
                            if (stop.id in _uiState.value.extendedStopIds) {
                                EnturRepository.EXTENDED_DEPARTURE_WINDOW_SECONDS
                            } else {
                                EnturRepository.DEFAULT_DEPARTURE_WINDOW_SECONDS
                            }
                        val result =
                            enturRepository.getDepartures(
                                stop.id,
                                limit = 15,
                                quayIds = quayIds,
                                timeRangeSeconds = timeRange,
                            )
                        // The repository swallows exceptions, cancellation included, so a
                        // superseded request still returns here; don't let it overwrite the
                        // newer refresh.
                        ensureActive()
                        rawDepartures = rawDepartures + (stop.id to result.data.filter { it.line !in _uiState.value.filteredLines })
                        stopFetchedAt = stopFetchedAt + (stop.id to result.fetchedAtMillis)
                        _uiState.update { it.copy(loadingStopIds = it.loadingStopIds - stop.id) }
                        updateGroupedDepartures()
                        _uiState.update { it.withFreshness(stopFetchedAt, clock.now().toEpochMilliseconds(), clockFormatter) }
                    }
                }
            }
    }

    fun searchFurtherAhead(stopId: String) {
        _uiState.update { it.copy(extendedStopIds = it.extendedStopIds + stopId) }
        refreshDepartures()
    }

    private fun updateGroupedDepartures() {
        val showCancelled = _uiState.value.showCancelledLines
        // Follow favorites order, not arrival order, and leave out stops that haven't loaded
        // yet so the panel can tell "not loaded" apart from "no departures".
        val grouped =
            _uiState.value.favorites.mapNotNull { stop ->
                rawDepartures[stop.id]?.let { StopDepartures(stop.id, groupDepartures(it, showCancelled)) }
            }
        _uiState.update { it.copy(departures = grouped) }
    }

    /** Fetches each destination's fastest trip concurrently, publishing each as it arrives. */
    private fun refreshFastestTrip() {
        tripsJob?.cancel()
        val destinations = _uiState.value.destinationStations
        val lat = settingsRepository.getSavedLatitude()
        val lon = settingsRepository.getSavedLongitude()

        if (destinations.isEmpty()) {
            _uiState.update { it.copy(loadingTripDestinationIds = emptySet()) }
            return
        }

        val destinationIds = destinations.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                fastestTrips = it.fastestTrips.filterKeys { id -> id in destinationIds },
                loadingTripDestinationIds = destinationIds,
            )
        }

        tripsJob =
            viewModelScope.launch {
                for (destination in destinations) {
                    launch {
                        val trip =
                            if (lat != null && lon != null) {
                                enturRepository.getFastestTrip(lat = lat, lon = lon, to = destination).data
                            } else {
                                val fromIds = _uiState.value.favorites.map { it.id }
                                if (fromIds.isEmpty()) {
                                    null
                                } else {
                                    enturRepository.getFastestTrip(fromIds = fromIds, to = destination).data
                                }
                            }
                        ensureActive() // see refreshDepartures
                        _uiState.update {
                            it.copy(
                                fastestTrips =
                                    if (trip != null) {
                                        it.fastestTrips + (destination.id to trip.copy(destinationName = destination.name))
                                    } else {
                                        it.fastestTrips - destination.id
                                    },
                                loadingTripDestinationIds = it.loadingTripDestinationIds - destination.id,
                            )
                        }
                    }
                }
            }
    }

    /** Starts a weather fetch for the saved location; false when there is no location yet. */
    private fun refreshWeather(): Boolean {
        val lat = settingsRepository.getSavedLatitude()
        val lon = settingsRepository.getSavedLongitude()
        if (lat == null || lon == null) return false

        viewModelScope.launch {
            // Cached or live, the forecast is hour-stamped and past hours are dropped, so a stale
            // one can only show fewer hours — never wrong ones. It does not raise the offline
            // banner, which tracks the transit board.
            val weather = weatherRepository.getWeather(lat, lon)
            _uiState.update { it.copy(weather = weather.data) }
        }
        return true
    }

    fun reorderFavorites(
        fromIndex: Int,
        toIndex: Int,
    ) {
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

/**
 * Sets the board's freshness from each stop's cache fetch time (null for a live stop). The board
 * is only as fresh as its stalest stop, and reports when the data was actually fetched, not when
 * we last tried.
 */
private fun StopBoardViewModel.UiState.withFreshness(
    stopFetchedAt: Map<String, Long?>,
    nowMillis: Long,
    clockFormatter: ClockFormatter,
): StopBoardViewModel.UiState {
    val oldestCacheFetch = stopFetchedAt.values.filterNotNull().minOrNull()
    return copy(
        lastUpdated = clockFormatter.timeWithSeconds(oldestCacheFetch ?: nowMillis),
        staleSinceMillis = oldestCacheFetch,
        staleAgeMinutes = oldestCacheFetch?.let { since -> ((nowMillis - since) / MILLIS_PER_MINUTE).toInt() },
    )
}

private const val MILLIS_PER_MINUTE = 60_000L

/** Collapses consecutive departures of the same line to the same destination into one row. */
private fun groupDepartures(
    departures: List<Departure>,
    showCancelled: Boolean,
): List<DepartureGroup> {
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

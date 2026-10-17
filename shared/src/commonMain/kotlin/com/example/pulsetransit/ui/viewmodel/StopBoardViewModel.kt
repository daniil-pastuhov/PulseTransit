package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureGroup
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.RouteOptions
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopDepartures
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.data.distanceMeters
import com.example.pulsetransit.data.legStops
import com.example.pulsetransit.data.stopsAhead
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.ConnectivityMonitor
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
// tests. The function count is the board's public surface plus its refresh/clock loops. Its size
// is both modes sharing one departures pipeline; following the device lives in DeviceFollower.
@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
class StopBoardViewModel(
    private val enturRepository: EnturRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val sensorManager: SystemSensorManager,
    private val cameraMotionDetector: CameraMotionDetector,
    private val clockFormatter: ClockFormatter,
    private val connectivityMonitor: ConnectivityMonitor,
    private val locationProvider: LocationProvider,
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

        // While anything failed to load, try again this soon rather than waiting out the
        // (possibly stretched) normal interval, so a board recovers quickly once Entur answers.
        const val FAILURE_RETRY_SECONDS = 15

        // A stop's board starts with one page of departures and grows by a page each time the
        // user scrolls to its end, up to the cap, so an endless scroll can't grow the request
        // (refetched on every refresh) without bound.
        const val DEPARTURE_PAGE_SIZE = 15
        const val MAX_DEPARTURES = 150

        // A grown board shrinks back to one page this long after the user last asked for more,
        // so a board scrolled once doesn't refetch the bigger request on every refresh for good.
        const val LOAD_MORE_EXPIRY_MILLIS = 5 * 60 * 1000L

        // The forecast is for an area, so it is refetched early only after moving this far.
        const val WEATHER_MOVE_METERS = 1_000.0

        const val NEARBY_RADIUS_KM = 1.0
    }

    data class UiState(
        val mode: AppMode = AppMode.BOARD,
        /**
         * The stops whose departures the board shows: the saved favourites in board mode, and the
         * selected nearby stop (at most one) in on-spot mode.
         */
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
        /**
         * Route alternatives per destination station id, best first; a destination with no route
         * is absent. Board mode asks for one, on-spot mode for up to [tripOptionsCount].
         */
        val fastestTrips: Map<String, List<TripInfo>> = emptyMap(),
        /** The on-spot alternatives setting: [SettingsRepository.TRIP_OPTIONS_AUTO] or a fixed count. */
        val tripOptionsCount: Int = SettingsRepository.TRIP_OPTIONS_AUTO,
        /** On-spot mode only: finding the device. */
        val location: OnSpotLocation = OnSpotLocation.Idle,
        /** On-spot mode only: stops near the device, nearest first. */
        val nearbyStops: List<NearbyStop> = emptyList(),
        /** On-spot mode only: the stop whose departures are shown. */
        val selectedStopId: String? = null,
        /** On-spot mode only: the user picked [selectedStopId], so it no longer follows the nearest. */
        val isStopPinned: Boolean = false,
        val isSearchingNearby: Boolean = false,
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
        /**
         * Stops whose board can grow when scrolled to its end: the last fetch was live and either
         * filled its limit or can still widen to the extended window. Cached or failed boards
         * can't, since there is nothing more to show until Entur answers again, and neither can a
         * board whose last page added no rows (a single line folds every page into one row).
         */
        val canLoadMoreStopIds: Set<String> = emptySet(),
        /**
         * Stops fetching their next page. Kept apart from [loadingStopIds] on purpose: growing one
         * board is not a board refresh, so it shows only at the end of that stop's list.
         */
        val loadingMoreStopIds: Set<String> = emptySet(),
        /**
         * Favorites whose last fetch failed with nothing cached to fall back on, so their empty
         * board means "unknown", not "no departures". A failure that was covered by the cache
         * shows up as [staleSinceMillis] instead.
         */
        val failedStopIds: Set<String> = emptySet(),
        /** Destinations whose fastest trip failed to load with nothing cached. */
        val failedTripDestinationIds: Set<String> = emptySet(),
        /** Whether the device has a working connection; explains failures, never gates requests. */
        val isOnline: Boolean = true,
    ) {
        val isLoading: Boolean get() = loadingStopIds.isNotEmpty() || loadingTripDestinationIds.isNotEmpty()

        /** True while any part of the transit board is missing or stale because a fetch failed. */
        val hasLoadFailure: Boolean
            get() = failedStopIds.isNotEmpty() || failedTripDestinationIds.isNotEmpty() || staleSinceMillis != null
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

    /**
     * How many departures each stop asks for; absent means one page. Reset to one page
     * [LOAD_MORE_EXPIRY_MILLIS] after the stop's last load-more, timed by [loadMoreAtMillis].
     */
    private var departureLimits: Map<String, Int> = emptyMap()

    /** When each stop last asked for another page, by [clock]. */
    private var loadMoreAtMillis: Map<String, Long> = emptyMap()

    /**
     * Load-mores in flight: the stop's limit and row count before the page was asked for, so a
     * page that adds no rows can be undone.
     */
    private var loadMoreBases: Map<String, LoadMoreBase> = emptyMap()

    /**
     * Stops whose last page added no rows, so asking again would only fold more times into the
     * same rows. They can't load more until their grown limit expires.
     */
    private var saturatedStopIds: Set<String> = emptySet()

    private data class LoadMoreBase(
        val limit: Int,
        val groupCount: Int,
    )

    /** Stops whose last live fetch returned fewer departures than asked: the window ran out. */
    private var exhaustedStopIds: Set<String> = emptySet()

    // A new refresh supersedes one still in flight instead of racing it to the UI state.
    private var departuresJob: Job? = null

    /** Load-more fetches in flight, one per stop; a full refresh supersedes them all. */
    private var loadMoreJobs: Map<String, Job> = emptyMap()
    private var tripsJob: Job? = null

    /** On-spot: follows the device while the screen is showing (see [startLocationTracking]). */
    private val follower =
        DeviceFollower(
            locationProvider,
            clock,
            viewModelScope,
            onLocation = ::showLocation,
            onMoved = ::onDeviceMoved,
        )
    private var isTrackingRequested = false
    private var nearbyJob: Job? = null
    private var lastWeatherFix: Pair<Double, Double>? = null

    private val isOnSpot: Boolean get() = _uiState.value.mode == AppMode.ON_SPOT

    init {
        loadSettings() // also starts the light sensor when the theme is AUTO
        startClock()
        startAutoRefresh()
        observeSettings()

        var wasOnline = true
        connectivityMonitor
            .isOnlineFlow()
            .onEach { online ->
                _uiState.update { it.copy(isOnline = online) }
                // Back online: refresh now instead of waiting for the next tick. Weather is on a
                // 30-minute cadence, so a forecast that failed while offline is retried too.
                if (online && !wasOnline) {
                    if (_uiState.value.weather.isEmpty()) lastWeatherUpdateTime = 0
                    refreshData()
                }
                wasOnline = online
            }.launchIn(viewModelScope)
    }

    private fun observeSettings() {
        settingsRepository
            .getFullScreenModeFlow()
            .onEach { isFullScreen -> _uiState.update { it.copy(isFullScreenMode = isFullScreen && !isOnSpot) } }
            .launchIn(viewModelScope)

        settingsRepository
            .getAlwaysOnEnabledFlow()
            .onEach { alwaysOn -> _uiState.update { it.copy(isAlwaysOnEnabled = alwaysOn && !isOnSpot) } }
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
                // Routes avoid filtered lines too, but they are only asked for again on a change:
                // the first emission is what loadSettings already fetched them with.
                val changed = filteredLines != _uiState.value.filteredLines
                _uiState.update { it.copy(filteredLines = filteredLines) }
                refreshDepartures()
                if (changed) refreshFastestTrip()
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
            .onEach { setting ->
                val enabled = setting && !isOnSpot
                _uiState.update { it.copy(cameraMotionWake = enabled) }
                if (enabled && !uiState.value.isScreenDimmed) {
                    wakeScreen()
                } else if (!enabled) {
                    dimJob?.cancel()
                }
            }.launchIn(viewModelScope)
    }

    fun loadSettings() {
        val mode = settingsRepository.getAppMode() ?: AppMode.BOARD
        if (mode != _uiState.value.mode) switchMode(mode)
        val onSpot = mode == AppMode.ON_SPOT
        val favs = if (onSpot) _uiState.value.favorites else settingsRepository.getFavorites()
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
        val tripOptions = settingsRepository.getTripOptionsCount()

        _uiState.update {
            it.copy(
                favorites = favs,
                themeMode = theme,
                activeTheme = theme,
                // Pixel shift, auto-dim, camera wake, full screen and always-on are for the wall
                // board; a phone in the hand needs none of them.
                burnInLevel = if (onSpot) BurnInProtectionLevel.OFF else burnIn,
                dateStyle = dateStyle,
                refreshIntervalSeconds = interval,
                showDelays = showDelays,
                showMessages = showMessages,
                showCancelledLines = showCancelledLines,
                destinationStations = destinations,
                fastestTrips = if (destinations.isEmpty()) emptyMap() else it.fastestTrips,
                isFullScreenMode = isFullScreen && !onSpot,
                isAlwaysOnEnabled = alwaysOn && !onSpot,
                autoDimSeconds = autoDim,
                autoThemeDarkLux = autoDark,
                autoThemeLightLux = autoLight,
                cameraMotionWake = cameraWake && !onSpot,
                filteredLines = filtered,
                tripOptionsCount = tripOptions,
            )
        }

        setupSensors()
    }

    /**
     * Drops everything that belongs to the mode being left, so neither mode shows the other's
     * stops or routes, and starts or stops following the device to match.
     */
    private fun switchMode(mode: AppMode) {
        follower.reset()
        nearbyJob?.cancel()
        departuresJob?.cancel()
        tripsJob?.cancel()
        rawDepartures = emptyMap()
        stopFetchedAt = emptyMap()
        lastWeatherFix = null
        lastWeatherUpdateTime = 0
        _uiState.update {
            it.copy(
                mode = mode,
                favorites = emptyList(),
                departures = emptyList(),
                fastestTrips = emptyMap(),
                weather = emptyList(),
                loadingStopIds = emptySet(),
                loadingTripDestinationIds = emptySet(),
                failedStopIds = emptySet(),
                failedTripDestinationIds = emptySet(),
                staleSinceMillis = null,
                staleAgeMinutes = null,
                location = OnSpotLocation.Idle,
                nearbyStops = emptyList(),
                selectedStopId = null,
                isStopPinned = false,
                isSearchingNearby = false,
                isScreenDimmed = false,
            )
        }
        if (mode == AppMode.ON_SPOT) {
            dimJob?.cancel()
            stopCameraMotion()
            if (isTrackingRequested) follower.start()
        }
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

    fun isCameraMotionEnabled(): Boolean = !isOnSpot && settingsRepository.isCameraMotionWakeEnabled()

    /**
     * Starts watching the camera for motion, if the setting is on. The camera stays open until
     * [stopCameraMotion], so the screen calls the pair from its start/stop lifecycle events.
     */
    fun startCameraMotion() {
        cameraMotionJob?.cancel()
        cameraMotionJob = null
        if (!isCameraMotionEnabled()) return

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
                    // Wait for this round's results, so the next interval knows whether it failed.
                    awaitLatest { departuresJob }
                    awaitLatest { tripsJob }
                    var seconds = calculateNextRefreshInterval()
                    if (seconds <= 0) break
                    if (_uiState.value.hasLoadFailure) seconds = minOf(seconds, FAILURE_RETRY_SECONDS)
                    delay((seconds * MILLIS_PER_SECOND).milliseconds)
                }
            }
    }

    private fun calculateNextRefreshInterval(): Int {
        val base = _uiState.value.refreshIntervalSeconds
        // Someone holding the phone is reading it, whatever the hour.
        if (isOnSpot || !settingsRepository.isDynamicRefreshEnabled()) return base

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
        loadMoreJobs.values.forEach { it.cancel() }
        loadMoreJobs = emptyMap()
        loadMoreBases = emptyMap()
        expireGrownLimits()
        val favs = _uiState.value.favorites
        if (favs.isEmpty()) {
            rawDepartures = emptyMap()
            stopFetchedAt = emptyMap()
            _uiState.update {
                it.copy(
                    departures = emptyList(),
                    loadingStopIds = emptySet(),
                    loadingMoreStopIds = emptySet(),
                    failedStopIds = emptySet(),
                    lastUpdated = "No favorites",
                )
            }
            return
        }

        val favIds = favs.map { it.id }.toSet()
        // Drop stops that are no longer favorites; the rest keep showing until replaced.
        rawDepartures = rawDepartures.filterKeys { it in favIds }
        stopFetchedAt = stopFetchedAt.filterKeys { it in favIds }
        departureLimits = departureLimits.filterKeys { it in favIds }
        loadMoreAtMillis = loadMoreAtMillis.filterKeys { it in favIds }
        saturatedStopIds = saturatedStopIds.intersect(favIds)
        exhaustedStopIds = exhaustedStopIds.intersect(favIds)
        _uiState.update {
            it.copy(
                loadingStopIds = favIds,
                loadingMoreStopIds = emptySet(),
                failedStopIds = it.failedStopIds.intersect(favIds),
                canLoadMoreStopIds = it.canLoadMoreStopIds.intersect(favIds),
            )
        }
        updateGroupedDepartures()

        departuresJob =
            viewModelScope.launch {
                for (stop in favs) {
                    launch { fetchStopDepartures(stop) }
                }
            }
    }

    /** Drops every stop's grown limit once [LOAD_MORE_EXPIRY_MILLIS] have passed since its last load-more. */
    private fun expireGrownLimits() {
        val now = clock.now().toEpochMilliseconds()
        val expired = loadMoreAtMillis.filterValues { now - it >= LOAD_MORE_EXPIRY_MILLIS }.keys
        if (expired.isEmpty()) return
        departureLimits = departureLimits - expired
        loadMoreAtMillis = loadMoreAtMillis - expired
        saturatedStopIds = saturatedStopIds - expired
    }

    /** Fetches one stop's departures at its current limit and window, and publishes them. */
    private suspend fun fetchStopDepartures(stop: StopPlace) {
        val stopId = stop.id
        val quayIds = settingsRepository.getSelectedQuays(stopId)
        val timeRange =
            if (stopId in _uiState.value.extendedStopIds) {
                EnturRepository.EXTENDED_DEPARTURE_WINDOW_SECONDS
            } else {
                EnturRepository.DEFAULT_DEPARTURE_WINDOW_SECONDS
            }
        val limit = departureLimits[stopId] ?: DEPARTURE_PAGE_SIZE
        val result =
            enturRepository.getDepartures(
                stopId,
                limit = limit,
                quayIds = quayIds,
                timeRangeSeconds = timeRange,
            )
        // The repository swallows exceptions, cancellation included, so a superseded request
        // still returns here; don't let it overwrite the newer one.
        currentCoroutineContext().ensureActive()
        publishStopDepartures(stopId, limit, result)
        updateGroupedDepartures()
        _uiState.update { it.withFreshness(stopFetchedAt, clock.now().toEpochMilliseconds(), clockFormatter) }
    }

    /** Records one stop's answer: its departures, freshness, failure and whether it can grow. */
    private fun publishStopDepartures(
        stopId: String,
        limit: Int,
        result: Sourced<List<Departure>>,
    ) {
        val departures = result.data.filter { it.line !in _uiState.value.filteredLines }
        rawDepartures = rawDepartures + (stopId to departures)
        stopFetchedAt = stopFetchedAt + (stopId to result.fetchedAtMillis)
        val isLive = !result.failed && !result.isFromCache
        // Counted before the line filter, which is ours, not the window's.
        val exhausted = result.data.size < limit
        if (isLive) {
            exhaustedStopIds = if (exhausted) exhaustedStopIds + stopId else exhaustedStopIds - stopId
        }
        val base = loadMoreBases[stopId]
        loadMoreBases = loadMoreBases - stopId
        if (isLive && base != null) {
            // Rows, not departures, are what the user scrolled for: a page whose departures all
            // folded into rows already shown changed nothing visible, so undo it and stop asking.
            val groupCount = groupDepartures(departures, _uiState.value.showCancelledLines).size
            if (groupCount <= base.groupCount) {
                departureLimits = departureLimits + (stopId to base.limit)
                saturatedStopIds = saturatedStopIds + stopId
            }
        }
        val canLoadMore =
            isLive &&
                limit < MAX_DEPARTURES &&
                stopId !in saturatedStopIds &&
                (!exhausted || stopId !in _uiState.value.extendedStopIds)
        _uiState.update {
            it.copy(
                loadingStopIds = it.loadingStopIds - stopId,
                loadingMoreStopIds = it.loadingMoreStopIds - stopId,
                failedStopIds =
                    if (result.failed) it.failedStopIds + stopId else it.failedStopIds - stopId,
                canLoadMoreStopIds =
                    if (canLoadMore) it.canLoadMoreStopIds + stopId else it.canLoadMoreStopIds - stopId,
            )
        }
    }

    fun searchFurtherAhead(stopId: String) {
        _uiState.update { it.copy(extendedStopIds = it.extendedStopIds + stopId) }
        refreshDepartures()
    }

    /**
     * The stops [departure]'s trip still has ahead of it from [stopPlaceId], for its details. Asked
     * for only while the details are open, so it is not part of the board's state; empty when
     * Entur has no trip for it or cannot be reached.
     */
    suspend fun journeyStopsAhead(
        departure: Departure,
        stopPlaceId: String,
    ): List<JourneyStop> {
        val journeyId = departure.serviceJourneyId ?: return emptyList()
        val date = departure.serviceDate ?: return emptyList()
        return enturRepository.getJourneyStops(journeyId, date).stopsAhead(departure, stopPlaceId)
    }

    /**
     * The stops [leg] of a route rides through, boarding and alighting stops included, for the
     * route's details. Like [journeyStopsAhead], asked for only while they are open; empty for a
     * walk, or when Entur has no trip for it or cannot be reached.
     */
    suspend fun tripLegStops(leg: TripLeg): List<JourneyStop> {
        val journeyId = leg.serviceJourneyId ?: return emptyList()
        val date = leg.serviceDate ?: return emptyList()
        return enturRepository.getJourneyStops(journeyId, date).legStops(leg)
    }

    /**
     * Shows more of [stopId]'s departures, for a board scrolled to its end. Asks for another page,
     * and once the default window has run out, widens it to the extended one instead, so a quiet
     * stop keeps scrolling into the next days. Only that stop is fetched; the rest of the board is
     * left alone. A page that adds no rows is undone, and the grown limit lapses back to one page
     * [LOAD_MORE_EXPIRY_MILLIS] after the last call. Ignored while that stop is still loading, since the list stays at its end until
     * the answer arrives and would ask again on every scroll frame.
     */
    fun loadMoreDepartures(stopId: String) {
        val state = _uiState.value
        val stop = state.favorites.firstOrNull { it.id == stopId } ?: return
        if (stopId !in state.canLoadMoreStopIds || stopId in state.loadingStopIds || stopId in state.loadingMoreStopIds) return
        val limit = departureLimits[stopId] ?: DEPARTURE_PAGE_SIZE
        departureLimits = departureLimits + (stopId to minOf(limit + DEPARTURE_PAGE_SIZE, MAX_DEPARTURES))
        loadMoreAtMillis = loadMoreAtMillis + (stopId to clock.now().toEpochMilliseconds())
        val groupCount = rawDepartures[stopId]?.let { groupDepartures(it, state.showCancelledLines).size } ?: 0
        loadMoreBases = loadMoreBases + (stopId to LoadMoreBase(limit, groupCount))
        _uiState.update {
            it.copy(
                loadingMoreStopIds = it.loadingMoreStopIds + stopId,
                extendedStopIds = if (stopId in exhaustedStopIds) it.extendedStopIds + stopId else it.extendedStopIds,
            )
        }
        loadMoreJobs = loadMoreJobs + (stopId to viewModelScope.launch { fetchStopDepartures(stop) })
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

    /**
     * Fetches each destination's routes concurrently, publishing each as it arrives: one from the
     * saved location (or the favourites) in board mode, several from the device in on-spot mode.
     */
    private fun refreshFastestTrip() {
        tripsJob?.cancel()
        val destinations = _uiState.value.destinationStations
        val onSpot = isOnSpot
        val fix = follower.latestFix
        val lat = if (onSpot) fix?.latitude else settingsRepository.getSavedLatitude()
        val lon = if (onSpot) fix?.longitude else settingsRepository.getSavedLongitude()
        val count = if (onSpot) tripOptionsToFetch() else 1
        val options =
            RouteOptions(
                preference = settingsRepository.getRoutePreference(),
                excludedModes = settingsRepository.getExcludedRouteModes(),
                bannedLines = _uiState.value.filteredLines,
                walkingSpeed = settingsRepository.getWalkingSpeed(),
            )

        // On-spot mode has nowhere to route from until the device is found.
        if (destinations.isEmpty() || (onSpot && fix == null)) {
            _uiState.update { it.copy(loadingTripDestinationIds = emptySet(), failedTripDestinationIds = emptySet()) }
            return
        }

        val destinationIds = destinations.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                fastestTrips = it.fastestTrips.filterKeys { id -> id in destinationIds },
                loadingTripDestinationIds = destinationIds,
                failedTripDestinationIds = it.failedTripDestinationIds.intersect(destinationIds),
            )
        }

        tripsJob =
            viewModelScope.launch {
                for (destination in destinations) {
                    launch {
                        val result = fetchTrips(destination, lat, lon, count, options)
                        val trips = result.data.map { it.copy(destinationName = destination.name) }
                        ensureActive() // see fetchStopDepartures
                        _uiState.update {
                            it.copy(
                                fastestTrips =
                                    if (trips.isNotEmpty()) {
                                        it.fastestTrips + (destination.id to trips)
                                    } else {
                                        it.fastestTrips - destination.id
                                    },
                                loadingTripDestinationIds = it.loadingTripDestinationIds - destination.id,
                                failedTripDestinationIds =
                                    if (result.failed) {
                                        it.failedTripDestinationIds + destination.id
                                    } else {
                                        it.failedTripDestinationIds - destination.id
                                    },
                            )
                        }
                    }
                }
            }
    }

    /** The most alternatives the screen may show: the set count, or the maximum when it fits them itself. */
    private fun tripOptionsToFetch(): Int =
        _uiState.value.tripOptionsCount.takeIf { it != SettingsRepository.TRIP_OPTIONS_AUTO } ?: SettingsRepository.MAX_TRIP_OPTIONS

    /** Routes to [destination] from the coordinates, or failing those from the favourites. */
    private suspend fun fetchTrips(
        destination: StopPlace,
        lat: Double?,
        lon: Double?,
        count: Int,
        options: RouteOptions,
    ): Sourced<List<TripInfo>> {
        if (lat != null && lon != null) {
            return enturRepository.getTripOptions(lat = lat, lon = lon, to = destination, count = count, options = options)
        }
        val fromIds = _uiState.value.favorites.map { it.id }
        if (fromIds.isEmpty()) return Sourced(emptyList())
        val trip = enturRepository.getFastestTrip(fromIds = fromIds, to = destination, options = options)
        return Sourced(listOfNotNull(trip.data), trip.fetchedAtMillis, trip.failed)
    }

    /**
     * Starts a weather fetch for the saved location, or the device's in on-spot mode; false when
     * there is no location yet.
     */
    private fun refreshWeather(): Boolean {
        val lat = if (isOnSpot) follower.latestFix?.latitude else settingsRepository.getSavedLatitude()
        val lon = if (isOnSpot) follower.latestFix?.longitude else settingsRepository.getSavedLongitude()
        if (lat == null || lon == null) return false
        lastWeatherFix = lat to lon

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
        if (isOnSpot) return
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

    /**
     * Starts following the device in on-spot mode; in board mode it only remembers that the
     * screen is showing, so switching to on-spot starts it. Stays on until
     * [stopLocationTracking], so the screen calls the pair from its start/stop lifecycle events.
     */
    fun startLocationTracking() {
        isTrackingRequested = true
        if (isOnSpot) follower.start()
    }

    fun stopLocationTracking() {
        isTrackingRequested = false
        follower.stop()
    }

    /** Looks for the device again after it could not be found or access was refused. */
    fun retryLocation() {
        if (isOnSpot) follower.start()
    }

    /** Shows [stopId]'s departures and keeps it, instead of the nearest stop, until [followNearest]. */
    fun selectStop(stopId: String) {
        val state = _uiState.value
        val stop = state.nearbyStops.firstOrNull { it.stop.id == stopId }?.stop ?: return
        _uiState.update {
            it.copy(
                isStopPinned =
                    stopId !=
                        state.nearbyStops
                            .first()
                            .stop.id,
            )
        }
        showStop(stop)
    }

    /** Goes back to showing whichever stop is nearest, and following it as the device moves. */
    fun followNearest() {
        _uiState.update { it.copy(isStopPinned = false) }
        _uiState.value.nearbyStops
            .firstOrNull()
            ?.let { showStop(it.stop) }
    }

    /**
     * Shows where on-spot mode stands in finding the device. Starting to look again (each time the
     * board comes back on screen) keeps showing a location already found rather than a spinner.
     */
    private fun showLocation(location: OnSpotLocation) {
        _uiState.update {
            val keepFound =
                it.location is OnSpotLocation.Located &&
                    (location is OnSpotLocation.Locating || location is OnSpotLocation.Unavailable)
            if (keepFound) it else it.copy(location = location)
        }
    }

    /** Refreshes what depends on where the device is, now it has moved far enough to matter. */
    private fun onDeviceMoved(fix: LocationResult.Fix) {
        refreshNearbyStops(fix)
        refreshFastestTrip()
        val weatherAt = lastWeatherFix
        val movedFar =
            weatherAt == null ||
                distanceMeters(weatherAt.first, weatherAt.second, fix.latitude, fix.longitude) >= WEATHER_MOVE_METERS
        if (movedFar && refreshWeather()) lastWeatherUpdateTime = clock.now().toEpochMilliseconds()
    }

    private fun refreshNearbyStops(fix: LocationResult.Fix) {
        nearbyJob?.cancel()
        _uiState.update { it.copy(isSearchingNearby = true) }
        nearbyJob =
            viewModelScope.launch {
                val stops = enturRepository.getNearbyStopPlaces(fix.latitude, fix.longitude, NEARBY_RADIUS_KM)
                ensureActive() // see fetchStopDepartures
                val nearby =
                    stops
                        .map { stop ->
                            val distance =
                                if (stop.latitude != null && stop.longitude != null) {
                                    distanceMeters(fix.latitude, fix.longitude, stop.latitude, stop.longitude).toInt()
                                } else {
                                    null
                                }
                            NearbyStop(stop, distance)
                        }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
                applyNearbyStops(nearby)
            }
    }

    private fun applyNearbyStops(nearby: List<NearbyStop>) {
        // The geocoder fails silently to an empty list, so an empty answer while stops are already
        // listed is more likely a failed request than an empty neighbourhood: keep what we have.
        if (nearby.isEmpty() && _uiState.value.nearbyStops.isNotEmpty()) {
            _uiState.update { it.copy(isSearchingNearby = false) }
            return
        }
        val state = _uiState.value
        val keepPinned = state.isStopPinned && nearby.any { it.stop.id == state.selectedStopId }
        val selected = if (keepPinned) nearby.first { it.stop.id == state.selectedStopId } else nearby.firstOrNull()
        _uiState.update { it.copy(nearbyStops = nearby, isStopPinned = keepPinned, isSearchingNearby = false) }
        if (selected == null) {
            _uiState.update { it.copy(selectedStopId = null, favorites = emptyList()) }
            refreshDepartures()
        } else {
            showStop(selected.stop)
        }
    }

    /** Makes [stop] the one on-spot board stop, fetching its departures if it is a change. */
    private fun showStop(stop: StopPlace) {
        if (_uiState.value.selectedStopId == stop.id) return
        _uiState.update { it.copy(selectedStopId = stop.id, favorites = listOf(stop)) }
        refreshDepartures()
    }
}

/**
 * Joins the job [current] returns, following on to any newer job that supersedes it meanwhile: a
 * settings change can start a fresh refresh that cancels the one being waited on.
 */
private suspend fun awaitLatest(current: () -> Job?) {
    var job = current()
    while (job != null) {
        job.join()
        val next = current()
        if (next === job) return
        job = next
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

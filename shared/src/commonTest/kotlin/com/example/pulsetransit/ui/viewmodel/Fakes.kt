package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.CameraSensitivity
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.OnSpotSplit
import com.example.pulsetransit.data.RouteOptions
import com.example.pulsetransit.data.RoutePreference
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.ClockText
import com.example.pulsetransit.platform.ConnectivityMonitor
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** Feeds lux values to the ViewModel on demand. */
class FakeSystemSensorManager : SystemSensorManager {
    // Buffered so tests can emit without a collector already suspended on it.
    val lux = MutableSharedFlow<Float>(extraBufferCapacity = 32)

    override fun getLightFlow(): Flow<Float> = lux
}

class FakeCameraMotionDetector : CameraMotionDetector {
    val motion = MutableSharedFlow<Unit>(extraBufferCapacity = 32)

    override fun motionEvents(): Flow<Unit> = motion
}

/** Formats as recognisable tokens, so a test can tell which instant was rendered. */
class FakeClockFormatter : ClockFormatter {
    override fun clockText(epochMillis: Long) =
        ClockText(
            time = "time:$epochMillis",
            dayOfMonth = "day:$epochMillis",
            weekdayShort = "wd:$epochMillis",
            monthShort = "mon:$epochMillis",
            weekdayLong = "weekday:$epochMillis",
            monthLong = "month:$epochMillis",
        )

    override fun timeWithSeconds(epochMillis: Long) = "hms:$epochMillis"
}

class FakeConnectivityMonitor : ConnectivityMonitor {
    val online = MutableStateFlow(true)

    override fun isOnlineFlow(): Flow<Boolean> = online
}

/** Returns whatever each test sets, per stop id. */
class FakeEnturRepository : EnturRepository {
    var departures: (String) -> Sourced<List<Departure>> = { Sourced(emptyList()) }
    var trip: Sourced<TripInfo?> = Sourced(null)

    /** Per-destination override of [trip]. */
    var tripTo: (StopPlace) -> Sourced<TripInfo?> = { trip }

    /** Simulated network latency, so tests can observe results arriving one at a time. */
    var departuresDelayMillis: (String) -> Long = { 0L }
    var tripDelayMillis: (StopPlace) -> Long = { 0L }
    var departureCalls = 0
    var tripCalls = 0
    var lastRouteOptions: RouteOptions? = null
    val requestedQuays = mutableMapOf<String, Set<String>?>()
    val requestedTimeRanges = mutableMapOf<String, Int>()
    val requestedLimits = mutableMapOf<String, Int>()
    var lastFastestTripFromIds: List<String>? = null
    var lastFastestTripCoords: Pair<Double, Double>? = null
    var lastTripOptionsCount: Int? = null

    /** Route alternatives by coordinates; defaults to the single [tripTo] answer. */
    var tripOptionsTo: (StopPlace, Int) -> Sourced<List<TripInfo>> = { to, _ ->
        tripTo(to).let { Sourced(listOfNotNull(it.data), it.fetchedAtMillis, it.failed) }
    }

    /** Stops [getNearbyStopPlaces] answers with, whatever the coordinates. */
    var nearby: List<StopPlace> = emptyList()
    var nearbyCalls = 0
    var lastNearbyCoords: Pair<Double, Double>? = null

    /**
     * Waits like a network call, and like the real repository (which catches `Exception`) swallows
     * cancellation and returns anyway, so the ViewModel's own guard against stale results is tested.
     */
    @Suppress("SwallowedException")
    private suspend fun latency(millis: Long) {
        try {
            delay(millis)
        } catch (_: CancellationException) {
        }
    }

    override suspend fun searchStopPlaces(query: String) = emptyList<StopPlace>()

    /** What [searchPlaces] answers with, by query; the queries it was asked, in order. */
    var places: (String) -> List<StopPlace> = { emptyList() }
    val placeQueries = mutableListOf<String>()
    var lastPlaceFocus: Pair<Double?, Double?>? = null

    override suspend fun searchPlaces(
        query: String,
        focusLat: Double?,
        focusLon: Double?,
    ): List<StopPlace> {
        placeQueries += query
        lastPlaceFocus = focusLat to focusLon
        return places(query)
    }

    override suspend fun getNearbyStopPlaces(
        lat: Double,
        lon: Double,
        radiusKm: Double,
    ): List<StopPlace> {
        nearbyCalls++
        lastNearbyCoords = lat to lon
        return nearby
    }

    var stopsWithQuays = mapOf<String, StopPlace>()

    override suspend fun getStopPlaceWithQuays(stopId: String): StopPlace? = stopsWithQuays[stopId]

    var journeyStops = listOf<JourneyStop>()
    var journeyStopsCalls = 0

    override suspend fun getJourneyStops(
        serviceJourneyId: String,
        serviceDate: String,
    ): List<JourneyStop> {
        journeyStopsCalls++
        return journeyStops
    }

    override suspend fun getDepartures(
        stopId: String,
        limit: Int,
        quayIds: Set<String>?,
        timeRangeSeconds: Int,
    ): Sourced<List<Departure>> {
        departureCalls++
        requestedQuays[stopId] = quayIds
        requestedTimeRanges[stopId] = timeRangeSeconds
        requestedLimits[stopId] = limit
        // Answer as of when the request was sent, so a slow superseded request still carries its
        // own (older) data when it returns.
        val result = departures(stopId)
        latency(departuresDelayMillis(stopId))
        return result
    }

    override suspend fun getFastestTrip(
        fromIds: List<String>,
        to: StopPlace,
        options: RouteOptions,
    ): Sourced<TripInfo?> {
        tripCalls++
        lastRouteOptions = options
        lastFastestTripFromIds = fromIds
        val result = tripTo(to)
        latency(tripDelayMillis(to))
        return result
    }

    override suspend fun getTripOptions(
        lat: Double,
        lon: Double,
        to: StopPlace,
        count: Int,
        options: RouteOptions,
    ): Sourced<List<TripInfo>> {
        tripCalls++
        lastRouteOptions = options
        lastFastestTripCoords = lat to lon
        lastTripOptionsCount = count
        val result = tripOptionsTo(to, count)
        latency(tripDelayMillis(to))
        return result
    }
}

class FakeWeatherRepository : WeatherRepository {
    var weather: Sourced<List<WeatherInfo>> = Sourced(emptyList())
    var calls = 0

    /** How long a fetch takes, so a test can change settings while one is in flight. */
    var delayMillis = 0L

    override suspend fun getWeather(
        lat: Double,
        lon: Double,
    ): Sourced<List<WeatherInfo>> {
        calls++
        if (delayMillis > 0) delay(delayMillis)
        return weather
    }
}

/**
 * In-memory settings. Values are public vars so a test can set up state before the ViewModel reads
 * them in `init`, and the flows are hot so tests can push changes afterwards.
 */
class FakeSettingsRepository : SettingsRepository {
    var mode: AppMode? = AppMode.BOARD
    var tripOptions = SettingsRepository.TRIP_OPTIONS_AUTO
    val appModeFlow = MutableStateFlow<AppMode?>(AppMode.BOARD)

    override fun getAppMode() = mode

    override fun setAppMode(mode: AppMode) {
        this.mode = mode
        appModeFlow.value = mode
    }

    override fun getAppModeFlow(): Flow<AppMode?> = appModeFlow

    override fun getTripOptionsCount() = tripOptions

    override fun setTripOptionsCount(count: Int) {
        tripOptions = count
    }

    var split = OnSpotSplit.DEFAULT

    override fun getOnSpotSplit() = split

    override fun setOnSpotSplit(split: Float) {
        this.split = split
    }

    var favoriteStops = listOf<StopPlace>()
    var theme = ThemeMode.SYSTEM
    var refreshInterval = 60
    var burnInLevel = BurnInProtectionLevel.MEDIUM
    var dateStyle = DateDisplayStyle.BESIDE
    var latitude: Double? = 59.9149
    var longitude: Double? = 10.7322
    var dynamicRefresh = false
    var weatherShown = true
    var destinations = listOf<StopPlace>()
    var selectedQuays = mapOf<String, Set<String>>()
    var sensitivity = CameraSensitivity.MEDIUM

    // The flow is the single source of truth for every setting the ViewModel also observes.
    // Backing these with separate vars would let a test set a value that `observeSettings`
    // immediately overwrites with the flow's stale initial emission.
    val autoDimFlow = MutableStateFlow(30)
    val autoThemeDarkLuxFlow = MutableStateFlow(10f)
    val autoThemeLightLuxFlow = MutableStateFlow(40f)
    val showDelaysFlow = MutableStateFlow(true)
    val showMessagesFlow = MutableStateFlow(true)
    val cameraMotionFlow = MutableStateFlow(false)
    val fullScreenFlow = MutableStateFlow(false)
    val alwaysOnFlow = MutableStateFlow(false)
    val showCancelledFlow = MutableStateFlow(false)
    val filteredFlow = MutableStateFlow(emptySet<String>())

    var autoDim: Int
        get() = autoDimFlow.value
        set(value) {
            autoDimFlow.value = value
        }
    var darkLux: Float
        get() = autoThemeDarkLuxFlow.value
        set(value) {
            autoThemeDarkLuxFlow.value = value
        }
    var lightLux: Float
        get() = autoThemeLightLuxFlow.value
        set(value) {
            autoThemeLightLuxFlow.value = value
        }
    var delaysShown: Boolean
        get() = showDelaysFlow.value
        set(value) {
            showDelaysFlow.value = value
        }
    var messagesShown: Boolean
        get() = showMessagesFlow.value
        set(value) {
            showMessagesFlow.value = value
        }
    var showCancelled: Boolean
        get() = showCancelledFlow.value
        set(value) {
            showCancelledFlow.value = value
        }
    var cameraMotionWake: Boolean
        get() = cameraMotionFlow.value
        set(value) {
            cameraMotionFlow.value = value
        }
    var fullScreen: Boolean
        get() = fullScreenFlow.value
        set(value) {
            fullScreenFlow.value = value
        }
    var alwaysOn: Boolean
        get() = alwaysOnFlow.value
        set(value) {
            alwaysOnFlow.value = value
        }
    var filtered: Set<String>
        get() = filteredFlow.value
        set(value) {
            filteredFlow.value = value
        }

    var preference = RoutePreference.FASTEST
    var excludedModes = setOf<RouteTransportMode>()
    var walking = WalkingSpeed.NORMAL

    override fun getWalkingSpeed() = walking

    override fun setWalkingSpeed(speed: WalkingSpeed) {
        walking = speed
    }

    override fun getRoutePreference() = preference

    override fun setRoutePreference(preference: RoutePreference) {
        this.preference = preference
    }

    override fun getExcludedRouteModes() = excludedModes

    override fun setExcludedRouteModes(modes: Set<RouteTransportMode>) {
        excludedModes = modes
    }

    override fun getFavorites() = favoriteStops

    override fun addFavorite(stopPlace: StopPlace) {}

    override fun removeFavorite(stopId: String) {}

    override fun reorderFavorites(stopIds: List<String>) {}

    override fun isFavorite(stopId: String) = favoriteStops.any { it.id == stopId }

    override fun getRefreshIntervalSeconds() = refreshInterval

    override fun setRefreshIntervalSeconds(seconds: Int) {
        refreshInterval = seconds
    }

    override fun getAutoDimSeconds() = autoDim

    override fun setAutoDimSeconds(seconds: Int) {
        autoDim = seconds
    }

    override fun getAutoDimSecondsFlow(): Flow<Int> = autoDimFlow

    override fun getAutoThemeDarkLux() = darkLux

    override fun setAutoThemeDarkLux(lux: Float) {
        darkLux = lux
    }

    override fun getAutoThemeDarkLuxFlow(): Flow<Float> = autoThemeDarkLuxFlow

    override fun getAutoThemeLightLux() = lightLux

    override fun setAutoThemeLightLux(lux: Float) {
        lightLux = lux
    }

    override fun getAutoThemeLightLuxFlow(): Flow<Float> = autoThemeLightLuxFlow

    override fun getFilteredLines() = filtered

    override fun addFilteredLine(line: String) {
        filtered = filtered + line
    }

    override fun removeFilteredLine(line: String) {
        filtered = filtered - line
    }

    override fun getFilteredLinesFlow(): Flow<Set<String>> = filteredFlow

    override fun getThemeMode() = theme

    override fun setThemeMode(mode: ThemeMode) {
        theme = mode
    }

    override fun getBurnInProtectionLevel() = burnInLevel

    override fun setBurnInProtectionLevel(level: BurnInProtectionLevel) {
        burnInLevel = level
    }

    override fun getDateDisplayStyle() = dateStyle

    override fun setDateDisplayStyle(style: DateDisplayStyle) {
        dateStyle = style
    }

    override fun getSavedLatitude() = latitude

    override fun getSavedLongitude() = longitude

    override fun setSavedLocation(
        lat: Double?,
        lon: Double?,
    ) {
        latitude = lat
        longitude = lon
    }

    override fun isShowDelays() = delaysShown

    override fun setShowDelays(show: Boolean) {
        delaysShown = show
    }

    override fun getShowDelaysFlow(): Flow<Boolean> = showDelaysFlow

    override fun isShowMessages() = messagesShown

    override fun setShowMessages(show: Boolean) {
        messagesShown = show
    }

    override fun getShowMessagesFlow(): Flow<Boolean> = showMessagesFlow

    override fun getSelectedQuays(stopId: String): Set<String>? = selectedQuays[stopId]

    override fun setSelectedQuays(
        stopId: String,
        quayIds: Set<String>?,
    ) {
        selectedQuays = if (quayIds == null) selectedQuays - stopId else selectedQuays + (stopId to quayIds)
    }

    override fun isCameraMotionWakeEnabled() = cameraMotionWake

    override fun setCameraMotionWakeEnabled(enabled: Boolean) {
        cameraMotionWake = enabled
    }

    override fun getCameraMotionWakeFlow(): Flow<Boolean> = cameraMotionFlow

    override fun getCameraSensitivity() = sensitivity

    override fun setCameraSensitivity(sensitivity: CameraSensitivity) {
        this.sensitivity = sensitivity
    }

    override fun isShowWeather() = weatherShown

    override fun setShowWeather(show: Boolean) {
        weatherShown = show
    }

    override fun isDynamicRefreshEnabled() = dynamicRefresh

    override fun setDynamicRefreshEnabled(enabled: Boolean) {
        dynamicRefresh = enabled
    }

    override fun getDestinationStations() = destinations

    override fun addDestinationStation(stopPlace: StopPlace) {
        destinations = destinations + stopPlace
    }

    override fun removeDestinationStation(stopId: String) {}

    override fun isFullScreenMode() = fullScreen

    override fun setFullScreenMode(enabled: Boolean) {
        fullScreen = enabled
    }

    override fun getFullScreenModeFlow(): Flow<Boolean> = fullScreenFlow

    override fun isAlwaysOnEnabled() = alwaysOn

    override fun setAlwaysOnEnabled(enabled: Boolean) {
        alwaysOn = enabled
    }

    override fun getAlwaysOnEnabledFlow(): Flow<Boolean> = alwaysOnFlow

    var dismissedUpdate: String? = null

    override fun getDismissedUpdateVersion() = dismissedUpdate

    override fun setDismissedUpdateVersion(version: String) {
        dismissedUpdate = version
    }

    override fun isShowCancelledLines() = showCancelled

    override fun setShowCancelledLines(show: Boolean) {
        showCancelled = show
    }

    override fun getShowCancelledLinesFlow(): Flow<Boolean> = showCancelledFlow
}

/**
 * Answers each accuracy with whatever the test queued, recording what was asked for. Continuous
 * updates come from [updates], one hot flow per accuracy that a test emits fixes into.
 */
class FakeLocationProvider : LocationProvider {
    var results: Map<LocationAccuracy, LocationResult> = emptyMap()
    val requested = mutableListOf<LocationAccuracy>()

    val updates = LocationAccuracy.entries.associateWith { MutableSharedFlow<LocationResult>(extraBufferCapacity = 16) }
    val updateRequests = mutableListOf<LocationAccuracy>()

    /** How many update flows are being collected right now, i.e. how many "GPS sessions" are open. */
    var activeCollectors = 0
        private set

    override suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult {
        requested += accuracy
        return results[accuracy] ?: LocationResult.Unavailable
    }

    override fun locationUpdates(
        accuracy: LocationAccuracy,
        minDistanceMeters: Float,
    ): Flow<LocationResult> =
        updates
            .getValue(accuracy)
            .onStart {
                updateRequests += accuracy
                activeCollectors++
            }.onCompletion { activeCollectors-- }
}

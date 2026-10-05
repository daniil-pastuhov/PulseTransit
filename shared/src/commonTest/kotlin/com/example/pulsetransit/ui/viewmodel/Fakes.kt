package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.CameraSensitivity
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.ClockText
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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

/** Returns whatever each test sets, per stop id. */
class FakeEnturRepository : EnturRepository {
    var departures: (String) -> Sourced<List<Departure>> = { Sourced(emptyList()) }
    var trip: Sourced<TripInfo?> = Sourced(null)
    val requestedQuays = mutableMapOf<String, Set<String>?>()
    val requestedTimeRanges = mutableMapOf<String, Int>()
    var lastFastestTripFromIds: List<String>? = null
    var lastFastestTripCoords: Pair<Double, Double>? = null

    override suspend fun searchStopPlaces(query: String) = emptyList<StopPlace>()

    override suspend fun searchPlaces(
        query: String,
        focusLat: Double?,
        focusLon: Double?,
    ) = emptyList<StopPlace>()

    override suspend fun getNearbyStopPlaces(
        lat: Double,
        lon: Double,
        radiusKm: Double,
    ) = emptyList<StopPlace>()

    override suspend fun getStopPlaceWithQuays(stopId: String): StopPlace? = null

    override suspend fun getDepartures(
        stopId: String,
        limit: Int,
        quayIds: Set<String>?,
        timeRangeSeconds: Int,
    ): Sourced<List<Departure>> {
        requestedQuays[stopId] = quayIds
        requestedTimeRanges[stopId] = timeRangeSeconds
        return departures(stopId)
    }

    override suspend fun getFastestTrip(
        fromIds: List<String>,
        to: StopPlace,
    ): Sourced<TripInfo?> {
        lastFastestTripFromIds = fromIds
        return trip
    }

    override suspend fun getFastestTrip(
        lat: Double,
        lon: Double,
        to: StopPlace,
    ): Sourced<TripInfo?> {
        lastFastestTripCoords = lat to lon
        return trip
    }
}

class FakeWeatherRepository : WeatherRepository {
    var weather: Sourced<List<WeatherInfo>> = Sourced(emptyList())
    var calls = 0

    override suspend fun getWeather(
        lat: Double,
        lon: Double,
    ): Sourced<List<WeatherInfo>> {
        calls++
        return weather
    }
}

/**
 * In-memory settings. Values are public vars so a test can set up state before the ViewModel reads
 * them in `init`, and the flows are hot so tests can push changes afterwards.
 */
class FakeSettingsRepository : SettingsRepository {
    var favoriteStops = listOf<StopPlace>()
    var theme = ThemeMode.SYSTEM
    var refreshInterval = 60
    var burnInLevel = BurnInProtectionLevel.MEDIUM
    var dateStyle = DateDisplayStyle.BESIDE
    var latitude: Double? = 59.9149
    var longitude: Double? = 10.7322
    var dynamicRefresh = false
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
    ) {}

    override fun isCameraMotionWakeEnabled() = cameraMotionWake

    override fun setCameraMotionWakeEnabled(enabled: Boolean) {
        cameraMotionWake = enabled
    }

    override fun getCameraMotionWakeFlow(): Flow<Boolean> = cameraMotionFlow

    override fun getCameraSensitivity() = sensitivity

    override fun setCameraSensitivity(sensitivity: CameraSensitivity) {
        this.sensitivity = sensitivity
    }

    override fun isDynamicRefreshEnabled() = dynamicRefresh

    override fun setDynamicRefreshEnabled(enabled: Boolean) {
        dynamicRefresh = enabled
    }

    override fun getDestinationStations() = destinations

    override fun addDestinationStation(stopPlace: StopPlace) {}

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

    override fun isShowCancelledLines() = showCancelled

    override fun setShowCancelledLines(show: Boolean) {
        showCancelled = show
    }

    override fun getShowCancelledLinesFlow(): Flow<Boolean> = showCancelledFlow
}

/** Answers each accuracy with whatever the test queued, recording what was asked for. */
class FakeLocationProvider : LocationProvider {
    var results: Map<LocationAccuracy, LocationResult> = emptyMap()
    val requested = mutableListOf<LocationAccuracy>()

    override suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult {
        requested += accuracy
        return results[accuracy] ?: LocationResult.Unavailable
    }
}

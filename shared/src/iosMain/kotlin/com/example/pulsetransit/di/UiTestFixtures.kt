package com.example.pulsetransit.di

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.RouteOptions
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.platform.AppUpdate
import com.example.pulsetransit.platform.AppUpdateChecker
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.offsetAt
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock
import kotlin.time.Instant

/*
 * Fixed data for the iOS UI tests, used when the debug app is launched with -PTUITest YES: a board
 * at Oslo S with one route to Majorstuen that starts a minute after launch, so its first journey
 * alert is due at once. Nothing reaches the network, and settings live in their own store, so the
 * tests start from a known state and never touch the user's.
 */

private const val SUITE = "com.homieapps.pulsetransit.uitest"
private const val MINUTE = 60_000L
private const val DEPARTURE_COUNT = 4
private const val DEPARTURE_GAP_MINUTES = 3

private val osloS = StopPlace(id = "NSR:StopPlace:337", name = "Oslo S", locality = "Oslo", latitude = 59.9111, longitude = 10.7528)
private val majorstuen =
    StopPlace(id = "NSR:StopPlace:58381", name = "Majorstuen", locality = "Oslo", latitude = 59.9297, longitude = 10.7155)

/** The test store, emptied first when [reset]: a test that relaunches to check restoring keeps it. */
internal fun uiTestSettings(reset: Boolean): Settings {
    val defaults = NSUserDefaults(suiteName = SUITE)
    if (reset) defaults.removePersistentDomainForName(SUITE)
    return NSUserDefaultsSettings(defaults)
}

/** Seeds the board the tests expect, once per store. */
internal fun seedUiTestSettings(settings: SettingsRepository) {
    if (settings.getAppMode() != null) return
    settings.setAppMode(AppMode.BOARD)
    settings.addFavorite(osloS)
    settings.addDestinationStation(majorstuen)
    settings.setShowWeather(false)
}

internal fun uiTestModule(launchMillis: Long) =
    module {
        single<EnturRepository> { FixtureEnturRepository(launchMillis) }
        single<WeatherRepository> { FixtureWeatherRepository() }
        single<AppUpdateChecker> { NoUpdate() }
    }

private fun iso(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis / 1000 * 1000)
    return instant.format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET, TimeZone.of("Europe/Oslo").offsetAt(instant))
}

/**
 * Oslo S to Majorstuen, timed from [base]: walk 2 min to Jernbanetorget from base + 1 min, metro 5
 * from base + 4 min to base + 10 min, then walk 3 min to the destination.
 */
private class FixtureEnturRepository(
    private val base: Long,
) : EnturRepository {
    private fun at(minutes: Int) = iso(base + minutes * MINUTE)

    private val trip =
        TripInfo(
            destinationName = majorstuen.name,
            expectedStartTime = at(1),
            expectedEndTime = at(13),
            lineName = "5",
            durationMinutes = 12,
            originName = "Jernbanetorget",
            legs =
                listOf(
                    TripLeg(
                        mode = "foot",
                        fromName = "Oslo S",
                        toName = "Jernbanetorget",
                        durationMinutes = 2,
                        expectedStartTime = at(1),
                        expectedEndTime = at(3),
                        distanceMeters = 200,
                        toLatitude = 59.9118,
                        toLongitude = 10.7500,
                    ),
                    TripLeg(
                        mode = "metro",
                        lineName = "5",
                        fromName = "Jernbanetorget",
                        toName = "Majorstuen",
                        durationMinutes = 6,
                        expectedStartTime = at(4),
                        expectedEndTime = at(10),
                        aimedStartTime = at(4),
                        isRealtime = true,
                        platform = "2",
                        destination = "Vestli",
                        serviceJourneyId = "RUT:ServiceJourney:5",
                        serviceDate = at(0).take(10),
                        fromQuayId = "NSR:Quay:1",
                        toQuayId = "NSR:Quay:4",
                        fromLatitude = 59.9118,
                        fromLongitude = 10.7500,
                        toLatitude = 59.9297,
                        toLongitude = 10.7155,
                    ),
                    TripLeg(
                        mode = "foot",
                        fromName = "Majorstuen",
                        toName = "Destination",
                        durationMinutes = 3,
                        expectedStartTime = at(10),
                        expectedEndTime = at(13),
                        distanceMeters = 250,
                        fromLatitude = 59.9297,
                        fromLongitude = 10.7155,
                        toLatitude = 59.9290,
                        toLongitude = 10.7120,
                    ),
                ),
        )

    private val metroStops =
        listOf(
            JourneyStop("NSR:Quay:1", "NSR:StopPlace:1", "Jernbanetorget", at(4), latitude = 59.9118, longitude = 10.7500),
            JourneyStop("NSR:Quay:2", "NSR:StopPlace:2", "Stortinget", at(6), latitude = 59.9133, longitude = 10.7390),
            JourneyStop("NSR:Quay:3", "NSR:StopPlace:3", "Nationaltheatret", at(7), latitude = 59.9149, longitude = 10.7317),
            JourneyStop("NSR:Quay:4", "NSR:StopPlace:4", "Majorstuen", at(10), latitude = 59.9297, longitude = 10.7155),
        )

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
    ) = listOf(osloS)

    override suspend fun getDepartures(
        stopId: String,
        limit: Int,
        quayIds: Set<String>?,
        timeRangeSeconds: Int,
    ): Sourced<List<Departure>> {
        val now = Clock.System.now().toEpochMilliseconds()
        return Sourced.live(
            (1..DEPARTURE_COUNT).map { n ->
                val at = iso(now + n * DEPARTURE_GAP_MINUTES * MINUTE)
                Departure(
                    line = "L$n",
                    destination = "Destination $n",
                    expectedTime = at,
                    aimedTime = at,
                    isRealtime = true,
                    transportMode = "rail",
                )
            },
        )
    }

    override suspend fun getStopPlaceWithQuays(stopId: String) = osloS

    override suspend fun getJourneyStops(
        serviceJourneyId: String,
        serviceDate: String,
    ) = metroStops

    override suspend fun getFastestTrip(
        fromIds: List<String>,
        to: StopPlace,
        options: RouteOptions,
    ): Sourced<TripInfo?> = Sourced.live(trip)

    override suspend fun getTripOptions(
        lat: Double,
        lon: Double,
        to: StopPlace,
        count: Int,
        options: RouteOptions,
    ): Sourced<List<TripInfo>> = Sourced.live(listOf(trip))
}

private class FixtureWeatherRepository : WeatherRepository {
    override suspend fun getWeather(
        lat: Double,
        lon: Double,
    ) = Sourced.live(emptyList<WeatherInfo>())
}

private class NoUpdate : AppUpdateChecker {
    override suspend fun availableUpdate(): AppUpdate? = null
}

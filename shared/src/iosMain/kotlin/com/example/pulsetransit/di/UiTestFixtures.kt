package com.example.pulsetransit.di

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.DataSource
import com.example.pulsetransit.data.DataSourceMode
import com.example.pulsetransit.data.DataSourceRepository
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
 * alert is due at once, and on the wall board later ones as its alternatives. Nothing reaches the
 * network, and settings live in their own store, so the tests start from a known state and never
 * touch the user's.
 */

private const val SUITE = "com.homieapps.pulsetransit.uitest"
private const val MINUTE = 60_000L
private const val DEPARTURE_COUNT = 4
private const val DEPARTURE_GAP_MINUTES = 3

/** The metro lines of the board's alternatives to Majorstuen, [ALTERNATIVE_GAP_MINUTES] apart after the first route. */
private val ALTERNATIVE_LINES = listOf("3", "2", "5")
private const val ALTERNATIVE_GAP_MINUTES = 3

private val osloS = StopPlace(id = "NSR:StopPlace:337", name = "Oslo S", locality = "Oslo", latitude = 59.9111, longitude = 10.7528)
private val majorstuen =
    StopPlace(id = "NSR:StopPlace:58381", name = "Majorstuen", locality = "Oslo", latitude = 59.9297, longitude = 10.7155)

/** The more destinations of [UiTestScenario.THREE]: one route with a train, one with three transfers. */
private val lillestrom = StopPlace(id = "NSR:StopPlace:62339", name = "Lillestrøm stasjon", locality = "Lillestrøm")
private val gardermoen = StopPlace(id = "NSR:StopPlace:58211", name = "Oslo lufthavn Gardermoen", locality = "Ullensaker")

/**
 * Which board the fixtures seed, from the launch argument -PTUITestScenario: by default one
 * destination, Majorstuen; THREE also routes to Lillestrøm (a train) and Gardermoen (walk, bus 31,
 * walk, metro 5, tram 17, walk, FLY1), for looking at the route cards' layouts. ONSPOT is on-spot
 * mode with no destination, for looking at the route prompt; ONSPOTRECENT the same with recent places
 * to suggest. On-spot mode needs a location: set one on the simulator (Oslo S finds the fixtures' stop).
 */
private enum class UiTestScenario { ONE, THREE, ONSPOT, ONSPOTRECENT }

private fun uiTestScenario(): UiTestScenario {
    val name = NSUserDefaults.standardUserDefaults.stringForKey("PTUITestScenario")
    return UiTestScenario.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UiTestScenario.ONE
}

/** What any place search finds, so a test can add a second destination. */
private val nationaltheatret =
    StopPlace(id = "NSR:StopPlace:58404", name = "Nationaltheatret", locality = "Oslo", latitude = 59.9147, longitude = 10.7312)

/** The test store, emptied first when [reset]: a test that relaunches to check restoring keeps it. */
internal fun uiTestSettings(reset: Boolean): Settings {
    val defaults = NSUserDefaults(suiteName = SUITE)
    if (reset) defaults.removePersistentDomainForName(SUITE)
    return NSUserDefaultsSettings(defaults)
}

/** Seeds the board the tests expect, once per store. */
internal fun seedUiTestSettings(settings: SettingsRepository) {
    if (settings.getAppMode() != null) return
    val scenario = uiTestScenario()
    if (scenario == UiTestScenario.ONSPOT || scenario == UiTestScenario.ONSPOTRECENT) {
        settings.setAppMode(AppMode.ON_SPOT)
        if (scenario == UiTestScenario.ONSPOTRECENT) {
            // Added oldest first, so Majorstuen is the newest.
            listOf(gardermoen, lillestrom, nationaltheatret, majorstuen).forEach { settings.addRecentDestination(it) }
        }
        settings.setShowWeather(false)
        return
    }
    settings.setAppMode(AppMode.BOARD)
    settings.addFavorite(osloS)
    settings.addDestinationStation(majorstuen)
    if (scenario == UiTestScenario.THREE) {
        settings.addDestinationStation(lillestrom)
        settings.addDestinationStation(gardermoen)
    }
    // -PTUITestExpanded YES opens every card's alternatives, for looking at them.
    if (NSUserDefaults.standardUserDefaults.boolForKey("PTUITestExpanded")) {
        settings.getDestinationStations().forEach { settings.setRouteAlternativesShown(it.id, true) }
    }
    settings.setShowWeather(false)
}

internal fun uiTestModule(launchMillis: Long) =
    module {
        single<EnturRepository> { FixtureEnturRepository(launchMillis) }
        single<WeatherRepository> { FixtureWeatherRepository() }
        single<AppUpdateChecker> { NoUpdate() }
        single<DataSourceRepository> { FixtureDataSourceRepository() }
    }

private fun iso(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis / 1000 * 1000)
    return instant.format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET, TimeZone.of("Europe/Oslo").offsetAt(instant))
}

/**
 * Oslo S to Majorstuen, timed from [base]: walk 2 min to Jernbanetorget from base + 1 min, metro 5
 * from base + 4 min to base + 10 min, then walk 3 min to the destination. The alternatives are the
 * same journey later, on other lines.
 */
private class FixtureEnturRepository(
    private val base: Long,
) : EnturRepository {
    private fun at(minutes: Int) = iso(base + minutes * MINUTE)

    /** The journey with the metro on [line], later minutes after the first route. */
    private val route: (line: String, later: Int) -> TripInfo = { line, later ->
        fun at(minutes: Int) = iso(base + (minutes + later) * MINUTE)
        TripInfo(
            destinationName = majorstuen.name,
            expectedStartTime = at(1),
            expectedEndTime = at(13),
            lineName = line,
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
                        lineName = line,
                        fromName = "Jernbanetorget",
                        toName = "Majorstuen",
                        durationMinutes = 6,
                        expectedStartTime = at(4),
                        expectedEndTime = at(10),
                        aimedStartTime = at(4),
                        isRealtime = true,
                        platform = "2",
                        destination = "Vestli",
                        serviceJourneyId = if (later == 0) "RUT:ServiceJourney:5" else "RUT:ServiceJourney:$line-$later",
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
    }

    private val trip = route("5", 0)

    /** One ride or walk of a fixture route: a line (null for a walk), its mode, from, to and minutes. */
    private class Part(
        val line: String?,
        val mode: String,
        val from: String,
        val to: String,
        val minutes: Int,
        val platform: String? = null,
    )

    /** A route to [to] leaving [leave] minutes after launch, its [parts] end to end, a minute's wait before each ride. */
    private fun routeOf(
        to: StopPlace,
        leave: Int,
        parts: List<Part>,
    ): TripInfo {
        var minute = leave
        val legs =
            parts.map { part ->
                if (part.line != null) minute += 1
                val start = minute
                minute += part.minutes
                TripLeg(
                    mode = part.mode,
                    lineName = part.line,
                    fromName = part.from,
                    toName = part.to,
                    durationMinutes = part.minutes,
                    expectedStartTime = at(start),
                    expectedEndTime = at(minute),
                    aimedStartTime = at(start),
                    isRealtime = part.line != null,
                    platform = part.platform,
                    destination = part.line?.let { to.name },
                    serviceJourneyId = part.line?.let { "RUT:ServiceJourney:$it-$start" },
                    serviceDate = part.line?.let { at(0).take(10) },
                )
            }
        return TripInfo(
            destinationName = to.name,
            expectedStartTime = at(leave),
            expectedEndTime = at(minute),
            lineName = parts.firstOrNull { it.line != null }?.line ?: "?",
            durationMinutes = minute - leave,
            originName = osloS.name,
            legs = legs,
        )
    }

    private val lillestromRoutes =
        listOf("R12" to 5, "R14" to 15, "R13" to 25, "RE11" to 35).map { (line, leave) ->
            routeOf(
                lillestrom,
                leave,
                listOf(Part(null, "foot", "Kirkegata", "Oslo S", 2), Part(line, "rail", "Oslo S", lillestrom.name, 11, platform = "11")),
            )
        }

    private val gardermoenRoutes =
        listOf(
            routeOf(
                gardermoen,
                5,
                listOf(
                    Part(null, "foot", "Kirkegata", "Jernbanetorget", 4),
                    Part("31", "bus", "Jernbanetorget", "Tøyen", 6, platform = "E"),
                    Part(null, "foot", "Tøyen", "Tøyen", 3),
                    Part("5", "metro", "Tøyen", "Storo", 9, platform = "1"),
                    Part("17", "tram", "Storo", "Grefsen stasjon", 4),
                    Part(null, "foot", "Grefsen", "Grefsen stasjon", 6),
                    Part("FLY1", "airportRail", "Grefsen stasjon", gardermoen.name, 18, platform = "2"),
                ),
            ),
            routeOf(
                gardermoen,
                16,
                listOf(
                    Part(null, "foot", "Kirkegata", "Stortorvet", 5),
                    Part("37", "bus", "Stortorvet", "Carl Berners plass", 7, platform = "B"),
                    Part("4", "metro", "Carl Berners plass", "Sinsen", 3),
                    Part(null, "foot", "Sinsen", "Grefsen stasjon", 5),
                    Part("FLY2", "airportRail", "Grefsen stasjon", gardermoen.name, 19, platform = "2"),
                ),
            ),
            routeOf(
                gardermoen,
                24,
                listOf(Part(null, "foot", "Kirkegata", "Oslo S", 4), Part("FLY1", "airportRail", "Oslo S", gardermoen.name, 22, platform = "13")),
            ),
            routeOf(
                gardermoen,
                35,
                listOf(Part(null, "foot", "Kirkegata", "Oslo bussterminal", 2), Part("FB2", "coach", "Oslo bussterminal", gardermoen.name, 50, platform = "1")),
            ),
        )

    /** Each destination's routes, best first. */
    private fun routesTo(to: StopPlace): List<TripInfo> =
        when (to.id) {
            lillestrom.id -> lillestromRoutes
            gardermoen.id -> gardermoenRoutes
            else -> listOf(trip) + alternatives
        }

    private val alternatives = ALTERNATIVE_LINES.mapIndexed { index, line -> route(line, (index + 1) * ALTERNATIVE_GAP_MINUTES) }

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
    ) = if (query.isBlank()) emptyList() else listOf(nationaltheatret)

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
    ): Sourced<TripInfo?> = Sourced.live(routesTo(to).first())

    override suspend fun getTripOptionsFromStops(
        fromIds: List<String>,
        to: StopPlace,
        count: Int,
        options: RouteOptions,
    ): Sourced<List<TripInfo>> = Sourced.live(routesTo(to).take(count.coerceAtLeast(1)))

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

private class FixtureDataSourceRepository : DataSourceRepository {
    override suspend fun getDataSources() =
        listOf(
            DataSource("Bygdøyfergen", "https://nyc.no/", listOf(DataSourceMode.BOAT)),
            DataSource("NOR-WAY Bussekspress", "https://nor-way.no", listOf(DataSourceMode.BUS, DataSourceMode.COACH)),
            DataSource(
                "Ruter",
                "https://ruter.no/",
                listOf(DataSourceMode.BUS, DataSourceMode.METRO, DataSourceMode.TRAM, DataSourceMode.BOAT),
            ),
            DataSource("Vy", "https://www.vy.no/", listOf(DataSourceMode.TRAIN)),
        )
}

private class NoUpdate : AppUpdateChecker {
    override suspend fun availableUpdate(): AppUpdate? = null
}

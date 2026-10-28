package com.example.pulsetransit.data.widget

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.isoOslo
import com.example.pulsetransit.ui.viewmodel.FakeEnturRepository
import com.example.pulsetransit.ui.viewmodel.FakeSettingsRepository
import com.example.pulsetransit.ui.viewmodel.FakeWeatherRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class BoardWidgetSourceTest {
    private val now = 1_800_000_000_000L
    private val entur = FakeEnturRepository()
    private val weather = FakeWeatherRepository()
    private val settings = FakeSettingsRepository()
    private val source =
        BoardWidgetSource(
            enturRepository = entur,
            weatherRepository = weather,
            settingsRepository = settings,
            clock =
                object : Clock {
                    override fun now() = Instant.fromEpochMilliseconds(now)
                },
            timeZone = TimeZone.of("Europe/Oslo"),
        )

    private val oslo = StopPlace("NSR:StopPlace:59872", "Oslo S", latitude = 59.911, longitude = 10.750)
    private val majorstuen = StopPlace("NSR:StopPlace:58381", "Majorstuen", latitude = 59.929, longitude = 10.715)

    private fun departure(
        line: String,
        inMinutes: Int,
    ) = Departure(line, "Somewhere", isoOslo(now + inMinutes * 60_000L), isoOslo(now + inMinutes * 60_000L), isRealtime = true)

    private fun trip(inMinutes: Int) =
        TripInfo("Majorstuen", isoOslo(now + inMinutes * 60_000L), isoOslo(now + (inMinutes + 15) * 60_000L), "2", 15, "Home")

    @Test
    fun `departures without a favourite say so`() =
        runTest {
            assertEquals(DeparturesWidgetState.NoStops, source.departures(null, maxRows = 3, timesPerRow = 2))
        }

    @Test
    fun `departures ask for the stop's selected quays and apply the line filter`() =
        runTest {
            settings.favoriteStops = listOf(oslo)
            settings.selectedQuays = mapOf(oslo.id to setOf("NSR:Quay:1"))
            settings.filtered = setOf("17")
            entur.departures = { Sourced(listOf(departure("17", 1), departure("31", 2))) }

            val state = assertIs<DeparturesWidgetState.Board>(source.departures(null, maxRows = 3, timesPerRow = 2))

            assertEquals(setOf("NSR:Quay:1"), entur.requestedQuays[oslo.id])
            assertEquals(listOf("31"), state.rows.map { it.line })
        }

    @Test
    fun `cached departures carry their fetch time`() =
        runTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.cached(listOf(departure("31", 2)), fetchedAtMillis = now - 600_000L) }

            val state = assertIs<DeparturesWidgetState.Board>(source.departures(null, maxRows = 3, timesPerRow = 2))

            assertEquals(now - 600_000L, state.freshness.staleSinceMillis)
        }

    @Test
    fun `routes go from the saved location with the route settings`() =
        runTest {
            settings.destinations = listOf(majorstuen)
            settings.filtered = setOf("17")
            entur.tripOptionsTo = { _, _ -> Sourced(listOf(trip(3))) }

            val state = assertIs<RouteWidgetState.Route>(source.route(null))

            assertEquals<Pair<Double?, Double?>?>(settings.latitude to settings.longitude, entur.lastFastestTripCoords)
            assertEquals(1, entur.lastTripOptionsCount)
            assertEquals(setOf("17"), entur.lastRouteOptions?.bannedLines)
            assertEquals(now + 3 * 60_000L, state.route?.leaveAtMillis)
        }

    @Test
    fun `on the spot routes and weather are for where the device last was`() =
        runTest {
            settings.mode = AppMode.ON_SPOT
            settings.lastDeviceFix = 59.95 to 10.80
            settings.destinations = listOf(majorstuen)
            entur.tripOptionsTo = { _, _ -> Sourced(listOf(trip(3))) }

            source.route(null)
            source.glance()

            assertEquals<Pair<Double?, Double?>?>(59.95 to 10.80, entur.lastFastestTripCoords)
            assertEquals(59.95 to 10.80, weather.lastCoords)
        }

    @Test
    fun `on the spot with no fix yet the saved location is used`() =
        runTest {
            settings.mode = AppMode.ON_SPOT
            settings.destinations = listOf(majorstuen)
            entur.tripOptionsTo = { _, _ -> Sourced(listOf(trip(3))) }

            source.route(null)

            assertEquals<Pair<Double?, Double?>?>(settings.latitude to settings.longitude, entur.lastFastestTripCoords)
        }

    @Test
    fun `a wall board routes from its saved location wherever the device was`() =
        runTest {
            settings.lastDeviceFix = 59.95 to 10.80
            settings.destinations = listOf(majorstuen)
            entur.tripOptionsTo = { _, _ -> Sourced(listOf(trip(3))) }

            source.route(null)

            assertEquals<Pair<Double?, Double?>?>(settings.latitude to settings.longitude, entur.lastFastestTripCoords)
        }

    @Test
    fun `without a saved location routes go from the favourites`() =
        runTest {
            settings.latitude = null
            settings.longitude = null
            settings.favoriteStops = listOf(oslo)
            settings.destinations = listOf(majorstuen)
            entur.trip = Sourced(trip(3))

            assertIs<RouteWidgetState.Route>(source.route(null))
            assertEquals(listOf(oslo.id), entur.lastFastestTripFromIds)
        }

    @Test
    fun `with nowhere to route from the widget says so`() =
        runTest {
            settings.latitude = null
            settings.longitude = null
            settings.destinations = listOf(majorstuen)

            assertEquals(RouteWidgetState.NoOrigin(majorstuen), source.route(null))
        }

    @Test
    fun `a saved location at the destination is not routed`() =
        runTest {
            settings.latitude = majorstuen.latitude
            settings.longitude = majorstuen.longitude
            settings.destinations = listOf(majorstuen)

            assertEquals(RouteWidgetState.AtDestination(majorstuen), source.route(null))
            assertEquals(0, entur.tripCalls)
        }

    @Test
    fun `a route that starts with a walk begun a moment ago is still offered`() =
        runTest {
            settings.destinations = listOf(majorstuen)
            val walkFirst =
                trip(0).copy(
                    expectedStartTime = isoOslo(now - 1_000L),
                    legs =
                        listOf(
                            TripLeg(mode = "foot", expectedStartTime = isoOslo(now - 1_000L)),
                            TripLeg(mode = "metro", lineName = "2", expectedStartTime = isoOslo(now + 4 * 60_000L)),
                        ),
                )
            entur.tripOptionsTo = { _, _ -> Sourced(listOf(walkFirst)) }

            val state = assertIs<RouteWidgetState.Route>(source.route(null))

            assertEquals(now + 4 * 60_000L, state.route?.boardAtMillis)
        }

    @Test
    fun `a cached route that has left is not offered`() =
        runTest {
            settings.destinations = listOf(majorstuen)
            entur.tripOptionsTo = { _, _ -> Sourced.cached(listOf(trip(-5)), fetchedAtMillis = now - 900_000L) }

            val state = assertIs<RouteWidgetState.Route>(source.route(null))

            assertNull(state.route)
            assertEquals(now - 900_000L, state.freshness.staleSinceMillis)
        }

    @Test
    fun `the glance shows the current forecast and the first favourite's next departure`() =
        runTest {
            settings.favoriteStops = listOf(oslo, majorstuen)
            weather.weather = Sourced(listOf(WeatherInfo("t", 7.5, "cloudy"), WeatherInfo("t2", 8.0, "rain")))
            entur.departures = { stopId ->
                Sourced(if (stopId == oslo.id) listOf(departure("31", 2), departure("1", 3)) else emptyList())
            }

            val state = source.glance()

            assertEquals(7.5, state.weather?.temperature)
            assertEquals(oslo, state.stop)
            assertEquals("31", state.next?.line)
        }

    @Test
    fun `the glance does not fetch weather while it is turned off`() =
        runTest {
            settings.weatherShown = false

            val state = source.glance()

            assertNull(state.weather)
            assertEquals(0, weather.calls)
            assertNull(state.stop)
            assertFalse(state.freshness.failed)
        }
}

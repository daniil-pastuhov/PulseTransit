package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.BoardRouteAlternatives
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Board mode's route alternatives: how many are fetched and which cards show them. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardRouteAlternativesTest : BoardTestBase() {
    private val bergen = StopPlace(id = "NSR:StopPlace:2", name = "Bergen")
    private val home = StopPlace(id = "NSR:StopPlace:9", name = "Home")

    @Test
    fun `from the saved location the board asks for the first route and its alternatives`() =
        boardTest {
            settings.destinations = listOf(oslo)
            entur.tripOptionsTo = { _, count -> Sourced(List(count) { trip("Oslo S") }) }

            val vm = viewModel(virtualClock())

            val trips = vm.uiState.value.fastestTrips[oslo.id]
            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION, entur.lastTripOptionsCount)
            assertNull(entur.lastTripOptionsFromStopsCount)
            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION, trips?.size)
        }

    @Test
    fun `without a saved location the alternatives come from the favourites`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            settings.favoriteStops = listOf(home)
            settings.destinations = listOf(oslo)
            entur.tripOptionsFromStopsTo = { _, count -> Sourced(List(count) { trip("Oslo S") }, fetchedAtMillis = 1_000L) }

            val vm = viewModel(virtualClock())

            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION, entur.lastTripOptionsFromStopsCount)
            assertEquals(listOf(home.id), entur.lastFastestTripFromIds)
            assertNull(entur.lastTripOptionsCount)
            val trips = vm.uiState.value.fastestTrips[oslo.id]
            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION, trips?.size)
            assertEquals(1, entur.tripCalls, "one call per destination, as for one route")
        }

    @Test
    fun `showing alternatives is kept across loadSettings and a restart`() =
        boardTest {
            settings.destinations = listOf(oslo, bergen)
            val vm = viewModel(virtualClock())
            assertFalse(vm.uiState.value.showsRouteAlternatives(oslo.id), "collapsed by default")

            vm.toggleRouteAlternatives(oslo.id)
            vm.loadSettings()

            assertEquals(setOf(oslo.id), vm.uiState.value.routeAlternativesShownIds)
            assertTrue(vm.uiState.value.showsRouteAlternatives(oslo.id))
            assertFalse(vm.uiState.value.showsRouteAlternatives(bergen.id))
            assertEquals(setOf(oslo.id), settings.alternativesShown)
            assertEquals(setOf(oslo.id), viewModel(virtualClock()).uiState.value.routeAlternativesShownIds, "a restart keeps it")

            vm.toggleRouteAlternatives(oslo.id)
            assertEquals(emptySet(), vm.uiState.value.routeAlternativesShownIds)
            assertEquals(emptySet(), settings.alternativesShown)
        }

    @Test
    fun `refreshing and dimming never close alternatives`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel(virtualClock())
            vm.toggleRouteAlternatives(oslo.id)

            vm.dimScreen()
            advanceTimeBy(125.seconds)
            runCurrent()
            vm.wakeScreen()
            runCurrent()

            assertFalse(vm.uiState.value.isScreenDimmed)
            assertTrue(entur.tripCalls > 1, "routes were refreshed")
            assertTrue(vm.uiState.value.showsRouteAlternatives(oslo.id))
        }

    @Test
    fun `Settings writes the same choice the board reads`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel(virtualClock())

            settings.setRouteAlternativesShown(oslo.id, true) // what Settings' switch does
            vm.loadSettings() // on coming back to the board

            assertTrue(vm.uiState.value.showsRouteAlternatives(oslo.id))
        }

    @Test
    fun `removing a destination forgets that its alternatives were shown`() =
        boardTest {
            settings.destinations = listOf(oslo, bergen)
            val vm = viewModel(virtualClock())
            vm.toggleRouteAlternatives(oslo.id)
            vm.toggleRouteAlternatives(bergen.id)

            settings.removeDestinationStation(oslo.id)
            vm.loadSettings()

            assertEquals(setOf(bergen.id), vm.uiState.value.routeAlternativesShownIds)
            assertFalse(vm.uiState.value.showsRouteAlternatives(oslo.id))
        }

    @Test
    fun `only saved destinations can show alternatives`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel(virtualClock())

            vm.toggleRouteAlternatives("NSR:StopPlace:unknown")
            vm.showTemporaryDestination(bergen)
            runCurrent()
            vm.toggleRouteAlternatives(bergen.id)

            assertEquals(emptySet(), vm.uiState.value.routeAlternativesShownIds)
            assertFalse(vm.uiState.value.showsRouteAlternatives(bergen.id))
        }

    private val departed = "2000-01-01T12:00:00+01:00"
    private val later = "2999-01-01T12:00:00+01:00"

    @Test
    fun `a card offers its alternatives only while it has some to show`() =
        boardTest {
            settings.destinations = listOf(oslo, bergen)
            val laterTrip = trip("Oslo S").copy(expectedStartTime = later)
            entur.tripOptionsTo = { to, _ -> Sourced(if (to.id == oslo.id) listOf(trip("Oslo S"), laterTrip) else listOf(trip("Bergen"))) }
            val vm = viewModel(virtualClock())
            val now = virtualClock().now().toEpochMilliseconds()

            assertTrue(vm.uiState.value.canToggleRouteAlternatives(oslo.id, now))
            assertFalse(vm.uiState.value.canToggleRouteAlternatives(bergen.id, now), "only one route: nothing to open")

            vm.toggleRouteAlternatives(bergen.id)
            assertFalse(vm.uiState.value.canToggleRouteAlternatives(bergen.id, now), "no \"Fewer\" with nothing under it")
            assertTrue(vm.uiState.value.showsRouteAlternatives(bergen.id), "still open as chosen")
        }

    @Test
    fun `an open card whose alternatives have left hides its control until new ones come`() =
        boardTest {
            settings.destinations = listOf(oslo)
            settings.alternativesShown = setOf(oslo.id)
            var alternativeTime = departed
            entur.tripOptionsTo = { _, _ ->
                Sourced(listOf(trip("Oslo S").copy(expectedStartTime = later), trip("Oslo S").copy(expectedStartTime = alternativeTime)))
            }
            val vm = viewModel(virtualClock())
            val now = virtualClock().now().toEpochMilliseconds()

            assertFalse(vm.uiState.value.canToggleRouteAlternatives(oslo.id, now))
            assertTrue(vm.uiState.value.showsRouteAlternatives(oslo.id))
            assertEquals(setOf(oslo.id), settings.alternativesShown, "the choice is kept")

            alternativeTime = later
            vm.refreshNow()
            runCurrent()

            assertTrue(vm.uiState.value.canToggleRouteAlternatives(oslo.id, now), "back once there are alternatives")
            assertTrue(vm.uiState.value.showsRouteAlternatives(oslo.id))
        }

    @Test
    fun `the searched-for destination's banner has no control`() =
        boardTest {
            settings.destinations = listOf(oslo)
            entur.tripOptionsTo = { to, _ -> Sourced(List(4) { trip(to.name).copy(expectedStartTime = later) }) }
            val vm = viewModel(virtualClock())
            val now = virtualClock().now().toEpochMilliseconds()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(
                4,
                vm.uiState.value.fastestTrips[bergen.id]
                    ?.size,
            )
            assertFalse(vm.uiState.value.canToggleRouteAlternatives(bergen.id, now))
        }

    @Test
    fun `on the spot no card shows board alternatives`() =
        boardTest {
            settings.destinations = listOf(oslo)
            settings.alternativesShown = setOf(oslo.id)
            settings.mode = AppMode.ON_SPOT
            val vm = viewModel(virtualClock())

            assertFalse(vm.uiState.value.showsRouteAlternatives(oslo.id))
        }

    @Test
    fun `opening the search on a saved destination's routes leaves the board as it was`() =
        boardTest {
            settings.destinations = listOf(oslo)
            settings.alternativesShown = setOf(oslo.id)
            entur.tripOptionsTo = { _, _ -> Sourced(List(4) { trip("Oslo S").copy(expectedStartTime = later) }) }
            val vm = viewModel(virtualClock())
            val before = vm.uiState.value

            // "+N in details" opens the sheet on Oslo S without picking it; closing it clears the search.
            vm.clearRouteSearch()
            // Picking it again in the sheet, routed for now, shows the routes the board has.
            vm.showTemporaryDestination(oslo)
            runCurrent()

            val after = vm.uiState.value
            assertNull(after.temporaryDestination)
            assertEquals(before.routeDestinations, after.routeDestinations)
            assertEquals(before.fastestTrips, after.fastestTrips)
            assertTrue(after.showsRouteAlternatives(oslo.id))
        }
}

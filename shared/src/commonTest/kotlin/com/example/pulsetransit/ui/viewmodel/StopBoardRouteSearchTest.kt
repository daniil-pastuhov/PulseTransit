package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.PlaceKind
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The board's route search and the temporary destination it picks. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardRouteSearchTest : BoardTestBase() {
    private val bergen = StopPlace(id = "NSR:StopPlace:2", name = "Bergen", kind = PlaceKind.STOP)
    private val address = StopPlace(id = "addr:1", name = "Storgata 1", kind = PlaceKind.ADDRESS, latitude = 59.9, longitude = 10.7)

    @Test
    fun `a temporary destination is routed to and listed before the saved ones`() =
        boardTest {
            settings.destinations = listOf(oslo)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            vm.showTemporaryDestination(address)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(address, state.temporaryDestination)
            assertEquals(listOf(address, oslo), state.routeDestinations)
            assertEquals("Storgata 1", state.fastestTrips[address.id]?.single()?.destinationName)
            assertTrue(oslo.id in state.fastestTrips, "the saved destination keeps its route")
            assertEquals(listOf(oslo), settings.destinations, "showing a place does not save it")
        }

    @Test
    fun `a temporary destination is routed to without any saved ones`() =
        boardTest {
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertTrue(bergen.id in vm.uiState.value.fastestTrips)
        }

    @Test
    fun `dismissing the temporary destination drops it and its route`() =
        boardTest {
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()

            vm.dismissTemporaryDestination()

            val state = vm.uiState.value
            assertNull(state.temporaryDestination)
            assertTrue(state.routeDestinations.isEmpty())
            assertFalse(bergen.id in state.fastestTrips)
        }

    @Test
    fun `a route that arrives after its temporary destination was dismissed is ignored`() =
        boardTest {
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            entur.tripDelayMillis = { 1_000L }
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()

            vm.dismissTemporaryDestination()
            advanceTimeBy(1_001L)
            runCurrent()

            assertFalse(bergen.id in vm.uiState.value.fastestTrips)
            assertTrue(
                vm.uiState.value.loadingTripDestinationIds
                    .isEmpty(),
            )
        }

    @Test
    fun `saving the temporary destination keeps its route without asking again`() =
        boardTest {
            settings.destinations = listOf(oslo)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()
            val calls = entur.tripCalls

            vm.saveTemporaryDestination()
            runCurrent()
            // Past the next clock tick, which rechecks which saved destinations are shown.
            advanceTimeBy(1_001)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(listOf(oslo, bergen), settings.destinations)
            assertEquals(listOf(oslo, bergen), state.destinationStations)
            assertEquals(listOf(oslo, bergen), state.routeDestinations)
            assertNull(state.temporaryDestination)
            assertTrue(bergen.id in state.fastestTrips)
            assertEquals(calls, entur.tripCalls)
        }

    @Test
    fun `the temporary destination cannot be saved once the destinations are full`() =
        boardTest {
            settings.destinations = List(SettingsRepository.MAX_DESTINATION_STATIONS) { StopPlace("NSR:StopPlace:x$it", "X$it") }
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertFalse(vm.uiState.value.canSaveTemporaryDestination)
            vm.saveTemporaryDestination()

            assertEquals(bergen, vm.uiState.value.temporaryDestination)
            assertEquals(SettingsRepository.MAX_DESTINATION_STATIONS, settings.destinations.size)
        }

    @Test
    fun `picking a destination that is already saved adds no temporary one`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel()

            vm.showTemporaryDestination(oslo)

            assertNull(vm.uiState.value.temporaryDestination)
            assertEquals(listOf(oslo), vm.uiState.value.routeDestinations)
        }

    @Test
    fun `a temporary destination saved in Settings meanwhile becomes a saved one`() =
        boardTest {
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()

            settings.destinations = listOf(bergen)
            vm.loadSettings()

            assertNull(vm.uiState.value.temporaryDestination)
            assertEquals(listOf(bergen), vm.uiState.value.routeDestinations)
        }

    @Test
    fun `the search waits for typing to pause and only the last query is asked`() =
        boardTest {
            settings.latitude = 60.0
            settings.longitude = 11.0
            entur.places = { query -> if (query == "Berg") listOf(bergen) else emptyList() }
            val vm = viewModel()

            vm.onRouteSearchQueryChanged("Be")
            advanceTimeBy(100L)
            vm.onRouteSearchQueryChanged("Berg")
            runCurrent()
            assertTrue(vm.uiState.value.isSearchingRoutePlaces)
            advanceTimeBy(301L)
            runCurrent()

            assertEquals(listOf("Berg"), entur.placeQueries)
            assertEquals(60.0 to 11.0, entur.lastPlaceFocus, "board mode searches near the saved location")
            assertEquals(listOf(bergen), vm.uiState.value.routeSearchResults)
            assertFalse(vm.uiState.value.isSearchingRoutePlaces)
        }

    @Test
    fun `picking a result clears the search`() =
        boardTest {
            entur.places = { listOf(bergen) }
            val vm = viewModel()
            vm.onRouteSearchQueryChanged("Berg")
            advanceTimeBy(301L)
            runCurrent()

            vm.showTemporaryDestination(bergen)

            val state = vm.uiState.value
            assertEquals("", state.routeSearchQuery)
            assertTrue(state.routeSearchResults.isEmpty())
        }

    @Test
    fun `on-spot mode routes to the temporary destination from the device`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            entur.tripOptionsTo = { to, count -> Sourced.live(List(count) { trip(to.name) }) }
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()
            check(location.updates.getValue(LocationAccuracy.PRECISE).tryEmit(LocationResult.Fix(59.91, 10.75)))
            runCurrent()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(59.91 to 10.75, entur.lastFastestTripCoords)
            assertEquals(
                SettingsRepository.MAX_TRIP_OPTIONS,
                vm.uiState.value.fastestTrips[bergen.id]
                    ?.size,
            )
        }

    @Test
    fun `switching modes drops the temporary destination`() =
        boardTest {
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()

            settings.mode = AppMode.ON_SPOT
            vm.loadSettings()

            assertNull(vm.uiState.value.temporaryDestination)
        }
}

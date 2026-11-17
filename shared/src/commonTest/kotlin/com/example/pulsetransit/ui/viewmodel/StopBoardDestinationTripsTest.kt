package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.BoardRouteAlternatives
import com.example.pulsetransit.data.DestinationTrips
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripTime
import com.example.pulsetransit.data.isoOslo
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** One destination's longer list of routes, opened from its card. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardDestinationTripsTest : BoardTestBase() {
    private val bergen = StopPlace(id = "NSR:StopPlace:2", name = "Bergen")

    /** [count] routes leaving every 5 minutes from [fromMinutes] after the clock's now. */
    private fun routes(
        clock: Clock,
        count: Int,
        fromMinutes: Int = 2,
    ): List<TripInfo> {
        val now = clock.now().toEpochMilliseconds()
        return List(count) { index ->
            TripInfo(
                destinationName = "",
                expectedStartTime = isoOslo(now + (fromMinutes + index * 5) * minute),
                lineName = "L${fromMinutes + index * 5}",
                durationMinutes = 20,
                originName = "Origin",
            )
        }
    }

    @Test
    fun `opening a destination fetches more routes than its card from the same origin`() =
        boardTest {
            val clock = virtualClock()
            settings.destinations = listOf(oslo)
            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count)) }
            val vm = viewModel(clock)
            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION, entur.lastTripOptionsCount)

            vm.openDestinationTrips(oslo.id)
            val opening = vm.uiState.value.destinationTrips
            assertNotNull(opening)
            assertTrue(opening.isLoading)
            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION, opening.trips.size, "starts from the card's routes")
            runCurrent()

            val list = assertNotNull(vm.uiState.value.destinationTrips)
            assertEquals(DestinationTrips.FIRST_PAGE, entur.lastTripOptionsCount)
            assertEquals(DestinationTrips.FIRST_PAGE, list.trips.size)
            assertFalse(list.isLoading)
            assertTrue(list.canLoadLater)
            assertEquals("Oslo S", list.trips.first().destinationName)
            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION, vm.uiState.value.fastestTrips[oslo.id]?.size, "the card keeps its own")
        }

    @Test
    fun `later asks for routes leaving after the last one listed`() =
        boardTest {
            val clock = virtualClock()
            settings.destinations = listOf(oslo)
            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count)) }
            val vm = viewModel(clock)
            vm.openDestinationTrips(oslo.id)
            runCurrent()
            val last = assertNotNull(vm.uiState.value.destinationTrips).trips.last()

            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count, fromMinutes = 2 + DestinationTrips.FIRST_PAGE * 5)) }
            vm.loadLaterDestinationTrips()
            assertTrue(assertNotNull(vm.uiState.value.destinationTrips).isLoadingLater)
            runCurrent()

            val time = entur.lastRouteOptions?.time
            assertTrue(time is TripTime.DepartAt)
            assertEquals(
                DestinationTrips(oslo, trips = listOf(last)).laterPageTime(),
                time,
            )
            assertEquals(DestinationTrips.LATER_PAGE, entur.lastTripOptionsCount)
            val list = assertNotNull(vm.uiState.value.destinationTrips)
            assertEquals(DestinationTrips.FIRST_PAGE + DestinationTrips.LATER_PAGE, list.trips.size)
            assertFalse(list.isLoadingLater)
        }

    @Test
    fun `the open list is refreshed with the board and keeps its later routes`() =
        boardTest {
            val clock = virtualClock()
            settings.destinations = listOf(oslo)
            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count)) }
            val vm = viewModel(clock)
            vm.openDestinationTrips(oslo.id)
            runCurrent()
            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count, fromMinutes = 2 + DestinationTrips.FIRST_PAGE * 5)) }
            vm.loadLaterDestinationTrips()
            runCurrent()
            val callsBefore = entur.tripCalls

            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count)) }
            vm.refreshData() // what the auto-refresh loop runs
            runCurrent()

            assertTrue(entur.tripCalls > callsBefore, "routes were refreshed")
            val list = assertNotNull(vm.uiState.value.destinationTrips)
            assertEquals(DestinationTrips.FIRST_PAGE + DestinationTrips.LATER_PAGE, list.trips.size)
        }

    @Test
    fun `offline the listed routes stay and say so`() =
        boardTest {
            val clock = virtualClock()
            settings.destinations = listOf(oslo)
            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count)) }
            val vm = viewModel(clock)
            vm.openDestinationTrips(oslo.id)
            runCurrent()

            entur.tripOptionsTo = { _, _ -> Sourced.failed(emptyList()) }
            vm.refreshDestinationTrips()
            runCurrent()

            val list = assertNotNull(vm.uiState.value.destinationTrips)
            assertTrue(list.failed)
            assertEquals(DestinationTrips.FIRST_PAGE, list.trips.size)
            assertFalse(list.canLoadLater)
        }

    @Test
    fun `a searched-for place is listed at its own time`() =
        boardTest {
            val clock = virtualClock()
            settings.destinations = listOf(oslo)
            val vm = viewModel(clock)
            val at = clock.now().toEpochMilliseconds() + 60 * minute
            vm.setTripTime(TripTime.DepartAt(at))
            vm.showTemporaryDestination(bergen)
            runCurrent()

            vm.openDestinationTrips(bergen.id)
            runCurrent()

            assertEquals(TripTime.DepartAt(at), entur.routeOptionsTo[bergen.id]?.time)
        }

    @Test
    fun `on the spot the list routes from the device`() =
        boardTest {
            val clock = virtualClock()
            settings.mode = AppMode.ON_SPOT
            settings.destinations = listOf(oslo)
            entur.tripOptionsTo = { _, count -> Sourced(routes(clock, count)) }
            val vm = viewModel(clock)
            vm.startLocationTracking()
            runCurrent()
            check(location.updates.getValue(LocationAccuracy.PRECISE).tryEmit(LocationResult.Fix(59.91, 10.75)))
            runCurrent()

            vm.openDestinationTrips(oslo.id)
            runCurrent()

            assertEquals(59.91 to 10.75, entur.lastFastestTripCoords)
            assertEquals(DestinationTrips.FIRST_PAGE, assertNotNull(vm.uiState.value.destinationTrips).trips.size)
        }

    @Test
    fun `closing forgets the list and an unknown destination opens nothing`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel(virtualClock())

            vm.openDestinationTrips("NSR:StopPlace:unknown")
            assertNull(vm.uiState.value.destinationTrips)

            vm.openDestinationTrips(oslo.id)
            vm.closeDestinationTrips()
            runCurrent()
            assertNull(vm.uiState.value.destinationTrips)
        }
}

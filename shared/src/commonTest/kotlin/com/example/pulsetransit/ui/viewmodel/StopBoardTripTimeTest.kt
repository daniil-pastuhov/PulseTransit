package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.PlaceKind
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** A departure or arrival time on the board's route search. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardTripTimeTest : BoardTestBase() {
    private val bergen = StopPlace(id = "NSR:StopPlace:2", name = "Bergen", kind = PlaceKind.STOP)
    private val hour = 60 * 60 * 1000L

    private fun inAnHour() = Clock.System.now().toEpochMilliseconds() + hour

    @Test
    fun `the time applies to the searched destination only`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            settings.destinations = listOf(oslo)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            val arriveBy = TripTime.ArriveBy(inAnHour())

            vm.setTripTime(arriveBy)
            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(arriveBy, entur.routeOptionsTo[bergen.id]?.time)
            assertEquals(TripTime.Now, entur.routeOptionsTo[oslo.id]?.time, "saved destinations leave now")
            assertEquals(arriveBy, vm.uiState.value.temporaryTripTime)
        }

    @Test
    fun `changing the time searches the temporary destination again`() =
        boardTest {
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()
            val calls = entur.tripCalls
            val departAt = TripTime.DepartAt(inAnHour())

            vm.setTripTime(departAt)
            runCurrent()

            assertEquals(calls + 1, entur.tripCalls)
            assertEquals(departAt, entur.routeOptionsTo[bergen.id]?.time)
        }

    @Test
    fun `a time already past means now`() =
        boardTest {
            val vm = viewModel()

            vm.setTripTime(TripTime.DepartAt(Clock.System.now().toEpochMilliseconds() - 1_000L))

            assertEquals(TripTime.Now, vm.uiState.value.temporaryTripTime)
        }

    @Test
    fun `the time goes back to now once it passes`() =
        boardTest {
            settings.refreshInterval = 60
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel(virtualClock())
            vm.setTripTime(TripTime.DepartAt(Clock.System.now().toEpochMilliseconds() + 30_000L))
            vm.showTemporaryDestination(bergen)
            runCurrent()

            advanceTimeBy(61_000L)
            runCurrent()

            assertEquals(TripTime.Now, vm.uiState.value.temporaryTripTime)
            assertEquals(TripTime.Now, entur.routeOptionsTo[bergen.id]?.time)
        }

    @Test
    fun `closing the route forgets its time`() =
        boardTest {
            val vm = viewModel()
            vm.setTripTime(TripTime.ArriveBy(inAnHour()))
            vm.showTemporaryDestination(bergen)
            runCurrent()

            vm.dismissTemporaryDestination()

            assertEquals(TripTime.Now, vm.uiState.value.temporaryTripTime)
        }

    @Test
    fun `closing the search without a pick forgets its time`() =
        boardTest {
            val vm = viewModel()
            vm.setTripTime(TripTime.ArriveBy(inAnHour()))

            vm.clearRouteSearch()

            assertEquals(TripTime.Now, vm.uiState.value.temporaryTripTime)
        }

    @Test
    fun `saving a timed route saves the place and searches it again for now`() =
        boardTest {
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            vm.setTripTime(TripTime.ArriveBy(inAnHour()))
            vm.showTemporaryDestination(bergen)
            runCurrent()

            vm.saveTemporaryDestination()
            runCurrent()

            assertEquals(listOf(bergen), settings.destinations)
            assertNull(vm.uiState.value.temporaryDestination)
            assertEquals(TripTime.Now, vm.uiState.value.temporaryTripTime)
            assertEquals(TripTime.Now, entur.routeOptionsTo[bergen.id]?.time)
        }

    @Test
    fun `undoing the save of a timed route brings back its time and its timed routes`() =
        boardTest {
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            val arriveBy = TripTime.ArriveBy(inAnHour())
            vm.setTripTime(arriveBy)
            vm.showTemporaryDestination(bergen)
            runCurrent()
            vm.saveTemporaryDestination()
            runCurrent()

            vm.undo()
            runCurrent()

            assertTrue(settings.destinations.isEmpty())
            assertEquals(bergen, vm.uiState.value.temporaryDestination)
            assertEquals(arriveBy, vm.uiState.value.temporaryTripTime)
            assertEquals(arriveBy, entur.routeOptionsTo[bergen.id]?.time)
        }

    @Test
    fun `a saved destination picked with a time stands in for the saved one`() =
        boardTest {
            settings.destinations = listOf(oslo)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            val arriveBy = TripTime.ArriveBy(inAnHour())
            vm.setTripTime(arriveBy)

            vm.showTemporaryDestination(oslo)
            runCurrent()

            assertEquals(oslo, vm.uiState.value.temporaryDestination)
            assertEquals(listOf(oslo), vm.uiState.value.routeDestinations, "shown once")
            assertEquals(arriveBy, entur.routeOptionsTo[oslo.id]?.time)

            vm.dismissTemporaryDestination()
            runCurrent()

            assertEquals(listOf(oslo), vm.uiState.value.routeDestinations)
            assertEquals(TripTime.Now, entur.routeOptionsTo[oslo.id]?.time, "back to leaving now")
            assertTrue(oslo.id in vm.uiState.value.fastestTrips)
        }

    @Test
    fun `a destination with no route is told apart from one still loading`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.tripTo = { Sourced.live(null) }
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            val state = vm.uiState.value
            assertTrue(bergen.id in state.noRouteDestinationIds)
            assertFalse(bergen.id in state.loadingTripDestinationIds)
            assertFalse(bergen.id in state.failedTripDestinationIds)
        }

    @Test
    fun `only the searched destination says it has no route by the time asked`() =
        boardTest {
            val madrid = StopPlace(id = "NSR:StopPlace:9", name = "Madrid")
            settings.destinations = listOf(madrid)
            entur.tripTo = { Sourced.live(null) }
            val vm = viewModel()
            val arriveBy = TripTime.ArriveBy(inAnHour())
            vm.setTripTime(arriveBy)

            vm.showTemporaryDestination(bergen)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(arriveBy, state.noRouteTime(bergen.id))
            assertEquals(TripTime.Now, state.noRouteTime(madrid.id), "saved destinations leave now")
            assertNull(state.noRouteTime(oslo.id), "one never searched for has no answer")
        }

    @Test
    fun `with nowhere to route from an empty answer is not no route`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertFalse(bergen.id in vm.uiState.value.noRouteDestinationIds)
        }

    @Test
    fun `favourite stops are somewhere to route from without a saved location`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            settings.favoriteStops = listOf(oslo)
            entur.tripTo = { Sourced.live(null) }
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertTrue(bergen.id in vm.uiState.value.noRouteDestinationIds)
        }

    @Test
    fun `a saved destination searched for with a time stays up when settings are read again`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel()
            val arriveBy = TripTime.ArriveBy(inAnHour())
            vm.setTripTime(arriveBy)
            vm.showTemporaryDestination(oslo)
            runCurrent()

            vm.loadSettings()

            assertEquals(oslo, vm.uiState.value.temporaryDestination)
            assertEquals(arriveBy, vm.uiState.value.temporaryTripTime)
        }

    @Test
    fun `a time chosen without a place picked is forgotten when settings are read again`() =
        boardTest {
            val vm = viewModel()
            vm.setTripTime(TripTime.ArriveBy(inAnHour()))

            vm.loadSettings()

            assertEquals(TripTime.Now, vm.uiState.value.temporaryTripTime)
        }
}

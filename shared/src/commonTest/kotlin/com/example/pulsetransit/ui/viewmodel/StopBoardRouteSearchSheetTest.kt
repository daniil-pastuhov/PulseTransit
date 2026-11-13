package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.PlaceKind
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** What route search shows in its sheet: recent places, alternatives, and the earliest arrival. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardRouteSearchSheetTest : BoardTestBase() {
    private val bergen = StopPlace(id = "NSR:StopPlace:2", name = "Bergen", kind = PlaceKind.STOP)
    private val majorstuen = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen", kind = PlaceKind.STOP)
    private val hour = 3_600_000L

    private fun inAnHour() = Clock.System.now().toEpochMilliseconds() + hour

    @Test
    fun `places searched for are remembered newest first`() =
        boardTest {
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()
            vm.showTemporaryDestination(majorstuen)
            runCurrent()

            assertEquals(listOf(majorstuen, bergen), vm.uiState.value.recentDestinations)
            assertEquals(listOf(majorstuen, bergen), settings.recent)
        }

    @Test
    fun `a saved destination searched for again is remembered too`() =
        boardTest {
            settings.destinations = listOf(bergen)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(listOf(bergen), vm.uiState.value.recentDestinations)
        }

    @Test
    fun `remembered places are read back when the board opens`() =
        boardTest {
            settings.recent = listOf(majorstuen)
            val vm = viewModel()

            assertEquals(listOf(majorstuen), vm.uiState.value.recentDestinations)
        }

    @Test
    fun `a place searched for gets alternatives to choose from`() =
        boardTest {
            entur.tripOptionsTo = { to, count -> Sourced.live(List(count) { trip(to.name) }) }
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(SettingsRepository.MAX_TRIP_OPTIONS, entur.lastTripOptionsCount)
            assertEquals(
                SettingsRepository.MAX_TRIP_OPTIONS,
                vm.uiState.value.fastestTrips[bergen.id]
                    ?.size,
            )
        }

    @Test
    fun `an arrival time no route can meet gets the earliest arrival after it`() =
        boardTest {
            val late = trip("Bergen").copy(expectedEndTime = "2026-10-14T09:10:00+02:00")
            val soonest = trip("Bergen").copy(expectedEndTime = "2026-10-14T08:50:00+02:00")
            entur.tripOptionsTo = { _, _ ->
                if (entur.lastRouteOptions?.time is TripTime.ArriveBy) Sourced.live(emptyList()) else Sourced.live(listOf(late, soonest))
            }
            val vm = viewModel()
            vm.setTripTime(TripTime.ArriveBy(inAnHour()))

            vm.showTemporaryDestination(bergen)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(true, bergen.id in state.noRouteDestinationIds)
            assertEquals(soonest.expectedEndTime, state.earliestArrivals[bergen.id]?.expectedEndTime)
        }

    @Test
    fun `leaving now with no route looks for no earliest arrival`() =
        boardTest {
            entur.tripOptionsTo = { _, _ -> Sourced.live(emptyList()) }
            val vm = viewModel()

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(1, entur.tripCalls)
            assertNull(vm.uiState.value.earliestArrivals[bergen.id])
        }

    @Test
    fun `a saved destination searched for with a time of its own is searched at that time`() =
        boardTest {
            settings.destinations = listOf(bergen)
            entur.tripOptionsTo = { to, count -> Sourced.live(List(count) { trip(to.name) }) }
            val vm = viewModel()
            vm.showTemporaryDestination(bergen)
            runCurrent()
            val arriveBy = TripTime.ArriveBy(inAnHour())

            // What route search does when the time changes with a place picked.
            vm.setTripTime(arriveBy)
            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(bergen, vm.uiState.value.temporaryDestination)
            assertEquals(arriveBy, entur.routeOptionsTo[bergen.id]?.time)
        }

    @Test
    fun `the earliest arrival is searched from ninety minutes before the time asked for`() =
        boardTest {
            entur.tripOptionsTo = { _, _ -> Sourced.live(emptyList()) }
            val vm = viewModel()
            val arriveBy = Clock.System.now().toEpochMilliseconds() + 3 * hour
            vm.setTripTime(TripTime.ArriveBy(arriveBy))

            vm.showTemporaryDestination(bergen)
            runCurrent()

            val searched = entur.lastRouteOptions?.time as TripTime.DepartAt
            assertEquals(arriveBy - 90 * 60_000L, searched.atMillis)
        }

    @Test
    fun `the earliest arrival is never searched from the past`() =
        boardTest {
            entur.tripOptionsTo = { _, _ -> Sourced.live(emptyList()) }
            val vm = viewModel()
            val before = Clock.System.now().toEpochMilliseconds()
            vm.setTripTime(TripTime.ArriveBy(before + hour / 2))

            vm.showTemporaryDestination(bergen)
            runCurrent()

            val searched = entur.lastRouteOptions?.time as TripTime.DepartAt
            assertTrue(searched.atMillis >= before, "searched from ${searched.atMillis}, before now $before")
        }

    @Test
    fun `an arrival time that routes meet needs no second search`() =
        boardTest {
            entur.tripOptionsTo = { to, _ -> Sourced.live(listOf(trip(to.name))) }
            val vm = viewModel()
            vm.setTripTime(TripTime.ArriveBy(inAnHour()))

            vm.showTemporaryDestination(bergen)
            runCurrent()

            assertEquals(1, entur.tripCalls)
        }

    @Test
    fun `an earliest arrival goes once routes are found`() =
        boardTest {
            var found = false
            entur.tripOptionsTo = { to, _ ->
                when {
                    found -> Sourced.live(listOf(trip(to.name)))
                    entur.lastRouteOptions?.time is TripTime.ArriveBy -> Sourced.live(emptyList())
                    else -> Sourced.live(listOf(trip(to.name).copy(expectedEndTime = "2026-10-14T08:50:00+02:00")))
                }
            }
            val vm = viewModel()
            vm.setTripTime(TripTime.ArriveBy(inAnHour()))
            vm.showTemporaryDestination(bergen)
            runCurrent()
            assertTrue(bergen.id in vm.uiState.value.earliestArrivals)

            found = true
            vm.refreshNow()
            runCurrent()

            assertNull(vm.uiState.value.earliestArrivals[bergen.id])
        }
}

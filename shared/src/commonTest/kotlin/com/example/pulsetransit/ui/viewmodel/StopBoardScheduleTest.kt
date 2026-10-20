package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.DestinationSchedule
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Saved destinations shown only inside their schedule. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardScheduleTest : BoardTestBase() {
    private val work = StopPlace(id = "NSR:StopPlace:work", name = "Work")

    // Mondays and weekdays 08:00–09:00, with the board starting a minute before the window opens.
    private val mondayBeforeEight = LocalDateTime(2026, 10, 19, 7, 59)
    private val weekdayMornings = DestinationSchedule(DestinationSchedule.WEEKDAYS, startMinute = 8 * 60, endMinute = 9 * 60)

    private val asked = mutableListOf<String>()

    private fun scheduleWork() {
        settings.destinations = listOf(oslo, work)
        settings.schedules = mapOf(work.id to weekdayMornings)
        entur.tripTo = { to ->
            asked += to.id
            Sourced.live(trip(to.name))
        }
    }

    /** A clock in UTC that starts at [start] and follows the test's virtual time. */
    private fun TestScope.clockAt(start: LocalDateTime): Clock {
        val startMillis = start.toInstant(TimeZone.UTC).toEpochMilliseconds()
        return object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(startMillis + testScheduler.currentTime)
        }
    }

    private fun TestScope.board(start: LocalDateTime = mondayBeforeEight) = viewModel(clockAt(start), TimeZone.UTC)

    @Test
    fun `a destination outside its schedule is hidden and not routed to`() =
        boardTest {
            scheduleWork()
            val vm = board()

            val state = vm.uiState.value
            assertEquals(listOf(oslo), state.routeDestinations)
            assertEquals(listOf(oslo, work), state.destinationStations, "it is still saved")
            assertFalse(work.id in asked)
            assertFalse(work.id in state.fastestTrips)
        }

    @Test
    fun `a destination is routed to as soon as its window opens`() =
        boardTest {
            scheduleWork()
            val vm = board()
            asked.clear()

            advanceTimeBy(minute + 1)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(listOf(oslo, work), state.routeDestinations)
            assertTrue(work.id in asked, "fetched at 08:00 - not on the next refresh")
            assertEquals("Work", state.fastestTrips[work.id]?.single()?.destinationName)
        }

    @Test
    fun `a destination and its route are dropped when its window closes`() =
        boardTest {
            scheduleWork()
            val vm = board(start = LocalDateTime(2026, 10, 19, 8, 59))
            assertTrue(work.id in vm.uiState.value.fastestTrips)

            advanceTimeBy(minute + 1)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(listOf(oslo), state.routeDestinations)
            assertFalse(work.id in state.fastestTrips)
            assertTrue(oslo.id in state.fastestTrips, "an unscheduled destination keeps its route")
        }

    @Test
    fun `a weekday schedule hides the destination at the weekend`() =
        boardTest {
            scheduleWork()
            val vm = board(start = LocalDateTime(2026, 10, 24, 8, 30))

            assertEquals(listOf(oslo), vm.uiState.value.routeDestinations)
        }

    @Test
    fun `a hidden saved destination can still be shown from the route search`() =
        boardTest {
            scheduleWork()
            val vm = board()

            vm.showTemporaryDestination(work)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(work, state.temporaryDestination)
            assertEquals(listOf(work, oslo), state.routeDestinations)
            assertTrue(work.id in state.fastestTrips)
            assertFalse(state.canSaveTemporaryDestination, "it is saved already")
        }

    @Test
    fun `a temporary destination becomes the saved one when its window opens`() =
        boardTest {
            scheduleWork()
            val vm = board()
            vm.showTemporaryDestination(work)
            runCurrent()

            advanceTimeBy(minute + 1)
            runCurrent()

            val state = vm.uiState.value
            assertNull(state.temporaryDestination)
            assertEquals(listOf(oslo, work), state.routeDestinations)
        }

    @Test
    fun `schedules apply in on-spot mode too`() =
        boardTest {
            scheduleWork()
            settings.mode = AppMode.ON_SPOT
            val vm = board()
            vm.startLocationTracking()
            runCurrent()
            check(location.updates.getValue(LocationAccuracy.PRECISE).tryEmit(LocationResult.Fix(59.91, 10.75)))
            runCurrent()

            assertTrue(oslo.id in asked)
            assertFalse(work.id in asked)
            assertEquals(listOf(oslo), vm.uiState.value.routeDestinations)
        }
}

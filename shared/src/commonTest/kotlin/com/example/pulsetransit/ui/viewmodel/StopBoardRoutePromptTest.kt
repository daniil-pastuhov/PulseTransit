package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.DestinationSchedule
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** The on-spot routes section's prompt when it has no route cards to show. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardRoutePromptTest : BoardTestBase() {
    private val here = LocationResult.Fix(59.9139, 10.7522)
    private val work = StopPlace("NSR:StopPlace:work", "Work", latitude = 59.95, longitude = 10.70)
    private val atHere = StopPlace("NSR:StopPlace:here", "Here", latitude = here.latitude, longitude = here.longitude)
    private val bergen = StopPlace("NSR:StopPlace:bergen", "Bergen")
    private val cafe = StopPlace("NSR:StopPlace:cafe", "Cafe")

    // A Monday, an hour before a weekday-mornings window opens.
    private val mondaySeven = LocalDateTime(2026, 10, 19, 7, 0)
    private val weekdayMornings = DestinationSchedule(DestinationSchedule.WEEKDAYS, startMinute = 8 * 60, endMinute = 9 * 60)

    private fun emit(fix: LocationResult.Fix) = check(location.updates.getValue(LocationAccuracy.PRECISE).tryEmit(fix))

    private fun TestScope.clockAt(start: LocalDateTime): Clock {
        val startMillis = start.toInstant(TimeZone.UTC).toEpochMilliseconds()
        return object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(startMillis + testScheduler.currentTime)
        }
    }

    /** An on-spot board given its first fix at [here]. */
    private fun TestScope.located(clock: Clock = virtualClock()): StopBoardViewModel {
        settings.mode = AppMode.ON_SPOT
        val vm = viewModel(clock, TimeZone.UTC)
        vm.startLocationTracking()
        runCurrent()
        emit(here)
        runCurrent()
        return vm
    }

    private fun noRoutes() {
        entur.tripOptionsTo = { _, _ -> Sourced.live(emptyList<TripInfo>()) }
    }

    @Test
    fun `with nothing saved the prompt offers to add a destination and suggests recent places`() =
        boardTest {
            settings.recent = listOf(bergen, cafe)
            val vm = located()

            val state = vm.uiState.value
            assertEquals(OnSpotRoutePrompt.NO_DESTINATIONS, state.onSpotRoutePrompt)
            assertTrue(state.onSpotRoutePrompt.offersAddDestination)
            assertEquals(listOf(bergen, cafe), state.onSpotRouteSuggestions)
        }

    @Test
    fun `saved destinations all outside their schedule give none active - and are suggested after the recents`() =
        boardTest {
            settings.destinations = listOf(work, bergen)
            settings.schedules = mapOf(work.id to weekdayMornings, bergen.id to weekdayMornings)
            settings.recent = listOf(bergen, cafe)
            noRoutes()
            val vm = located(clockAt(mondaySeven))

            val state = vm.uiState.value
            assertEquals(OnSpotRoutePrompt.NONE_ACTIVE, state.onSpotRoutePrompt)
            assertEquals(false, state.onSpotRoutePrompt.offersAddDestination)
            assertEquals(listOf(bergen, cafe, work), state.onSpotRouteSuggestions, "recents first and each place once")
        }

    @Test
    fun `active destinations without a route give none found - and are not suggested`() =
        boardTest {
            settings.destinations = listOf(work, bergen)
            settings.schedules = mapOf(bergen.id to weekdayMornings)
            settings.recent = listOf(work, cafe)
            noRoutes()
            val vm = located(clockAt(mondaySeven))

            val state = vm.uiState.value
            assertEquals(OnSpotRoutePrompt.NONE_FOUND, state.onSpotRoutePrompt)
            assertEquals(listOf(cafe, bergen), state.onSpotRouteSuggestions, "work was just tried; bergen is outside its window")
        }

    @Test
    fun `being at the only destination gives none found`() =
        boardTest {
            settings.destinations = listOf(atHere)
            val vm = located()

            assertEquals(setOf(atHere.id), vm.uiState.value.arrivedDestinationIds)
            assertEquals(OnSpotRoutePrompt.NONE_FOUND, vm.uiState.value.onSpotRoutePrompt)
        }

    @Test
    fun `no prompt while routes load - then none found once all answered`() =
        boardTest {
            settings.destinations = listOf(work)
            noRoutes()
            entur.tripDelayMillis = { 5_000L }
            val vm = located()

            assertTrue(work.id in vm.uiState.value.loadingTripDestinationIds)
            assertEquals(OnSpotRoutePrompt.NONE, vm.uiState.value.onSpotRoutePrompt)
            assertEquals(emptyList(), vm.uiState.value.onSpotRouteSuggestions)

            advanceTimeBy(5_001L)
            runCurrent()
            assertEquals(OnSpotRoutePrompt.NONE_FOUND, vm.uiState.value.onSpotRoutePrompt)
        }

    @Test
    fun `a failed destination keeps its card - so no prompt`() =
        boardTest {
            settings.destinations = listOf(work)
            entur.tripOptionsTo = { _, _ -> Sourced.failed(emptyList<TripInfo>()) }
            val vm = located()

            assertEquals(listOf(work), vm.uiState.value.shownRouteDestinations)
            assertEquals(OnSpotRoutePrompt.NONE, vm.uiState.value.onSpotRoutePrompt)
        }

    @Test
    fun `no prompt before the device is found while destinations are active`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            settings.destinations = listOf(work)
            noRoutes()
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()

            assertEquals(OnSpotRoutePrompt.NONE, vm.uiState.value.onSpotRoutePrompt)
        }

    @Test
    fun `a route to show means no prompt`() =
        boardTest {
            settings.destinations = listOf(work)
            entur.tripOptionsTo = { to, _ -> Sourced.live(listOf(trip(to.name))) }
            val vm = located()

            assertEquals(OnSpotRoutePrompt.NONE, vm.uiState.value.onSpotRoutePrompt)
        }

    @Test
    fun `tapping a suggestion shows its route card in place of the prompt`() =
        boardTest {
            settings.recent = listOf(bergen)
            entur.tripOptionsTo = { to, _ -> Sourced.live(listOf(trip(to.name))) }
            val vm = located()
            val suggestion =
                vm.uiState.value.onSpotRouteSuggestions
                    .single()

            vm.showTemporaryDestination(suggestion)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(listOf(bergen), state.shownRouteDestinations)
            assertEquals(OnSpotRoutePrompt.NONE, state.onSpotRoutePrompt)
        }

    @Test
    fun `the wall board never shows the prompt`() =
        boardTest {
            val vm = viewModel(virtualClock())

            assertEquals(emptyList(), vm.uiState.value.destinationStations)
            assertEquals(OnSpotRoutePrompt.NONE, vm.uiState.value.onSpotRoutePrompt)
        }
}

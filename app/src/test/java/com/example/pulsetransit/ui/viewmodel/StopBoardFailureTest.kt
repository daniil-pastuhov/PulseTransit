package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.Sourced
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Failed loads: telling "unknown" apart from "nothing departs", retrying sooner than the normal
 * interval, and refreshing as soon as the connection comes back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardFailureTest : BoardTestBase() {
    @Test
    fun `a stop that failed with nothing cached is marked failed, not empty`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.failed(emptyList()) }
            val vm = viewModel()

            assertEquals(setOf(oslo.id), vm.uiState.value.failedStopIds)
            assertTrue(vm.uiState.value.hasLoadFailure)
        }

    @Test
    fun `a live empty board is not a failure`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(emptyList()) }
            val vm = viewModel()

            assertTrue(
                vm.uiState.value.failedStopIds
                    .isEmpty(),
            )
            assertFalse(vm.uiState.value.hasLoadFailure)
        }

    @Test
    fun `a failed stop clears once a fetch succeeds`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.failed(emptyList()) }
            val vm = viewModel()
            assertEquals(setOf(oslo.id), vm.uiState.value.failedStopIds)

            val at = currentSystemTime() + 10 * minute
            entur.departures = { Sourced.live(listOf(departure("L1", "A", at))) }
            vm.refreshDepartures()
            runCurrent()

            assertTrue(
                vm.uiState.value.failedStopIds
                    .isEmpty(),
            )
            assertEquals(
                1,
                vm.uiState.value.departures
                    .single()
                    .groups.size,
            )
        }

    @Test
    fun `a failed route is marked failed and leaves no trip behind`() =
        boardTest {
            settings.destinations = listOf(oslo)
            entur.trip = Sourced.failed(null)
            val vm = viewModel()

            assertEquals(setOf(oslo.id), vm.uiState.value.failedTripDestinationIds)
            assertTrue(
                vm.uiState.value.fastestTrips
                    .isEmpty(),
            )
            assertTrue(vm.uiState.value.hasLoadFailure)
        }

    @Test
    fun `a failure is retried sooner than the normal interval`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            settings.refreshInterval = 60
            entur.departures = { Sourced.failed(emptyList()) }
            viewModel()
            val callsBefore = entur.departureCalls

            advanceTimeBy(16.seconds)

            assertTrue("expected a retry within 15 s", entur.departureCalls > callsBefore)
        }

    @Test
    fun `a board served from the cache is retried sooner too`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            settings.refreshInterval = 60
            val at = currentSystemTime() + 10 * minute
            entur.departures = { Sourced.cached(listOf(departure("L1", "A", at)), currentSystemTime() - 5 * minute) }
            viewModel()
            val callsBefore = entur.departureCalls

            advanceTimeBy(16.seconds)

            assertTrue("stale data should be retried within 15 s", entur.departureCalls > callsBefore)
        }

    @Test
    fun `a failed route alone is retried sooner`() =
        boardTest {
            settings.destinations = listOf(oslo)
            settings.refreshInterval = 60
            entur.trip = Sourced.failed(null)
            viewModel()
            val callsBefore = entur.tripCalls

            advanceTimeBy(16.seconds)

            assertTrue("a failed route should be retried within 15 s", entur.tripCalls > callsBefore)
        }

    @Test
    fun `a healthy board waits for the normal interval`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            settings.refreshInterval = 60
            entur.departures = { Sourced.live(emptyList()) }
            viewModel()
            val callsBefore = entur.departureCalls

            advanceTimeBy(16.seconds)
            assertEquals(callsBefore, entur.departureCalls)

            advanceTimeBy(45.seconds)
            assertTrue(entur.departureCalls > callsBefore)
        }

    @Test
    fun `losing the connection is reflected, and regaining it refreshes immediately`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(emptyList()) }
            val vm = viewModel()
            assertTrue(vm.uiState.value.isOnline)

            connectivity.online.value = false
            runCurrent()
            assertFalse(vm.uiState.value.isOnline)
            val callsWhileOffline = entur.departureCalls

            connectivity.online.value = true
            advanceTimeBy(1.milliseconds)

            assertTrue(vm.uiState.value.isOnline)
            assertTrue("reconnecting should refresh at once", entur.departureCalls > callsWhileOffline)
        }

    @Test
    fun `reconnecting retries a forecast that failed while offline`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(emptyList()) }
            val vm = viewModel()
            assertTrue(
                vm.uiState.value.weather
                    .isEmpty(),
            )
            val weatherCalls = weather.calls

            connectivity.online.value = false
            runCurrent()
            connectivity.online.value = true
            runCurrent()

            assertEquals(weatherCalls + 1, weather.calls)
        }
}

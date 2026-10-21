package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * A destination the trip's origin is already at has no banner, and is not asked Entur for. See
 * [BoardTestBase]. A degree of latitude is about 111 km, so 0.001 is about 110 m.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardArrivedTest : BoardTestBase() {
    private val savedLat = 59.9149
    private val savedLon = 10.7322

    /** About 110 m from the saved location: inside the arrival radius. */
    private val aroundTheCorner = StopPlace("NSR:StopPlace:corner", "Corner", latitude = savedLat + 0.001, longitude = savedLon)

    /** About 3 km from the saved location. */
    private val acrossTown = StopPlace("NSR:StopPlace:town", "Town", latitude = savedLat + 0.03, longitude = savedLon)

    private fun StopBoardViewModel.isShown(destination: StopPlace): Boolean {
        val state = uiState.value
        return destination.id in state.fastestTrips ||
            destination.id in state.loadingTripDestinationIds ||
            destination.id in state.failedTripDestinationIds
    }

    @Test
    fun `board mode skips a destination at the saved location and routes the rest`() =
        boardTest {
            settings.latitude = savedLat
            settings.longitude = savedLon
            settings.destinations = listOf(aroundTheCorner, acrossTown)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            assertEquals(1, entur.tripCalls, "only the far destination is asked for")
            assertFalse(vm.isShown(aroundTheCorner))
            assertEquals(setOf(acrossTown.id), vm.uiState.value.fastestTrips.keys)
        }

    @Test
    fun `board mode without a saved location skips a destination that is a favourite stop`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            settings.favoriteStops = listOf(aroundTheCorner.copy(latitude = null, longitude = null))
            settings.destinations = listOf(aroundTheCorner, acrossTown)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            assertEquals(1, entur.tripCalls)
            assertFalse(vm.isShown(aroundTheCorner))
            assertTrue(vm.isShown(acrossTown))
        }

    @Test
    fun `board mode without a saved location skips a destination next to a favourite stop`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            settings.favoriteStops = listOf(StopPlace("NSR:StopPlace:home", "Home", latitude = savedLat, longitude = savedLon))
            settings.destinations = listOf(aroundTheCorner, acrossTown)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            assertFalse(vm.isShown(aroundTheCorner))
            assertTrue(vm.isShown(acrossTown))
        }

    @Test
    fun `a destination without coordinates is always routed`() =
        boardTest {
            settings.latitude = savedLat
            settings.longitude = savedLon
            settings.destinations = listOf(oslo)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            assertTrue(vm.isShown(oslo))
        }

    @Test
    fun `a destination searched for from the board is routed even when already there`() =
        boardTest {
            settings.latitude = savedLat
            settings.longitude = savedLon
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()

            vm.showTemporaryDestination(aroundTheCorner)
            runCurrent()

            assertTrue(aroundTheCorner.id in vm.uiState.value.fastestTrips)
        }

    @Test
    fun `moving the saved location onto a routed destination drops its banner`() =
        boardTest {
            settings.latitude = savedLat + 0.05
            settings.longitude = savedLon
            settings.destinations = listOf(aroundTheCorner)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            val vm = viewModel()
            assertTrue(vm.isShown(aroundTheCorner))

            settings.latitude = savedLat
            vm.loadSettings()
            vm.refreshData()
            runCurrent()

            assertFalse(vm.isShown(aroundTheCorner))
        }

    // On-spot mode: the origin is the device.

    private val here = LocationResult.Fix(savedLat, savedLon)

    /** About 330 m from [aroundTheCorner]. */
    private val downTheRoad = LocationResult.Fix(savedLat + 0.004, savedLon)

    private fun emit(fix: LocationResult.Fix) = check(location.updates.getValue(LocationAccuracy.PRECISE).tryEmit(fix))

    private fun TestScope.onSpotBoardAt(fix: LocationResult.Fix): StopBoardViewModel {
        settings.mode = AppMode.ON_SPOT
        settings.destinations = listOf(aroundTheCorner, acrossTown)
        entur.tripOptionsTo = { to, _ -> Sourced.live(listOf(trip(to.name))) }
        val vm = viewModel(virtualClock())
        vm.startLocationTracking()
        runCurrent()
        emit(fix)
        runCurrent()
        return vm
    }

    @Test
    fun `on the spot a destination the device is at is hidden - and comes back when it leaves`() =
        boardTest {
            val vm = onSpotBoardAt(here)
            assertEquals(1, entur.tripCalls, "only the far destination is asked for")
            assertFalse(vm.isShown(aroundTheCorner))
            assertTrue(vm.isShown(acrossTown))

            advanceTimeBy(20.seconds)
            emit(downTheRoad)
            runCurrent()

            assertEquals(setOf(aroundTheCorner.id, acrossTown.id), vm.uiState.value.fastestTrips.keys)
        }

    @Test
    fun `on the spot arriving at a destination drops the banner it had`() =
        boardTest {
            val vm = onSpotBoardAt(downTheRoad)
            assertTrue(aroundTheCorner.id in vm.uiState.value.fastestTrips)
            val asked = entur.tripCalls

            advanceTimeBy(20.seconds)
            emit(here)
            runCurrent()

            assertFalse(vm.isShown(aroundTheCorner))
            assertTrue(vm.isShown(acrossTown))
            assertEquals(asked + 1, entur.tripCalls, "only the far destination is asked for again")
        }

    @Test
    fun `on the spot arriving at the only destination drops its banner`() =
        boardTest {
            val vm = onSpotBoardAt(downTheRoad)
            settings.destinations = listOf(aroundTheCorner)
            vm.loadSettings()
            vm.refreshData()
            runCurrent()
            assertEquals(setOf(aroundTheCorner.id), vm.uiState.value.fastestTrips.keys)

            advanceTimeBy(20.seconds)
            emit(here)
            runCurrent()

            assertTrue(
                vm.uiState.value.fastestTrips
                    .isEmpty(),
            )
            assertFalse(vm.isShown(aroundTheCorner))
        }
}

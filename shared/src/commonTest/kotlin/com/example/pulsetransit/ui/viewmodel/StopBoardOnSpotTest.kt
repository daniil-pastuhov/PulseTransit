package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.OnSpotSplit
import com.example.pulsetransit.data.SettingsRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** On-spot mode: following the device, the nearest stop and routes from where the user stands. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardOnSpotTest : BoardTestBase() {
    private val here = LocationResult.Fix(59.9139, 10.7522)

    // About 2 m, 33 m and 110 m north of [here]; a degree of latitude is about 111 km.
    private val twoMetresOn = LocationResult.Fix(here.latitude + 0.00002, here.longitude)
    private val thirtyMetresOn = LocationResult.Fix(here.latitude + 0.0003, here.longitude)
    private val hundredMetresOn = LocationResult.Fix(here.latitude + 0.001, here.longitude)

    private val near = StopPlace("NSR:StopPlace:near", "Near", latitude = here.latitude + 0.0005, longitude = here.longitude)
    private val far = StopPlace("NSR:StopPlace:far", "Far", latitude = here.latitude + 0.005, longitude = here.longitude)

    private fun onSpot() {
        settings.mode = AppMode.ON_SPOT
        settings.destinations = listOf(oslo)
    }

    private fun emit(
        result: LocationResult,
        accuracy: LocationAccuracy = LocationAccuracy.PRECISE,
    ) = check(location.updates.getValue(accuracy).tryEmit(result))

    /** An on-spot board that is showing and has been given its first precise fix at [here]. */
    private fun TestScope.locatedBoard(): StopBoardViewModel {
        val vm = viewModel(virtualClock())
        vm.startLocationTracking()
        runCurrent()
        emit(here)
        runCurrent()
        return vm
    }

    @Test
    fun `the first fix selects the nearest stop - not the geocoder's first`() =
        boardTest {
            onSpot()
            entur.nearby = listOf(far, near)
            val vm = locatedBoard()

            val state = vm.uiState.value
            assertEquals(OnSpotLocation.Located(here.latitude, here.longitude, LocationAccuracy.PRECISE), state.location)
            assertEquals(here.latitude to here.longitude, entur.lastNearbyCoords)
            assertEquals(listOf(near.id, far.id), state.nearbyStops.map { it.stop.id })
            assertEquals(near.id, state.selectedStopId)
            assertEquals(listOf(near), state.favorites)
            assertTrue(near.id in entur.requestedLimits, "the nearest stop's departures are fetched")
        }

    @Test
    fun `no precise fix falls back to an approximate one`() =
        boardTest {
            onSpot()
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()
            assertEquals(OnSpotLocation.Locating(LocationAccuracy.PRECISE), vm.uiState.value.location)

            advanceTimeBy(16.seconds)
            emit(here, LocationAccuracy.APPROXIMATE)
            runCurrent()

            assertEquals(listOf(LocationAccuracy.PRECISE, LocationAccuracy.APPROXIMATE), location.updateRequests)
            assertEquals(
                OnSpotLocation.Located(here.latitude, here.longitude, LocationAccuracy.APPROXIMATE),
                vm.uiState.value.location,
            )
            assertEquals(1, location.activeCollectors, "the precise request is closed when falling back")
        }

    @Test
    fun `no fix at either accuracy is unavailable - and retry looks again`() =
        boardTest {
            onSpot()
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()
            advanceTimeBy(16.seconds)
            assertEquals(OnSpotLocation.Locating(LocationAccuracy.APPROXIMATE), vm.uiState.value.location)
            advanceTimeBy(16.seconds)

            assertEquals(OnSpotLocation.Unavailable, vm.uiState.value.location)
            assertEquals(0, location.activeCollectors)

            vm.retryLocation()
            runCurrent()
            assertEquals(OnSpotLocation.Locating(LocationAccuracy.PRECISE), vm.uiState.value.location)
            assertEquals(3, location.updateRequests.size)
        }

    @Test
    fun `a passing spell without a signal does not cut the wait for a first fix short`() =
        boardTest {
            onSpot()
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()
            emit(LocationResult.Unavailable)
            runCurrent()
            advanceTimeBy(5.seconds)
            emit(here)
            runCurrent()

            assertEquals(OnSpotLocation.Located(here.latitude, here.longitude, LocationAccuracy.PRECISE), vm.uiState.value.location)
            assertEquals(listOf(LocationAccuracy.PRECISE), location.updateRequests)
        }

    @Test
    fun `a location already found keeps showing while the board looks again`() =
        boardTest {
            onSpot()
            val vm = locatedBoard()

            vm.stopLocationTracking()
            vm.startLocationTracking()
            runCurrent()

            assertTrue(vm.uiState.value.location is OnSpotLocation.Located, "no spinner when returning to the board")
        }

    @Test
    fun `denied access is reported without trying the other accuracy`() =
        boardTest {
            onSpot()
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()
            emit(LocationResult.PermissionDenied)
            runCurrent()

            assertEquals(OnSpotLocation.PermissionDenied, vm.uiState.value.location)
            assertEquals(listOf(LocationAccuracy.PRECISE), location.updateRequests)
        }

    @Test
    fun `a small move refetches nothing - a 20 m one refetches stops and routes`() =
        boardTest {
            onSpot()
            entur.nearby = listOf(near)
            val vm = locatedBoard()
            assertEquals(1, entur.nearbyCalls)
            val tripCalls = entur.tripCalls

            advanceTimeBy(20.seconds)
            emit(twoMetresOn)
            runCurrent()
            assertEquals(1, entur.nearbyCalls)
            assertEquals(tripCalls, entur.tripCalls)

            emit(thirtyMetresOn)
            runCurrent()
            assertEquals(2, entur.nearbyCalls)
            assertEquals(tripCalls + 1, entur.tripCalls)
            assertEquals(thirtyMetresOn.latitude to thirtyMetresOn.longitude, entur.lastFastestTripCoords)
            assertEquals(thirtyMetresOn.latitude, (vm.uiState.value.location as OnSpotLocation.Located).latitude)
            assertEquals(thirtyMetresOn.latitude to thirtyMetresOn.longitude, settings.getDeviceLocation(), "kept for the widgets")
        }

    @Test
    fun `moves in quick succession are throttled to the latest fix`() =
        boardTest {
            onSpot()
            locatedBoard()
            assertEquals(1, entur.nearbyCalls)

            emit(thirtyMetresOn)
            runCurrent()
            emit(hundredMetresOn)
            runCurrent()
            assertEquals(1, entur.nearbyCalls, "nothing more within the throttle period")

            advanceTimeBy(15.seconds)
            runCurrent()
            assertEquals(2, entur.nearbyCalls)
            assertEquals(hundredMetresOn.latitude to hundredMetresOn.longitude, entur.lastNearbyCoords)
        }

    @Test
    fun `a picked stop survives moving - until following the nearest again`() =
        boardTest {
            onSpot()
            entur.nearby = listOf(near, far)
            val vm = locatedBoard()

            vm.selectStop(far.id)
            assertEquals(far.id, vm.uiState.value.selectedStopId)
            assertTrue(vm.uiState.value.isStopPinned)

            advanceTimeBy(20.seconds)
            emit(thirtyMetresOn)
            runCurrent()
            assertEquals(far.id, vm.uiState.value.selectedStopId)

            vm.followNearest()
            assertEquals(near.id, vm.uiState.value.selectedStopId)
            assertFalse(vm.uiState.value.isStopPinned)
        }

    @Test
    fun `a picked stop that is no longer nearby gives way to the nearest`() =
        boardTest {
            onSpot()
            entur.nearby = listOf(near, far)
            val vm = locatedBoard()
            vm.selectStop(far.id)

            entur.nearby = listOf(near)
            advanceTimeBy(20.seconds)
            emit(thirtyMetresOn)
            runCurrent()

            assertEquals(near.id, vm.uiState.value.selectedStopId)
            assertFalse(vm.uiState.value.isStopPinned)
        }

    @Test
    fun `routes are not asked for before the device is found`() =
        boardTest {
            onSpot()
            viewModel(virtualClock())

            assertEquals(0, entur.tripCalls)
            assertNull(entur.lastFastestTripFromIds, "never falls back to favourites in on-spot mode")
        }

    @Test
    fun `the dragged split is saved and read back on the next launch`() =
        boardTest {
            onSpot()
            settings.split = 0.3f
            val vm = locatedBoard()
            assertEquals(0.3f, vm.uiState.value.onSpotSplit)

            vm.setOnSpotSplit(OnSpotSplit.STOP_COLLAPSED)

            assertEquals(OnSpotSplit.STOP_COLLAPSED, vm.uiState.value.onSpotSplit)
            assertEquals(OnSpotSplit.STOP_COLLAPSED, settings.split)
        }

    @Test
    fun `routes ask for the most alternatives on auto - and the set count otherwise`() =
        boardTest {
            onSpot()
            entur.tripOptionsTo = { to, count -> Sourced.live(List(count) { trip(to.name) }) }
            val vm = locatedBoard()
            assertEquals(SettingsRepository.MAX_TRIP_OPTIONS, entur.lastTripOptionsCount)
            assertEquals(
                SettingsRepository.MAX_TRIP_OPTIONS,
                vm.uiState.value.fastestTrips[oslo.id]
                    ?.size,
            )

            settings.tripOptions = 2
            vm.loadSettings()
            vm.refreshData()
            runCurrent()
            assertEquals(2, entur.lastTripOptionsCount)
            assertEquals(here.latitude to here.longitude, entur.lastFastestTripCoords)
        }

    @Test
    fun `weather follows the device - not the saved location`() =
        boardTest {
            onSpot()
            locatedBoard()

            assertEquals(1, weather.calls)
        }

    @Test
    fun `wall-board features are off in on-spot mode`() =
        boardTest {
            onSpot()
            settings.cameraMotionWake = true
            settings.fullScreenFlow.value = true
            settings.alwaysOnFlow.value = true
            settings.burnInLevel = BurnInProtectionLevel.HIGH
            settings.dynamicRefresh = true
            val vm = viewModel(virtualClock())
            vm.startCameraMotion()
            camera.motion.tryEmit(Unit)
            runCurrent()

            val state = vm.uiState.value
            assertFalse(state.cameraMotionWake)
            assertFalse(state.isFullScreenMode)
            assertFalse(state.isAlwaysOnEnabled)
            assertEquals(BurnInProtectionLevel.OFF, state.burnInLevel)
            assertFalse(vm.isCameraMotionEnabled())
        }

    @Test
    fun `board mode never follows the device and asks for one route`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()

            assertEquals(0, location.activeCollectors)
            assertEquals(OnSpotLocation.Idle, vm.uiState.value.location)
            assertEquals(1, entur.lastTripOptionsCount)
        }

    @Test
    fun `leaving the screen stops following the device`() =
        boardTest {
            onSpot()
            val vm = locatedBoard()
            assertEquals(1, location.activeCollectors)

            vm.stopLocationTracking()
            runCurrent()

            assertEquals(0, location.activeCollectors)
        }

    @Test
    fun `switching to on-spot in Settings starts following - and back to board stops`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()
            assertEquals(0, location.activeCollectors)

            settings.mode = AppMode.ON_SPOT
            vm.loadSettings()
            runCurrent()
            assertEquals(1, location.activeCollectors)
            assertTrue(
                vm.uiState.value.favorites
                    .isEmpty(),
                "favourites are not on-spot stops",
            )

            settings.mode = AppMode.BOARD
            vm.loadSettings()
            runCurrent()
            assertEquals(0, location.activeCollectors)
            assertEquals(listOf(oslo), vm.uiState.value.favorites)
        }

    @Test
    fun `an empty nearby answer keeps the stops already listed`() =
        boardTest {
            onSpot()
            entur.nearby = listOf(near, far)
            val vm = locatedBoard()

            // The geocoder fails silently to an empty list.
            entur.nearby = emptyList()
            advanceTimeBy(20.seconds)
            emit(thirtyMetresOn)
            runCurrent()

            assertEquals(
                listOf(near.id, far.id),
                vm.uiState.value.nearbyStops
                    .map { it.stop.id },
            )
            assertEquals(near.id, vm.uiState.value.selectedStopId)
        }

    @Test
    fun `weather is refetched only after moving far`() =
        boardTest {
            onSpot()
            locatedBoard()
            assertEquals(1, weather.calls)

            advanceTimeBy(20.seconds)
            emit(hundredMetresOn)
            runCurrent()
            assertEquals(1, weather.calls, "the forecast covers an area")

            advanceTimeBy(20.seconds)
            emit(LocationResult.Fix(here.latitude + 0.012, here.longitude)) // about 1.3 km
            runCurrent()
            assertEquals(2, weather.calls)
        }

    @Test
    fun `the refresh interval is not stretched in on-spot mode`() =
        boardTest {
            onSpot()
            settings.dynamicRefresh = true
            val vm = locatedBoard()
            vm.dimScreen() // would double a wall board's interval
            advanceTimeBy(61.seconds) // the first interval was set before dimming
            val calls = entur.tripCalls

            advanceTimeBy(61.seconds)

            assertEquals(calls + 1, entur.tripCalls)
        }
}

package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.SamplePlace
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * On-spot mode abroad: a device outside Norway is told so, and can stand at a [SamplePlace] instead,
 * as testers and store reviewers do. Also the header's "Norway time" label. See [BoardTestBase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardSamplePlaceTest : BoardTestBase() {
    private val london = LocationResult.Fix(51.5072, -0.1276)
    private val osloFix = LocationResult.Fix(59.9139, 10.7522)
    private val station = StopPlace("NSR:StopPlace:59872", "Oslo S", latitude = 59.9111, longitude = 10.7528)

    private fun onSpot() {
        settings.mode = AppMode.ON_SPOT
    }

    private fun emit(result: LocationResult) = check(location.updates.getValue(LocationAccuracy.PRECISE).tryEmit(result))

    private fun TestScope.boardAt(fix: LocationResult.Fix): StopBoardViewModel {
        val vm = viewModel(virtualClock())
        vm.startLocationTracking()
        runCurrent()
        emit(fix)
        runCurrent()
        return vm
    }

    @Test
    fun `a fix outside Norway says so`() =
        boardTest {
            onSpot()
            val vm = boardAt(london)

            assertTrue(vm.uiState.value.isOutsideNorway)
        }

    @Test
    fun `a fix in Norway does not`() =
        boardTest {
            onSpot()
            val vm = boardAt(osloFix)

            assertFalse(vm.uiState.value.isOutsideNorway)
        }

    @Test
    fun `a sample place stands in for the device - without asking for its location`() =
        boardTest {
            onSpot()
            entur.nearby = listOf(station)
            val vm = boardAt(london)
            val requestsBefore = location.updateRequests.size

            vm.useSamplePlace(SamplePlace.OSLO_S)
            runCurrent()

            val state = vm.uiState.value
            assertEquals(SamplePlace.OSLO_S, state.samplePlace)
            assertFalse(state.isOutsideNorway)
            assertEquals(OnSpotLocation.Located(SamplePlace.OSLO_S.latitude, SamplePlace.OSLO_S.longitude, LocationAccuracy.PRECISE), state.location)
            assertEquals(SamplePlace.OSLO_S.latitude to SamplePlace.OSLO_S.longitude, entur.lastNearbyCoords)
            assertEquals(station.id, state.selectedStopId)
            assertEquals(requestsBefore, location.updateRequests.size, "the device's location is not asked for")
            assertEquals(0, location.activeCollectors)
            assertEquals(SamplePlace.OSLO_S, settings.storedSamplePlace, "kept for the next launch")
        }

    @Test
    fun `a saved sample place is used from launch`() =
        boardTest {
            onSpot()
            settings.storedSamplePlace = SamplePlace.BERGEN
            val vm = viewModel(virtualClock())
            vm.startLocationTracking()
            runCurrent()

            val state = vm.uiState.value
            assertEquals(SamplePlace.BERGEN, state.samplePlace)
            assertEquals(OnSpotLocation.Located(SamplePlace.BERGEN.latitude, SamplePlace.BERGEN.longitude, LocationAccuracy.PRECISE), state.location)
            assertTrue(location.updateRequests.isEmpty())
        }

    @Test
    fun `using the device's location again follows it - and forgets the sample place`() =
        boardTest {
            onSpot()
            entur.nearby = listOf(station)
            val vm = boardAt(london)
            vm.useSamplePlace(SamplePlace.OSLO_S)
            runCurrent()

            vm.useDeviceLocation()
            runCurrent()
            assertNull(vm.uiState.value.samplePlace)
            assertEquals(OnSpotLocation.Locating(LocationAccuracy.PRECISE), vm.uiState.value.location, "the sample place's stops are gone")
            assertTrue(
                vm.uiState.value.favorites
                    .isEmpty(),
            )
            emit(london)
            runCurrent()

            assertTrue(vm.uiState.value.isOutsideNorway)
            assertNull(settings.storedSamplePlace)
        }

    @Test
    fun `the clock is labelled Norway time only where the device's clock reads otherwise`() =
        boardTest {
            val vm = viewModel(virtualClock(), deviceTimeZone = TimeZone.of("America/New_York"))
            assertTrue(vm.uiState.value.isNorwayTimeLabelled)

            // Same offset as Oslo all year, so no label.
            val stockholm = viewModel(virtualClock(), deviceTimeZone = TimeZone.of("Europe/Stockholm"))
            assertFalse(stockholm.uiState.value.isNorwayTimeLabelled)
        }
}

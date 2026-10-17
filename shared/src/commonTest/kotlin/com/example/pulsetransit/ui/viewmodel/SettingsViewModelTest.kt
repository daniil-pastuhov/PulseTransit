package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.Quay
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel.LocationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The location lookup and the per-stop quay selection. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val location = FakeLocationProvider()
    private val entur = FakeEnturRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Like `boardTest`: the light-sensor collector never ends, so the scope must be cancelled. */
    private fun settingsTest(body: suspend TestScope.(SettingsViewModel) -> Unit) =
        runTest(dispatcher) {
            val vm = SettingsViewModel(entur, settings, FakeSystemSensorManager(), location)
            try {
                runCurrent()
                body(vm)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `a precise fix is used and saved without trying approximate`() =
        settingsTest { vm ->
            location.results = mapOf(LocationAccuracy.PRECISE to LocationResult.Fix(59.91, 10.75))

            vm.requestLocationAndNearbyStops()
            runCurrent()

            assertEquals(LocationStatus.Found(LocationAccuracy.PRECISE), vm.uiState.value.locationStatus)
            assertEquals(listOf(LocationAccuracy.PRECISE), location.requested)
            assertEquals(59.91, settings.getSavedLatitude()!!, absoluteTolerance = 0.0)
        }

    @Test
    fun `no precise fix falls back to approximate`() =
        settingsTest { vm ->
            location.results = mapOf(LocationAccuracy.APPROXIMATE to LocationResult.Fix(60.0, 11.0))

            vm.requestLocationAndNearbyStops()
            runCurrent()

            assertEquals(LocationStatus.Found(LocationAccuracy.APPROXIMATE), vm.uiState.value.locationStatus)
            assertEquals(listOf(LocationAccuracy.PRECISE, LocationAccuracy.APPROXIMATE), location.requested)
            assertFalse(vm.uiState.value.showManualCoordinatesPrompt)
        }

    @Test
    fun `no fix at either accuracy asks for manual coordinates`() =
        settingsTest { vm ->
            vm.requestLocationAndNearbyStops()
            runCurrent()

            assertEquals(LocationStatus.Failed, vm.uiState.value.locationStatus)
            assertTrue(vm.uiState.value.showManualCoordinatesPrompt)
        }

    @Test
    fun `denied permission is reported as such and not retried at lower accuracy`() =
        settingsTest { vm ->
            location.results = mapOf(LocationAccuracy.PRECISE to LocationResult.PermissionDenied)

            vm.requestLocationAndNearbyStops()
            runCurrent()

            assertEquals(LocationStatus.Denied, vm.uiState.value.locationStatus)
            assertEquals(listOf(LocationAccuracy.PRECISE), location.requested)
            assertTrue(vm.uiState.value.showManualCoordinatesPrompt)
        }

    @Test
    fun `manual coordinates are validated`() =
        settingsTest { vm ->
            vm.setManualCoordinates("north", "10.7")
            assertEquals(LocationStatus.InvalidManualFormat, vm.uiState.value.locationStatus)

            vm.setManualCoordinates("59.9", "10.7")
            assertEquals(LocationStatus.Manual, vm.uiState.value.locationStatus)
            assertEquals(59.9, settings.getSavedLatitude()!!, absoluteTolerance = 0.0)
        }

    /** A favourite with quays A, B and C, saved with [saved] as its quay selection. */
    private fun withQuays(saved: Set<String>?) {
        val stop = StopPlace(id = "S", name = "Stop", quays = listOf(Quay("A"), Quay("B"), Quay("C")))
        settings.favoriteStops = listOf(stop.copy(quays = emptyList()))
        entur.stopsWithQuays = mapOf("S" to stop)
        saved?.let { settings.selectedQuays = mapOf("S" to it) }
    }

    @Test
    fun `unticking a quay while all are shown selects the others`() =
        withQuays(saved = null).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "A")

                assertEquals(setOf("B", "C"), vm.uiState.value.selectedQuays["S"])
                assertEquals(setOf("B", "C"), settings.getSelectedQuays("S"))
            }
        }

    @Test
    fun `a quay no longer listed is dropped from the selection on the next tap`() =
        withQuays(saved = setOf("A", "gone")).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "B")

                assertEquals(setOf("A", "B"), vm.uiState.value.selectedQuays["S"])
            }
        }

    @Test
    fun `with only unlisted quays saved a tap ticks the quay rather than all the others`() =
        withQuays(saved = setOf("gone")).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "B")

                assertEquals(setOf("B"), vm.uiState.value.selectedQuays["S"])
            }
        }

    @Test
    fun `ticking the last unticked quay goes back to showing all`() =
        withQuays(saved = setOf("A", "B")).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "C")

                assertEquals(emptySet(), vm.uiState.value.selectedQuays["S"])
                assertEquals(null, settings.getSelectedQuays("S"))
            }
        }

    @Test
    fun `route modes toggle but the last allowed kind cannot be excluded`() =
        settingsTest { vm ->
            vm.toggleRouteMode(RouteTransportMode.EXPRESS_BUS)
            assertEquals(setOf(RouteTransportMode.EXPRESS_BUS), settings.excludedModes)

            vm.toggleRouteMode(RouteTransportMode.EXPRESS_BUS)
            assertEquals(emptySet(), vm.uiState.value.excludedRouteModes)

            RouteTransportMode.entries.forEach(vm::toggleRouteMode)
            assertEquals(RouteTransportMode.entries.size - 1, settings.excludedModes.size)
            assertEquals(settings.excludedModes, vm.uiState.value.excludedRouteModes)
        }
}

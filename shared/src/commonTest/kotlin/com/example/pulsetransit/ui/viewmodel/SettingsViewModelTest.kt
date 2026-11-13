package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.CoordinateProblem
import com.example.pulsetransit.data.Quay
import com.example.pulsetransit.data.RouteModeGroup
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TypedCoordinates
import com.example.pulsetransit.data.journey.JourneyAlertKind
import com.example.pulsetransit.data.journey.JourneyStoreImpl
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.platform.JourneyPermissions
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.platform.PermissionState
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel.LocationStatus
import com.russhwolf.settings.MapSettings
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The location lookup and the per-stop quay selection. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val location = FakeLocationProvider()
    private val entur = FakeEnturRepository()
    private val journeyStore = JourneyStoreImpl(MapSettings())
    private val journeyPermissions =
        object : JourneyPermissions {
            override val isSupported = true

            override fun notificationState() = PermissionState.GRANTED

            override fun locationState() = PermissionState.GRANTED

            override fun markAsked(
                notifications: Boolean,
                location: Boolean,
            ) = Unit
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Like `boardTest`: the light-sensor collector never ends, so the scope must be cancelled. */
    private fun settingsTest(body: suspend TestScope.(SettingsViewModel) -> Unit) =
        runTest(dispatcher) {
            val vm = SettingsViewModel(entur, settings, FakeSystemSensorManager(), location, journeyStore, journeyPermissions)
            try {
                runCurrent()
                body(vm)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `removing a stop offers Undo which puts it back in its place with its directions`() =
        settingsTest { vm ->
            val (a, b, c) = listOf("A", "B", "C").map { StopPlace(id = "NSR:StopPlace:$it", name = it) }
            settings.favoriteStops = listOf(a, b, c)
            settings.selectedQuays = mapOf(b.id to setOf("NSR:Quay:1"))
            vm.loadSettings()
            runCurrent()

            vm.removeFavorite(b.id)
            assertEquals(listOf(a, c), settings.favoriteStops)
            assertEquals(b, (vm.uiState.value.pendingUndo as SettingsViewModel.SettingsUndo.StopRemoved).stop)

            vm.undo()
            runCurrent()

            assertEquals(listOf(a, b, c), settings.favoriteStops)
            assertEquals(listOf(a, b, c), vm.uiState.value.favorites)
            assertEquals(setOf("NSR:Quay:1"), vm.uiState.value.selectedQuays[b.id])
            assertNull(vm.uiState.value.pendingUndo)
        }

    @Test
    fun `adding a stop offers Undo which removes it again`() =
        settingsTest { vm ->
            val (a, b) = listOf("A", "B").map { StopPlace(id = "NSR:StopPlace:$it", name = it) }
            settings.favoriteStops = listOf(a)
            vm.loadSettings()
            runCurrent()

            vm.addFavorite(b)
            runCurrent()
            assertTrue(vm.uiState.value.pendingUndo is SettingsViewModel.SettingsUndo.StopAdded)

            vm.undo()

            assertEquals(listOf(a), settings.favoriteStops)
            assertEquals(listOf(a), vm.uiState.value.favorites)
        }

    @Test
    fun `undoing the removal of the first stop puts it back first`() =
        settingsTest { vm ->
            val (a, b) = listOf("A", "B").map { StopPlace(id = "NSR:StopPlace:$it", name = it) }
            settings.favoriteStops = listOf(a, b)
            vm.loadSettings()
            runCurrent()

            vm.removeFavorite(a.id)
            vm.undo()

            assertEquals(listOf(a, b), settings.favoriteStops)
        }

    @Test
    fun `adding a stop loads its directions to choose from`() =
        settingsTest { vm ->
            val a = StopPlace(id = "NSR:StopPlace:A", name = "A")
            val withQuays = a.copy(quays = listOf(Quay(id = "NSR:Quay:1", publicCode = "1")))
            entur.stopsWithQuays = mapOf(a.id to withQuays)

            vm.addFavorite(a)
            runCurrent()

            assertEquals(withQuays, vm.uiState.value.favoriteStopsWithQuays[a.id])
        }

    @Test
    fun `adding a stop already on the board offers no Undo`() =
        settingsTest { vm ->
            val a = StopPlace(id = "NSR:StopPlace:A", name = "A")
            settings.favoriteStops = listOf(a)
            vm.loadSettings()
            runCurrent()

            vm.addFavorite(a)

            assertNull(vm.uiState.value.pendingUndo)
        }

    @Test
    fun `dismissing Undo keeps the change`() =
        settingsTest { vm ->
            val (a, b) = listOf("A", "B").map { StopPlace(id = "NSR:StopPlace:$it", name = it) }
            settings.favoriteStops = listOf(a, b)
            vm.loadSettings()
            runCurrent()

            vm.removeFavorite(a.id)
            vm.dismissUndo()
            vm.undo()

            assertEquals(listOf(b), settings.favoriteStops)
            assertNull(vm.uiState.value.pendingUndo)
        }

    @Test
    fun `dragging a destination saves the new order`() =
        settingsTest { vm ->
            val (a, b, c) = listOf("A", "B", "C").map { StopPlace(id = "NSR:StopPlace:$it", name = it) }
            settings.destinations = listOf(a, b, c)
            vm.loadSettings()
            runCurrent()

            vm.moveDestinationStation(fromIndex = 2, toIndex = 0)

            assertEquals(listOf(c, a, b), vm.uiState.value.destinationStations)
            assertEquals(listOf(c, a, b), settings.destinations)

            vm.moveDestinationStation(fromIndex = 0, toIndex = 5)
            assertEquals(listOf(c, a, b), settings.destinations, "an index out of range changes nothing")
        }

    @Test
    fun `the journey alert mode is shown and saved for the next journey`() =
        settingsTest { vm ->
            assertEquals(null, vm.uiState.value.journeyMode)
            assertTrue(vm.uiState.value.journeyAlertsSupported)

            vm.updateJourneyMode(JourneyTrackingMode.LOCATION)

            assertEquals(JourneyTrackingMode.LOCATION, vm.uiState.value.journeyMode)
            assertEquals(JourneyTrackingMode.LOCATION, journeyStore.preferredMode())
        }

    @Test
    fun `journey alerts are all on by default and each can be turned off`() =
        settingsTest { vm ->
            assertTrue(
                vm.uiState.value.disabledJourneyAlerts
                    .isEmpty(),
            )

            vm.updateJourneyAlert(JourneyAlertKind.FASTER_ROUTE, enabled = false)

            assertEquals(setOf(JourneyAlertKind.FASTER_ROUTE), vm.uiState.value.disabledJourneyAlerts)
            assertEquals(setOf(JourneyAlertKind.FASTER_ROUTE), journeyStore.disabledAlerts())
            vm.updateJourneyAlert(JourneyAlertKind.FASTER_ROUTE, enabled = true)
            assertTrue(
                vm.uiState.value.disabledJourneyAlerts
                    .isEmpty(),
            )
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
    fun `manual coordinates are validated field by field`() =
        settingsTest { vm ->
            val before = settings.getSavedLatitude()
            vm.setManualCoordinates("north", "10.7")
            assertEquals(
                TypedCoordinates.Invalid(CoordinateProblem.NOT_A_NUMBER, null),
                vm.uiState.value.coordinatesError,
            )
            // Nothing is saved while either field is wrong.
            assertEquals(before, settings.getSavedLatitude())

            vm.setManualCoordinates("59,9", "10.7")
            assertEquals(LocationStatus.Manual, vm.uiState.value.locationStatus)
            assertNull(vm.uiState.value.coordinatesError)
            assertEquals(59.9, settings.getSavedLatitude()!!, absoluteTolerance = 0.0)
        }

    @Test
    fun `editing a coordinate clears its error`() =
        settingsTest { vm ->
            vm.setManualCoordinates("", "10.7")
            vm.clearCoordinatesError()
            assertNull(vm.uiState.value.coordinatesError)
        }

    @Test
    fun `nearby stops come nearest first with their distances`() =
        settingsTest { vm ->
            val far = StopPlace(id = "NSR:StopPlace:F", name = "Far", latitude = 59.9200, longitude = 10.7528)
            val near = StopPlace(id = "NSR:StopPlace:N", name = "Near", latitude = 59.9116, longitude = 10.7528)
            val unplaced = StopPlace(id = "NSR:StopPlace:U", name = "Unplaced")
            entur.nearby = listOf(far, unplaced, near)

            vm.setManualCoordinates("59.9111", "10.7528")
            runCurrent()

            val nearby = vm.uiState.value.nearbyStops
            assertEquals(listOf(near.id, far.id, unplaced.id), nearby.map { it.stop.id })
            assertEquals(56, nearby[0].distanceMeters)
            assertNull(nearby[2].distanceMeters)
        }

    @Test
    fun `a stop whose platforms failed to load can be retried`() =
        settingsTest { vm ->
            val a = StopPlace(id = "NSR:StopPlace:A", name = "A")
            vm.addFavorite(a)
            runCurrent()
            assertTrue(a.id in vm.uiState.value.quaysFailed)

            val withQuays = a.copy(quays = listOf(Quay(id = "NSR:Quay:1", publicCode = "1")))
            entur.stopsWithQuays = mapOf(a.id to withQuays)
            vm.retryQuays(a.id)
            // Loading again, not failed, while the answer is on its way.
            assertFalse(a.id in vm.uiState.value.quaysFailed)
            runCurrent()

            assertFalse(a.id in vm.uiState.value.quaysFailed)
            assertEquals(withQuays, vm.uiState.value.favoriteStopsWithQuays[a.id])
        }

    @Test
    fun `show all clears a stop's platform choice`() =
        settingsTest { vm ->
            withQuays(saved = setOf("A"))
            vm.loadSettings()
            runCurrent()

            vm.showAllQuays("S")

            assertEquals(emptySet(), vm.uiState.value.selectedQuays["S"])
            assertNull(settings.getSelectedQuays("S"))
        }

    @Test
    fun `stops can be moved and the board order is saved`() =
        settingsTest { vm ->
            val (a, b, c) = listOf("A", "B", "C").map { StopPlace(id = "NSR:StopPlace:$it", name = it) }
            settings.favoriteStops = listOf(a, b, c)
            vm.loadSettings()

            vm.moveFavorite(0, 2)
            assertEquals(listOf(b, c, a), settings.favoriteStops)
            assertEquals(listOf(b, c, a), vm.uiState.value.favorites)

            vm.moveFavorite(2, 1)
            assertEquals(listOf(b, a, c), settings.favoriteStops)

            // Out of range: nothing moves.
            vm.moveFavorite(0, 3)
            vm.moveFavorite(-1, 0)
            assertEquals(listOf(b, a, c), settings.favoriteStops)
        }

    /** A favourite with quays A, B and C, saved with [saved] as its quay selection. */
    private fun withQuays(saved: Set<String>?) {
        val stop = StopPlace(id = "S", name = "Stop", quays = listOf(Quay("A"), Quay("B"), Quay("C")))
        settings.favoriteStops = listOf(stop.copy(quays = emptyList()))
        entur.stopsWithQuays = mapOf("S" to stop)
        saved?.let { settings.selectedQuays = mapOf("S" to it) }
    }

    @Test
    fun `ticking a quay while all are shown chooses just that one`() =
        withQuays(saved = null).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "A")

                assertEquals(setOf("A"), vm.uiState.value.selectedQuays["S"])
                assertEquals(setOf("A"), settings.getSelectedQuays("S"))
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
    fun `with only unlisted quays saved a tap ticks just that quay`() =
        withQuays(saved = setOf("gone")).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "B")

                assertEquals(setOf("B"), vm.uiState.value.selectedQuays["S"])
            }
        }

    @Test
    fun `unticking the last chosen quay goes back to showing all`() =
        withQuays(saved = setOf("A")).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "A")

                assertEquals(emptySet(), vm.uiState.value.selectedQuays["S"])
                assertEquals(null, settings.getSelectedQuays("S"))
            }
        }

    @Test
    fun `ticking every quay keeps them chosen`() =
        withQuays(saved = setOf("A", "B")).let {
            settingsTest { vm ->
                vm.toggleQuay("S", "C")

                // Ticked as the user sees them, not turned back into "all" under their finger.
                assertEquals(setOf("A", "B", "C"), vm.uiState.value.selectedQuays["S"])
            }
        }

    @Test
    fun `a group box allows the whole group when any of it is off and excludes it all otherwise`() =
        settingsTest { vm ->
            val bus = RouteTransportMode.entries.filter { it.group == RouteModeGroup.BUS }.toSet()
            vm.toggleRouteModeGroup(RouteModeGroup.BUS)
            assertEquals(bus, settings.excludedModes)

            // Partly off (mixed): the box allows them all again.
            vm.toggleRouteMode(RouteTransportMode.LOCAL_BUS)
            vm.toggleRouteModeGroup(RouteModeGroup.BUS)
            assertEquals(emptySet(), vm.uiState.value.excludedRouteModes)
            assertEquals(emptySet(), settings.excludedModes)
        }

    @Test
    fun `a group box leaves the other groups as they are`() =
        settingsTest { vm ->
            vm.toggleRouteMode(RouteTransportMode.REGIONAL_TRAIN)
            vm.toggleRouteMode(RouteTransportMode.LOCAL_BUS)

            // Mixed bus box: allows the buses, and the regional train stays off.
            vm.toggleRouteModeGroup(RouteModeGroup.BUS)

            assertEquals(setOf(RouteTransportMode.REGIONAL_TRAIN), settings.excludedModes)
        }

    @Test
    fun `a group box cannot exclude the last allowed kinds`() =
        settingsTest { vm ->
            listOf(RouteModeGroup.BUS, RouteModeGroup.TRAIN, RouteModeGroup.BOAT).forEach(vm::toggleRouteModeGroup)
            val before = settings.excludedModes

            vm.toggleRouteModeGroup(RouteModeGroup.OTHER)

            assertEquals(before, settings.excludedModes)
            assertTrue(RouteTransportMode.entries.any { it !in vm.uiState.value.excludedRouteModes })
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

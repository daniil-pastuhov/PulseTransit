package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.journey.JourneyFixtures
import com.example.pulsetransit.data.journey.JourneyOpenRequests
import com.example.pulsetransit.data.journey.JourneyStoreImpl
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.platform.JourneyPermissions
import com.example.pulsetransit.platform.PermissionState
import com.example.pulsetransit.platform.PermissionState.DENIED
import com.example.pulsetransit.platform.PermissionState.GRANTED
import com.example.pulsetransit.platform.PermissionState.NOT_ASKED
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
import kotlin.time.Clock
import kotlin.time.Instant

/** Every path from the "Notify me" button to a followed journey, by permission state. */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyAlertsViewModelTest {
    private class FakePermissions(
        var notifications: PermissionState = NOT_ASKED,
        var location: PermissionState = NOT_ASKED,
        override val isSupported: Boolean = true,
    ) : JourneyPermissions {
        val asked = mutableListOf<Pair<Boolean, Boolean>>()

        override fun notificationState() = notifications

        override fun locationState() = location

        override fun markAsked(
            notifications: Boolean,
            location: Boolean,
        ) {
            asked += notifications to location
        }
    }

    private class FakeLauncher : JourneyLauncher {
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val store = JourneyStoreImpl(MapSettings())
    private val launcher = FakeLauncher()
    private val openRequests = JourneyOpenRequests()
    private val trip = JourneyFixtures.trip
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(JourneyFixtures.T0)
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vmTest(
        permissions: FakePermissions,
        body: suspend TestScope.(JourneyAlertsViewModel) -> Unit,
    ) = runTest(dispatcher) {
        val vm = JourneyAlertsViewModel(store, permissions, launcher, openRequests, clock)
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private val followedMode get() = store.active.value?.mode

    @Test
    fun `without journey alerts on the platform there is no button`() =
        vmTest(FakePermissions(GRANTED, GRANTED, isSupported = false)) { vm ->
            assertFalse(vm.uiState.value.available)
            vm.onNotifyClick(trip)
            assertNull(store.active.value)
        }

    @Test
    fun `with notifications denied there is no button`() =
        vmTest(FakePermissions(DENIED, GRANTED)) { vm ->
            assertFalse(vm.uiState.value.available)
            vm.onNotifyClick(trip)
            assertNull(store.active.value)
            assertNull(vm.uiState.value.rationaleFor)
        }

    @Test
    fun `never asked shows the rationale first`() =
        vmTest(FakePermissions(NOT_ASKED, NOT_ASKED)) { vm ->
            assertTrue(vm.uiState.value.available)
            vm.onNotifyClick(trip)

            assertEquals(trip, vm.uiState.value.rationaleFor)
            assertNull(store.active.value)
        }

    @Test
    fun `time-only asks for notifications alone`() =
        vmTest(FakePermissions(NOT_ASKED, NOT_ASKED)) { vm ->
            vm.onNotifyClick(trip)
            vm.onRationaleChoice(JourneyTrackingMode.TIME_ONLY)

            assertEquals(
                JourneyAlertsViewModel.PermissionRequest(JourneyTrackingMode.TIME_ONLY, notifications = true, location = false),
                vm.uiState.value.permissionRequest,
            )
            assertNull(vm.uiState.value.rationaleFor)

            vm.onPermissionsResult(notificationsGranted = true, locationGranted = false)
            assertEquals(JourneyTrackingMode.TIME_ONLY, followedMode)
            assertEquals(1, launcher.starts)
            assertNull(vm.uiState.value.permissionRequest)
        }

    @Test
    fun `noticing a missed ride asks for both and follows with location`() =
        vmTest(FakePermissions(NOT_ASKED, NOT_ASKED)) { vm ->
            vm.onNotifyClick(trip)
            vm.onRationaleChoice(JourneyTrackingMode.LOCATION)
            assertEquals(
                JourneyAlertsViewModel.PermissionRequest(JourneyTrackingMode.LOCATION, notifications = true, location = true),
                vm.uiState.value.permissionRequest,
            )

            vm.onPermissionsResult(notificationsGranted = true, locationGranted = true)
            assertEquals(JourneyTrackingMode.LOCATION, followedMode)
            assertEquals(JourneyTrackingMode.LOCATION, store.preferredMode())
        }

    @Test
    fun `location refused at the prompt falls back to time-only`() =
        vmTest(FakePermissions(NOT_ASKED, NOT_ASKED)) { vm ->
            vm.onNotifyClick(trip)
            vm.onRationaleChoice(JourneyTrackingMode.LOCATION)
            vm.onPermissionsResult(notificationsGranted = true, locationGranted = false)

            assertEquals(JourneyTrackingMode.TIME_ONLY, followedMode)
        }

    @Test
    fun `notifications refused at the prompt start nothing and hide the button`() =
        vmTest(FakePermissions(NOT_ASKED, NOT_ASKED)) { vm ->
            vm.onNotifyClick(trip)
            vm.onRationaleChoice(JourneyTrackingMode.TIME_ONLY)
            vm.onPermissionsResult(notificationsGranted = false, locationGranted = false)
            assertNull(store.active.value)
            assertEquals(0, launcher.starts)
        }

    @Test
    fun `the prompts are recorded so denied is told from never asked`() {
        val permissions = FakePermissions(NOT_ASKED, NOT_ASKED)
        vmTest(permissions) { vm ->
            vm.onNotifyClick(trip)
            vm.onRationaleChoice(JourneyTrackingMode.LOCATION)
            permissions.notifications = DENIED
            permissions.location = DENIED
            vm.onPermissionsResult(notificationsGranted = false, locationGranted = false)

            assertEquals(listOf(true to true), permissions.asked)
            assertFalse(vm.uiState.value.available)
        }
    }

    @Test
    fun `location denied before follows by time without asking again`() =
        vmTest(FakePermissions(GRANTED, DENIED)) { vm ->
            vm.onNotifyClick(trip)

            assertNull(vm.uiState.value.rationaleFor)
            assertEquals(JourneyTrackingMode.TIME_ONLY, followedMode)
        }

    @Test
    fun `granted before with a mode chosen starts straight away`() =
        vmTest(FakePermissions(GRANTED, GRANTED)) { vm ->
            store.setPreferredMode(JourneyTrackingMode.LOCATION)
            vm.onNotifyClick(trip)

            assertNull(vm.uiState.value.rationaleFor)
            assertEquals(JourneyTrackingMode.LOCATION, followedMode)
        }

    @Test
    fun `granted before but no mode chosen asks which without prompts`() =
        vmTest(FakePermissions(GRANTED, GRANTED)) { vm ->
            vm.onNotifyClick(trip)
            assertEquals(trip, vm.uiState.value.rationaleFor)

            vm.onRationaleChoice(JourneyTrackingMode.LOCATION)
            assertNull(vm.uiState.value.permissionRequest)
            assertEquals(JourneyTrackingMode.LOCATION, followedMode)
        }

    @Test
    fun `the rationale closing after a choice does not cancel its prompts`() =
        vmTest(FakePermissions(NOT_ASKED, NOT_ASKED)) { vm ->
            vm.onNotifyClick(trip)
            vm.onRationaleChoice(JourneyTrackingMode.LOCATION)
            // As SwiftUI reports the sheet going away once the choice has closed it.
            vm.onRationaleDismiss()
            vm.onPermissionsResult(notificationsGranted = true, locationGranted = true)

            assertEquals(JourneyTrackingMode.LOCATION, followedMode)
        }

    @Test
    fun `dismissing the rationale starts nothing`() =
        vmTest(FakePermissions(NOT_ASKED, NOT_ASKED)) { vm ->
            vm.onNotifyClick(trip)
            vm.onRationaleDismiss()
            vm.onRationaleChoice(JourneyTrackingMode.TIME_ONLY)

            assertNull(vm.uiState.value.rationaleFor)
            assertNull(store.active.value)
        }

    @Test
    fun `the followed journey is shown and can be stopped`() =
        vmTest(FakePermissions(GRANTED, DENIED)) { vm ->
            vm.onNotifyClick(trip)
            runCurrent()
            assertTrue(vm.uiState.value.isFollowing(trip))
            assertEquals(
                trip,
                vm.uiState.value.activeJourney
                    ?.trip,
            )

            vm.onStopClick()
            runCurrent()
            assertFalse(vm.uiState.value.isFollowing(trip))
            assertEquals(1, launcher.stops)
            assertEquals(JourneyFixtures.T0, store.history().single().endedAtMillis)
        }

    @Test
    fun `a journey saved before is there when the screen opens`() {
        store.start(JourneyFixtures.journey())
        vmTest(FakePermissions(GRANTED, GRANTED)) { vm ->
            assertTrue(vm.uiState.value.isFollowing(trip))
        }
    }

    @Test
    fun `permissions changed in system settings are picked up`() {
        val permissions = FakePermissions(GRANTED, GRANTED)
        vmTest(permissions) { vm ->
            permissions.notifications = DENIED
            vm.refreshPermissions()
            assertFalse(vm.uiState.value.available)

            permissions.notifications = GRANTED
            vm.refreshPermissions()
            assertTrue(vm.uiState.value.available)
        }
    }

    @Test
    fun `show journey from a notification opens the details once`() =
        vmTest(FakePermissions(GRANTED, GRANTED)) { vm ->
            openRequests.request()
            runCurrent()
            assertTrue(vm.uiState.value.openJourneyRequested)

            vm.onJourneyOpened()
            runCurrent()
            assertFalse(vm.uiState.value.openJourneyRequested)
        }
}

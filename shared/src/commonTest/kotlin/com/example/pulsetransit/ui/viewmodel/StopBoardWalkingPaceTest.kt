package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.SamplePlace
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.pace.WalkingPaceRecord
import com.example.pulsetransit.data.pace.WalkingPaceSample
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** The board's walking speed proposal: measured on the spot, shown on the spot only. See [BoardTestBase]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardWalkingPaceTest : BoardTestBase() {
    private val today = Clock.System.now().toEpochMilliseconds() / 86_400_000L

    /** Enough walks, on two days, at 1.55 m/s (5.6 km/h): Fast. */
    private val fastWalker =
        WalkingPaceRecord(
            samples = (0 until 6).map { WalkingPaceSample(1.55, 180, today - it % 2) },
            boutCount = 6,
        )

    private val here = LocationResult.Fix(59.9139, 10.7522)

    private fun emit(result: LocationResult) = check(location.updates.getValue(LocationAccuracy.PRECISE).tryEmit(result))

    private fun TestScope.locatedBoard(clock: Clock = virtualClock()): StopBoardViewModel {
        val vm = viewModel(clock)
        vm.startLocationTracking()
        runCurrent()
        emit(here)
        runCurrent()
        return vm
    }

    @Test
    fun `an on-spot board shows the walking speed proposal`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            settings.pace = fastWalker
            val vm = viewModel()

            val proposal = vm.uiState.value.walkingSpeedProposal
            assertEquals(WalkingSpeed.FAST, proposal?.speed)
            assertEquals(WalkingSpeed.NORMAL, proposal?.current)
            assertEquals(5.6, proposal?.kmh)
        }

    @Test
    fun `a wall board never shows it`() =
        boardTest {
            settings.pace = fastWalker
            val vm = viewModel()

            assertNull(vm.uiState.value.walkingSpeedProposal)
        }

    @Test
    fun `a sample place hides it until the device is followed again`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            settings.pace = fastWalker
            val vm = viewModel()

            vm.useSamplePlace(SamplePlace.OSLO_S)
            runCurrent()
            assertNull(vm.uiState.value.walkingSpeedProposal, "the sample place's notice takes its place")
            vm.loadSettings()
            runCurrent()
            assertNull(vm.uiState.value.walkingSpeedProposal, "nor after Settings closes")

            vm.useDeviceLocation()
            runCurrent()
            assertEquals(
                WalkingSpeed.FAST,
                vm.uiState.value.walkingSpeedProposal
                    ?.speed,
            )
        }

    @Test
    fun `switching to the wall board hides it`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            settings.pace = fastWalker
            val vm = viewModel()

            settings.mode = AppMode.BOARD
            vm.loadSettings()
            runCurrent()

            assertNull(vm.uiState.value.walkingSpeedProposal)
        }

    @Test
    fun `accepting it routes again at the new speed`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            settings.destinations = listOf(oslo)
            settings.pace = fastWalker
            val vm = locatedBoard()
            assertEquals(WalkingSpeed.NORMAL, entur.lastRouteOptions?.walkingSpeed)

            vm.acceptWalkingSpeedProposal()
            runCurrent()

            assertEquals(WalkingSpeed.FAST, settings.walking)
            assertEquals(WalkingSpeed.FAST, entur.lastRouteOptions?.walkingSpeed)
            assertNull(vm.uiState.value.walkingSpeedProposal)
        }

    @Test
    fun `not now hides it and keeps the setting`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            settings.pace = fastWalker
            val vm = viewModel()

            vm.dismissWalkingSpeedProposal()
            runCurrent()

            assertNull(vm.uiState.value.walkingSpeedProposal)
            assertEquals(WalkingSpeed.NORMAL, settings.walking)
        }

    @Test
    fun `walking while the on-spot board follows the device measures the pace`() =
        boardTest {
            settings.mode = AppMode.ON_SPOT
            val clock = virtualClock()
            val vm = locatedBoard(clock)
            val from = clock.now().toEpochMilliseconds()
            (1..8).forEach { i ->
                advanceTimeBy(15.seconds)
                emit(LocationResult.Fix(here.latitude + i * 21.0 / 111_195.0, here.longitude, from + i * 15_000L, accuracyMeters = 5.0))
                runCurrent()
            }

            vm.stopLocationTracking()

            val sample = settings.pace.samples.single()
            assertEquals(1.4, sample.metersPerSecond)
            assertEquals(105, sample.seconds, "from the first fix with an accuracy to the last")
        }

    @Test
    fun `a wall board measures nothing`() =
        boardTest {
            val clock = virtualClock()
            val vm = viewModel(clock)
            vm.startLocationTracking()
            runCurrent()
            val from = clock.now().toEpochMilliseconds()
            (0..8).forEach { i -> emit(LocationResult.Fix(here.latitude + i * 21.0 / 111_195.0, here.longitude, from + i * 15_000L, 5.0)) }
            runCurrent()
            vm.stopLocationTracking()

            assertTrue(settings.pace.samples.isEmpty())
        }
}

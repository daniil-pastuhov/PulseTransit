package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.isoOslo
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Shared harness for the [StopBoardViewModel] tests: fakes, the [boardTest] runner and fixtures.
 *
 * Note on driving the ViewModel: its clock and auto-refresh are `while (true)` loops, so
 * `advanceUntilIdle()` would never return. These tests use [runCurrent] to run the work that is
 * already scheduled, and advance virtual time explicitly where a delay matters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BoardTestBase {
    protected val dispatcher = StandardTestDispatcher()
    protected val entur = FakeEnturRepository()
    protected val weather = FakeWeatherRepository()
    protected val settings = FakeSettingsRepository()
    protected val sensors = FakeSystemSensorManager()
    protected val camera = FakeCameraMotionDetector()
    protected val clockFormatter = FakeClockFormatter()
    protected val connectivity = FakeConnectivityMonitor()

    protected val minute = 60 * 1000L
    protected val oslo = StopPlace(id = "NSR:StopPlace:1", name = "Oslo S", locality = "Oslo")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    protected val created = mutableListOf<StopBoardViewModel>()

    /**
     * Runs [body], then cancels every ViewModel it created.
     *
     * This is not optional: the ViewModel's clock and auto-refresh are `while (true)` loops
     * scheduled on the test dispatcher, and runTest drains the scheduler once the body returns.
     * Leaving them running means the scheduler never goes idle and the test stalls until its
     * timeout rather than finishing.
     */
    protected fun boardTest(body: suspend TestScope.() -> Unit) =
        // These tests drive a ViewModel whose loops never end, so a mistake stalls rather than
        // fails. The timeout turns that into a fast, diagnosable failure instead of a hung build.
        runTest(dispatcher, timeout = 10.seconds) {
            try {
                body()
            } finally {
                created.forEach { it.viewModelScope.cancel() }
                created.clear()
            }
        }

    protected fun TestScope.viewModel(clock: Clock = Clock.System): StopBoardViewModel {
        val vm = StopBoardViewModel(entur, weather, settings, sensors, camera, clockFormatter, connectivity, clock)
        created += vm
        runCurrent() // let init's loadSettings / first refresh / sensor wiring run
        return vm
    }

    /** A clock that starts at the real time and then follows the test's virtual time, so `advanceTimeBy` moves it. */
    protected fun TestScope.virtualClock(): Clock {
        val start = Clock.System.now().toEpochMilliseconds()
        return object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(start + testScheduler.currentTime)
        }
    }

    protected fun departure(
        line: String,
        destination: String,
        atMillis: Long,
    ) = Departure(
        line = line,
        destination = destination,
        expectedTime = isoOslo(atMillis),
        aimedTime = isoOslo(atMillis),
        isRealtime = true,
        quayId = "NSR:Quay:1",
    )

    protected fun trip(destination: String) =
        TripInfo(
            destinationName = destination,
            expectedStartTime = "2026-10-14T12:00:00+02:00",
            lineName = "L1",
            durationMinutes = 20,
            originName = "Origin",
        )
}

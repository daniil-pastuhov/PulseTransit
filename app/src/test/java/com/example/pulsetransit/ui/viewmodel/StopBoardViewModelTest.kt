package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * Note on driving the ViewModel: its clock and auto-refresh are `while (true)` loops, so
 * `advanceUntilIdle()` would never return. These tests use [runCurrent] to run the work that is
 * already scheduled, and advance virtual time explicitly where a delay matters.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val entur = FakeEnturRepository()
    private val weather = FakeWeatherRepository()
    private val settings = FakeSettingsRepository()
    private val sensors = FakeSystemSensorManager()
    private val camera = FakeCameraMotionDetector()

    private val minute = 60 * 1000L
    private val oslo = StopPlace(id = "NSR:StopPlace:1", name = "Oslo S", locality = "Oslo")

    /**
     * These tests drive a ViewModel whose loops never end, so a mistake stalls rather than fails.
     * This turns that into a fast, diagnosable failure instead of a hung build.
     */
    @get:Rule
    val timeout: TestRule = Timeout.seconds(10)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val created = mutableListOf<StopBoardViewModel>()

    /**
     * Runs [body], then cancels every ViewModel it created.
     *
     * This is not optional: the ViewModel's clock and auto-refresh are `while (true)` loops
     * scheduled on the test dispatcher, and runTest drains the scheduler once the body returns.
     * Leaving them running means the scheduler never goes idle and the test stalls until its
     * timeout rather than finishing.
     */
    private fun boardTest(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                created.forEach { it.viewModelScope.cancel() }
                created.clear()
            }
        }

    private fun TestScope.viewModel(): StopBoardViewModel {
        val vm = StopBoardViewModel(entur, weather, settings, sensors, camera)
        created += vm
        runCurrent() // let init's loadSettings / first refresh / sensor wiring run
        return vm
    }

    private fun departure(
        line: String,
        destination: String,
        atMillis: Long,
    ) = Departure(
        line = line,
        destination = destination,
        expectedTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date(atMillis)),
        aimedTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date(atMillis)),
        isRealtime = true,
        quayId = "NSR:Quay:1",
    )

    // --- Header date style -----------------------------------------------------------------

    @Test
    fun `tapping the clock cycles date styles, wraps around and persists each choice`() =
        boardTest {
            settings.dateStyle = DateDisplayStyle.TILE
            val vm = viewModel()
            assertEquals(DateDisplayStyle.TILE, vm.uiState.value.dateStyle)

            vm.cycleDateStyle()
            assertEquals(DateDisplayStyle.LONG, vm.uiState.value.dateStyle)
            assertEquals(DateDisplayStyle.LONG, settings.dateStyle)

            vm.cycleDateStyle() // last entry wraps to the first
            assertEquals(DateDisplayStyle.HIDDEN, vm.uiState.value.dateStyle)
            assertEquals(DateDisplayStyle.HIDDEN, settings.dateStyle)
        }

    // --- Auto theme hysteresis -------------------------------------------------------------

    @Test
    fun `auto theme switches to dark only below the dark threshold`() =
        boardTest {
            settings.theme = ThemeMode.AUTO
            val vm = viewModel()

            sensors.lux.tryEmit(5f) // at/below 10 lux
            runCurrent()

            assertEquals(ThemeMode.DARK, vm.uiState.value.activeTheme)
        }

    @Test
    fun `auto theme switches to light only above the light threshold`() =
        boardTest {
            settings.theme = ThemeMode.AUTO
            val vm = viewModel()

            sensors.lux.tryEmit(5f)
            runCurrent()
            sensors.lux.tryEmit(80f) // at/above 40 lux
            runCurrent()

            assertEquals(ThemeMode.LIGHT, vm.uiState.value.activeTheme)
        }

    @Test
    fun `light readings inside the dead zone hold the current theme`() =
        boardTest {
            settings.theme = ThemeMode.AUTO
            val vm = viewModel()

            sensors.lux.tryEmit(5f)
            runCurrent()
            assertEquals(ThemeMode.DARK, vm.uiState.value.activeTheme)

            // Between the two thresholds: must not flap back to light.
            listOf(11f, 25f, 39f).forEach {
                sensors.lux.tryEmit(it)
                runCurrent()
                assertEquals("lux=$it should hold DARK", ThemeMode.DARK, vm.uiState.value.activeTheme)
            }

            sensors.lux.tryEmit(40f) // crossing the light threshold
            runCurrent()
            assertEquals(ThemeMode.LIGHT, vm.uiState.value.activeTheme)

            // Same dead zone, now coming from light: must hold LIGHT.
            listOf(39f, 25f, 11f).forEach {
                sensors.lux.tryEmit(it)
                runCurrent()
                assertEquals("lux=$it should hold LIGHT", ThemeMode.LIGHT, vm.uiState.value.activeTheme)
            }

            sensors.lux.tryEmit(10f)
            runCurrent()
            assertEquals(ThemeMode.DARK, vm.uiState.value.activeTheme)
        }

    @Test
    fun `light sensor is ignored unless the theme mode is auto`() =
        boardTest {
            settings.theme = ThemeMode.LIGHT
            val vm = viewModel()

            sensors.lux.tryEmit(0f)
            runCurrent()

            assertEquals(ThemeMode.LIGHT, vm.uiState.value.activeTheme)
        }

    @Test
    fun `leaving auto stops the light sensor from overriding the chosen theme`() =
        boardTest {
            settings.theme = ThemeMode.AUTO
            val vm = viewModel()

            sensors.lux.tryEmit(5f)
            runCurrent()
            assertEquals(ThemeMode.DARK, vm.uiState.value.activeTheme)

            // The user picks LIGHT on the Settings screen; returning to the board re-reads settings.
            settings.theme = ThemeMode.LIGHT
            vm.loadSettings()
            runCurrent()

            sensors.lux.tryEmit(0f) // pitch dark: must no longer force DARK
            runCurrent()

            assertEquals(ThemeMode.LIGHT, vm.uiState.value.activeTheme)
        }

    @Test
    fun `returning to auto starts the light sensor again`() =
        boardTest {
            settings.theme = ThemeMode.LIGHT
            val vm = viewModel()

            settings.theme = ThemeMode.AUTO
            vm.loadSettings()
            runCurrent()

            sensors.lux.tryEmit(5f)
            runCurrent()

            assertEquals(ThemeMode.DARK, vm.uiState.value.activeTheme)
        }

    // --- Offline / staleness ---------------------------------------------------------------

    @Test
    fun `a live board reports no staleness and updates the timestamp to now`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(listOf(departure("L1", "Lillestrøm", currentSystemTime() + 5 * minute))) }
            val vm = viewModel()

            assertNull(vm.uiState.value.staleSinceMillis)
            assertNull(vm.uiState.value.staleAgeMinutes)
            assertTrue(
                vm.uiState.value.lastUpdated
                    .isNotEmpty(),
            )
        }

    @Test
    fun `a cached board reports the age and the original fetch time, not now`() =
        boardTest {
            val fetchedAt = currentSystemTime() - 12 * minute
            settings.favoriteStops = listOf(oslo)
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "Lillestrøm", currentSystemTime() + 5 * minute)), fetchedAt)
            }
            val vm = viewModel()

            assertEquals(fetchedAt, vm.uiState.value.staleSinceMillis)
            assertEquals(12, vm.uiState.value.staleAgeMinutes)
            // The board must not claim data fetched 12 minutes ago is current.
            val expected = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(fetchedAt))
            assertEquals(expected, vm.uiState.value.lastUpdated)
        }

    @Test
    fun `the board is only as fresh as its stalest stop`() =
        boardTest {
            val older = currentSystemTime() - 20 * minute
            val newer = currentSystemTime() - 3 * minute
            val second = oslo.copy(id = "NSR:StopPlace:2", name = "Nationaltheatret")
            settings.favoriteStops = listOf(oslo, second)
            entur.departures = { stopId ->
                val at = currentSystemTime() + 5 * minute
                if (stopId == oslo.id) {
                    Sourced.cached(listOf(departure("L1", "A", at)), newer)
                } else {
                    Sourced.cached(listOf(departure("L2", "B", at)), older)
                }
            }
            val vm = viewModel()

            assertEquals(older, vm.uiState.value.staleSinceMillis)
            assertEquals(20, vm.uiState.value.staleAgeMinutes)
        }

    @Test
    fun `one cached stop makes the whole board stale even if another is live`() =
        boardTest {
            val fetchedAt = currentSystemTime() - 7 * minute
            val second = oslo.copy(id = "NSR:StopPlace:2")
            settings.favoriteStops = listOf(oslo, second)
            entur.departures = { stopId ->
                val at = currentSystemTime() + 5 * minute
                if (stopId == oslo.id) {
                    Sourced.live(listOf(departure("L1", "A", at)))
                } else {
                    Sourced.cached(listOf(departure("L2", "B", at)), fetchedAt)
                }
            }
            val vm = viewModel()

            assertEquals(fetchedAt, vm.uiState.value.staleSinceMillis)
        }

    @Test
    fun `recovering from offline clears the stale banner`() =
        boardTest {
            val fetchedAt = currentSystemTime() - 9 * minute
            settings.favoriteStops = listOf(oslo)
            val at = currentSystemTime() + 5 * minute
            entur.departures = { Sourced.cached(listOf(departure("L1", "A", at)), fetchedAt) }
            val vm = viewModel()
            assertEquals(fetchedAt, vm.uiState.value.staleSinceMillis)

            entur.departures = { Sourced.live(listOf(departure("L1", "A", at))) }
            vm.refreshDepartures()
            runCurrent()

            assertNull("a successful refresh must clear staleness", vm.uiState.value.staleSinceMillis)
            assertNull(vm.uiState.value.staleAgeMinutes)
        }

    @Test
    fun `the stale age climbs as the clock ticks`() =
        boardTest {
            val fetchedAt = currentSystemTime() - 2 * minute
            settings.favoriteStops = listOf(oslo)
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "A", currentSystemTime() + 30 * minute)), fetchedAt)
            }
            val vm = viewModel()
            assertEquals(2, vm.uiState.value.staleAgeMinutes)

            // The clock loop recomputes the age; it is wall-clock based, so drive it with real offsets.
            val older = currentSystemTime() - 15 * minute
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "A", currentSystemTime() + 30 * minute)), older)
            }
            vm.refreshDepartures()
            runCurrent()

            assertEquals(15, vm.uiState.value.staleAgeMinutes)
        }

    // --- Departures ------------------------------------------------------------------------

    @Test
    fun `filtered lines are dropped from the board`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            settings.filtered = setOf("L1")
            val at = currentSystemTime() + 5 * minute
            entur.departures = {
                Sourced.live(listOf(departure("L1", "Lillestrøm", at), departure("R13", "Drammen", at)))
            }
            val vm = viewModel()

            val lines =
                vm.uiState.value.departures
                    .single()
                    .groups
                    .flatMap { g -> g.departures.map { it.line } }
            assertEquals(listOf("R13"), lines)
        }

    @Test
    fun `consecutive departures on the same line and destination are grouped`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val at = currentSystemTime() + 5 * minute
            entur.departures = {
                Sourced.live(
                    listOf(
                        departure("L1", "Lillestrøm", at),
                        departure("L1", "Lillestrøm", at + minute),
                        departure("R13", "Drammen", at + 2 * minute),
                        // Same line and destination again, but no longer consecutive: its own group.
                        departure("L1", "Lillestrøm", at + 3 * minute),
                    ),
                )
            }
            val vm = viewModel()

            val groups =
                vm.uiState.value.departures
                    .single()
                    .groups
            assertEquals(3, groups.size)
            assertEquals(2, groups[0].departures.size)
            assertEquals("R13", groups[1].departures.single().line)
            assertEquals(1, groups[2].departures.size)
        }

    @Test
    fun `cancelled departures are hidden unless the setting is on`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val at = currentSystemTime() + 5 * minute
            entur.departures = {
                Sourced.live(
                    listOf(
                        departure("L1", "Lillestrøm", at).copy(isCancelled = true),
                        departure("R13", "Drammen", at),
                    ),
                )
            }
            val vm = viewModel()

            assertEquals(
                listOf("R13"),
                vm.uiState.value.departures
                    .single()
                    .groups
                    .map { it.departures.first().line },
            )

            settings.showCancelledFlow.value = true
            runCurrent()

            assertEquals(
                listOf("L1", "R13"),
                vm.uiState.value.departures
                    .single()
                    .groups
                    .map { it.departures.first().line },
            )
        }

    @Test
    fun `no favorites means no request and a clear message`() =
        boardTest {
            settings.favoriteStops = emptyList()
            val vm = viewModel()

            assertEquals("No favorites", vm.uiState.value.lastUpdated)
            assertTrue(
                vm.uiState.value.departures
                    .isEmpty(),
            )
            assertTrue(entur.requestedQuays.isEmpty())
        }

    @Test
    fun `selected quays are passed through to the repository`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            settings.selectedQuays = mapOf(oslo.id to setOf("NSR:Quay:7"))
            entur.departures = { Sourced.live(emptyList()) }
            viewModel()

            assertEquals(setOf("NSR:Quay:7"), entur.requestedQuays[oslo.id])
        }

    // --- Fastest Route ---------------------------------------------------------------------

    @Test
    fun `fastest route uses configured location if present`() =
        boardTest {
            settings.destinations = listOf(oslo)
            settings.latitude = 60.0
            settings.longitude = 11.0
            viewModel()

            assertEquals(60.0, entur.lastFastestTripCoords?.first)
            assertEquals(11.0, entur.lastFastestTripCoords?.second)
            assertNull(entur.lastFastestTripFromIds)
        }

    @Test
    fun `fastest route falls back to favorites if no location is configured`() =
        boardTest {
            settings.destinations = listOf(oslo)
            settings.favoriteStops = listOf(oslo.copy(id = "FAV1"))
            settings.latitude = null
            settings.longitude = null
            viewModel()

            assertNull(entur.lastFastestTripCoords)
            assertEquals(listOf("FAV1"), entur.lastFastestTripFromIds)
        }

    // --- Weather ---------------------------------------------------------------------------

    @Test
    fun `weather is published from the payload regardless of its source`() =
        boardTest {
            val forecast = listOf(WeatherInfo("2026-07-15T14:00:00Z", 21.0, "clearsky_day", 0.0))
            // Cached weather is normal (MET's Expires gate), so it must not raise the offline banner.
            weather.weather = Sourced.cached(forecast, currentSystemTime() - 25 * minute)
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(emptyList()) }
            val vm = viewModel()

            assertEquals(forecast, vm.uiState.value.weather)
            assertNull("a weather cache hit is not an offline board", vm.uiState.value.staleSinceMillis)
        }

    @Test
    fun `weather is not requested without saved coordinates`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            viewModel()

            assertEquals(0, weather.calls)
        }

    // --- Screen dimming --------------------------------------------------------------------

    @Test
    fun `waking the screen schedules an auto-dim when camera wake is on`() =
        boardTest {
            settings.cameraMotionWake = true
            settings.autoDim = 30
            val vm = viewModel()

            vm.wakeScreen()
            runCurrent()
            assertEquals(false, vm.uiState.value.isScreenDimmed)

            advanceTimeBy(29_000.milliseconds)
            runCurrent()
            assertEquals("must stay awake until the delay elapses", false, vm.uiState.value.isScreenDimmed)

            advanceTimeBy(2_000.milliseconds)
            runCurrent()
            assertEquals(true, vm.uiState.value.isScreenDimmed)
        }

    @Test
    fun `motion wakes the screen again and restarts the dim timer`() =
        boardTest {
            settings.cameraMotionWake = true
            settings.autoDim = 30
            val vm = viewModel()
            vm.bindCameraMotion(TestLifecycleOwner())
            runCurrent()

            vm.dimScreen()
            assertEquals(true, vm.uiState.value.isScreenDimmed)

            camera.motion.tryEmit(true)
            runCurrent()

            assertEquals(false, vm.uiState.value.isScreenDimmed)
        }

    @Test
    fun `auto-dim of never keeps the screen awake`() =
        boardTest {
            settings.cameraMotionWake = true
            settings.autoDim = 0 // "Never"
            val vm = viewModel()

            vm.wakeScreen()
            advanceTimeBy((10 * 60 * 1000).milliseconds)
            runCurrent()

            assertEquals(false, vm.uiState.value.isScreenDimmed)
        }

    private fun currentSystemTime() = System.currentTimeMillis()
}

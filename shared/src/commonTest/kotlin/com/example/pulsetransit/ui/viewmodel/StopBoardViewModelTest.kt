package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.RouteOptions
import com.example.pulsetransit.data.RoutePreference
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.nowMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** See [BoardTestBase] for how these tests drive the ViewModel's endless loops. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardViewModelTest : BoardTestBase() {
    // --- Header date style -----------------------------------------------------------------

    @Test
    fun `tapping the clock cycles date styles - wraps around and persists each choice`() =
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
                assertEquals(ThemeMode.DARK, vm.uiState.value.activeTheme, message = "lux=$it should hold DARK")
            }

            sensors.lux.tryEmit(40f) // crossing the light threshold
            runCurrent()
            assertEquals(ThemeMode.LIGHT, vm.uiState.value.activeTheme)

            // Same dead zone, now coming from light: must hold LIGHT.
            listOf(39f, 25f, 11f).forEach {
                sensors.lux.tryEmit(it)
                runCurrent()
                assertEquals(ThemeMode.LIGHT, vm.uiState.value.activeTheme, message = "lux=$it should hold LIGHT")
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
            entur.departures = { Sourced.live(listOf(departure("L1", "Lillestrøm", nowMillis() + 5 * minute))) }
            val vm = viewModel()

            assertNull(vm.uiState.value.staleSinceMillis)
            assertNull(vm.uiState.value.staleAgeMinutes)
            assertTrue(
                vm.uiState.value.lastUpdated
                    .isNotEmpty(),
            )
        }

    @Test
    fun `a cached board reports the age and the original fetch time - not now`() =
        boardTest {
            val fetchedAt = nowMillis() - 12 * minute
            settings.favoriteStops = listOf(oslo)
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "Lillestrøm", nowMillis() + 5 * minute)), fetchedAt)
            }
            val vm = viewModel()

            assertEquals(fetchedAt, vm.uiState.value.staleSinceMillis)
            assertEquals(12, vm.uiState.value.staleAgeMinutes)
            // The board must not claim data fetched 12 minutes ago is current.
            assertEquals(clockFormatter.timeWithSeconds(fetchedAt), vm.uiState.value.lastUpdated)
        }

    @Test
    fun `the board is only as fresh as its stalest stop`() =
        boardTest {
            val older = nowMillis() - 20 * minute
            val newer = nowMillis() - 3 * minute
            val second = oslo.copy(id = "NSR:StopPlace:2", name = "Nationaltheatret")
            settings.favoriteStops = listOf(oslo, second)
            entur.departures = { stopId ->
                val at = nowMillis() + 5 * minute
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
            val fetchedAt = nowMillis() - 7 * minute
            val second = oslo.copy(id = "NSR:StopPlace:2")
            settings.favoriteStops = listOf(oslo, second)
            entur.departures = { stopId ->
                val at = nowMillis() + 5 * minute
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
            val fetchedAt = nowMillis() - 9 * minute
            settings.favoriteStops = listOf(oslo)
            val at = nowMillis() + 5 * minute
            entur.departures = { Sourced.cached(listOf(departure("L1", "A", at)), fetchedAt) }
            val vm = viewModel()
            assertEquals(fetchedAt, vm.uiState.value.staleSinceMillis)

            entur.departures = { Sourced.live(listOf(departure("L1", "A", at))) }
            vm.refreshDepartures()
            runCurrent()

            assertNull(vm.uiState.value.staleSinceMillis, message = "a successful refresh must clear staleness")
            assertNull(vm.uiState.value.staleAgeMinutes)
        }

    @Test
    fun `the stale age climbs as the clock ticks`() =
        boardTest {
            val fetchedAt = nowMillis() - 2 * minute
            settings.favoriteStops = listOf(oslo)
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "A", nowMillis() + 30 * minute)), fetchedAt)
            }
            val vm = viewModel()
            assertEquals(2, vm.uiState.value.staleAgeMinutes)

            // The clock loop recomputes the age; it is wall-clock based, so drive it with real offsets.
            val older = nowMillis() - 15 * minute
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "A", nowMillis() + 30 * minute)), older)
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
            val at = nowMillis() + 5 * minute
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
    fun `routes are searched with the route settings and the filtered lines`() =
        boardTest {
            settings.destinations = listOf(oslo)
            settings.filtered = setOf("L1")
            settings.preference = RoutePreference.FEWER_TRANSFERS
            settings.excludedModes = setOf(RouteTransportMode.CAR_FERRY)
            settings.walking = WalkingSpeed.SLOW
            viewModel()

            assertEquals(
                RouteOptions(RoutePreference.FEWER_TRANSFERS, setOf(RouteTransportMode.CAR_FERRY), setOf("L1"), WalkingSpeed.SLOW),
                entur.lastRouteOptions,
            )
        }

    @Test
    fun `filtering out a line searches the routes again without it`() =
        boardTest {
            settings.destinations = listOf(oslo)
            val vm = viewModel()
            val calls = entur.tripCalls

            vm.filterOutLine("31")
            runCurrent()

            assertEquals(calls + 1, entur.tripCalls)
            assertEquals(setOf("31"), entur.lastRouteOptions?.bannedLines)
        }

    @Test
    fun `consecutive departures on the same line and destination are grouped`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val at = nowMillis() + 5 * minute
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
            val at = nowMillis() + 5 * minute
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

    @Test
    fun `searching further ahead widens the window for that stop only and survives refresh`() =
        boardTest {
            val bergen = oslo.copy(id = "NSR:StopPlace:548")
            settings.favoriteStops = listOf(oslo, bergen)
            entur.departures = { Sourced.live(emptyList()) }
            val vm = viewModel()
            runCurrent()
            assertEquals(EnturRepository.DEFAULT_DEPARTURE_WINDOW_SECONDS, entur.requestedTimeRanges[oslo.id])

            vm.searchFurtherAhead(oslo.id)
            runCurrent()

            assertEquals(setOf(oslo.id), vm.uiState.value.extendedStopIds)
            assertEquals(EnturRepository.EXTENDED_DEPARTURE_WINDOW_SECONDS, entur.requestedTimeRanges[oslo.id])
            assertEquals(EnturRepository.DEFAULT_DEPARTURE_WINDOW_SECONDS, entur.requestedTimeRanges[bergen.id])

            vm.refreshDepartures()
            runCurrent()
            assertEquals(EnturRepository.EXTENDED_DEPARTURE_WINDOW_SECONDS, entur.requestedTimeRanges[oslo.id])
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
            weather.weather = Sourced.cached(forecast, nowMillis() - 25 * minute)
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(emptyList()) }
            val vm = viewModel()

            assertEquals(forecast, vm.uiState.value.weather)
            assertNull(vm.uiState.value.staleSinceMillis, message = "a weather cache hit is not an offline board")
        }

    @Test
    fun `weather is not requested without saved coordinates`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            viewModel()

            assertEquals(0, weather.calls)
        }

    @Test
    fun `weather is fetched on the next refresh once coordinates are saved`() =
        boardTest {
            settings.latitude = null
            settings.longitude = null
            val vm = viewModel()

            // As when Settings closes after "Detect location": the board restarts its refresh.
            settings.latitude = 59.9149
            settings.longitude = 10.7322
            vm.startAutoRefresh()
            runCurrent()

            assertEquals(1, weather.calls, "must not wait out the 30-minute weather cadence")
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
            assertEquals(false, vm.uiState.value.isScreenDimmed, message = "must stay awake until the delay elapses")

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
            vm.startCameraMotion()
            runCurrent()

            vm.dimScreen()
            assertEquals(true, vm.uiState.value.isScreenDimmed)

            camera.motion.tryEmit(Unit)
            runCurrent()

            assertEquals(false, vm.uiState.value.isScreenDimmed)
        }

    @Test
    fun `motion is ignored once the camera is stopped`() =
        boardTest {
            settings.cameraMotionWake = true
            settings.autoDim = 30
            val vm = viewModel()
            vm.startCameraMotion()
            runCurrent()

            vm.stopCameraMotion()
            vm.dimScreen()
            camera.motion.tryEmit(Unit)
            runCurrent()

            assertEquals(true, vm.uiState.value.isScreenDimmed, message = "a backgrounded board must not react to the camera")
        }

    @Test
    fun `camera motion does nothing while the setting is off`() =
        boardTest {
            settings.cameraMotionWake = false
            val vm = viewModel()
            vm.startCameraMotion()
            runCurrent()

            vm.dimScreen()
            camera.motion.tryEmit(Unit)
            runCurrent()

            assertEquals(true, vm.uiState.value.isScreenDimmed)
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
}

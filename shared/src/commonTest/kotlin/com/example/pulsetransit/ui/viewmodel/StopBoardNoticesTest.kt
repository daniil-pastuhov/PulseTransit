package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.DirectionSummary
import com.example.pulsetransit.data.Quay
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.nowMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** The Fjord board's state: when to call data stale, Undo, panel-head summaries and manual refresh. */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardNoticesTest : BoardTestBase() {
    @Test
    fun `cached data younger than two refresh intervals is not called stale`() =
        boardTest {
            settings.refreshInterval = 60
            settings.favoriteStops = listOf(oslo)
            val fetchedAt = nowMillis() - 90_000
            entur.departures = { Sourced.cached(listOf(departure("L1", "A", nowMillis() + 30 * minute)), fetchedAt) }
            val vm = viewModel()

            // Still reported for the footer's timestamp, but an unattended board shows no notice yet.
            assertEquals(fetchedAt, vm.uiState.value.staleSinceMillis)
            assertFalse(vm.uiState.value.showStaleNotice)
        }

    @Test
    fun `cached data older than two refresh intervals is called stale`() =
        boardTest {
            settings.refreshInterval = 60
            settings.favoriteStops = listOf(oslo)
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "A", nowMillis() + 30 * minute)), nowMillis() - 3 * minute)
            }
            val vm = viewModel()

            assertTrue(vm.uiState.value.showStaleNotice)
        }

    @Test
    fun `the stale notice appears once the data ages past two intervals`() =
        boardTest {
            settings.refreshInterval = 60
            settings.favoriteStops = listOf(oslo)
            val clock = virtualClock()
            val fetchedAt = clock.now().toEpochMilliseconds() - 60_000
            entur.departures = { Sourced.cached(listOf(departure("L1", "A", nowMillis() + 30 * minute)), fetchedAt) }
            val vm = viewModel(clock)
            assertFalse(vm.uiState.value.showStaleNotice)

            // The clock loop re-evaluates each second; staleness is measured against the fetch time.
            advanceTimeBy(61_000)
            runCurrent()

            assertTrue(vm.uiState.value.showStaleNotice)
        }

    @Test
    fun `at night the stale threshold stretches with the refresh interval`() =
        boardTest {
            settings.refreshInterval = 60
            settings.dynamicRefresh = true
            settings.favoriteStops = listOf(oslo)
            // 02:00 in Oslo, inside the night hours, where the interval is five times as long.
            val night = Instant.parse("2026-01-15T01:00:00Z")
            val clock =
                object : Clock {
                    override fun now() = night
                }
            val nightMillis = night.toEpochMilliseconds()
            var fetchedAt = nightMillis - 3 * minute
            entur.departures = { Sourced.cached(listOf(departure("L1", "A", nightMillis + 30 * minute)), fetchedAt) }
            val vm = viewModel(clock, TimeZone.of("Europe/Oslo"))
            assertFalse(vm.uiState.value.showStaleNotice, "3 min is within 2 × 5 min")

            fetchedAt = nightMillis - 11 * minute
            vm.refreshDepartures()
            runCurrent()
            assertTrue(vm.uiState.value.showStaleNotice)
        }

    @Test
    fun `a dimmed board waits twice as long before calling its data stale`() =
        boardTest {
            settings.refreshInterval = 60
            settings.dynamicRefresh = true
            settings.cameraMotionWake = true
            settings.autoDim = 30
            settings.favoriteStops = listOf(oslo)
            val clock = virtualClock()
            val fetchedAt = clock.now().toEpochMilliseconds() - 150_000
            entur.departures = { Sourced.cached(listOf(departure("L1", "A", nowMillis() + 30 * minute)), fetchedAt) }
            val vm = viewModel(clock)
            assertTrue(vm.uiState.value.showStaleNotice, "2.5 min is past 2 × 1 min while awake")

            vm.wakeScreen()
            advanceTimeBy(31_000)
            runCurrent()
            assertTrue(vm.uiState.value.isScreenDimmed)
            assertFalse(vm.uiState.value.showStaleNotice, "about 3 min is within 2 × 2 min while dimmed")
        }

    @Test
    fun `with auto-refresh off any cached data is called stale`() =
        boardTest {
            settings.refreshInterval = 0
            settings.favoriteStops = listOf(oslo)
            entur.departures = {
                Sourced.cached(listOf(departure("L1", "A", nowMillis() + 30 * minute)), nowMillis() - 10_000)
            }
            val vm = viewModel()

            assertTrue(vm.uiState.value.showStaleNotice)
        }

    @Test
    fun `a live board shows no stale notice`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(listOf(departure("L1", "A", nowMillis() + 5 * minute))) }
            val vm = viewModel()

            assertFalse(vm.uiState.value.showStaleNotice)
        }

    @Test
    fun `panel heads summarise the picked directions once the quays are known`() =
        boardTest {
            val withQuays =
                oslo.copy(quays = (2..5).map { Quay(id = "NSR:Quay:$it", publicCode = "$it") })
            val second = oslo.copy(id = "NSR:StopPlace:2", name = "Jernbanetorget")
            settings.favoriteStops = listOf(oslo, second)
            settings.selectedQuays = mapOf(oslo.id to setOf("NSR:Quay:2", "NSR:Quay:3", "NSR:Quay:4"))
            entur.stopsWithQuays = mapOf(oslo.id to withQuays)
            val vm = viewModel()
            runCurrent()

            val summaries = vm.uiState.value.directionSummaries
            assertEquals(DirectionSummary.Platforms("2–4", 3), summaries[oslo.id])
            assertEquals(DirectionSummary.All, summaries[second.id])
        }

    @Test
    fun `a refresh the user asks for is shown until it finishes - an automatic one never is`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { Sourced.live(listOf(departure("L1", "A", nowMillis() + 5 * minute))) }
            entur.departuresDelayMillis = { 1_000L }
            val vm = viewModel()
            assertFalse(vm.uiState.value.isManualRefreshing, "the first, automatic load")

            vm.refreshNow()
            runCurrent()
            assertTrue(vm.uiState.value.isManualRefreshing)

            advanceTimeBy(1_001)
            runCurrent()
            assertFalse(vm.uiState.value.isManualRefreshing)
        }

    @Test
    fun `filtering out a line offers Undo which shows it again`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val at = nowMillis() + 5 * minute
            entur.departures = { Sourced.live(listOf(departure("31", "A", at), departure("L2", "B", at))) }
            val vm = viewModel()

            vm.filterOutLine("31")
            runCurrent()
            assertEquals(setOf("31"), settings.filtered)
            assertEquals(BoardUndo.LineHidden("31", 1), vm.uiState.value.pendingUndo)
            assertFalse(
                vm.uiState.value.departures
                    .any { stop -> stop.groups.any { g -> g.departures.any { it.line == "31" } } },
            )

            vm.undo()
            runCurrent()

            assertTrue(settings.filtered.isEmpty())
            assertTrue(
                vm.uiState.value.departures
                    .any { stop -> stop.groups.any { g -> g.departures.any { it.line == "31" } } },
            )
            assertNull(vm.uiState.value.pendingUndo)
        }

    @Test
    fun `filtering out a line that is already hidden offers no Undo`() =
        boardTest {
            settings.filtered = setOf("31")
            val vm = viewModel()

            vm.filterOutLine("31")

            assertNull(vm.uiState.value.pendingUndo)
        }
}

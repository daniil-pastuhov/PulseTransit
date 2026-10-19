package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.nowMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Load more: scrolling a stop's board to its end grows that stop alone by a page, within the cap,
 * undoing a page that adds no rows and lapsing back to one page a while after the last one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardLoadMoreTest : BoardTestBase() {
    /** As many departures as asked for, up to [available], so the stop has that many in its window. */
    private fun fullPages(available: Int) =
        { limit: Int -> Sourced.live(List(minOf(limit, available)) { departure("L$it", "Somewhere", nowMillis() + it * 60_000L) }) }

    @Test
    fun `scrolling to the end fetches another page for that stop alone and keeps it across refreshes`() =
        boardTest {
            val bergen = oslo.copy(id = "NSR:StopPlace:548")
            settings.favoriteStops = listOf(oslo, bergen)
            val page = fullPages(available = 100)
            entur.departures = { stopId -> page(entur.requestedLimits.getValue(stopId)) }
            val vm = viewModel()
            assertEquals(15, entur.requestedLimits[oslo.id])
            assertEquals(setOf(oslo.id, bergen.id), vm.uiState.value.canLoadMoreStopIds)
            val calls = entur.departureCalls

            vm.loadMoreDepartures(oslo.id)
            runCurrent()

            assertEquals(calls + 1, entur.departureCalls) // Bergen is not refetched
            assertEquals(30, entur.requestedLimits[oslo.id])
            // A full page means more may follow, so the window stays as it was.
            assertEquals(EnturRepository.DEFAULT_DEPARTURE_WINDOW_SECONDS, entur.requestedTimeRanges[oslo.id])
            assertEquals(15, entur.requestedLimits[bergen.id])
            assertEquals(
                30,
                vm.uiState.value.departures
                    .first { it.stopId == oslo.id }
                    .groups.size,
            )

            vm.refreshDepartures()
            runCurrent()
            assertEquals(30, entur.requestedLimits[oslo.id])
        }

    @Test
    fun `load more stops at the cap`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val page = fullPages(available = 1_000)
            entur.departures = { stopId -> page(entur.requestedLimits.getValue(stopId)) }
            val vm = viewModel()

            repeat(20) {
                vm.loadMoreDepartures(oslo.id)
                runCurrent()
            }

            assertEquals(150, entur.requestedLimits[oslo.id])
            assertTrue(oslo.id !in vm.uiState.value.canLoadMoreStopIds)
        }

    @Test
    fun `a full refresh supersedes a load more still in flight`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val page = fullPages(available = 100)
            entur.departures = { stopId -> page(entur.requestedLimits.getValue(stopId)) }
            entur.departuresDelayMillis = { 1_000L }
            val vm = viewModel()
            advanceTimeBy(1_001.milliseconds)

            vm.loadMoreDepartures(oslo.id) // answers at +1000 ms
            advanceTimeBy(500.milliseconds)
            entur.departures = { Sourced.live(listOf(departure("Fresh", "A", nowMillis() + 60_000L))) }
            vm.refreshDepartures() // answers at +1500 ms
            advanceTimeBy(501.milliseconds)

            // The superseded page came back (the repository swallows cancellation) but must not
            // land, nor end the refresh's loading state early.
            assertTrue(oslo.id in vm.uiState.value.loadingStopIds)
            assertEquals(
                15,
                vm.uiState.value.departures
                    .single()
                    .groups.size,
            )

            advanceTimeBy(500.milliseconds)
            assertEquals(
                "Fresh",
                vm.uiState.value.departures
                    .single()
                    .groups
                    .single()
                    .departures
                    .single()
                    .line,
            )
            assertTrue(
                vm.uiState.value.loadingMoreStopIds
                    .isEmpty(),
            )
        }

    @Test
    fun `a stop that ran out of departures widens to the extended window and then stops`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val page = fullPages(available = 10)
            entur.departures = { stopId -> page(entur.requestedLimits.getValue(stopId)) }
            val vm = viewModel()
            assertTrue(oslo.id in vm.uiState.value.canLoadMoreStopIds)

            vm.loadMoreDepartures(oslo.id)
            runCurrent()

            assertEquals(EnturRepository.EXTENDED_DEPARTURE_WINDOW_SECONDS, entur.requestedTimeRanges[oslo.id])
            assertTrue(oslo.id !in vm.uiState.value.canLoadMoreStopIds)

            val calls = entur.departureCalls
            vm.loadMoreDepartures(oslo.id) // nothing more to load: no request
            runCurrent()
            assertEquals(calls, entur.departureCalls)
        }

    @Test
    fun `load more shows only on its stop - is ignored while loading and for cached boards`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val page = fullPages(available = 100)
            entur.departures = { stopId -> page(entur.requestedLimits.getValue(stopId)) }
            entur.departuresDelayMillis = { 1_000L }
            val vm = viewModel()
            advanceTimeBy(1_001.milliseconds)
            assertTrue(oslo.id in vm.uiState.value.canLoadMoreStopIds)

            val calls = entur.departureCalls
            vm.loadMoreDepartures(oslo.id)
            vm.loadMoreDepartures(oslo.id) // still loading the first one
            runCurrent()
            // Growing one board is not a board refresh: no refresh bar.
            assertEquals(setOf(oslo.id), vm.uiState.value.loadingMoreStopIds)
            assertTrue(
                vm.uiState.value.loadingStopIds
                    .isEmpty(),
            )
            advanceTimeBy(1_001.milliseconds)
            assertEquals(calls + 1, entur.departureCalls)
            assertEquals(30, entur.requestedLimits[oslo.id])
            assertTrue(
                vm.uiState.value.loadingMoreStopIds
                    .isEmpty(),
            )

            entur.departures = { Sourced.cached(listOf(departure("1", "A", nowMillis() + 60_000L)), nowMillis()) }
            vm.refreshDepartures()
            advanceTimeBy(1_001.milliseconds)
            assertTrue(oslo.id !in vm.uiState.value.canLoadMoreStopIds)
        }

    @Test
    fun `a single line stop does not keep growing when its next page adds no rows`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { stopId ->
                val limit = entur.requestedLimits.getValue(stopId)
                Sourced.live(List(minOf(limit, 100)) { departure("31", "Tonsenhagen", nowMillis() + it * 60_000L) })
            }
            val vm = viewModel()
            assertTrue(oslo.id in vm.uiState.value.canLoadMoreStopIds)

            vm.loadMoreDepartures(oslo.id)
            runCurrent()

            // Thirty departures, still one row: the page is undone and the stop stops asking.
            assertEquals(30, entur.requestedLimits[oslo.id])
            assertEquals(
                1,
                vm.uiState.value.departures
                    .single()
                    .groups.size,
            )
            assertTrue(oslo.id !in vm.uiState.value.canLoadMoreStopIds)
            val calls = entur.departureCalls
            vm.loadMoreDepartures(oslo.id)
            runCurrent()
            assertEquals(calls, entur.departureCalls)

            vm.refreshDepartures()
            runCurrent()
            assertEquals(15, entur.requestedLimits[oslo.id])
            assertTrue(oslo.id !in vm.uiState.value.canLoadMoreStopIds)
        }

    @Test
    fun `a grown board shrinks back to one page some minutes after the last load more`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val page = fullPages(available = 100)
            entur.departures = { stopId -> page(entur.requestedLimits.getValue(stopId)) }
            val vm = viewModel(virtualClock())

            vm.loadMoreDepartures(oslo.id)
            runCurrent()
            assertEquals(30, entur.requestedLimits[oslo.id])

            advanceTimeBy((4 * minute).milliseconds)
            vm.refreshDepartures()
            runCurrent()
            assertEquals(30, entur.requestedLimits[oslo.id])

            advanceTimeBy((1 * minute).milliseconds)
            vm.refreshDepartures()
            runCurrent()
            assertEquals(15, entur.requestedLimits[oslo.id])
            assertTrue(oslo.id in vm.uiState.value.canLoadMoreStopIds)
        }

    @Test
    fun `a saturated stop can load more again once its limit has expired`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            entur.departures = { stopId ->
                val limit = entur.requestedLimits.getValue(stopId)
                Sourced.live(List(minOf(limit, 100)) { departure("31", "Tonsenhagen", nowMillis() + it * 60_000L) })
            }
            val vm = viewModel(virtualClock())
            vm.loadMoreDepartures(oslo.id)
            runCurrent()
            assertTrue(oslo.id !in vm.uiState.value.canLoadMoreStopIds)

            advanceTimeBy((5 * minute).milliseconds)
            vm.refreshDepartures()
            runCurrent()
            assertEquals(15, entur.requestedLimits[oslo.id])
            assertTrue(oslo.id in vm.uiState.value.canLoadMoreStopIds)
        }
}

package com.example.pulsetransit.ui.viewmodel

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
 * Progressive loading: each stop's departures and each destination's fastest route load
 * concurrently and are published as they arrive, and a newer refresh supersedes an older one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopBoardLoadingTest : BoardTestBase() {
    @Test
    fun `each stop appears as soon as its own departures arrive`() =
        boardTest {
            val second = oslo.copy(id = "NSR:StopPlace:2", name = "Nationaltheatret")
            settings.favoriteStops = listOf(oslo, second)
            val at = nowMillis() + 10 * minute
            entur.departures = { stopId -> Sourced.live(listOf(departure("L1", stopId, at))) }
            entur.departuresDelayMillis = { stopId -> if (stopId == oslo.id) 100L else 1_000L }
            val vm = viewModel()

            assertEquals(setOf(oslo.id, second.id), vm.uiState.value.loadingStopIds)
            assertTrue(
                vm.uiState.value.departures
                    .isEmpty(),
            )

            advanceTimeBy(101.milliseconds)
            assertEquals(
                listOf(oslo.id),
                vm.uiState.value.departures
                    .map { it.stopId },
            )
            assertEquals(setOf(second.id), vm.uiState.value.loadingStopIds)
            assertTrue(vm.uiState.value.isLoading)

            advanceTimeBy(1_000.milliseconds)
            assertEquals(
                listOf(oslo.id, second.id),
                vm.uiState.value.departures
                    .map { it.stopId },
            )
            assertTrue(
                vm.uiState.value.loadingStopIds
                    .isEmpty(),
            )
        }

    @Test
    fun `stops load concurrently and keep favorites order whatever order they arrive in`() =
        boardTest {
            val second = oslo.copy(id = "NSR:StopPlace:2", name = "Nationaltheatret")
            settings.favoriteStops = listOf(oslo, second)
            val at = nowMillis() + 10 * minute
            entur.departures = { stopId -> Sourced.live(listOf(departure("L1", stopId, at))) }
            entur.departuresDelayMillis = { stopId -> if (stopId == oslo.id) 1_000L else 100L }
            val vm = viewModel()

            advanceTimeBy(101.milliseconds)
            assertEquals(
                listOf(second.id),
                vm.uiState.value.departures
                    .map { it.stopId },
            )

            // Sequential loading would need 1.1 s here.
            advanceTimeBy(900.milliseconds)
            assertEquals(
                listOf(oslo.id, second.id),
                vm.uiState.value.departures
                    .map { it.stopId },
            )
        }

    @Test
    fun `a refresh keeps showing loaded departures until the new ones arrive`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val at = nowMillis() + 10 * minute
            entur.departures = { Sourced.live(listOf(departure("L1", "A", at))) }
            val vm = viewModel()
            assertEquals(1, vm.uiState.value.departures.size)

            entur.departuresDelayMillis = { 500L }
            entur.departures = { Sourced.live(listOf(departure("L2", "B", at))) }
            vm.refreshDepartures()
            runCurrent()

            assertEquals(setOf(oslo.id), vm.uiState.value.loadingStopIds)
            assertEquals(
                "L1",
                vm.uiState.value.departures
                    .single()
                    .groups
                    .single()
                    .departures
                    .first()
                    .line,
            )

            advanceTimeBy(501.milliseconds)
            assertEquals(
                "L2",
                vm.uiState.value.departures
                    .single()
                    .groups
                    .single()
                    .departures
                    .first()
                    .line,
            )
        }

    @Test
    fun `a superseded refresh does not overwrite the newer one`() =
        boardTest {
            settings.favoriteStops = listOf(oslo)
            val at = nowMillis() + 10 * minute
            // The first request is slow; a refresh while it's in flight is fast and newer.
            entur.departuresDelayMillis = { 1_000L }
            entur.departures = { Sourced.live(listOf(departure("OLD", "A", at))) }
            val vm = viewModel()
            advanceTimeBy(500.milliseconds)

            entur.departuresDelayMillis = { 2_000L }
            entur.departures = { Sourced.live(listOf(departure("NEW", "A", at))) }
            vm.refreshDepartures()

            // The old request returns at 1 s; it must neither show its data nor end the loading.
            advanceTimeBy(1_000.milliseconds)
            assertTrue(
                vm.uiState.value.departures
                    .isEmpty(),
            )
            assertEquals(setOf(oslo.id), vm.uiState.value.loadingStopIds)

            advanceTimeBy(2_000.milliseconds)
            val lines =
                vm.uiState.value.departures
                    .single()
                    .groups
                    .flatMap { group -> group.departures.map { it.line } }
            assertEquals(listOf("NEW"), lines)
            assertTrue(
                vm.uiState.value.loadingStopIds
                    .isEmpty(),
            )
        }

    @Test
    fun `each fastest route appears as soon as its own trip arrives`() =
        boardTest {
            val bergen = oslo.copy(id = "NSR:StopPlace:548", name = "Bergen")
            settings.destinations = listOf(oslo, bergen)
            entur.tripTo = { to -> Sourced.live(trip(to.name)) }
            entur.tripDelayMillis = { to -> if (to.id == oslo.id) 100L else 1_000L }
            val vm = viewModel()

            assertEquals(setOf(oslo.id, bergen.id), vm.uiState.value.loadingTripDestinationIds)
            assertTrue(
                vm.uiState.value.fastestTrips
                    .isEmpty(),
            )

            advanceTimeBy(101.milliseconds)
            assertEquals(setOf(oslo.id), vm.uiState.value.fastestTrips.keys)
            assertEquals(
                "Oslo S",
                vm.uiState.value.fastestTrips[oslo.id]
                    ?.firstOrNull()
                    ?.destinationName,
            )
            assertEquals(setOf(bergen.id), vm.uiState.value.loadingTripDestinationIds)

            advanceTimeBy(1_000.milliseconds)
            assertEquals(setOf(oslo.id, bergen.id), vm.uiState.value.fastestTrips.keys)
            assertTrue(
                vm.uiState.value.loadingTripDestinationIds
                    .isEmpty(),
            )
        }

    @Test
    fun `a superseded fastest-route refresh does not overwrite the newer one`() =
        boardTest {
            settings.destinations = listOf(oslo)
            entur.tripDelayMillis = { 1_000L }
            entur.tripTo = { Sourced.live(trip("old")) }
            val vm = viewModel()
            advanceTimeBy(500.milliseconds)

            entur.tripDelayMillis = { 2_000L }
            entur.tripTo = { Sourced.live(trip("new").copy(lineName = "NEW")) }
            vm.refreshData()

            advanceTimeBy(1_000.milliseconds)
            assertTrue(
                vm.uiState.value.fastestTrips
                    .isEmpty(),
            )
            assertEquals(setOf(oslo.id), vm.uiState.value.loadingTripDestinationIds)

            advanceTimeBy(2_000.milliseconds)
            assertEquals(
                "NEW",
                vm.uiState.value.fastestTrips[oslo.id]
                    ?.firstOrNull()
                    ?.lineName,
            )
            assertTrue(
                vm.uiState.value.loadingTripDestinationIds
                    .isEmpty(),
            )
        }

    @Test
    fun `a destination without a route is dropped once it has loaded`() =
        boardTest {
            val bergen = oslo.copy(id = "NSR:StopPlace:548", name = "Bergen")
            settings.destinations = listOf(oslo, bergen)
            entur.tripTo = { to -> Sourced.live(if (to.id == oslo.id) trip(to.name) else null) }
            val vm = viewModel()

            assertEquals(setOf(oslo.id), vm.uiState.value.fastestTrips.keys)
            assertTrue(
                vm.uiState.value.loadingTripDestinationIds
                    .isEmpty(),
            )
        }
}

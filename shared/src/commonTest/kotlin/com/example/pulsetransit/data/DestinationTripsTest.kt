package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestinationTripsTest {
    private val minute = 60_000L
    private val start = 1_800_000_000_000L
    private val place = StopPlace(id = "NSR:StopPlace:2", name = "Majorstuen")

    private fun at(minutes: Int) =
        TripInfo(
            destinationName = place.name,
            expectedStartTime = isoOslo(start + minutes * minute),
            lineName = "L$minutes",
            durationMinutes = 20,
            originName = "Origin",
        )

    private fun starts(list: DestinationTrips) = list.trips.map { it.lineName }

    @Test
    fun `routes are listed in departure order and each once`() {
        val list = DestinationTrips(place).withFirstPage(Sourced.live(listOf(at(9), at(2), at(5), at(2))), laterAllowed = true)

        assertEquals(listOf("L2", "L5", "L9"), starts(list))
        assertFalse(list.isLoading)
        assertTrue(list.hasLoaded)
        assertTrue(list.canLoadLater)
    }

    @Test
    fun `a refresh replaces the near routes and keeps later pages past them`() {
        val listed = DestinationTrips(place, trips = listOf(at(2), at(5), at(20), at(30)))

        val refreshed = listed.withFirstPage(Sourced.live(listOf(at(3), at(6), at(12))), laterAllowed = true)

        assertEquals(listOf("L3", "L6", "L12", "L20", "L30"), starts(refreshed))
    }

    @Test
    fun `a failed refresh keeps what is listed`() {
        val listed = DestinationTrips(place, trips = listOf(at(2), at(5)), canLoadLater = true)

        val refreshed = listed.withFirstPage(Sourced.failed(emptyList()), laterAllowed = true)

        assertEquals(listOf("L2", "L5"), starts(refreshed))
        assertTrue(refreshed.failed)
        assertFalse(refreshed.canLoadLater)
        assertFalse(refreshed.hasNoRoutes)
    }

    @Test
    fun `cached routes say when they were fetched and offer no later page`() {
        val list = DestinationTrips(place).withFirstPage(Sourced.cached(listOf(at(2)), fetchedAtMillis = 42L), laterAllowed = true)

        assertEquals(42L, list.cachedAtMillis)
        assertFalse(list.canLoadLater)
    }

    @Test
    fun `no route at all is told apart from not loaded yet`() {
        assertFalse(DestinationTrips(place).hasNoRoutes)
        assertTrue(DestinationTrips(place).withFirstPage(Sourced.live(emptyList()), laterAllowed = true).hasNoRoutes)
    }

    @Test
    fun `the later page departs a minute after the last route`() {
        val list = DestinationTrips(place, trips = listOf(at(2), at(14), at(5)))

        assertEquals(TripTime.DepartAt(start + 15 * minute), list.laterPageTime())
        assertNull(DestinationTrips(place).laterPageTime())
    }

    @Test
    fun `a later page adds its new routes and one adding none ends later`() {
        val listed = DestinationTrips(place, trips = listOf(at(2), at(5)), canLoadLater = true, isLoadingLater = true)

        val grown = listed.withLaterPage(Sourced.live(listOf(at(5), at(8), at(11))))
        assertEquals(listOf("L2", "L5", "L8", "L11"), starts(grown))
        assertTrue(grown.canLoadLater)
        assertFalse(grown.isLoadingLater)

        val ended = grown.withLaterPage(Sourced.live(listOf(at(11))))
        assertFalse(ended.canLoadLater)
    }

    @Test
    fun `a failed later page can be tried again`() {
        val listed = DestinationTrips(place, trips = listOf(at(2)), canLoadLater = true, isLoadingLater = true)

        val after = listed.withLaterPage(Sourced.failed(emptyList()))

        assertTrue(after.canLoadLater)
        assertFalse(after.isLoadingLater)
    }

    @Test
    fun `an arrive by search has no later page`() {
        assertFalse(DestinationTrips(place).withFirstPage(Sourced.live(listOf(at(2))), laterAllowed = false).canLoadLater)
    }

    @Test
    fun `the list stops growing at its maximum`() {
        val full = DestinationTrips(place).withFirstPage(Sourced.live(List(DestinationTrips.MAX_TRIPS + 5) { at(it) }), laterAllowed = true)

        assertEquals(DestinationTrips.MAX_TRIPS, full.trips.size)
        assertFalse(full.canLoadLater)
    }

    @Test
    fun `departed routes drop out of the upcoming ones`() {
        val list = DestinationTrips(place, trips = listOf(at(0), at(3), at(6)))

        assertEquals(listOf("L3", "L6"), list.upcoming(start + 3 * minute + 30_000L).map { it.lineName })
    }
}

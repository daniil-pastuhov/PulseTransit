package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TripDetailsTest {
    private val metro =
        TripLeg(
            mode = "metro",
            lineName = "5",
            fromName = "Jernbanetorget",
            durationMinutes = 6,
            toName = "Majorstuen",
            expectedStartTime = "2026-10-17T10:24:34+02:00",
            expectedEndTime = "2026-10-17T10:30:03+02:00",
            aimedStartTime = "2026-10-17T10:22:00+02:00",
            serviceJourneyId = "RUT:ServiceJourney:5",
            fromQuayId = "NSR:Quay:2",
            toQuayId = "NSR:Quay:4",
        )
    private val walk =
        TripLeg(
            mode = "foot",
            fromName = "Majorstuen",
            durationMinutes = 3,
            toName = "Majorstuen",
            expectedStartTime = "2026-10-17T10:30:03+02:00",
            expectedEndTime = "2026-10-17T10:33:00+02:00",
            distanceMeters = 210,
        )
    private val bus =
        TripLeg(
            mode = "bus",
            lineName = "20",
            fromName = "Majorstuen",
            durationMinutes = 8,
            toName = "Destination",
            expectedStartTime = "2026-10-17T10:38:00+02:00",
            expectedEndTime = "2026-10-17T10:46:00+02:00",
            serviceJourneyId = "RUT:ServiceJourney:20",
        )

    private fun trip(
        vararg legs: TripLeg,
        start: String = "2026-10-17T10:24:34+02:00",
    ) = TripInfo(
        destinationName = "Skøyen",
        expectedStartTime = start,
        lineName = "5",
        durationMinutes = 22,
        originName = "Jernbanetorget",
        legs = legs.toList(),
    )

    private fun stop(quay: String) = JourneyStop(quayId = quay, stopPlaceId = null, name = quay, expectedTime = "2026-10-17T10:00:00+02:00")

    @Test
    fun `transfers count vehicle changes and walking adds up foot legs`() {
        assertEquals(0, transferCount(trip(metro, walk)))
        assertEquals(1, transferCount(trip(metro, walk, bus)))
        assertEquals(0, transferCount(trip(walk)))
        assertEquals(210, walkDistanceMeters(trip(metro, walk, bus)))
    }

    @Test
    fun `zero-minute quay transfers are not shown`() {
        assertTrue(metro.isShown)
        assertTrue(walk.isShown)
        assertFalse(TripLeg(mode = "foot", durationMinutes = 0, distanceMeters = 8).isShown)
    }

    @Test
    fun `a leg's delay is how late it leaves against its timetable`() {
        assertEquals(2, legDelayMinutes(metro))
        assertEquals(0, legDelayMinutes(metro.copy(aimedStartTime = null)))
        // Leaving early is not a delay.
        assertEquals(0, legDelayMinutes(metro.copy(aimedStartTime = "2026-10-17T10:30:00+02:00")))
    }

    @Test
    fun `the wait before a leg runs from the previous arrival`() {
        val route = trip(metro, walk, bus)
        assertNull(waitMinutesBefore(route, 0))
        // The walk starts as the metro arrives.
        assertNull(waitMinutesBefore(route, 1))
        assertEquals(5, waitMinutesBefore(route, 2))
        assertNull(waitMinutesBefore(trip(metro, bus.copy(expectedStartTime = null)), 1))
    }

    @Test
    fun `the last leg ends at the destination by name`() {
        val route = trip(metro, walk, bus)
        assertEquals("Majorstuen", legEndName(route, 0))
        assertEquals("Skøyen", legEndName(route, 2))
        assertEquals("Destination", legEndName(route.copy(destinationName = ""), 2))
        assertNull(legEndName(route, 3))
    }

    @Test
    fun `leg stops run from boarding to alighting quay`() {
        val stops = listOf("NSR:Quay:1", "NSR:Quay:2", "NSR:Quay:3", "NSR:Quay:4", "NSR:Quay:5").map(::stop)
        assertEquals(listOf("NSR:Quay:2", "NSR:Quay:3", "NSR:Quay:4"), stops.legStops(metro).map { it.quayId })
        // A quay not on the trip widens to its start or end rather than losing the stops.
        assertEquals(5, stops.legStops(metro.copy(fromQuayId = "NSR:Quay:9", toQuayId = null)).size)
        assertEquals(
            listOf(
                "NSR:Quay:2",
                "NSR:Quay:3",
                "NSR:Quay:4",
                "NSR:Quay:5",
            ),
            stops.legStops(metro.copy(toQuayId = "NSR:Quay:9")).map {
                it.quayId
            },
        )
        assertTrue(emptyList<JourneyStop>().legStops(metro).isEmpty())
    }

    @Test
    fun `a loop alights at the stop after boarding`() {
        val stops = listOf("NSR:Quay:4", "NSR:Quay:2", "NSR:Quay:3", "NSR:Quay:4").map(::stop)
        assertEquals(listOf("NSR:Quay:2", "NSR:Quay:3", "NSR:Quay:4"), stops.legStops(metro).map { it.quayId })
    }

    @Test
    fun `open details follow their trip through a refresh`() {
        val selected = trip(metro, walk)
        val updated = trip(metro.copy(expectedStartTime = "2026-10-17T10:26:00+02:00"), walk, start = "2026-10-17T10:26:00+02:00")
        val other = trip(bus)

        assertSame(updated, currentTrip(listOf(other, updated), selected))
        // Gone from the refresh: the details keep what they had.
        assertSame(selected, currentTrip(listOf(other), selected))
        assertSame(selected, currentTrip(null, selected))
    }

    @Test
    fun `a trip without journeys is known by its start`() {
        val cachedEarlier = trip(TripLeg(mode = "metro", lineName = "5"))
        assertEquals(cachedEarlier.expectedStartTime, tripKey(cachedEarlier))
        assertEquals("RUT:ServiceJourney:5|RUT:ServiceJourney:20", tripKey(trip(metro, walk, bus)))
    }

    private val walkToMetro =
        walk.copy(fromName = "Origin", toName = "Jernbanetorget", toLatitude = 59.911, toLongitude = 10.750)
    private val metroWithCoordinates = metro.copy(fromLatitude = 59.9115, fromLongitude = 10.7505)
    private val busWithCoordinates = bus.copy(fromLatitude = 59.929, fromLongitude = 10.715)
    private val walkToDestination = walk.copy(toName = "Destination", toLatitude = 59.922, toLongitude = 10.678)

    @Test
    fun `a walk to a ride leads to where the ride boards`() {
        val trip = trip(walkToMetro, metroWithCoordinates, walk, busWithCoordinates)
        assertEquals(WalkDestination(59.9115, 10.7505), walkDestination(trip, 0))
        assertEquals(WalkDestination(59.929, 10.715), walkDestination(trip, 2))
    }

    @Test
    fun `the last walk leads to where it ends`() {
        val trip = trip(walkToMetro, metroWithCoordinates, walkToDestination)
        assertEquals(WalkDestination(59.922, 10.678), walkDestination(trip, 2))
    }

    @Test
    fun `the end of the walk stands in when the ride has no coordinates`() {
        assertEquals(WalkDestination(59.911, 10.750), walkDestination(trip(walkToMetro, metro), 0))
    }

    @Test
    fun `a walk followed by another walk leads to its own end`() {
        // Entur gives every leg where it starts, walks too; a walk's start is no boarding stop.
        val secondWalk = walk.copy(fromLatitude = 59.9112, fromLongitude = 10.7502, toLatitude = 59.912, toLongitude = 10.751)
        val trip = trip(walkToMetro, secondWalk, metroWithCoordinates)
        assertEquals(WalkDestination(59.911, 10.750), walkDestination(trip, 0))
        assertEquals(WalkDestination(59.9115, 10.7505), walkDestination(trip, 1))
    }

    @Test
    fun `a zero-minute transfer before the ride is passed over`() {
        val transfer = TripLeg(mode = "foot", durationMinutes = 0)
        assertEquals(WalkDestination(59.9115, 10.7505), walkDestination(trip(walkToMetro, transfer, metroWithCoordinates), 0))
    }

    @Test
    fun `without ride coordinates the leg arriving at the stop is used rather than the walk`() {
        val transfer = TripLeg(mode = "foot", durationMinutes = 0, toLatitude = 59.9118, toLongitude = 10.7508)
        assertEquals(WalkDestination(59.9118, 10.7508), walkDestination(trip(walkToMetro, transfer, metro), 0))
    }

    @Test
    fun `a trailing zero-minute leg is where the last walk ends`() {
        val transfer = TripLeg(mode = "foot", durationMinutes = 0, toLatitude = 59.923, toLongitude = 10.679)
        assertEquals(WalkDestination(59.923, 10.679), walkDestination(trip(metroWithCoordinates, walkToDestination, transfer), 1))
    }

    @Test
    fun `no destination for rides or hidden walks or legs out of range`() {
        val transfer = TripLeg(mode = "foot", durationMinutes = 0, toLatitude = 59.9, toLongitude = 10.7)
        val trip = trip(transfer, metroWithCoordinates, walkToDestination)
        assertNull(walkDestination(trip, 0))
        assertNull(walkDestination(trip, 1))
        assertNull(walkDestination(trip, 3))
        assertNull(walkDestination(trip, -1))
    }

    @Test
    fun `no destination without coordinates`() {
        assertNull(walkDestination(trip(walk, metro), 0))
        assertNull(walkDestination(trip(metro, walk), 1))
    }

    @Test
    fun `the earliest arriving trip is picked by time not by text`() {
        val base = TripInfo(destinationName = "X", expectedStartTime = "2026-07-15T08:00:00+02:00", lineName = "1", durationMinutes = 30, originName = "Y")
        // 08:30+02:00 is 06:30Z: earlier in time, though later as text than "06:40Z".
        val localEarlier = base.copy(expectedEndTime = "2026-07-15T08:30:00+02:00")
        val utc = base.copy(expectedEndTime = "2026-07-15T06:40:00Z")
        val unknown = base.copy(expectedEndTime = null)
        assertEquals(localEarlier, earliestArriving(listOf(utc, unknown, localEarlier)))
        assertEquals(null, earliestArriving(listOf(unknown)))
    }
}

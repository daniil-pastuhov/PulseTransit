package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import com.example.pulsetransit.data.journey.JourneyFixtures.trip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The trip details' progress, for a trip opened mid-journey whether or not it is followed. */
class TripUnderWayTest {
    private fun underWayAt(minutes: Int) = JourneyPosition.underWay(trip, liveStops(), at(minutes))

    @Test
    fun `nothing before the trip starts or once it has arrived`() {
        assertNull(JourneyPosition.underWay(trip, liveStops(), at(0) - 1))
        assertNull(underWayAt(31))
        assertNull(underWayAt(40))
    }

    @Test
    fun `from its first moment the trip is under way on its first walk`() {
        val walking = assertNotNull(underWayAt(0))
        assertEquals(JourneyPhase.WALKING, walking.phase)
        assertEquals(0, walking.currentLegIndex)
        assertFalse(walking.isLegDone(0))
    }

    @Test
    fun `waiting for the metro the walk is done and the metro is current`() {
        val waiting = assertNotNull(underWayAt(5))
        assertEquals(JourneyPhase.WAITING, waiting.phase)
        assertEquals(1, waiting.currentLegIndex)
        assertTrue(waiting.isLegDone(0))
        assertFalse(waiting.isLegDone(1))
    }

    @Test
    fun `riding the bus follows its live stops`() {
        val riding = assertNotNull(underWayAt(20))
        assertEquals(JourneyPhase.RIDING, riding.phase)
        assertEquals(3, riding.currentLegIndex)
        assertEquals(listOf(true, true, false), listOf(0, 1, 3).map { riding.isLegDone(it) })
        assertEquals(2, riding.stopsLeft)
    }

    @Test
    fun `a late metro keeps the trip on it past its timetable`() {
        val riding = assertNotNull(JourneyPosition.underWay(trip, liveStops(metroDelay = 3), at(15)))
        assertEquals(1, riding.currentLegIndex)
        assertFalse(riding.isLegDone(1))
    }

    @Test
    fun `the last walk is current until the arrival`() {
        val walking = assertNotNull(underWayAt(30))
        assertEquals(4, walking.currentLegIndex)
        assertTrue(walking.isLegDone(3))
    }

    @Test
    fun `nothing for a trip without times`() {
        val untimed = trip.copy(legs = trip.legs.map { it.copy(expectedStartTime = null, expectedEndTime = null, aimedStartTime = null) })
        assertNull(JourneyPosition.underWay(untimed, emptyMap(), at(10)))
    }

    @Test
    fun `the details never say location is off since they do not follow it`() {
        val riding = assertNotNull(underWayAt(9))
        assertFalse(riding.locationLost)
        assertFalse(JourneyDetail.LocationLost in riding.details)
    }

    @Test
    fun `a leg is done from the moment it ends`() {
        val atWalkEnd = assertNotNull(underWayAt(4))
        assertTrue(atWalkEnd.isLegDone(0))
        assertEquals(1, atWalkEnd.currentLegIndex)
    }
}

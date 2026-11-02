package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.PlaceKind
import com.example.pulsetransit.data.journey.JourneyFixtures.TRAM_JOURNEY
import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyFixtures.home
import com.example.pulsetransit.data.journey.JourneyFixtures.journey
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import com.example.pulsetransit.data.journey.JourneyFixtures.majorstuen
import com.example.pulsetransit.data.journey.JourneyFixtures.tramOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The fixture journey changes from metro 5 to bus 20 at Majorstuen: arrives T0+14, leaves T0+18, home T0+31. */
class FasterRoutesTest {
    @Test
    fun `the search is due from arriving at the transfer stop until the next ride leaves`() {
        assertNull(FasterRoutes.dueSearch(journey(), at(13)), "still on the metro")
        val search = assertNotNull(FasterRoutes.dueSearch(journey(), at(14)))
        assertEquals(1, search.rideIndex)
        assertEquals(majorstuen.second to majorstuen.third, search.latitude to search.longitude)
        assertNotNull(FasterRoutes.dueSearch(journey(), at(17)))
        assertNull(FasterRoutes.dueSearch(journey(), at(18)), "the bus has left")
    }

    @Test
    fun `the search follows live times`() {
        val late = journey(legStops = liveStops(metroDelay = 3))
        assertNull(FasterRoutes.dueSearch(late, at(15)), "the late metro has not arrived yet")
        assertNotNull(FasterRoutes.dueSearch(late, at(17)))
    }

    @Test
    fun `a transfer is searched once and never while muted`() {
        assertNull(FasterRoutes.dueSearch(journey().copy(checkedTransfers = setOf(1)), at(15)))
        assertNull(FasterRoutes.dueSearch(journey(muted = true), at(15)))
    }

    @Test
    fun `the destination is where the last leg ends by position`() {
        val destination = assertNotNull(FasterRoutes.destination(journey()))
        assertEquals(home.first, destination.latitude)
        assertEquals(home.second, destination.longitude)
        assertEquals(PlaceKind.PLACE, destination.kind)
        assertEquals("Home", destination.name)
    }

    @Test
    fun `only a route arriving clearly sooner is offered and the soonest wins`() {
        assertNull(FasterRoutes.fasterOf(journey(), listOf(tramOn(arrival = 29))), "two minutes is not worth it")
        assertNull(FasterRoutes.fasterOf(journey(), listOf(tramOn(arrival = 31))), "the same arrival")
        val best = FasterRoutes.fasterOf(journey(), listOf(tramOn(arrival = 27), tramOn(arrival = 25), tramOn(arrival = 28)))
        assertEquals(JourneyFixtures.iso(25), best?.expectedEndTime)
    }

    @Test
    fun `a route is compared with the journey's live arrival`() {
        // Bus 20 runs 4 minutes late, so the journey arrives at T0+35 and a tram at T0+31 is 4 sooner.
        val late = journey(legStops = liveStops(busDelay = 4))
        assertNotNull(FasterRoutes.fasterOf(late, listOf(tramOn(arrival = 31))))
    }

    @Test
    fun `the alert names the first ride and how much sooner it arrives`() {
        val alert = assertNotNull(FasterRoutes.alert(journey(), 1, tramOn(arrival = 25)))
        assertEquals("faster-1", alert.id)
        assertEquals("19", alert.lineName)
        assertEquals("Majorstuen", alert.stopName)
        assertEquals("C", alert.platform)
        assertEquals(at(16), alert.departsAtMillis)
        assertEquals(at(25), alert.arrivesAtMillis)
        assertEquals(6, alert.minutesSooner)
    }

    @Test
    fun `switching follows the offered route to the same destination`() {
        val offered = journey(JourneyTrackingMode.LOCATION).copy(fasterRoute = tramOn())
        val switched = assertNotNull(FasterRoutes.switched(offered, at(15)))
        assertEquals(TRAM_JOURNEY, switched.id)
        assertEquals("Home", switched.trip.destinationName)
        assertEquals(JourneyTrackingMode.LOCATION, switched.mode)
        assertEquals(at(15), switched.startedAtMillis)
        assertNull(FasterRoutes.switched(journey(), at(15)), "nothing offered")
        assertNull(FasterRoutes.switched(offered, at(16)), "the tram has left")
    }
}

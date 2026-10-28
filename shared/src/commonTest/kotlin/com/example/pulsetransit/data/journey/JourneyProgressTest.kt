package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.journey.JourneyFixtures.MINUTE
import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyFixtures.journey
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import com.example.pulsetransit.data.journey.JourneyTimelineSegment.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JourneyProgressTest {
    private fun progressAt(
        minutes: Int,
        journey: TrackedJourney = journey(legStops = liveStops()),
    ) = JourneyPosition.progress(journey, at(minutes), locationLost = false)

    @Test
    fun `waiting names the vehicle and says when and where it leaves`() {
        val waiting = progressAt(5)

        assertEquals(JourneyPhase.WAITING, waiting.phase)
        assertEquals("Vestli", waiting.towards)
        assertEquals(
            listOf(
                JourneyDetail.Departs(at(6), delayMinutes = 0),
                JourneyDetail.Platform("2"),
                JourneyDetail.From("Jernbanetorget"),
                JourneyDetail.Arrival(at(31)),
            ),
            waiting.details,
        )
    }

    @Test
    fun `a late ride says how late from its live times`() {
        val waiting = progressAt(8, journey(legStops = liveStops(metroDelay = 3)))

        assertEquals(JourneyDetail.Departs(at(9), delayMinutes = 3), waiting.details.first())
        assertEquals(at(9), waiting.nextAtMillis)
    }

    @Test
    fun `riding shows the next stop and the change after the ride`() {
        val riding = progressAt(9)

        assertEquals(JourneyPhase.RIDING, riding.phase)
        assertEquals("Majorstuen", riding.targetName)
        assertEquals(
            listOf(
                JourneyDetail.NextStop("Nationaltheatret"),
                JourneyDetail.StopsLeft(2),
                JourneyDetail.Change("20", "B", at(18)),
                JourneyDetail.Arrival(at(31)),
            ),
            riding.details,
        )
    }

    @Test
    fun `the next stop is left out when it is the one to get off at`() {
        val riding = progressAt(12)

        assertEquals(JourneyDetail.StopsLeft(1), riding.details.first())
    }

    @Test
    fun `the last ride has no change but the arrival`() {
        val riding = progressAt(20)

        assertEquals(
            listOf(
                JourneyDetail.NextStop("Frognerparken"),
                JourneyDetail.StopsLeft(2),
                JourneyDetail.Arrival(at(31)),
            ),
            riding.details,
        )
    }

    @Test
    fun `walking to a ride counts down to its departure`() {
        val walking = progressAt(1)

        assertEquals(JourneyPhase.WALKING, walking.phase)
        assertEquals(at(6), walking.nextAtMillis)
        assertEquals(5, walking.minutesToNext)
        assertEquals(listOf(JourneyDetail.Change("5", "2", at(6)), JourneyDetail.Arrival(at(31))), walking.details)
    }

    @Test
    fun `minutes to the next moment round up`() {
        val progress = JourneyPosition.progress(journey(legStops = liveStops()), at(1) + 1_000, locationLost = false)

        assertEquals(5, progress.minutesToNext)
        assertEquals(1, JourneyPosition.progress(journey(legStops = liveStops()), at(6) - MINUTE + 1, false).minutesToNext)
    }

    @Test
    fun `the walk to the destination counts down to the arrival`() {
        val walking = progressAt(28)

        assertEquals(at(31), walking.nextAtMillis)
        assertEquals(listOf<JourneyDetail>(JourneyDetail.Arrival(at(31))), walking.details)
        assertEquals(emptyList(), walking.upcomingSteps)
    }

    @Test
    fun `after arrival it says when and counts down nothing`() {
        val arrived = progressAt(32)

        assertEquals(JourneyPhase.ARRIVED, arrived.phase)
        assertNull(arrived.currentStepIndex)
        assertNull(arrived.minutesToNext)
        assertEquals(listOf<JourneyDetail>(JourneyDetail.Arrived(at(31))), arrived.details)
    }

    @Test
    fun `notes come after everything else`() {
        val progress = JourneyPosition.progress(journey(legStops = liveStops(), muted = true), at(28), locationLost = true)

        assertEquals(listOf(JourneyDetail.Arrival(at(31)), JourneyDetail.LocationLost, JourneyDetail.Muted), progress.details)
    }

    @Test
    fun `the timeline covers the journey with its waits`() {
        val progress = progressAt(9)

        assertEquals(
            listOf(
                JourneyTimelineSegment(Kind.WALK, at(0), at(4)),
                JourneyTimelineSegment(Kind.WAIT, at(4), at(6)),
                JourneyTimelineSegment(Kind.RIDE, at(6), at(14)),
                JourneyTimelineSegment(Kind.WAIT, at(14), at(18)),
                JourneyTimelineSegment(Kind.RIDE, at(18), at(26)),
                JourneyTimelineSegment(Kind.WALK, at(26), at(31)),
            ),
            progress.timeline,
        )
        assertEquals(at(0), progress.startAtMillis)
        assertEquals(listOf(3, 4), progress.upcomingSteps.map { it.legIndex })
    }

    @Test
    fun `elapsed fraction runs from the start to the arrival`() {
        assertEquals(0.0, progressAt(-5).elapsedFraction)
        assertEquals(9.0 / 31, progressAt(9).elapsedFraction, 1e-9)
        assertEquals(1.0, progressAt(32).elapsedFraction)
    }
}

package com.example.pulsetransit.data.journey.watch

import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyAlertPlanner
import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyFixtures.iso
import com.example.pulsetransit.data.journey.JourneyFixtures.journey
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import com.example.pulsetransit.data.journey.JourneyPhase
import com.example.pulsetransit.data.journey.JourneyPosition
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.platform.WatchText
import com.example.pulsetransit.platform.WatchWording
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchPlanTest {
    /** Words everything by what it is, so a change in the journey is a change in the words. */
    private object Wording : WatchWording {
        override suspend fun title(progress: JourneyProgress) = "Journey to ${progress.destinationName}"

        override suspend fun progress(progress: JourneyProgress) = WatchText("${progress.phase} ${progress.lineName} ${progress.targetName}", progress.details.joinToString())

        override suspend fun alert(alert: JourneyAlert) = WatchText(alert.id, null)
    }

    private fun live() = journey(legStops = liveStops())

    private fun moments(
        journey: TrackedJourney,
        minutes: Int,
    ) = WatchPlans.moments(journey, at(minutes), locationLost = false)

    private suspend fun planAt(
        journey: TrackedJourney,
        minutes: Int,
        upcoming: List<ScheduledAlert> = JourneyAlertPlanner.upcomingAlerts(journey, at(minutes)),
    ) = WatchPlans.plan(journey, upcoming, at(minutes), locationLost = false, wording = Wording)

    @Test
    fun `moments start with the progress now`() {
        val moments = moments(live(), 1)

        assertEquals(JourneyPosition.progress(live(), at(1), locationLost = false), moments.first())
        assertEquals(at(1), moments.first().nowMillis)
        assertEquals(JourneyPhase.WALKING, moments.first().phase)
    }

    @Test
    fun `moments come at each step start and end and at each stop passed while riding`() {
        val moments = moments(live(), 0)

        assertEquals(listOf(0, 4, 6, 8, 10, 14, 18, 22, 26, 31).map { at(it) }, moments.map { it.nowMillis })
        assertEquals(
            listOf(
                JourneyPhase.WALKING,
                JourneyPhase.WAITING,
                JourneyPhase.RIDING,
                JourneyPhase.RIDING,
                JourneyPhase.RIDING,
                JourneyPhase.WAITING,
                JourneyPhase.RIDING,
                JourneyPhase.RIDING,
                JourneyPhase.WALKING,
                JourneyPhase.ARRIVED,
            ),
            moments.map { it.phase },
        )
        assertEquals(listOf(3, 2, 1), moments.filter { it.lineName == "5" && it.phase == JourneyPhase.RIDING }.map { it.stopsLeft })
    }

    @Test
    fun `moments are in time order and end with the arrival under the cap`() {
        val moments = moments(live(), 5)

        assertEquals(listOf(5, 6, 8, 10, 14, 18, 22, 26, 31).map { at(it) }, moments.map { it.nowMillis })
        assertEquals(moments.map { it.nowMillis }.sorted(), moments.map { it.nowMillis })
        assertEquals(JourneyPhase.ARRIVED, moments.last().phase)
    }

    @Test
    fun `moments without live stops change only at the steps`() {
        val moments = moments(journey(), 0)

        assertEquals(listOf(0, 4, 6, 14, 18, 26, 31).map { at(it) }, moments.map { it.nowMillis })
        assertEquals(JourneyPhase.ARRIVED, moments.last().phase)
    }

    @Test
    fun `a moment that says the same as the one before is dropped`() {
        // A stop time on the first walk is a moment to look at but changes nothing the walk says.
        val walkStop = JourneyStop(quayId = "NSR:Quay:7", stopPlaceId = null, name = "On the way", expectedTime = iso(2))
        val journey = journey(legStops = liveStops() + (0 to listOf(walkStop)))
        val moments = moments(journey, 0)

        assertFalse(at(2) in moments.map { it.nowMillis })
        assertEquals(listOf(0, 4, 6, 8, 10, 14, 18, 22, 26, 31).map { at(it) }, moments.map { it.nowMillis })
        moments.zipWithNext().forEach { (before, after) -> assertTrue(before.copy(nowMillis = 0) != after.copy(nowMillis = 0)) }
    }

    @Test
    fun `moments stop at the frame cap`() {
        // The bus stops every minute, so the journey changes more often than the watch is sent.
        val stops =
            (18..48).map { minute ->
                JourneyStop(quayId = "NSR:Quay:$minute", stopPlaceId = null, name = "Stop $minute", expectedTime = iso(minute))
            }
        val journey = journey(legStops = mapOf(1 to liveStops().getValue(1), 3 to stops))
        val moments = moments(journey, 0)

        assertEquals(WatchPlans.MAX_FRAMES, moments.size)
        assertEquals((listOf(0, 4, 6, 8, 10, 14) + (18..35)).map { at(it) }, moments.map { it.nowMillis })
        assertTrue(moments.none { it.phase == JourneyPhase.ARRIVED })
    }

    @Test
    fun `the plan has a frame per moment worded and counted down`() =
        runTest {
            val journey = live()
            val plan = planAt(journey, 0)
            val moments = moments(journey, 0)

            assertEquals(journey.id, plan.journeyId)
            assertEquals(moments.map { it.nowMillis }, plan.frames.map { it.fromMillis })
            assertEquals(moments.map { it.phase }, plan.frames.map { it.phase })
            val first = plan.frames.first()
            assertEquals(Wording.progress(moments.first()).title, first.headline)
            assertEquals(Wording.progress(moments.first()).text, first.detail)
            assertEquals(at(6), first.nextAtMillis)
            assertEquals(at(14), plan.frames[2].nextAtMillis)
        }

    @Test
    fun `the arrived frame counts down to nothing`() =
        runTest {
            val last = planAt(live(), 0).frames.last()

            assertEquals(JourneyPhase.ARRIVED, last.phase)
            assertEquals(at(31), last.fromMillis)
            assertNull(last.nextAtMillis)
        }

    @Test
    fun `the plan has the title and segments and arrival of the journey now`() =
        runTest {
            val journey = live()
            val plan = planAt(journey, 0)

            assertEquals("Journey to Home", plan.title)
            assertEquals(JourneyPosition.progress(journey, at(0), locationLost = false).timeline, plan.segments)
            assertEquals(at(0), plan.segments.first().startMillis)
            assertEquals(at(31), plan.segments.last().endMillis)
            assertEquals(at(31), plan.arrivalAtMillis)
        }

    @Test
    fun `alerts are sorted by time and capped`() =
        runTest {
            val upcoming =
                (0 until WatchPlans.MAX_ALERTS + 3).map { ScheduledAlert(JourneyAlert.NextStopIsYours(it, "Stop $it"), at(30 - it)) }
            val plan = planAt(live(), 0, upcoming)

            assertEquals(WatchPlans.MAX_ALERTS, plan.alerts.size)
            val soonest = upcoming.sortedBy { it.atMillis }.take(WatchPlans.MAX_ALERTS)
            assertEquals(soonest.map { it.atMillis }, plan.alerts.map { it.atMillis })
            assertEquals(at(30 - (WatchPlans.MAX_ALERTS + 2)), plan.alerts.first().atMillis)
            val alert = plan.alerts.first()
            assertEquals("next-stop-${WatchPlans.MAX_ALERTS + 2}", alert.id)
            assertEquals(alert.id, alert.title)
            assertNull(alert.text)
        }

    @Test
    fun `the journey alerts still to come are in its plan`() =
        runTest {
            val journey = live()
            val plan = planAt(journey, 0)

            assertTrue(plan.alerts.isNotEmpty())
            assertEquals(plan.alerts.sortedBy { it.atMillis }, plan.alerts)
            assertTrue(plan.alerts.all { it.atMillis > at(0) })
        }

    @Test
    fun `nothing sent is not covered`() =
        runTest {
            assertFalse(planAt(live(), 0).isCoveredBy(null, at(0)))
        }

    @Test
    fun `the same plan is covered by itself`() =
        runTest {
            val plan = planAt(live(), 0)

            assertTrue(plan.isCoveredBy(plan, at(0)))
        }

    @Test
    fun `the same journey a little later is covered before the next change`() =
        runTest {
            val sent = planAt(live(), 0)
            val later = planAt(live(), 1)

            assertNotNull(sent)
            assertTrue(later != sent)
            assertTrue(later.isCoveredBy(sent, at(1)))
        }

    @Test
    fun `the same journey after a frame boundary is covered by the frames sent ahead`() =
        runTest {
            val sent = planAt(live(), 0)
            val later = planAt(live(), 5)

            assertEquals(sent.frames.size - 1, later.frames.size)
            assertTrue(later.isCoveredBy(sent, at(5)))
            // Well into the journey as well, with the alerts already shown gone from both.
            assertTrue(planAt(live(), 20).isCoveredBy(sent, at(20)))
        }

    @Test
    fun `changed live times are not covered`() =
        runTest {
            val sent = planAt(live(), 0)
            val delayed = planAt(journey(legStops = liveStops(metroDelay = 2)), 1)

            assertFalse(delayed.isCoveredBy(sent, at(1)))
        }

    @Test
    fun `alerts taken away are not covered`() =
        runTest {
            val sent = planAt(live(), 0)

            assertFalse(planAt(live(), 1, upcoming = emptyList()).isCoveredBy(sent, at(1)))
        }

    @Test
    fun `a muted journey is not covered by the plan sent before`() =
        runTest {
            val sent = planAt(live(), 0)
            val muted = journey(legStops = liveStops(), muted = true)

            assertFalse(planAt(muted, 1).isCoveredBy(sent, at(1)))
        }

    @Test
    fun `a different title is not covered`() =
        runTest {
            val sent = planAt(live(), 0)

            assertFalse(planAt(live(), 1).isCoveredBy(sent.copy(title = "Journey to Work"), at(1)))
        }

    @Test
    fun `another journey is not covered`() =
        runTest {
            val sent = planAt(live(), 0)

            assertFalse(planAt(live(), 1).isCoveredBy(sent.copy(journeyId = "another"), at(1)))
        }
}

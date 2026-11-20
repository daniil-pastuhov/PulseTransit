package com.example.pulsetransit.data.journey.watch

import com.example.pulsetransit.data.journey.JourneyFixtures.T0
import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyPhase
import com.example.pulsetransit.data.journey.JourneyTimelineSegment
import com.example.pulsetransit.data.journey.JourneyTimelineSegment.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WatchMessagesTest {
    private fun seconds(minutes: Int) = (at(minutes) / 1_000).toInt()

    private val plan =
        WatchPlan(
            journeyId = "journey",
            title = "Journey to Home",
            frames =
                listOf(
                    WatchPlan.Frame(at(0), JourneyPhase.WALKING, "Walk to Jernbanetorget", "Then 5", at(6)),
                    WatchPlan.Frame(at(4), JourneyPhase.WAITING, "5 towards Vestli", "", at(6)),
                    WatchPlan.Frame(at(6), JourneyPhase.RIDING, "On 5 to Majorstuen", "3 stops left", at(14)),
                    WatchPlan.Frame(at(31), JourneyPhase.ARRIVED, "Arrived", "", null),
                ),
            segments =
                listOf(
                    JourneyTimelineSegment(Kind.WALK, at(0), at(4)),
                    JourneyTimelineSegment(Kind.WAIT, at(4), at(6)),
                    JourneyTimelineSegment(Kind.RIDE, at(6), at(14)),
                ),
            alerts =
                listOf(
                    WatchPlan.Alert("depart-1", at(5), "5 leaves", "Platform 2"),
                    WatchPlan.Alert("arrived", at(31), "Arrived", null),
                ),
            arrivalAtMillis = at(31),
        )

    /** Fails unless [value] is only strings and whole numbers or lists of them. */
    private fun assertCarried(value: Any?) {
        when (value) {
            is String, is Int -> Unit
            is List<*> -> value.forEach { assertCarried(it) }
            else -> fail("${value?.let { it::class.simpleName }} $value is not a string or a whole number")
        }
    }

    @Test
    fun `a plan names its type and version and journey`() {
        val message = WatchMessages.plan(plan)

        assertEquals(WatchMessages.TYPE_PLAN, message[WatchMessages.TYPE])
        assertEquals(WatchMessages.VERSION, message[WatchMessages.KEY_VERSION])
        assertEquals("journey", message[WatchMessages.KEY_JOURNEY])
        assertEquals("Journey to Home", message[WatchMessages.KEY_TITLE])
        assertEquals(seconds(31), message[WatchMessages.KEY_ARRIVAL])
        assertEquals((T0 / 1_000).toInt() + 31 * 60, message[WatchMessages.KEY_ARRIVAL])
    }

    @Test
    fun `plan frames are from and phase and headline and detail and next in seconds`() {
        val frames = WatchMessages.plan(plan)[WatchMessages.KEY_FRAMES]

        assertEquals(
            listOf(
                listOf(seconds(0), 2, "Walk to Jernbanetorget", "Then 5", seconds(6)),
                listOf(seconds(4), 0, "5 towards Vestli", "", seconds(6)),
                listOf(seconds(6), 1, "On 5 to Majorstuen", "3 stops left", seconds(14)),
                listOf(seconds(31), 3, "Arrived", "", 0),
            ),
            frames,
        )
    }

    @Test
    fun `plan segments are kind and start and end in seconds`() {
        assertEquals(
            listOf(
                listOf(0, seconds(0), seconds(4)),
                listOf(1, seconds(4), seconds(6)),
                listOf(2, seconds(6), seconds(14)),
            ),
            WatchMessages.plan(plan)[WatchMessages.KEY_SEGMENTS],
        )
    }

    @Test
    fun `plan alerts are at and id and title and text with no text empty`() {
        assertEquals(
            listOf(
                listOf(seconds(5), "depart-1", "5 leaves", "Platform 2"),
                listOf(seconds(31), "arrived", "Arrived", ""),
            ),
            WatchMessages.plan(plan)[WatchMessages.KEY_ALERTS],
        )
    }

    @Test
    fun `a plan with no arrival sends 0`() {
        val message = WatchMessages.plan(plan.copy(arrivalAtMillis = null, segments = emptyList(), alerts = emptyList()))

        assertEquals(0, message[WatchMessages.KEY_ARRIVAL])
        assertEquals(emptyList<Any>(), message[WatchMessages.KEY_SEGMENTS])
        assertEquals(emptyList<Any>(), message[WatchMessages.KEY_ALERTS])
    }

    @Test
    fun `every value of every message is a string or a whole number or a list of them`() {
        listOf(
            WatchMessages.plan(plan),
            WatchMessages.plan(plan.copy(arrivalAtMillis = null)),
            WatchMessages.alert("journey", "missed", "Missed", null),
            WatchMessages.end("journey", arrived = true),
            WatchMessages.none(),
        ).forEach { message -> message.values.forEach { assertCarried(it) } }
    }

    @Test
    fun `phase and segment codes are distinct and fixed`() {
        val phases = listOf(JourneyPhase.WAITING, JourneyPhase.RIDING, JourneyPhase.WALKING, JourneyPhase.ARRIVED)
        assertEquals(listOf(0, 1, 2, 3), phases.map(WatchMessages::phaseCode))
        assertEquals(listOf(0, 1, 2), listOf(Kind.WALK, Kind.WAIT, Kind.RIDE).map(WatchMessages::segmentCode))
        val codes = JourneyPhase.entries.map(WatchMessages::phaseCode)
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `an alert carries its journey and id and texts`() {
        assertEquals(
            mapOf<String, Any>(
                WatchMessages.TYPE to WatchMessages.TYPE_ALERT,
                WatchMessages.KEY_VERSION to WatchMessages.VERSION,
                WatchMessages.KEY_JOURNEY to "journey",
                WatchMessages.KEY_ALERT to "depart-1",
                WatchMessages.KEY_TITLE to "5 leaves",
                WatchMessages.KEY_TEXT to "Platform 2",
            ),
            WatchMessages.alert("journey", "depart-1", "5 leaves", "Platform 2"),
        )
        assertEquals("", WatchMessages.alert("journey", "missed", "Missed", null)[WatchMessages.KEY_TEXT])
    }

    @Test
    fun `an end says whether the journey arrived as 1 or 0`() {
        assertEquals(
            mapOf<String, Any>(
                WatchMessages.TYPE to WatchMessages.TYPE_END,
                WatchMessages.KEY_VERSION to WatchMessages.VERSION,
                WatchMessages.KEY_JOURNEY to "journey",
                WatchMessages.KEY_ARRIVED to 1,
            ),
            WatchMessages.end("journey", arrived = true),
        )
        assertEquals(0, WatchMessages.end("journey", arrived = false)[WatchMessages.KEY_ARRIVED])
    }

    @Test
    fun `none is only its type and version`() {
        assertEquals(
            mapOf<String, Any>(WatchMessages.TYPE to WatchMessages.TYPE_NONE, WatchMessages.KEY_VERSION to 1),
            WatchMessages.none(),
        )
    }

    @Test
    fun `messages from the watch app are read by type`() {
        assertEquals(WatchEvent.Started, WatchMessages.parse(mapOf(WatchMessages.TYPE to "start")))
        assertEquals(WatchEvent.Alive, WatchMessages.parse(mapOf(WatchMessages.TYPE to "alive")))
        assertEquals(WatchEvent.Closed, WatchMessages.parse(mapOf(WatchMessages.TYPE to "bye")))
        val stop = mapOf(WatchMessages.TYPE to "stop", WatchMessages.KEY_JOURNEY to "journey")
        assertEquals(WatchEvent.Stop("journey"), WatchMessages.parse(stop))
    }

    @Test
    fun `an unknown message reads as nothing`() {
        assertNull(WatchMessages.parse(mapOf(WatchMessages.TYPE to "later")))
        assertNull(WatchMessages.parse(mapOf(WatchMessages.TYPE to WatchMessages.TYPE_PLAN)))
        assertNull(WatchMessages.parse(mapOf(WatchMessages.TYPE to null)))
        assertNull(WatchMessages.parse(emptyMap()))
    }

    @Test
    fun `a stop without a journey reads as nothing`() {
        assertNull(WatchMessages.parse(mapOf(WatchMessages.TYPE to WatchMessages.TYPE_STOP)))
        assertNull(WatchMessages.parse(mapOf(WatchMessages.TYPE to WatchMessages.TYPE_STOP, WatchMessages.KEY_JOURNEY to 7)))
        val noId = mapOf(WatchMessages.TYPE to WatchMessages.TYPE_STOP, WatchMessages.KEY_JOURNEY to "")
        assertTrue(WatchMessages.parse(noId) is WatchEvent.Stop)
    }
}

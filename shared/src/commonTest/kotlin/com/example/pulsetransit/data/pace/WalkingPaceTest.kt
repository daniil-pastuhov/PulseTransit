package com.example.pulsetransit.data.pace

import com.example.pulsetransit.data.WalkingSpeed
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The bout detector against made-up fix streams, and the proposal rule against made-up records. */
class WalkingPaceTest {
    private val start = 1_793_174_400_000L

    /** A stream heading north from [lat], one fix every [everySeconds], moving at each segment's speed. */
    private class Stream(
        var atMillis: Long,
        var lat: Double,
    ) {
        val fixes = mutableListOf<PaceFix>()

        fun go(
            steps: Int,
            mps: Double,
            everySeconds: Int = 15,
            accuracy: Double? = 5.0,
            platformSpeed: Double? = null,
        ): Stream {
            repeat(steps) {
                atMillis += everySeconds * 1000L
                lat += mps * everySeconds / 111_195.0
                fixes += PaceFix(atMillis, lat, 10.75, accuracy, platformSpeed)
            }
            return this
        }

        fun first(accuracy: Double? = 5.0): Stream {
            fixes += PaceFix(atMillis, lat, 10.75, accuracy)
            return this
        }
    }

    private fun stream() = Stream(start, 59.91).first()

    private fun bouts(fixes: List<PaceFix>): List<WalkingBout> {
        val detector = WalkingBoutDetector()
        return fixes.mapNotNull(detector::add) + listOfNotNull(detector.finish())
    }

    private fun near(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.02,
    ) = assertTrue(abs(expected - actual) <= tolerance, "expected about $expected but was $actual")

    @Test
    fun `a steady two-minute walk is one bout at its speed`() {
        val found = bouts(stream().go(steps = 8, mps = 1.4).fixes)

        assertEquals(1, found.size)
        near(1.4, found.single().speedMetersPerSecond)
        assertEquals(120_000L, found.single().durationMillis)
    }

    @Test
    fun `fixes without an accuracy or with a poor one are never used`() {
        assertTrue(bouts(Stream(start, 59.91).first(null).go(steps = 8, mps = 1.4, accuracy = null).fixes).isEmpty())
        assertTrue(bouts(Stream(start, 59.91).first(50.0).go(steps = 8, mps = 1.4, accuracy = 50.0).fixes).isEmpty())
    }

    @Test
    fun `a walk under a minute or under 80 m counts for nothing`() {
        assertTrue(bouts(stream().go(steps = 3, mps = 1.4).fixes).isEmpty(), "45 s")
        // Long enough, but only 0.7 m/s for 100 s: 70 m.
        assertTrue(bouts(stream().go(steps = 4, mps = 0.7, everySeconds = 25).fixes).isEmpty(), "70 m")
    }

    @Test
    fun `a gap in the fixes ends the bout and the next one starts after it`() {
        val walk = stream().go(steps = 8, mps = 1.4)
        walk.atMillis += 60_000 // a minute without fixes: standing at a crossing, say
        walk.first().go(steps = 8, mps = 1.2)

        val found = bouts(walk.fixes)

        assertEquals(2, found.size)
        near(1.4, found[0].speedMetersPerSecond)
        near(1.2, found[1].speedMetersPerSecond)
    }

    @Test
    fun `a ride drops the walk it is part of and the walk after it counts only after the cooldown`() {
        val trip =
            stream()
                .go(steps = 4, mps = 1.4)
                .go(steps = 6, mps = 10.0, everySeconds = 10) // the bus
        val busOff = trip.atMillis
        trip.go(steps = 16, mps = 1.3)

        val found = bouts(trip.fixes)

        assertEquals(1, found.size)
        assertTrue(found.single().startMillis >= busOff + WalkingBoutDetector.VEHICLE_COOLDOWN_MILLIS - 15_000)
        near(1.3, found.single().speedMetersPerSecond)
    }

    @Test
    fun `a bus crawling at walking speed after a fast stretch is not a walk`() {
        val trip =
            stream()
                .go(steps = 6, mps = 8.0, everySeconds = 10)
                .go(steps = 6, mps = 1.4) // 90 s in traffic: within the cooldown
                .go(steps = 3, mps = 8.0, everySeconds = 10)

        assertTrue(bouts(trip.fixes).isEmpty())
    }

    @Test
    fun `running is not walking`() {
        // 2.6 m/s, under the per-step limit but over the walking band.
        assertTrue(bouts(stream().go(steps = 12, mps = 2.6, everySeconds = 10).fixes).isEmpty())
    }

    @Test
    fun `a vehicle's speed from the platform spoils the bout`() {
        assertTrue(bouts(stream().go(steps = 8, mps = 1.4, platformSpeed = 8.0).fixes).isEmpty())
    }

    @Test
    fun `jitter around one spot is not a walk`() {
        val jitter = stream()
        repeat(10) { i -> jitter.go(steps = 1, mps = if (i % 2 == 0) 1.6 else -1.6) }

        assertTrue(bouts(jitter.fixes).isEmpty())
    }

    @Test
    fun `dawdling below the walking band counts for nothing`() {
        assertTrue(bouts(stream().go(steps = 6, mps = 0.5, everySeconds = 40).fixes).isEmpty())
    }

    @Test
    fun `repeated fixes are ignored`() {
        val walk = stream().go(steps = 8, mps = 1.4)
        val doubled = walk.fixes.flatMap { listOf(it, it) }

        val found = bouts(doubled)

        assertEquals(1, found.size)
        near(1.4, found.single().speedMetersPerSecond)
    }

    // The proposal rule.

    private val today = start / 86_400_000L

    private fun record(
        vararg speeds: Double,
        days: Int = 2,
        seconds: Int = 180,
        boutCount: Int = speeds.size,
        declined: DeclinedWalkingSpeed? = null,
    ) = WalkingPaceRecord(
        samples = speeds.mapIndexed { i, speed -> WalkingPaceSample(speed, seconds, today - (i % days)) },
        boutCount = boutCount,
        declined = declined,
    )

    private val fastWalker = doubleArrayOf(1.55, 1.6, 1.5, 1.58, 1.52, 1.62)

    @Test
    fun `too few bouts measure nothing`() {
        assertNull(WalkingPace.measured(record(1.55, 1.6, 1.5, 1.58, 1.52)))
    }

    @Test
    fun `bouts on one day only measure nothing`() {
        assertNull(WalkingPace.measured(record(*fastWalker, days = 1)))
    }

    @Test
    fun `too little walking in all measures nothing`() {
        assertNull(WalkingPace.measured(record(*fastWalker, seconds = 60)))
    }

    @Test
    fun `the median decides so one odd walk does not`() {
        val proposal = WalkingPace.proposal(record(1.55, 1.56, 1.54, 1.55, 1.57, 0.7, 1.53), WalkingSpeed.NORMAL)

        assertNotNull(proposal)
        assertEquals(WalkingSpeed.FAST, proposal.speed)
        assertTrue(proposal.isFaster)
        assertEquals(56, proposal.kmhTenths)
        assertEquals(5.6, proposal.kmh)
    }

    @Test
    fun `a slow walker is proposed Slow`() {
        val proposal = WalkingPace.proposal(record(0.95, 1.0, 0.9, 0.98, 1.02, 0.97), WalkingSpeed.NORMAL)

        assertEquals(WalkingSpeed.SLOW, proposal?.speed)
        assertFalse(proposal!!.isFaster)
    }

    @Test
    fun `the levels have dead zones between them`() {
        assertEquals(WalkingSpeed.SLOW, WalkingPace.levelFor(1.0))
        assertNull(WalkingPace.levelFor(1.1))
        assertEquals(WalkingSpeed.NORMAL, WalkingPace.levelFor(1.15))
        assertEquals(WalkingSpeed.NORMAL, WalkingPace.levelFor(1.3))
        assertEquals(WalkingSpeed.NORMAL, WalkingPace.levelFor(1.38))
        assertNull(WalkingPace.levelFor(1.43))
        assertEquals(WalkingSpeed.FAST, WalkingPace.levelFor(1.5))
    }

    @Test
    fun `a borderline pace is measured but proposes nothing`() {
        val borderline = record(1.42, 1.43, 1.44, 1.42, 1.43, 1.44)

        assertNotNull(WalkingPace.measured(borderline))
        assertNull(WalkingPace.proposal(borderline, WalkingSpeed.NORMAL))
        assertNull(WalkingPace.proposal(borderline, WalkingSpeed.SLOW), "nor from another level")
    }

    @Test
    fun `nothing is proposed for the level already set`() {
        assertNull(WalkingPace.proposal(record(*fastWalker), WalkingSpeed.FAST))
        assertEquals(WalkingSpeed.FAST, WalkingPace.proposal(record(*fastWalker), WalkingSpeed.SLOW)?.speed)
    }

    @Test
    fun `a declined level comes back only after many more bouts`() {
        val declined = DeclinedWalkingSpeed(WalkingSpeed.FAST.name, boutCount = 8)
        val soon = record(*fastWalker, boutCount = 8 + WalkingPace.REPROPOSE_AFTER_BOUTS - 1, declined = declined)
        val later = record(*fastWalker, boutCount = 8 + WalkingPace.REPROPOSE_AFTER_BOUTS, declined = declined)

        assertNull(WalkingPace.proposal(soon, WalkingSpeed.NORMAL))
        assertEquals(WalkingSpeed.FAST, WalkingPace.proposal(later, WalkingSpeed.NORMAL)?.speed)
    }

    @Test
    fun `a declined level does not hold back another`() {
        val declined = DeclinedWalkingSpeed(WalkingSpeed.FAST.name, boutCount = 6)
        val slow = record(0.95, 1.0, 0.9, 0.98, 1.02, 0.97, declined = declined)

        assertEquals(WalkingSpeed.SLOW, WalkingPace.proposal(slow, WalkingSpeed.NORMAL)?.speed)
    }

    @Test
    fun `only the latest bouts are kept and all are counted`() {
        var kept = WalkingPaceRecord()
        repeat(WalkingPace.MAX_SAMPLES + 5) { i ->
            kept = WalkingPace.adding(kept, WalkingBout(start + i * 1_000_000L, start + i * 1_000_000L + 120_000, 168.0))
        }

        assertEquals(WalkingPace.MAX_SAMPLES, kept.samples.size)
        assertEquals(WalkingPace.MAX_SAMPLES + 5, kept.boutCount)
    }

    @Test
    fun `a walk just after midnight in Norway is on the new day`() {
        // 00:10 to 00:12 on 16 January in Oslo, still the 15th in UTC.
        val end = Instant.parse("2026-01-15T23:12:00Z").toEpochMilliseconds()
        val kept = WalkingPace.adding(WalkingPaceRecord(), WalkingBout(end - 120_000, end, 168.0))

        assertEquals(LocalDate(2026, 1, 16).toEpochDays(), kept.samples.single().day)
    }

    @Test
    fun `a kept bout holds its speed and duration and day and nothing else`() {
        val kept = WalkingPace.adding(WalkingPaceRecord(), WalkingBout(start, start + 120_000, 168.0))

        assertEquals(WalkingPaceSample(metersPerSecond = 1.4, seconds = 120, day = today), kept.samples.single())
        val json = Json.encodeToString(kept)
        assertFalse("lat" in json || "lon" in json, json)
    }
}

package com.example.pulsetransit.data.pace

import com.example.pulsetransit.data.NorwayTimeZone
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.distanceMeters
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Instant

/*
 * How fast the user really walks, learnt from the location fixes the app already receives while it
 * follows the device (on-spot mode, and the walks of a followed journey). Everything here is pure:
 * [WalkingBoutDetector] turns fixes into walking bouts, and [WalkingPace] decides from the bouts
 * kept whether to propose another [WalkingSpeed]. Only per-bout speeds with a day stamp are ever
 * kept ([WalkingPaceRecord]), never a coordinate.
 */

/** One location fix as the pace estimator reads it. */
data class PaceFix(
    /** When the fix was taken, epoch millis. */
    val atMillis: Long,
    val latitude: Double,
    val longitude: Double,
    /** Horizontal accuracy in metres; null when the platform did not say, which makes the fix unusable. */
    val accuracyMeters: Double?,
    /** The platform's own speed estimate in m/s, when it has one. */
    val speedMetersPerSecond: Double? = null,
)

/** A sustained walk: from the first fix to the last, along the fixes. */
data class WalkingBout(
    val startMillis: Long,
    val endMillis: Long,
    val distanceMeters: Double,
) {
    val durationMillis: Long get() = endMillis - startMillis
    val speedMetersPerSecond: Double get() = distanceMeters / (durationMillis / MILLIS_PER_SECOND)
}

private const val MILLIS_PER_SECOND = 1000.0

/**
 * Recognises walking bouts in a stream of fixes from one source, oldest first. Feed every fix to
 * [add]; it returns a bout when one has just ended, and [finish] ends the one under way (the source
 * stopped). A bout counts only when it is sustained, reasonably straight and at a walking speed;
 * anything else (standing still with GPS jitter, a vehicle, a bike, running, a gap) yields nothing.
 */
class WalkingBoutDetector {
    companion object {
        /** A fix less sure than this wanders by about one update step (20-25 m) on its own. */
        const val MAX_ACCURACY_METERS = 20.0

        /**
         * Updates come every 20-25 m, so every 10-30 s at a walk; a longer silence means the user
         * stood still (the distance filter sends nothing then) or the signal was lost.
         */
        const val MAX_GAP_MILLIS = 45_000L

        /** Between two fixes, faster than any walk even with GPS error: a vehicle, a bike or a run. */
        const val MAX_SEGMENT_SPEED_MPS = 3.0

        /** After something that fast, a vehicle may still be slowing in traffic or at a stop. */
        const val VEHICLE_COOLDOWN_MILLIS = 120_000L

        /** Shorter than this is a few steps to a door, not a walk to measure. */
        const val MIN_BOUT_MILLIS = 60_000L

        /** Fewer metres than this is within a few fixes' error. */
        const val MIN_BOUT_METERS = 80.0

        /** At least three steps between fixes, so one jump cannot make a bout. */
        const val MIN_BOUT_FIXES = 4

        /** Start-to-end distance over path length: jitter around one spot goes back and forth. */
        const val MIN_STRAIGHTNESS = 0.5

        /** Slower is dawdling or waiting; faster is running or riding: neither is a walking pace. */
        const val MIN_WALK_MPS = 0.6
        const val MAX_WALK_MPS = 2.2
    }

    private val fixes = mutableListOf<PaceFix>()
    private var pathMeters = 0.0
    private var lastFastAtMillis: Long? = null
    private var lastSeenMillis: Long? = null

    /** Takes the next fix; returns the bout it ended, if any. */
    @Suppress("ReturnCount") // each return is one way a fix is set aside or starts a bout afresh
    fun add(fix: PaceFix): WalkingBout? {
        val accuracy = fix.accuracyMeters
        if (accuracy == null || accuracy < 0 || accuracy > MAX_ACCURACY_METERS) return null
        val seen = lastSeenMillis
        if (seen != null && fix.atMillis <= seen) return null // repeated or out of order
        lastSeenMillis = fix.atMillis
        val last = fixes.lastOrNull()
        if (last == null) {
            startAt(fix)
            return null
        }
        val gap = fix.atMillis - last.atMillis
        if (gap > MAX_GAP_MILLIS) {
            val bout = evaluate()
            startAt(fix)
            return bout
        }
        val step = distanceMeters(last.latitude, last.longitude, fix.latitude, fix.longitude)
        val stepSpeed = step / (gap / MILLIS_PER_SECOND)
        val platformFast = (fix.speedMetersPerSecond ?: 0.0) > MAX_SEGMENT_SPEED_MPS
        if (stepSpeed > MAX_SEGMENT_SPEED_MPS || platformFast) {
            // Not walking: whatever led up to it is dropped with it.
            lastFastAtMillis = fix.atMillis
            startAt(fix)
            return null
        }
        val fast = lastFastAtMillis
        if (fast != null && fix.atMillis - fast < VEHICLE_COOLDOWN_MILLIS) {
            startAt(fix) // a bout starts only once the cooldown is over
            return null
        }
        fixes += fix
        pathMeters += step
        return null
    }

    /** Ends the bout under way, as when the source stops; returns it if it counts. */
    fun finish(): WalkingBout? {
        val bout = evaluate()
        reset()
        return bout
    }

    /** Forgets everything, the bout under way included. */
    fun reset() {
        fixes.clear()
        pathMeters = 0.0
        lastFastAtMillis = null
        lastSeenMillis = null
    }

    private fun startAt(fix: PaceFix) {
        fixes.clear()
        fixes += fix
        pathMeters = 0.0
    }

    private fun evaluate(): WalkingBout? {
        if (fixes.size < MIN_BOUT_FIXES) return null
        val first = fixes.first()
        val last = fixes.last()
        val bout = WalkingBout(first.atMillis, last.atMillis, pathMeters)
        val net = distanceMeters(first.latitude, first.longitude, last.latitude, last.longitude)
        val counts =
            bout.durationMillis >= MIN_BOUT_MILLIS &&
                bout.distanceMeters >= MIN_BOUT_METERS &&
                net / bout.distanceMeters >= MIN_STRAIGHTNESS &&
                bout.speedMetersPerSecond in MIN_WALK_MPS..MAX_WALK_MPS
        return bout.takeIf { counts }
    }
}

/** One measured walk as kept: its speed, how long it lasted and the day it was on. Nothing else. */
@Serializable
data class WalkingPaceSample(
    val metersPerSecond: Double,
    val seconds: Int,
    /** The day it ended on, in days since the epoch by Norway's calendar, only to tell days apart. */
    val day: Long,
)

/** A proposal the user turned down: [speed] (a [WalkingSpeed] name) when [boutCount] bouts had been measured. */
@Serializable
data class DeclinedWalkingSpeed(
    val speed: String,
    val boutCount: Int,
)

/** Everything kept about the user's pace: the latest bouts, how many were ever measured, and a decline. */
@Serializable
data class WalkingPaceRecord(
    /** Oldest first, at most [WalkingPace.MAX_SAMPLES]. */
    val samples: List<WalkingPaceSample> = emptyList(),
    /** Bouts measured since the data was last forgotten, including those dropped from [samples]. */
    val boutCount: Int = 0,
    val declined: DeclinedWalkingSpeed? = null,
)

/** The user's measured pace: the median of the kept bouts. */
data class MeasuredWalkingPace(
    val metersPerSecond: Double,
    /** How many bouts it is the median of. */
    val bouts: Int,
) {
    /** km/h in tenths, rounded: 54 for 5.4 km/h. Format it with the locale's decimal separator. */
    val kmhTenths: Int get() = (metersPerSecond * KMH_TENTHS_PER_MPS).roundToInt()

    /** km/h rounded to one decimal, as [kmhTenths] says. */
    val kmh: Double get() = kmhTenths / TENTHS

    private companion object {
        const val KMH_TENTHS_PER_MPS = 36.0
        const val TENTHS = 10.0
    }
}

/** A different walking speed for routes, from [pace], than the [current] one. */
data class WalkingSpeedProposal(
    val speed: WalkingSpeed,
    val current: WalkingSpeed,
    val pace: MeasuredWalkingPace,
) {
    /** True when the user walks faster than [current] assumes ("You walk faster..."), false when slower. */
    val isFaster: Boolean get() = speed.ordinal > current.ordinal

    val kmh: Double get() = pace.kmh
    val kmhTenths: Int get() = pace.kmhTenths
}

/**
 * The proposal rule. With enough evidence (at least [MIN_BOUTS] bouts, on [MIN_DAYS] days or more,
 * [MIN_TOTAL_SECONDS] of walking in all) the median bout speed is the user's pace, mapped to a
 * level by [levelFor]. A proposal exists only when that level differs from the current setting,
 * and not for a level the user declined until [REPROPOSE_AFTER_BOUTS] more bouts have come.
 */
object WalkingPace {
    /** The latest bouts kept: about a few weeks of walks, so the pace follows the user over time. */
    const val MAX_SAMPLES = 40

    /** One walk can be unusual (late, carrying bags); several agree on how one walks. */
    const val MIN_BOUTS = 6

    /** One day can be unusual too (snow, ice, company). */
    const val MIN_DAYS = 2

    /** Ten minutes of walking in all, so many short bouts alone do not decide. */
    const val MIN_TOTAL_SECONDS = 600

    /** "Substantially more evidence" before a declined level is proposed again. */
    const val REPROPOSE_AFTER_BOUTS = 15

    /*
     * Entur walks at the set speed (SLOW 1.0, NORMAL about 1.33, FAST 1.7 m/s), but its walks along
     * the map come out slower than that, for corners and crossings. A bout is measured along the
     * fixes, which cut corners a little, and includes waits under [WalkingBoutDetector.MAX_GAP_MILLIS]
     * (a red light), so it reads a little below the speed one walks at: about 0.95 of it. The levels
     * then read as about 0.95, 1.26 and 1.6 m/s measured, and the bands split halfway between them,
     * with a dead zone of about 0.05 m/s on each side of each split where nothing is proposed:
     *
     * - SLOW:   below 1.05 m/s (3.8 km/h)
     * - NORMAL: 1.15 to 1.38 m/s (4.1 to 5.0 km/h)
     * - FAST:   above 1.48 m/s (5.3 km/h)
     */
    const val SLOW_BELOW_MPS = 1.05
    const val NORMAL_FROM_MPS = 1.15
    const val NORMAL_TO_MPS = 1.38
    const val FAST_ABOVE_MPS = 1.48

    private const val MILLIS_PER_SECOND = 1000L

    /** The level [metersPerSecond] measured points at, or null in a dead zone between two. */
    fun levelFor(metersPerSecond: Double): WalkingSpeed? =
        when {
            metersPerSecond < SLOW_BELOW_MPS -> WalkingSpeed.SLOW
            metersPerSecond in NORMAL_FROM_MPS..NORMAL_TO_MPS -> WalkingSpeed.NORMAL
            metersPerSecond > FAST_ABOVE_MPS -> WalkingSpeed.FAST
            else -> null
        }

    /** The user's pace from [record], or null without enough evidence yet. */
    fun measured(record: WalkingPaceRecord): MeasuredWalkingPace? {
        val samples = record.samples
        val enough =
            samples.size >= MIN_BOUTS &&
                samples.map { it.day }.distinct().size >= MIN_DAYS &&
                samples.sumOf { it.seconds } >= MIN_TOTAL_SECONDS
        if (!enough) return null
        return MeasuredWalkingPace(median(samples.map { it.metersPerSecond }), samples.size)
    }

    /** What to propose instead of [current], or null; see [WalkingPace]. */
    fun proposal(
        record: WalkingPaceRecord,
        current: WalkingSpeed,
    ): WalkingSpeedProposal? {
        val pace = measured(record) ?: return null
        val level = levelFor(pace.metersPerSecond) ?: return null
        if (level == current) return null
        val declined = record.declined
        if (declined != null && declined.speed == level.name && record.boutCount - declined.boutCount < REPROPOSE_AFTER_BOUTS) {
            return null
        }
        return WalkingSpeedProposal(level, current, pace)
    }

    /**
     * [record] with [bout] added, keeping the latest [MAX_SAMPLES]. Days are [timeZone]'s, so a walk
     * just after midnight is on the new day, as the user sees it, not on UTC's.
     */
    fun adding(
        record: WalkingPaceRecord,
        bout: WalkingBout,
        timeZone: TimeZone = NorwayTimeZone,
    ): WalkingPaceRecord {
        val sample =
            WalkingPaceSample(
                metersPerSecond = (bout.speedMetersPerSecond * HUNDREDTHS).roundToInt() / HUNDREDTHS,
                seconds = (bout.durationMillis / MILLIS_PER_SECOND).toInt(),
                day =
                    Instant
                        .fromEpochMilliseconds(bout.endMillis)
                        .toLocalDateTime(timeZone)
                        .date
                        .toEpochDays(),
            )
        return record.copy(samples = (record.samples + sample).takeLast(MAX_SAMPLES), boutCount = record.boutCount + 1)
    }

    /** [record] remembering that [speed] was declined now. */
    fun declining(
        record: WalkingPaceRecord,
        speed: WalkingSpeed,
    ): WalkingPaceRecord = record.copy(declined = DeclinedWalkingSpeed(speed.name, record.boutCount))

    private const val HUNDREDTHS = 100.0

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}

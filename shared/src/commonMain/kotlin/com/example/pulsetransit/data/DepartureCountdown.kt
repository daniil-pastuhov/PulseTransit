package com.example.pulsetransit.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** What the board shows in a departure's time column; each platform renders it in its own style. */
sealed interface DepartureCountdown {
    /** Leaving within [DepartureCountdown.DEPARTING_NOW_SECONDS]. */
    data object Now : DepartureCountdown

    /** Within the next half hour: shown as "5m". */
    data class Minutes(
        val minutes: Long,
    ) : DepartureCountdown

    /**
     * Further out: shown as a wall-clock time. [dayOffset] is how many calendar days ahead it is,
     * printed as a small "+1" when the user widened the search past today.
     */
    data class Clock(
        val epochMillis: Long,
        val dayOffset: Int,
    ) : DepartureCountdown

    /** The timestamp could not be read. */
    data object Unknown : DepartureCountdown

    companion object {
        const val DEPARTING_NOW_SECONDS = 15L
        const val RELATIVE_TIME_CUTOFF_SECONDS = 30 * 60L
    }
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/** [departureCountdown] in the device's time zone. A separate overload, since Swift has no default arguments. */
fun departureCountdown(
    expectedTime: String,
    nowMillis: Long,
): DepartureCountdown = departureCountdown(expectedTime, nowMillis, TimeZone.currentSystemDefault())

/** Classifies a departure's ISO-8601 [expectedTime] relative to [nowMillis] in [timeZone]. */
fun departureCountdown(
    expectedTime: String,
    nowMillis: Long,
    timeZone: TimeZone,
): DepartureCountdown {
    val expected = Instant.parseOrNull(expectedTime) ?: return DepartureCountdown.Unknown
    val diffSeconds = (expected.toEpochMilliseconds() - nowMillis) / MILLIS_PER_SECOND
    return when {
        diffSeconds < DepartureCountdown.DEPARTING_NOW_SECONDS -> DepartureCountdown.Now
        diffSeconds < SECONDS_PER_MINUTE -> DepartureCountdown.Minutes(1)
        diffSeconds < DepartureCountdown.RELATIVE_TIME_CUTOFF_SECONDS -> DepartureCountdown.Minutes(diffSeconds / SECONDS_PER_MINUTE)
        else -> {
            val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
            val day = expected.toLocalDateTime(timeZone).date
            DepartureCountdown.Clock(expected.toEpochMilliseconds(), today.daysUntil(day))
        }
    }
}

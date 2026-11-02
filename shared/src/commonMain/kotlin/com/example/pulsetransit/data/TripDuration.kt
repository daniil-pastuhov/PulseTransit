package com.example.pulsetransit.data

/** A calendar-ish unit a trip duration is spelled out in. Months are 30 days and years 365. */
enum class TimeSpanUnit(
    val minutes: Int,
) {
    YEARS(365 * 24 * 60),
    MONTHS(30 * 24 * 60),
    DAYS(24 * 60),
    HOURS(60),
    MINUTES(1),
}

/** One piece of a spelled-out duration, such as "17 h". */
data class TimeSpanPart(
    val unit: TimeSpanUnit,
    val value: Int,
)

/**
 * [minutes] as the units a person would say it in, largest first: "17 h 23 min" rather than
 * "1043 min". At most two parts, and the second only if it is the next unit down and not zero,
 * so a day and five minutes reads "1 d", not "1 d 5 min". Under an hour it stays in minutes;
 * zero or less is "0 min".
 */
fun tripDurationParts(minutes: Int): List<TimeSpanPart> {
    if (minutes <= 0) return listOf(TimeSpanPart(TimeSpanUnit.MINUTES, 0))
    val units = TimeSpanUnit.entries
    val first = units.indexOfFirst { minutes >= it.minutes }
    val largest = units[first]
    val parts = mutableListOf(TimeSpanPart(largest, minutes / largest.minutes))
    units.getOrNull(first + 1)?.let { next ->
        val value = minutes % largest.minutes / next.minutes
        if (value > 0) parts += TimeSpanPart(next, value)
    }
    return parts
}

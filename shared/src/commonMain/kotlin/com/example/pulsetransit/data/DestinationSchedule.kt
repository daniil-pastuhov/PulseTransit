package com.example.pulsetransit.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable

/**
 * When a saved destination's route is shown: on [isoDays] from [startMinute] up to [endMinute].
 * A destination without one is always shown.
 *
 * Days are ISO numbers (1 = Monday … 7 = Sunday) rather than `DayOfWeek`, so the stored JSON and
 * the Swift side stay plain ints. A window whose end is not after its start runs past midnight,
 * and the hours after midnight belong to the day it started on: Friday 22:00–02:00 is shown
 * early on Saturday, not early on Friday.
 */
@Serializable
data class DestinationSchedule(
    val isoDays: Set<Int> = ALL_DAYS,
    /** Minutes since midnight, inclusive: 0..1439. */
    val startMinute: Int = 0,
    /** Minutes since midnight, exclusive: 1..1440. */
    val endMinute: Int = MINUTES_PER_DAY,
) {
    init {
        require(isoDays.all { it in 1..DAYS_PER_WEEK }) { "isoDays must be 1..7: $isoDays" }
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in 1..MINUTES_PER_DAY) { "endMinute out of range: $endMinute" }
    }

    /** Whether the window ends on the day after it starts. */
    val endsNextDay: Boolean get() = endMinute <= startMinute

    fun isActive(at: LocalDateTime): Boolean {
        val day = at.dayOfWeek.isoDayNumber
        val minute = at.hour * MINUTES_PER_HOUR + at.minute
        if (!endsNextDay) return day in isoDays && minute >= startMinute && minute < endMinute
        val previousDay = if (day == 1) DAYS_PER_WEEK else day - 1
        return (day in isoDays && minute >= startMinute) || (previousDay in isoDays && minute < endMinute)
    }

    /**
     * The days as text, Monday first: three or more days in a row become a range ("Mon–Fri"),
     * the rest are listed ("Mon, Wed, Sat, Sun"). [dayName] gives
     * the platform's localized name for an ISO day number.
     */
    fun daysText(dayName: (Int) -> String): String {
        val runs = mutableListOf<IntRange>()
        for (day in isoDays.sorted()) {
            val last = runs.lastOrNull()
            if (last != null && last.last == day - 1) runs[runs.lastIndex] = last.first..day else runs += day..day
        }
        return runs.joinToString(", ") { run ->
            if (run.last - run.first >= 2) {
                "${dayName(run.first)}–${dayName(run.last)}"
            } else {
                run.joinToString(", ") { dayName(it) }
            }
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        private const val MINUTES_PER_HOUR = 60
        private const val DAYS_PER_WEEK = 7

        val ALL_DAYS: Set<Int> = (1..DAYS_PER_WEEK).toSet()
        val WEEKDAYS: Set<Int> = (1..5).toSet()
        val WEEKEND: Set<Int> = setOf(6, 7)
    }
}

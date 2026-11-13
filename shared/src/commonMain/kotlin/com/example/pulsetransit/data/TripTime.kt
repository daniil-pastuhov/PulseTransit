package com.example.pulsetransit.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** When a searched route should travel: from now, from a given time, or so as to arrive by one. */
sealed interface TripTime {
    /** Leave now, as for every saved destination. */
    data object Now : TripTime

    data class DepartAt(
        val atMillis: Long,
    ) : TripTime

    data class ArriveBy(
        val atMillis: Long,
    ) : TripTime
}

/** The chosen moment, or null for [TripTime.Now]. */
val TripTime.atMillis: Long?
    get() =
        when (this) {
            TripTime.Now -> null
            is TripTime.DepartAt -> atMillis
            is TripTime.ArriveBy -> atMillis
        }

val TripTime.isArriveBy: Boolean
    get() = this is TripTime.ArriveBy

/**
 * Whether the chosen moment is no longer ahead: a departure time that has come is the same as
 * leaving now, and an arrival time that has come can no longer be met.
 */
fun TripTime.hasPassed(nowMillis: Long): Boolean = atMillis?.let { it <= nowMillis } ?: false

/**
 * The next [hour]:[minute] in Norway at or after [nowMillis]: today if it is still ahead, else tomorrow.
 * A time picker offers only a time of day, and a time already past today means tomorrow's.
 */
fun nextTimeOfDay(
    hour: Int,
    minute: Int,
    nowMillis: Long,
): Long = nextTimeOfDay(hour, minute, nowMillis, NorwayTimeZone)

internal fun nextTimeOfDay(
    hour: Int,
    minute: Int,
    nowMillis: Long,
    timeZone: TimeZone,
): Long {
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    val todayAt = today.atTime(LocalTime(hour, minute)).toInstant(timeZone).toEpochMilliseconds()
    if (todayAt > nowMillis) return todayAt
    return today
        .plus(1, DateTimeUnit.DAY)
        .atTime(LocalTime(hour, minute))
        .toInstant(timeZone)
        .toEpochMilliseconds()
}

/**
 * [hour]:[minute] on the day [dayOffset] days after [nowMillis]'s (0 today, 1 tomorrow): the
 * travel-time picker's explicit Today/Tomorrow choice. Unlike [nextTimeOfDay] it never rolls over,
 * so a time already past today stays today and reads as passed ([TripTime.hasPassed]).
 */
fun timeOnDay(
    dayOffset: Int,
    hour: Int,
    minute: Int,
    nowMillis: Long,
): Long = timeOnDay(dayOffset, hour, minute, nowMillis, NorwayTimeZone)

internal fun timeOnDay(
    dayOffset: Int,
    hour: Int,
    minute: Int,
    nowMillis: Long,
    timeZone: TimeZone,
): Long =
    Instant
        .fromEpochMilliseconds(nowMillis)
        .toLocalDateTime(timeZone)
        .date
        .plus(dayOffset, DateTimeUnit.DAY)
        .atTime(LocalTime(hour, minute))
        .toInstant(timeZone)
        .toEpochMilliseconds()

/** [atMillis] as a time of day in Norway, to start a time picker from. */
fun localTimeOfDay(atMillis: Long): LocalTime = localTimeOfDay(atMillis, NorwayTimeZone)

internal fun localTimeOfDay(
    atMillis: Long,
    timeZone: TimeZone,
): LocalTime = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(timeZone).time

/** Calendar days from [nowMillis] to [atMillis] in Norway: 0 today, 1 tomorrow. */
fun calendarDaysFrom(
    nowMillis: Long,
    atMillis: Long,
): Int = calendarDaysFrom(nowMillis, atMillis, NorwayTimeZone)

internal fun calendarDaysFrom(
    nowMillis: Long,
    atMillis: Long,
    timeZone: TimeZone,
): Int {
    val from = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(timeZone).date
    val to = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(timeZone).date
    return from.daysUntil(to)
}

/** "08:30", the 24-hour time of [atMillis] in Norway, as the board shows times. */
fun clockTimeText(atMillis: Long): String = clockTimeText(atMillis, NorwayTimeZone)

internal fun clockTimeText(
    atMillis: Long,
    timeZone: TimeZone,
): String {
    val time: LocalDateTime = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(timeZone)
    return time.hour.toString().padStart(2, '0') + ":" + time.minute.toString().padStart(2, '0')
}

/** Entur's `dateTime` for [atMillis]: whole seconds in UTC, e.g. "2026-11-03T07:30:00Z". */
internal fun enturDateTime(atMillis: Long): String = Instant.fromEpochSeconds(atMillis / MILLIS_PER_SECOND).toString()

private const val MILLIS_PER_SECOND = 1000L

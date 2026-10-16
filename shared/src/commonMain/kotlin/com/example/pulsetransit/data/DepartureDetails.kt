package com.example.pulsetransit.data

import kotlin.time.Instant

private const val MILLIS_PER_MINUTE = 60_000L

/** A typical gap needs at least this many gaps between departures to mean anything. */
private const val MIN_GAPS_FOR_HEADWAY = 2

/**
 * The stops a trip still has ahead of it from where [departure] boards: its own quay first, then
 * the rest. Matched by quay, else by [stopPlaceId] (a quay can move between a stop's children);
 * the whole trip when neither is on it, rather than nothing.
 */
fun List<JourneyStop>.stopsAhead(
    departure: Departure,
    stopPlaceId: String,
): List<JourneyStop> {
    val start =
        indexOfFirst { departure.quayId != null && it.quayId == departure.quayId }
            .takeIf { it >= 0 }
            ?: indexOfFirst { it.stopPlaceId == stopPlaceId }.takeIf { it >= 0 }
            ?: 0
    return drop(start)
}

/** Whole minutes from [fromTime] to [toTime], both ISO-8601; null if either cannot be read. */
fun minutesBetween(
    fromTime: String,
    toTime: String,
): Long? {
    val from = Instant.parseOrNull(fromTime) ?: return null
    val to = Instant.parseOrNull(toTime) ?: return null
    return (to - from).inWholeMinutes
}

/** Whole minutes from [nowMillis] until an ISO-8601 [time], never negative; null if it cannot be read. */
fun minutesUntil(
    time: String,
    nowMillis: Long,
): Long? {
    val at = Instant.parseOrNull(time) ?: return null
    return ((at.toEpochMilliseconds() - nowMillis) / MILLIS_PER_MINUTE).coerceAtLeast(0)
}

/**
 * How often [departures] leave, as the median gap in minutes between consecutive ones that run:
 * "every ~15 min". Null with too few to tell, or when they leave at one time.
 */
fun typicalHeadwayMinutes(departures: List<Departure>): Int? {
    val times =
        departures
            .filterNot { it.isCancelled }
            .mapNotNull { Instant.parseOrNull(it.expectedTime)?.toEpochMilliseconds() }
            .sorted()
    val gaps = times.zipWithNext { a, b -> (b - a) / MILLIS_PER_MINUTE }.sorted()
    if (gaps.size < MIN_GAPS_FOR_HEADWAY) return null
    return gaps[gaps.size / 2].toInt().takeIf { it > 0 }
}

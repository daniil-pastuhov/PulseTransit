package com.example.pulsetransit.data

import kotlin.time.Instant

/** Whether [this] is ridden on a line rather than walked. */
val TripLeg.isTransit: Boolean get() = lineName != null

/**
 * Whether the details list [this] at all: every ridden leg, and walks that take time. Zero-minute
 * foot legs are transfers between quays of one stop, as the board leaves them out too.
 */
val TripLeg.isShown: Boolean get() = isTransit || durationMinutes > 0

/** How many times [trip] changes vehicle: 0 for a direct ride. */
fun transferCount(trip: TripInfo): Int = (trip.legs.count { it.isTransit } - 1).coerceAtLeast(0)

/** How far [trip] walks in all, in metres. */
fun walkDistanceMeters(trip: TripInfo): Int = trip.legs.filterNot { it.isTransit }.sumOf { it.distanceMeters }

/** Whole minutes [leg] leaves after its timetable, never negative; 0 when either time is missing. */
fun legDelayMinutes(leg: TripLeg): Int {
    val expected = leg.expectedStartTime?.let(Instant::parseOrNull) ?: return 0
    val aimed = leg.aimedStartTime?.let(Instant::parseOrNull) ?: return 0
    return (expected - aimed).inWholeMinutes.toInt().coerceAtLeast(0)
}

/**
 * Minutes spent waiting before [trip]'s leg at [index]: from when the leg before it arrives until
 * this one leaves. Null for the first leg, when a time is missing, or when there is no wait.
 */
fun waitMinutesBefore(
    trip: TripInfo,
    index: Int,
): Int? {
    val previous = trip.legs.getOrNull(index - 1)?.expectedEndTime ?: return null
    val start = trip.legs.getOrNull(index)?.expectedStartTime ?: return null
    return minutesBetween(previous, start)?.toInt()?.takeIf { it > 0 }
}

/**
 * Where [trip]'s leg at [index] ends. The last one ends at the destination, which Entur calls just
 * "Destination" when it is a coordinate, so the destination's own name is used there.
 */
fun legEndName(
    trip: TripInfo,
    index: Int,
): String? {
    val leg = trip.legs.getOrNull(index) ?: return null
    return if (index == trip.legs.lastIndex && trip.destinationName.isNotBlank()) trip.destinationName else leg.toName
}

/**
 * The stops [leg] rides through, from where it boards to where it gets off, both included. Matched
 * by quay; from the start or to the end of the trip when a quay is not on it, rather than nothing.
 */
fun List<JourneyStop>.legStops(leg: TripLeg): List<JourneyStop> {
    val start = indexOfFirst { leg.fromQuayId != null && it.quayId == leg.fromQuayId }.coerceAtLeast(0)
    val end =
        (start until size)
            .firstOrNull { leg.toQuayId != null && this[it].quayId == leg.toQuayId }
            ?: lastIndex
    return subList(start, end + 1).toList()
}

/**
 * What identifies [trip] across refreshes: the journeys it rides, which stay the same while its
 * times move with live data. Trips cached before journeys were kept fall back to their start.
 */
fun tripKey(trip: TripInfo): String =
    trip.legs
        .mapNotNull { it.serviceJourneyId }
        .ifEmpty { listOf(trip.expectedStartTime) }
        .joinToString("|")

/**
 * The current version of [selected] among [trips], so open details follow live updates. Falls back
 * to [selected] once a refresh no longer has it, so details opened on a trip that just left still
 * show something.
 */
fun currentTrip(
    trips: List<TripInfo>?,
    selected: TripInfo,
): TripInfo {
    val key = tripKey(selected)
    return trips?.firstOrNull { tripKey(it) == key } ?: selected
}

package com.example.pulsetransit.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * A fastest route's arrival as its banner title shows it: "Fastest to Majorstuen 18:31".
 * [dayOffset] is how many calendar days after the departure it arrives, printed as a small "+1"
 * like the board's next-day times.
 */
data class TripArrival(
    val time: String,
    val dayOffset: Int,
)

// "yyyy-MM-dd" and "HH:mm" within a yyyy-MM-ddTHH:mm:ss... timestamp.
private const val ISO_DATE_END = 10
private const val ISO_HOUR_START = 11
private const val ISO_MINUTE_END = 16

/**
 * [trip]'s arrival, or null when Entur gave none or it can't be read. The time is read straight out
 * of the timestamp, which Entur writes in local Oslo time, the same way the banner reads the
 * departure, so the two always agree.
 */
fun tripArrival(trip: TripInfo): TripArrival? {
    val end = trip.expectedEndTime ?: return null
    if (end.length < ISO_MINUTE_END || end[ISO_DATE_END] != 'T') return null
    val endDate = isoDate(end) ?: return null
    val startDate = isoDate(trip.expectedStartTime)
    val dayOffset = startDate?.daysUntil(endDate)?.coerceAtLeast(0) ?: 0
    return TripArrival(end.substring(ISO_HOUR_START, ISO_MINUTE_END), dayOffset)
}

private fun isoDate(iso: String): LocalDate? =
    if (iso.length < ISO_DATE_END) null else runCatching { LocalDate.parse(iso.substring(0, ISO_DATE_END)) }.getOrNull()

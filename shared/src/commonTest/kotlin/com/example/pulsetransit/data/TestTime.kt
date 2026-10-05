package com.example.pulsetransit.data

import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.Instant

/** Wall-clock now, for tests whose subject reads the real clock too. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Whole seconds, as the APIs send them; the sub-second part would only add noise. */
private fun wholeSeconds(millis: Long) = Instant.fromEpochMilliseconds(millis / 1000 * 1000)

/** How Entur writes times: local Oslo time with its offset, "2026-07-15T10:05:00+02:00". */
fun isoOslo(millis: Long): String {
    val instant = wholeSeconds(millis)
    return instant.format(DateTimeComponents.Formats.ISO_DATE_TIME_OFFSET, TimeZone.of("Europe/Oslo").offsetAt(instant))
}

/** How MET writes times: UTC with a Z, "2026-07-15T08:05:00Z". */
fun isoUtc(millis: Long): String = wholeSeconds(millis).toString()

/** An RFC-1123 HTTP date, as MET sends in `Expires` and `Last-Modified`. */
fun httpDate(millis: Long): String = GMTDate(millis).toHttpDate()

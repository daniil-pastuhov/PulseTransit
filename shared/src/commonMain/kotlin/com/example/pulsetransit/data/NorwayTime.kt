package com.example.pulsetransit.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Instant

/**
 * Every time the app shows is Norway's: the board is for Norwegian stops, so a departure reads the
 * same as the station's own sign wherever the device's clock is set (a tester abroad, a phone on
 * its home time zone). Swift formats with [NORWAY_TIME_ZONE_ID].
 */
const val NORWAY_TIME_ZONE_ID = "Europe/Oslo"

val NorwayTimeZone: TimeZone = TimeZone.of(NORWAY_TIME_ZONE_ID)

/**
 * Whether [deviceZone] reads a different clock time than Norway at [atMillis], so the board labels
 * its times as Norway's. Compares offsets, not ids, so Stockholm or Berlin get no label.
 */
fun differsFromNorwayTime(
    atMillis: Long,
    deviceZone: TimeZone,
): Boolean {
    val at = Instant.fromEpochMilliseconds(atMillis)
    return deviceZone.offsetAt(at) != NorwayTimeZone.offsetAt(at)
}

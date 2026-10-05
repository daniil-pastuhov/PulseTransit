package com.example.pulsetransit.data

import kotlin.time.Instant

/**
 * Parses the ISO-8601 timestamps returned by Entur ("2026-07-15T10:05:00+02:00") and MET
 * ("2026-07-15T08:00:00Z") into epoch millis, or null if unparseable.
 */
internal fun parseIsoToMillis(iso: String): Long? = Instant.parseOrNull(iso)?.toEpochMilliseconds()

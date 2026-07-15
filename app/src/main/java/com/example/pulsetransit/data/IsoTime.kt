package com.example.pulsetransit.data

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses the ISO-8601 timestamps returned by Entur ("2026-07-15T10:05:00+02:00") and MET
 * ("2026-07-15T08:00:00Z") into epoch millis, or null if unparseable.
 *
 * java.time is unavailable below API 26 and desugaring is not enabled, so the offset is
 * normalised to the RFC-822 form that SimpleDateFormat's 'Z' pattern accepts.
 */
internal fun parseIsoToMillis(iso: String): Long? = try {
    val formatted = iso.replace("Z", "+0000").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(formatted)?.time
} catch (_: Exception) {
    null
}

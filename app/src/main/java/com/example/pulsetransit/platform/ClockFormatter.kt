package com.example.pulsetransit.platform

/**
 * Locale-aware rendering of the board's clock and timestamps.
 *
 * kotlinx-datetime has no localized weekday or month names, so this stays platform code: the
 * ViewModel asks for strings and each platform formats them in the user's locale.
 */
interface ClockFormatter {
    /** Everything the header shows, for the instant [epochMillis]. */
    fun clockText(epochMillis: Long): ClockText

    /** A wall-clock time with seconds ("14:05:09"), used for the board's last-updated stamp. */
    fun timeWithSeconds(epochMillis: Long): String
}

data class ClockText(
    /** Hours and minutes, "14:05". */
    val time: String,
    val dayOfMonth: String,
    val weekdayShort: String,
    val monthShort: String,
    val weekdayLong: String,
    val monthLong: String,
)

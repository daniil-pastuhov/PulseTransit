package com.example.pulsetransit.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [ClockFormatter] in the device's default locale and time zone.
 *
 * The formats are built per call: [SimpleDateFormat] is not thread-safe, and building them fresh
 * also picks up a locale change without restarting the app.
 */
class SystemClockFormatter : ClockFormatter {
    override fun clockText(epochMillis: Long): ClockText {
        val date = Date(epochMillis)

        fun format(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        return ClockText(
            time = format("HH:mm"),
            dayOfMonth = format("d"),
            weekdayShort = format("EEE"),
            monthShort = format("LLL"),
            weekdayLong = format("EEEE"),
            monthLong = format("LLLL"),
        )
    }

    override fun timeWithSeconds(epochMillis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
}

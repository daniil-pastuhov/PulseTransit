package com.example.pulsetransit.platform

import com.example.pulsetransit.data.NORWAY_TIME_ZONE_ID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * [ClockFormatter] in the device's default locale, in Norway time.
 *
 * The formats are built per call: [SimpleDateFormat] is not thread-safe, and building them fresh
 * also picks up a locale change without restarting the app.
 */
class SystemClockFormatter : ClockFormatter {
    override fun clockText(epochMillis: Long): ClockText {
        val date = Date(epochMillis)

        fun format(pattern: String) = norwayFormat(pattern).format(date)
        return ClockText(
            time = format("HH:mm"),
            dayOfMonth = format("d"),
            weekdayShort = format("EEE"),
            monthShort = format("LLL"),
            weekdayLong = format("EEEE"),
            monthLong = format("LLLL"),
        )
    }

    override fun timeWithSeconds(epochMillis: Long): String = norwayFormat("HH:mm:ss").format(Date(epochMillis))
}

/** [pattern] in the default locale, in Norway time like every time the app shows (see `NorwayTimeZone`). */
private fun norwayFormat(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(NORWAY_TIME_ZONE_ID) }

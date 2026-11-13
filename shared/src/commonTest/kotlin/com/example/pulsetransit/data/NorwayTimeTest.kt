package com.example.pulsetransit.data

import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Times show in Norway time whatever the device's zone; these use the public overloads, which take none. */
class NorwayTimeTest {
    // 12:00 UTC: 13:00 in Norway in winter, 14:00 in summer.
    private val winterNoonUtc = Instant.parse("2026-11-13T12:00:00Z").toEpochMilliseconds()
    private val summerNoonUtc = Instant.parse("2026-07-01T12:00:00Z").toEpochMilliseconds()

    @Test
    fun `clock times are Norway's`() {
        assertEquals("13:00", clockTimeText(winterNoonUtc))
        assertEquals("14:00", clockTimeText(summerNoonUtc))
    }

    @Test
    fun `a departure's clock time and day are Norway's`() {
        // 23:30 UTC is 00:30 the next day in Norway, though still the same day in UTC or New York.
        val now = Instant.parse("2026-11-13T20:00:00Z").toEpochMilliseconds()
        val clock = departureCountdown("2026-11-13T23:30:00Z", now)
        assertEquals(DepartureCountdown.Clock(Instant.parse("2026-11-13T23:30:00Z").toEpochMilliseconds(), 1), clock)
    }

    @Test
    fun `a departure's details clock and day are Norway's`() {
        // However close: the details list every stop as a clock time, with its day in Norway.
        val now = Instant.parse("2026-11-13T23:00:00Z").toEpochMilliseconds()
        val clock = departureClock("2026-11-13T23:05:00Z", now)
        assertEquals(DepartureCountdown.Clock(Instant.parse("2026-11-13T23:05:00Z").toEpochMilliseconds(), 0), clock)
        // 23:30 UTC is 00:30 on the 14th in Norway, though still the 13th in UTC or New York.
        val later = departureClock("2026-11-13T23:30:00Z", Instant.parse("2026-11-13T22:00:00Z").toEpochMilliseconds())
        assertEquals(1, later?.dayOffset)
    }

    @Test
    fun `a picked time of day is Norway's`() {
        assertEquals(Instant.parse("2026-11-13T17:00:00Z").toEpochMilliseconds(), nextTimeOfDay(18, 0, winterNoonUtc))
        assertEquals(Instant.parse("2026-11-14T07:00:00Z").toEpochMilliseconds(), timeOnDay(1, 8, 0, winterNoonUtc))
    }

    @Test
    fun `a picker's starting time and the days ahead are Norway's`() {
        assertEquals(LocalTime(13, 0), localTimeOfDay(winterNoonUtc))
        // From 23:00 to 00:30 in Norway is a day on: tomorrow there, still today in UTC or New York.
        val now = Instant.parse("2026-11-13T22:00:00Z").toEpochMilliseconds()
        assertEquals(1, calendarDaysFrom(now, Instant.parse("2026-11-13T23:30:00Z").toEpochMilliseconds()))
    }

    @Test
    fun `only a clock reading otherwise differs from Norway time`() {
        assertTrue(differsFromNorwayTime(winterNoonUtc, TimeZone.of("America/New_York")))
        assertTrue(differsFromNorwayTime(winterNoonUtc, TimeZone.UTC))
        assertFalse(differsFromNorwayTime(winterNoonUtc, TimeZone.of("Europe/Oslo")))
        assertFalse(differsFromNorwayTime(winterNoonUtc, TimeZone.of("Europe/Berlin")))
    }

    @Test
    fun `daylight saving changing on other dates is a difference only while it lasts`() {
        // London is an hour behind all year, so it always differs.
        assertTrue(differsFromNorwayTime(summerNoonUtc, TimeZone.of("Europe/London")))
        // Lagos (UTC+1, no summer time) matches Norway in winter only.
        assertFalse(differsFromNorwayTime(winterNoonUtc, TimeZone.of("Africa/Lagos")))
        assertTrue(differsFromNorwayTime(summerNoonUtc, TimeZone.of("Africa/Lagos")))
    }
}

package com.example.pulsetransit.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTimeTest {
    private val oslo = TimeZone.of("Europe/Oslo")

    private fun at(
        day: Int,
        hour: Int,
        minute: Int,
    ) = LocalDateTime(2026, 11, day, hour, minute).toInstant(oslo).toEpochMilliseconds()

    @Test
    fun `a time still ahead today is today`() {
        assertEquals(at(3, 18, 30), nextTimeOfDay(18, 30, at(3, 17, 0), oslo))
    }

    @Test
    fun `a time already past today is tomorrow`() {
        assertEquals(at(4, 8, 0), nextTimeOfDay(8, 0, at(3, 17, 0), oslo))
    }

    @Test
    fun `the current minute is tomorrow`() {
        assertEquals(at(4, 17, 0), nextTimeOfDay(17, 0, at(3, 17, 0), oslo))
    }

    @Test
    fun `an explicit day keeps a time that has passed on that day`() {
        assertEquals(at(3, 8, 0), timeOnDay(0, 8, 0, at(3, 17, 0), oslo))
        assertEquals(at(4, 8, 0), timeOnDay(1, 8, 0, at(3, 17, 0), oslo))
    }

    @Test
    fun `tomorrow late in the evening is the next calendar day`() {
        assertEquals(at(4, 0, 30), timeOnDay(1, 0, 30, at(3, 23, 50), oslo))
    }

    @Test
    fun `days are counted by the local calendar`() {
        assertEquals(0, calendarDaysFrom(at(3, 0, 5), at(3, 23, 55), oslo))
        assertEquals(1, calendarDaysFrom(at(3, 23, 55), at(4, 0, 5), oslo))
    }

    @Test
    fun `clock text is the padded local time`() {
        assertEquals("08:05", clockTimeText(at(3, 8, 5), oslo))
        assertEquals(LocalTime(8, 5), localTimeOfDay(at(3, 8, 5), oslo))
    }

    @Test
    fun `entur gets whole seconds in UTC`() {
        assertEquals("2026-11-03T07:30:00Z", enturDateTime(at(3, 8, 30) + 456))
    }

    @Test
    fun `only a chosen time can pass`() {
        assertFalse(TripTime.Now.hasPassed(Long.MAX_VALUE))
        assertTrue(TripTime.ArriveBy(1_000L).hasPassed(1_000L))
        assertFalse(TripTime.DepartAt(1_001L).hasPassed(1_000L))
    }

    @Test
    fun `a departure time reached has passed`() {
        assertTrue(TripTime.DepartAt(1_000L).hasPassed(1_000L))
    }

    @Test
    fun `the public helpers use Norway's time zone whatever the device's`() {
        val zone = oslo
        val now = at(3, 17, 0)
        assertEquals(nextTimeOfDay(8, 0, now, zone), nextTimeOfDay(8, 0, now))
        assertEquals(clockTimeText(now, zone), clockTimeText(now))
        assertEquals(calendarDaysFrom(now, now + 12 * 3_600_000L, zone), calendarDaysFrom(now, now + 12 * 3_600_000L))
        assertEquals(localTimeOfDay(now, zone), localTimeOfDay(now))
        assertEquals(timeOnDay(1, 8, 0, now, zone), timeOnDay(1, 8, 0, now))
    }
}

package com.example.pulsetransit.data

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestinationScheduleTest {
    // 2026-10-19 is a Monday.
    private fun monday(
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime(2026, 10, 19, hour, minute)

    private fun day(
        dayOfMonth: Int,
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime(2026, 10, dayOfMonth, hour, minute)

    private val weekdayMornings = DestinationSchedule(DestinationSchedule.WEEKDAYS, startMinute = 8 * 60, endMinute = 9 * 60)

    @Test
    fun `the window includes its start and excludes its end`() {
        assertFalse(weekdayMornings.isActive(monday(7, 59)))
        assertTrue(weekdayMornings.isActive(monday(8, 0)))
        assertTrue(weekdayMornings.isActive(monday(8, 59)))
        assertFalse(weekdayMornings.isActive(monday(9, 0)))
    }

    @Test
    fun `only the chosen days are active`() {
        assertTrue(weekdayMornings.isActive(day(23, 8, 30)), "Friday")
        assertFalse(weekdayMornings.isActive(day(24, 8, 30)), "Saturday")
        assertFalse(weekdayMornings.isActive(day(25, 8, 30)), "Sunday")
    }

    @Test
    fun `the default schedule is always active`() {
        val always = DestinationSchedule()
        assertTrue(always.isActive(monday(0, 0)))
        assertTrue(always.isActive(day(25, 23, 59)))
        assertFalse(always.endsNextDay)
    }

    @Test
    fun `a window past midnight belongs to the day it started on`() {
        val fridayNights = DestinationSchedule(setOf(5), startMinute = 22 * 60, endMinute = 2 * 60)
        assertTrue(fridayNights.endsNextDay)

        assertFalse(fridayNights.isActive(day(23, 21, 59)), "Friday before it starts")
        assertTrue(fridayNights.isActive(day(23, 23, 0)), "Friday night")
        assertTrue(fridayNights.isActive(day(24, 1, 59)), "early Saturday")
        assertFalse(fridayNights.isActive(day(24, 2, 0)), "Saturday once it ends")
        assertFalse(fridayNights.isActive(day(23, 1, 0)), "early Friday is Thursday's night")
        assertFalse(fridayNights.isActive(day(24, 23, 0)), "Saturday night")
    }

    @Test
    fun `a Sunday night window runs into Monday`() {
        val sundayNights = DestinationSchedule(setOf(7), startMinute = 23 * 60, endMinute = 60)

        assertTrue(sundayNights.isActive(monday(0, 30)))
    }

    @Test
    fun `days in a row of three or more read as a range`() {
        val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

        assertEquals("Mon–Fri", weekdayMornings.daysText { names.getValue(it) })
        assertEquals("Mon–Sun", DestinationSchedule().daysText { names.getValue(it) })
        assertEquals("Sat, Sun", DestinationSchedule(DestinationSchedule.WEEKEND).daysText { names.getValue(it) })
        assertEquals("Mon, Wed–Fri, Sun", DestinationSchedule(setOf(7, 1, 3, 4, 5)).daysText { names.getValue(it) })
    }

    @Test
    fun `out of range values are rejected`() {
        assertFailsWith<IllegalArgumentException> { DestinationSchedule(setOf(0)) }
        assertFailsWith<IllegalArgumentException> { DestinationSchedule(startMinute = 24 * 60) }
        assertFailsWith<IllegalArgumentException> { DestinationSchedule(endMinute = 0) }
    }

    @Test
    fun `a schedule round-trips through JSON`() {
        val json = Json.encodeToString(DestinationSchedule.serializer(), weekdayMornings)

        assertEquals(weekdayMornings, Json.decodeFromString(DestinationSchedule.serializer(), json))
    }
}

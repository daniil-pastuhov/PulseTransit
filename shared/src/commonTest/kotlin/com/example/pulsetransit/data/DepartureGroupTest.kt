package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DepartureGroupTest {
    private fun group(size: Int) =
        DepartureGroup(
            List(size) {
                Departure(
                    line = "31",
                    destination = "Tonsenhagen",
                    expectedTime = "2026-07-15T10:${it.toString().padStart(2, '0')}:00+02:00",
                    aimedTime = "2026-07-15T10:00:00+02:00",
                    isRealtime = true,
                )
            },
        )

    @Test
    fun `a short group shows every departure and hides none`() {
        val group = group(3)
        assertEquals(group.departures, group.visibleDepartures)
        assertEquals(0, group.hiddenDepartureCount)
    }

    @Test
    fun `a group of exactly five shows all five`() {
        val group = group(5)
        assertEquals(5, group.visibleDepartures.size)
        assertEquals(0, group.hiddenDepartureCount)
    }

    @Test
    fun `a long group shows the next five in order and counts the rest`() {
        val group = group(45)
        assertEquals(group.departures.take(5), group.visibleDepartures)
        assertEquals(40, group.hiddenDepartureCount)
    }
}

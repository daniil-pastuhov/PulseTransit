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

    private fun departure(
        line: String,
        destination: String,
        minute: Int,
    ) = Departure(
        line = line,
        destination = destination,
        expectedTime = "2026-07-15T10:${minute.toString().padStart(2, '0')}:00+02:00",
        aimedTime = "2026-07-15T10:${minute.toString().padStart(2, '0')}:00+02:00",
        isRealtime = true,
    )

    @Test
    fun `details gather a line's departures from every group at the stop in order`() {
        val brenna1 = departure("73", "Brenna", 1)
        val mortensrud = departure("73", "Mortensrud", 5)
        val brenna2 = departure("73", "Brenna", 10)
        val otherLine = departure("81", "Brenna", 12)
        val brenna3 = departure("73", "Brenna", 20)
        val groups =
            listOf(
                DepartureGroup(listOf(brenna1)),
                DepartureGroup(listOf(mortensrud)),
                DepartureGroup(listOf(brenna2)),
                DepartureGroup(listOf(otherLine)),
                DepartureGroup(listOf(brenna3)),
            )
        assertEquals(listOf(brenna1, brenna2, brenna3), groups[2].departuresLike(groups))
    }

    @Test
    fun `details fall back to the group's own departures once a refresh has dropped them`() {
        val gone = DepartureGroup(listOf(departure("73", "Brenna", 1)))
        val current = listOf(DepartureGroup(listOf(departure("81", "Filipstad", 3))))
        assertEquals(gone.departures, gone.departuresLike(current))
    }
}

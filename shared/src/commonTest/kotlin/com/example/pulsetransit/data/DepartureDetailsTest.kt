package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DepartureDetailsTest {
    private fun at(
        hour: Int,
        minute: Int,
    ) = "2026-07-15T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00+02:00"

    private fun departure(
        time: String,
        quayId: String? = "NSR:Quay:2",
        isCancelled: Boolean = false,
    ) = Departure(
        line = "73",
        destination = "Holmlia stasjon",
        expectedTime = time,
        aimedTime = time,
        isRealtime = true,
        isCancelled = isCancelled,
        quayId = quayId,
    )

    private val trip =
        listOf(
            JourneyStop("NSR:Quay:1", "NSR:StopPlace:1", "Mortensrud T", at(20, 29)),
            JourneyStop("NSR:Quay:2", "NSR:StopPlace:2", "Kantarellen legesenter", at(20, 32)),
            JourneyStop("NSR:Quay:3", "NSR:StopPlace:3", "Holmlia stasjon", at(20, 42)),
        )

    @Test
    fun `the stops ahead start at the departure's own quay`() {
        assertEquals(trip.drop(1), trip.stopsAhead(departure(at(20, 32)), "NSR:StopPlace:2"))
    }

    @Test
    fun `without a matching quay the stops ahead start at the stop place`() {
        assertEquals(trip.drop(1), trip.stopsAhead(departure(at(20, 32), quayId = "NSR:Quay:99"), "NSR:StopPlace:2"))
    }

    @Test
    fun `a trip that never calls here is shown whole rather than not at all`() {
        assertEquals(trip, trip.stopsAhead(departure(at(20, 32), quayId = null), "NSR:StopPlace:99"))
    }

    @Test
    fun `minutes between two stops`() {
        assertEquals(10L, minutesBetween(at(20, 32), at(20, 42)))
        assertNull(minutesBetween("soon", at(20, 42)))
    }

    @Test
    fun `minutes until a departure never go below zero`() {
        val now = 1_784_102_400_000L // 2026-07-15T10:00:00+02:00
        assertEquals(75L, minutesUntil(at(11, 15), now))
        assertEquals(0L, minutesUntil(at(9, 50), now))
    }

    @Test
    fun `the headway is the median gap between departures that run`() {
        val departures =
            listOf(
                departure(at(20, 2)),
                departure(at(20, 17)),
                departure(at(20, 25), isCancelled = true),
                departure(at(20, 32)),
                departure(at(21, 2)),
            )
        assertEquals(15, typicalHeadwayMinutes(departures))
    }

    @Test
    fun `too few departures have no headway`() {
        assertNull(typicalHeadwayMinutes(listOf(departure(at(20, 2)), departure(at(20, 17)))))
    }
}

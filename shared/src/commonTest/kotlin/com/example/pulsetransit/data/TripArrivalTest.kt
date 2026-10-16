package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripArrivalTest {
    private fun trip(
        start: String,
        end: String?,
    ) = TripInfo(
        destinationName = "Majorstuen",
        expectedStartTime = start,
        expectedEndTime = end,
        lineName = "2",
        durationMinutes = 42,
        originName = "Oslo S",
    )

    @Test
    fun `arrival is the local wall time Entur wrote`() {
        assertEquals(
            TripArrival("18:31", 0),
            tripArrival(trip("2026-07-15T17:49:00+02:00", "2026-07-15T18:31:00+02:00")),
        )
    }

    @Test
    fun `arrival after midnight is a day after departure`() {
        assertEquals(
            TripArrival("00:12", 1),
            tripArrival(trip("2026-07-15T23:40:00+02:00", "2026-07-16T00:12:00+02:00")),
        )
    }

    @Test
    fun `no arrival when Entur gave none or it is unreadable`() {
        assertNull(tripArrival(trip("2026-07-15T17:49:00+02:00", null)))
        assertNull(tripArrival(trip("2026-07-15T17:49:00+02:00", "")))
        assertNull(tripArrival(trip("2026-07-15T17:49:00+02:00", "not a timestamp")))
    }

    @Test
    fun `an unreadable departure still shows the arrival`() {
        assertEquals(TripArrival("18:31", 0), tripArrival(trip("", "2026-07-15T18:31:00+02:00")))
    }
}

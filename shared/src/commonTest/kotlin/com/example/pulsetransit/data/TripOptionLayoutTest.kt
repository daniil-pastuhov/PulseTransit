package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TripOptionLayoutTest {
    @Test
    fun `auto fits as many rows as the space allows per destination`() {
        // 600 high, two destinations: 300 each, less a 40 header, leaves room for 4 rows of 60.
        assertEquals(4, autoTripOptionCount(600f, destinationCount = 2, rowHeight = 60f, headerHeight = 40f))
    }

    @Test
    fun `auto shows at least one and at most the maximum`() {
        assertEquals(1, autoTripOptionCount(100f, destinationCount = 5, rowHeight = 60f, headerHeight = 40f))
        assertEquals(
            SettingsRepository.MAX_TRIP_OPTIONS,
            autoTripOptionCount(5_000f, destinationCount = 1, rowHeight = 60f, headerHeight = 40f),
        )
    }

    @Test
    fun `a fixed setting wins over auto`() {
        assertEquals(2, visibleTripOptionCount(setting = 2, autoCount = 5))
        assertEquals(5, visibleTripOptionCount(setting = SettingsRepository.TRIP_OPTIONS_AUTO, autoCount = 5))
    }

    @Test
    fun `distance between coordinates is in metres`() {
        // A thousandth of a degree of latitude is about 111 m anywhere.
        val metres = distanceMeters(59.9139, 10.7522, 59.9149, 10.7522)
        assertTrue(metres in 110.0..112.0, "was $metres")
        assertEquals(0.0, distanceMeters(59.9, 10.7, 59.9, 10.7))
    }
}

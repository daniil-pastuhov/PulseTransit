package com.example.pulsetransit.data.journey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings' count of journey alerts, which leaves out the kinds the chosen mode cannot send. */
class JourneyAlertCountTest {
    @Test
    fun `following by time leaves the missed ride out of the count`() {
        assertEquals(JourneyAlertCount(6, 6), journeyAlertCount(JourneyTrackingMode.TIME_ONLY, emptySet()))
        // Turning the missed ride off changes nothing it could send.
        assertEquals(JourneyAlertCount(6, 6), journeyAlertCount(JourneyTrackingMode.TIME_ONLY, setOf(JourneyAlertKind.MISSED)))
    }

    @Test
    fun `following by location counts every kind`() {
        assertEquals(JourneyAlertCount(7, 7), journeyAlertCount(JourneyTrackingMode.LOCATION, emptySet()))
        assertEquals(
            JourneyAlertCount(4, 7),
            journeyAlertCount(
                JourneyTrackingMode.LOCATION,
                setOf(JourneyAlertKind.WALK, JourneyAlertKind.FASTER_ROUTE, JourneyAlertKind.ARRIVED),
            ),
        )
    }

    @Test
    fun `with no mode chosen yet only time alerts apply`() {
        assertEquals(JourneyAlertCount(5, 6), journeyAlertCount(null, setOf(JourneyAlertKind.ARRIVED)))
        assertFalse(JourneyAlertKind.MISSED.appliesTo(null))
    }

    @Test
    fun `all means every applicable kind is on`() {
        assertTrue(JourneyAlertCount(6, 6).isAll)
        assertFalse(JourneyAlertCount(5, 6).isAll)
    }
}

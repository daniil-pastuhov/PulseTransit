package com.example.pulsetransit.data

import com.example.pulsetransit.data.TimeSpanUnit.DAYS
import com.example.pulsetransit.data.TimeSpanUnit.HOURS
import com.example.pulsetransit.data.TimeSpanUnit.MINUTES
import com.example.pulsetransit.data.TimeSpanUnit.MONTHS
import com.example.pulsetransit.data.TimeSpanUnit.YEARS
import kotlin.test.Test
import kotlin.test.assertEquals

class TripDurationTest {
    private fun parts(vararg pairs: Pair<TimeSpanUnit, Int>) = pairs.map { (unit, value) -> TimeSpanPart(unit, value) }

    @Test
    fun `under an hour stays in minutes`() {
        assertEquals(parts(MINUTES to 51), tripDurationParts(51))
        assertEquals(parts(MINUTES to 59), tripDurationParts(59))
    }

    @Test
    fun `zero and negative read as zero minutes`() {
        assertEquals(parts(MINUTES to 0), tripDurationParts(0))
        assertEquals(parts(MINUTES to 0), tripDurationParts(-5))
    }

    @Test
    fun `hours carry their minutes`() {
        assertEquals(parts(HOURS to 17, MINUTES to 23), tripDurationParts(1043))
        assertEquals(parts(HOURS to 1), tripDurationParts(60))
    }

    @Test
    fun `days carry hours but not minutes`() {
        assertEquals(parts(DAYS to 1, HOURS to 2), tripDurationParts(26 * 60 + 30))
        assertEquals(parts(DAYS to 1), tripDurationParts(24 * 60 + 5))
    }

    @Test
    fun `months and years use 30 and 365 days`() {
        assertEquals(parts(MONTHS to 2, DAYS to 3), tripDurationParts(63 * 24 * 60))
        assertEquals(parts(YEARS to 1, MONTHS to 1), tripDurationParts(400 * 24 * 60))
        assertEquals(parts(YEARS to 2), tripDurationParts(2 * 365 * 24 * 60 + 90))
    }
}

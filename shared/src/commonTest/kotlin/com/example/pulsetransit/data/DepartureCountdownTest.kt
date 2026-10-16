package com.example.pulsetransit.data

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DepartureCountdownTest {
    private val oslo = TimeZone.of("Europe/Oslo")

    // 2026-07-15T10:00:00+02:00
    private val now = 1_784_102_400_000L
    private val minute = 60_000L

    private fun at(offsetMillis: Long) = isoOslo(now + offsetMillis)

    @Test
    fun `anything under fifteen seconds away is leaving now`() {
        assertEquals(DepartureCountdown.Now, departureCountdown(at(14_000), now, oslo))
        assertEquals(DepartureCountdown.Now, departureCountdown(at(-2 * minute), now, oslo))
    }

    @Test
    fun `under a minute rounds up to one rather than down to zero`() {
        assertEquals(DepartureCountdown.Minutes(1), departureCountdown(at(15_000), now, oslo))
        assertEquals(DepartureCountdown.Minutes(1), departureCountdown(at(59_000), now, oslo))
    }

    @Test
    fun `whole minutes until the half-hour cutoff`() {
        assertEquals(DepartureCountdown.Minutes(5), departureCountdown(at(5 * minute + 30_000), now, oslo))
        assertEquals(DepartureCountdown.Minutes(29), departureCountdown(at(30 * minute - 1_000), now, oslo))
    }

    @Test
    fun `from half an hour out it is a clock time with a day offset`() {
        assertEquals(DepartureCountdown.Clock(now + 30 * minute, 0), departureCountdown(at(30 * minute), now, oslo))
        // 10:00 + 15 h is 01:00 the next day in Oslo.
        assertEquals(DepartureCountdown.Clock(now + 15 * 60 * minute, 1), departureCountdown(at(15 * 60 * minute), now, oslo))
    }

    @Test
    fun `an unreadable timestamp is unknown rather than a crash`() {
        assertEquals(DepartureCountdown.Unknown, departureCountdown("tomorrow-ish", now, oslo))
    }

    @Test
    fun `a departure's clock is a wall-clock time even when it is minutes away`() {
        assertEquals(DepartureCountdown.Clock(now + 5 * minute, 0), departureClock(at(5 * minute), now, oslo))
        assertEquals(DepartureCountdown.Clock(now + 15 * 60 * minute, 1), departureClock(at(15 * 60 * minute), now, oslo))
    }

    @Test
    fun `an unreadable timestamp has no clock`() {
        assertEquals(null, departureClock("tomorrow-ish", now, oslo))
    }
}

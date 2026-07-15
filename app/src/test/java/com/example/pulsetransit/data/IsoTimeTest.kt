package com.example.pulsetransit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoTimeTest {

    @Test
    fun `parses Entur timestamp with positive offset`() {
        // 2026-07-15T10:05:00+02:00 == 08:05:00Z
        assertEquals(1_784_102_700_000L, parseIsoToMillis("2026-07-15T10:05:00+02:00"))
    }

    @Test
    fun `parses MET timestamp with Z suffix`() {
        assertEquals(1_784_102_700_000L, parseIsoToMillis("2026-07-15T08:05:00Z"))
    }

    @Test
    fun `same instant in different offsets parses identically`() {
        assertEquals(
            parseIsoToMillis("2026-07-15T08:05:00Z"),
            parseIsoToMillis("2026-07-15T10:05:00+02:00")
        )
    }

    @Test
    fun `parses negative offset`() {
        assertEquals(
            parseIsoToMillis("2026-07-15T08:05:00Z"),
            parseIsoToMillis("2026-07-15T04:05:00-04:00")
        )
    }

    @Test
    fun `returns null rather than throwing on unparseable input`() {
        assertNull(parseIsoToMillis(""))
        assertNull(parseIsoToMillis("not a timestamp"))
        assertNull(parseIsoToMillis("2026-07-15"))
    }
}

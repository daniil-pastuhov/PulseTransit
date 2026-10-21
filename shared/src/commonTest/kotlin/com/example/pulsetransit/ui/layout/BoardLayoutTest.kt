package com.example.pulsetransit.ui.layout

import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardLayoutTest {
    @Test
    fun `a phone in portrait stacks its stops as before`() {
        assertEquals(1, BoardLayout.columns(2, 400f, 600f))
        assertEquals(1, BoardLayout.columns(4, 400f, 600f))
    }

    @Test
    fun `a phone in landscape puts its stops side by side as before`() {
        assertEquals(2, BoardLayout.columns(2, 800f, 300f))
        assertEquals(3, BoardLayout.columns(3, 800f, 300f))
        assertEquals(4, BoardLayout.columns(4, 800f, 300f))
    }

    @Test
    fun `a tablet in portrait puts stops side by side`() {
        // A 13-inch iPad's board area.
        assertEquals(2, BoardLayout.columns(2, 1020f, 1150f))
        assertEquals(3, BoardLayout.columns(3, 1020f, 1150f))
    }

    @Test
    fun `a panel too narrow for its destinations wraps to another row`() {
        // A Pixel Tablet in portrait: three across would cut the destinations short.
        assertEquals(2, BoardLayout.columns(3, 788f, 1000f))
    }

    @Test
    fun `rows come out even`() {
        // Three fit side by side on a 13-inch iPad, so four and five split over two rows.
        assertEquals(2, BoardLayout.columns(4, 1020f, 1150f))
        assertEquals(3, BoardLayout.columns(5, 1020f, 1150f))
    }

    @Test
    fun `one stop always takes the whole board`() {
        assertEquals(1, BoardLayout.columns(1, 1400f, 800f))
        assertEquals(1, BoardLayout.columns(0, 1400f, 800f))
    }

    @Test
    fun `uneven stops leave the first stop the tallest panel`() {
        assertEquals(listOf(1, 2), BoardLayout.columnSizes(3, 2))
        assertEquals(listOf(1, 2, 2), BoardLayout.columnSizes(5, 3))
        assertEquals(listOf(2, 2), BoardLayout.columnSizes(4, 2))
    }

    @Test
    fun `one column stacks every stop and one row gives each its own column`() {
        assertEquals(listOf(3), BoardLayout.columnSizes(3, 1))
        assertEquals(listOf(1, 1, 1), BoardLayout.columnSizes(3, 3))
        assertEquals(emptyList(), BoardLayout.columnSizes(0, 2))
    }

    @Test
    fun `wide rows start at the breakpoint`() {
        assertFalse(BoardLayout.isWideRow(439f))
        assertTrue(BoardLayout.isWideRow(440f))
    }

    @Test
    fun `row capacity counts whole rows only`() {
        assertEquals(0, BoardLayout.rowCapacity(0f))
        assertEquals(1, BoardLayout.rowCapacity(103f))
        assertEquals(20, BoardLayout.rowCapacity(1040f))
    }

    @Test
    fun `the platform shows only where departures leave from more than one`() {
        assertTrue(BoardLayout.showsPlatform(listOf(group("1"), group("2"))))
        assertFalse(BoardLayout.showsPlatform(listOf(group("A"), group("A"))))
        assertFalse(BoardLayout.showsPlatform(listOf(group(null), group(null))))
        assertFalse(BoardLayout.showsPlatform(listOf(group("A"), group(null))))
        assertFalse(BoardLayout.showsPlatform(emptyList()))
    }

    private fun group(platform: String?) =
        DepartureGroup(
            listOf(
                Departure(
                    line = "31",
                    destination = "Snarøya",
                    expectedTime = "2026-10-21T12:00:00+02:00",
                    aimedTime = "2026-10-21T12:00:00+02:00",
                    isRealtime = true,
                    platform = platform,
                ),
            ),
        )
}

package com.example.pulsetransit.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WrappedRowsTest {
    private fun arrange(
        widths: List<Float>,
        maxWidth: Float = 100f,
        maxLines: Int = 2,
        markerWidth: Float = 20f,
    ) = WrappedRows.arrange(widths, maxWidth, spacing = 5f, maxLines = maxLines, markerWidth = markerWidth)

    @Test
    fun `a chain that fits stays on one row`() {
        assertEquals(WrappedRows.Arrangement(listOf(3), hidden = 0), arrange(listOf(30f, 30f, 30f)))
    }

    @Test
    fun `the spacing counts towards a row's width`() {
        // 30 + 5 + 30 + 5 + 31 = 101, one more than the row holds.
        assertEquals(listOf(2, 3), arrange(listOf(30f, 30f, 31f)).rowEnds)
    }

    @Test
    fun `a chain wraps onto a second row without hiding anything`() {
        val arrangement = arrange(listOf(40f, 40f, 40f, 40f))
        assertEquals(listOf(2, 4), arrangement.rowEnds)
        assertEquals(0, arrangement.hidden)
    }

    @Test
    fun `beyond the last row the end of the chain is hidden and the marker gets room`() {
        // Rows of two 40s; the third row is cut, and the second row drops an item for the marker.
        val arrangement = arrange(listOf(40f, 40f, 40f, 40f, 40f, 40f))
        assertEquals(listOf(2, 3), arrangement.rowEnds)
        assertEquals(3, arrangement.hidden)
        assertEquals(3, arrangement.shown)
    }

    @Test
    fun `a narrow marker keeps the whole last row`() {
        // 40 + 5 + 40 + 5 + 10 = 100 fits exactly.
        val arrangement = arrange(listOf(40f, 40f, 40f, 40f, 40f), markerWidth = 10f)
        assertEquals(listOf(2, 4), arrangement.rowEnds)
        assertEquals(1, arrangement.hidden)
    }

    @Test
    fun `the gap before the marker counts towards the last row`() {
        // 40 + 5 + 40 + 5 + 12 = 102: the marker alone would fit (97), with its gap it doesn't.
        val arrangement = arrange(listOf(40f, 40f, 40f, 40f, 40f), markerWidth = 12f)
        assertEquals(listOf(2, 3), arrangement.rowEnds)
        assertEquals(2, arrangement.hidden)
    }

    @Test
    fun `a last row too full for the marker leaves only the marker`() {
        val arrangement = arrange(listOf(100f, 100f, 100f), markerWidth = 20f)
        assertEquals(listOf(1, 1), arrangement.rowEnds)
        assertEquals(2, arrangement.hidden)
    }

    @Test
    fun `one line shows the start of the chain and the marker`() {
        val arrangement = arrange(listOf(30f, 30f, 30f, 30f), maxLines = 1)
        assertEquals(listOf(2), arrangement.rowEnds)
        assertEquals(2, arrangement.hidden)
    }

    @Test
    fun `an item as wide as the row takes a row of its own`() {
        assertEquals(listOf(1, 2, 3), arrange(listOf(30f, 100f, 30f), maxLines = 3).rowEnds)
    }

    @Test
    fun `no items make no rows`() {
        assertEquals(WrappedRows.Arrangement(emptyList(), hidden = 0), arrange(emptyList()))
    }

    @Test
    fun `an unlimited chain is never cut`() {
        val arrangement = arrange(List(20) { 40f }, maxLines = Int.MAX_VALUE)
        assertEquals(10, arrangement.rowEnds.size)
        assertEquals(0, arrangement.hidden)
    }

    @Test
    fun `the double overload for Swift arranges the same`() {
        val doubles =
            WrappedRows.arrange(doubleArrayOf(40.0, 40.0, 40.0, 40.0, 40.0, 40.0), 100.0, 5.0, maxLines = 2, markerWidth = 20.0)
        assertEquals(arrange(listOf(40f, 40f, 40f, 40f, 40f, 40f)), doubles)
    }

    @Test
    fun `zero lines is a mistake`() {
        assertFailsWith<IllegalArgumentException> { arrange(listOf(10f), maxLines = 0) }
    }
}

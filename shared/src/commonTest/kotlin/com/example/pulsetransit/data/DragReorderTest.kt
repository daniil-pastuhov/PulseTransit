package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Where a dragged row lands in Settings' reorderable lists. */
class DragReorderTest {
    private val heights = listOf(100, 100, 200, 100)

    @Test
    fun `a row moves past a neighbour once it covers more than half of it`() {
        assertEquals(0, dropIndex(0, 50f, heights))
        assertEquals(1, dropIndex(0, 51f, heights))
        assertEquals(1, dropIndex(0, 199f, heights))
        // The third row is taller: it takes more than half of its 200 px to pass it.
        assertEquals(2, dropIndex(0, 201f, heights))
    }

    @Test
    fun `dragging up moves towards the start`() {
        assertEquals(3, dropIndex(3, -99f, heights))
        assertEquals(2, dropIndex(3, -101f, heights))
        assertEquals(0, dropIndex(3, -1000f, heights))
    }

    @Test
    fun `the ends stop the row`() {
        assertEquals(3, dropIndex(0, 10_000f, heights))
        assertEquals(0, dropIndex(0, -10f, heights))
    }
}

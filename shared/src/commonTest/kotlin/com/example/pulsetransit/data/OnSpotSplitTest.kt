package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnSpotSplitTest {
    @Test
    fun `an open split gives the routes their share`() {
        assertEquals(600f, OnSpotSplit.routesSize(0.6f, totalSize = 1000f, barSize = 56f, minSize = 120f))
    }

    @Test
    fun `a folded section keeps only its bar`() {
        assertEquals(56f, OnSpotSplit.routesSize(OnSpotSplit.ROUTES_COLLAPSED, totalSize = 1000f, barSize = 56f, minSize = 120f))
        assertEquals(944f, OnSpotSplit.routesSize(OnSpotSplit.STOP_COLLAPSED, totalSize = 1000f, barSize = 56f, minSize = 120f))
    }

    @Test
    fun `an open section is never shown below its minimum`() {
        // 0.9 of a short landscape board would leave the stop 40 high.
        assertEquals(280f, OnSpotSplit.routesSize(0.9f, totalSize = 400f, barSize = 56f, minSize = 120f))
        assertEquals(120f, OnSpotSplit.routesSize(0.05f, totalSize = 400f, barSize = 56f, minSize = 120f))
    }

    @Test
    fun `a board too short for two minimums still opens both sections`() {
        // 300 long: the minimum drops to a third, so the default split shows both, not two bars.
        val min = OnSpotSplit.minSectionSize(totalSize = 300f, minSize = 240f)
        assertEquals(100f, min)
        val routes = OnSpotSplit.routesSize(OnSpotSplit.DEFAULT, totalSize = 300f, barSize = 56f, minSize = 240f)
        assertTrue(routes >= min && 300f - routes >= min)
        assertEquals(0.5f, OnSpotSplit.settle(150f, totalSize = 300f, minSize = 240f))
        assertEquals(200f, OnSpotSplit.routesSize(0.9f, totalSize = 300f, barSize = 56f, minSize = 240f))
    }

    @Test
    fun `dragging stops at either bar`() {
        assertEquals(650f, OnSpotSplit.dragRoutesSize(600f, delta = 50f, totalSize = 1000f, barSize = 56f))
        assertEquals(56f, OnSpotSplit.dragRoutesSize(100f, delta = -500f, totalSize = 1000f, barSize = 56f))
        assertEquals(944f, OnSpotSplit.dragRoutesSize(900f, delta = 500f, totalSize = 1000f, barSize = 56f))
    }

    @Test
    fun `a drop leaving a section below its minimum folds it`() {
        assertEquals(OnSpotSplit.ROUTES_COLLAPSED, OnSpotSplit.settle(100f, totalSize = 1000f, minSize = 120f))
        assertEquals(OnSpotSplit.STOP_COLLAPSED, OnSpotSplit.settle(900f, totalSize = 1000f, minSize = 120f))
        assertEquals(0.5f, OnSpotSplit.settle(500f, totalSize = 1000f, minSize = 120f))
        // Exactly the minimum is still open.
        assertEquals(0.12f, OnSpotSplit.settle(120f, totalSize = 1000f, minSize = 120f))
    }

    @Test
    fun `a stored split out of range is made valid`() {
        assertEquals(OnSpotSplit.DEFAULT, OnSpotSplit.sanitize(Float.NaN))
        assertEquals(OnSpotSplit.STOP_COLLAPSED, OnSpotSplit.sanitize(3f))
        assertEquals(OnSpotSplit.ROUTES_COLLAPSED, OnSpotSplit.sanitize(-1f))
    }
}

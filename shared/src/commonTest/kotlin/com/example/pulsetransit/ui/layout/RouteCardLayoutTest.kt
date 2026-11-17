package com.example.pulsetransit.ui.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteCardLayoutTest {
    @Test
    fun `a card's size class follows its own width`() {
        assertEquals(RouteCardSize.COMPACT, RouteCardLayout.size(400f, 1f))
        assertEquals(RouteCardSize.REGULAR, RouteCardLayout.size(540f, 1f))
        assertEquals(RouteCardSize.WIDE, RouteCardLayout.size(860f, 1f))
        assertEquals(RouteCardSize.WALL, RouteCardLayout.size(1400f, 1f))
    }

    @Test
    fun `larger text steps a card down a class`() {
        assertEquals(RouteCardSize.REGULAR, RouteCardLayout.size(900f, 1.5f))
        assertEquals(RouteCardSize.COMPACT, RouteCardLayout.size(796f, 1.5f))
        // Smaller text never steps a card up.
        assertEquals(RouteCardSize.COMPACT, RouteCardLayout.size(500f, 0.85f))
    }

    @Test
    fun `only the wide classes keep their next departures beside the journey`() {
        assertFalse(RouteCardSize.COMPACT.nextInHead)
        assertFalse(RouteCardSize.REGULAR.nextInHead)
        assertTrue(RouteCardSize.WIDE.nextInHead)
        assertTrue(RouteCardSize.WALL.nextInHead)
        assertFalse(RouteCardSize.COMPACT.hasTimeline)
        assertTrue(RouteCardSize.REGULAR.hasTimeline)
    }

    @Test
    fun `a narrow compact card is under 360 effective dp`() {
        assertTrue(RouteCardLayout.isNarrow(359f, 1f))
        assertFalse(RouteCardLayout.isNarrow(360f, 1f))
        assertTrue(RouteCardLayout.isNarrow(500f, 1.5f))
    }

    @Test
    fun `cards fill a grid row and the rows come out even`() {
        assertEquals(1, RouteCardLayout.columns(1, 1920f, 12f, 1f))
        // A phone: one card a row.
        assertEquals(1, RouteCardLayout.columns(3, 376f, 12f, 1f))
        // Three fit across a portrait tablet in two columns: two and one.
        assertEquals(2, RouteCardLayout.columns(3, 796f, 12f, 1f))
        // Five that fit three across: three and two.
        assertEquals(3, RouteCardLayout.columns(5, 1132f, 12f, 1f))
        // A TV fits all five in a row.
        assertEquals(5, RouteCardLayout.columns(5, 1872f, 12f, 1f))
    }

    @Test
    fun `larger text fits fewer cards across`() {
        assertEquals(3, RouteCardLayout.columns(3, 912f, 12f, 1f))
        assertEquals(2, RouteCardLayout.columns(3, 912f, 12f, 1.5f))
    }

    @Test
    fun `a short last row's cards share its width`() {
        assertEquals(listOf(392f, 392f, 796f), RouteCardLayout.cardWidths(3, 2, 796f, 12f))
    }

    @Test
    fun `a note band stays only beside a card with a band`() {
        val own = listOf(RouteCardFooter.TOGGLE, RouteCardFooter.NOTE, RouteCardFooter.NOTE, RouteCardFooter.NONE)
        assertEquals(
            listOf(RouteCardFooter.TOGGLE, RouteCardFooter.NOTE, RouteCardFooter.NONE, RouteCardFooter.NONE),
            RouteCardBands.footers(own, columns = 2),
        )
        assertEquals(listOf(RouteCardFooter.NONE), RouteCardBands.footers(listOf(RouteCardFooter.NOTE), columns = 1))
        assertEquals(
            listOf(RouteCardFooter.ACTIONS, RouteCardFooter.NOTE),
            RouteCardBands.footers(listOf(RouteCardFooter.ACTIONS, RouteCardFooter.NOTE), columns = 2),
        )
    }

    @Test
    fun `the rail density is tried only when a card has a band to leave out`() {
        assertEquals(RouteCardDensity.entries, RouteCardBands.densities(listOf(RouteCardFooter.TOGGLE)))
        assertEquals(
            listOf(RouteCardDensity.FULL, RouteCardDensity.SHORT, RouteCardDensity.LINE),
            RouteCardBands.densities(listOf(RouteCardFooter.NONE, RouteCardFooter.ACTIONS)),
        )
    }

    @Test
    fun `toggle and note bands show only at full density but the searched-for buttons until one line`() {
        RouteCardDensity.entries.forEach { density ->
            assertEquals(density == RouteCardDensity.FULL, RouteCardBands.showsBand(RouteCardFooter.TOGGLE, density))
            assertEquals(density == RouteCardDensity.FULL, RouteCardBands.showsBand(RouteCardFooter.NOTE, density))
            assertEquals(density != RouteCardDensity.LINE, RouteCardBands.showsBand(RouteCardFooter.ACTIONS, density))
            assertFalse(RouteCardBands.showsBand(RouteCardFooter.NONE, density))
        }
    }

    @Test
    fun `a card's alternatives stay reachable at every density`() {
        // The toggle bar at full density, the rail at every other.
        RouteCardDensity.entries.forEach { density ->
            val rail = RouteCardBands.hasRail(RouteCardFooter.TOGGLE, RouteCardSize.COMPACT, hasAlternatives = true, density = density)
            val band = RouteCardBands.showsBand(RouteCardFooter.TOGGLE, density)
            assertTrue(rail != band, "density $density")
        }
        // A wide card lists them beside its journey, and needs the rail only on one line.
        assertFalse(RouteCardBands.hasRail(RouteCardFooter.NONE, RouteCardSize.WIDE, hasAlternatives = true, density = RouteCardDensity.SHORT))
        assertTrue(RouteCardBands.hasRail(RouteCardFooter.NONE, RouteCardSize.WIDE, hasAlternatives = true, density = RouteCardDensity.LINE))
        assertFalse(RouteCardBands.hasRail(RouteCardFooter.NONE, RouteCardSize.WIDE, hasAlternatives = false, density = RouteCardDensity.LINE))
        assertFalse(RouteCardBands.hasRail(RouteCardFooter.NOTE, RouteCardSize.COMPACT, hasAlternatives = false, density = RouteCardDensity.LINE))
    }

    @Test
    fun `a grid row is as tall as its tallest card`() {
        assertEquals(listOf(150f, 60f), RouteCardLayout.rowHeights(listOf(100f, 150f, 60f), columns = 2))
        assertEquals(222f, RouteCardLayout.gridHeight(listOf(100f, 150f, 60f), columns = 2, gap = 12f))
        assertTrue(RouteCardLayout.fits(listOf(100f, 150f, 60f), columns = 2, gap = 12f, maxHeight = 222f))
        assertFalse(RouteCardLayout.fits(listOf(100f, 150f, 60f), columns = 2, gap = 12f, maxHeight = 221f))
    }

    @Test
    fun `a collapsed card beside an expanded one keeps the collapsed row's height`() {
        // Grid row 1: the second card expanded (its rows 200 tall); grid row 2: both collapsed.
        val heights =
            RouteCardLayout.cardHeights(
                natural = listOf(160f, 380f, 150f, 170f),
                collapsed = listOf(160f, 180f, 150f, 170f),
                hasRows = listOf(false, true, false, false),
                columns = 2,
            )
        assertEquals(listOf(180f, 380f, 170f, 170f), heights)
    }

    @Test
    fun `cards all expanded or all collapsed are as tall as their grid row`() {
        val expanded =
            RouteCardLayout.cardHeights(
                natural = listOf(300f, 380f),
                collapsed = listOf(160f, 180f),
                hasRows = listOf(true, true),
                columns = 2,
            )
        assertEquals(listOf(380f, 380f), expanded)
        val collapsed =
            RouteCardLayout.cardHeights(
                natural = listOf(160f, 180f),
                collapsed = listOf(160f, 180f),
                hasRows = listOf(false, false),
                columns = 2,
            )
        assertEquals(listOf(180f, 180f), collapsed)
    }

    @Test
    fun `a shorter card lines its journey up and gives the rest to its rows`() {
        val stretch = RouteCardLayout.stretch(head = 100f, rowHeadMax = 120f, rows = 2, rowsHeight = 100f, footer = 52f, rowHeight = 312f)
        assertEquals(CardStretch(headBand = 120f, rowExtra = 20f, footerBand = 52f), stretch)
    }

    @Test
    fun `without rows a shorter card's band grows`() {
        val stretch = RouteCardLayout.stretch(head = 100f, rowHeadMax = 130f, rows = 0, rowsHeight = 0f, footer = 52f, rowHeight = 300f)
        assertEquals(CardStretch(headBand = 130f, rowExtra = 0f, footerBand = 170f), stretch)
    }

    @Test
    fun `without rows or band a shorter card's journey takes the height`() {
        val stretch = RouteCardLayout.stretch(head = 80f, rowHeadMax = 100f, rows = 0, rowsHeight = 0f, footer = 0f, rowHeight = 160f)
        assertEquals(CardStretch(headBand = 160f, rowExtra = 0f, footerBand = 0f), stretch)
    }

    @Test
    fun `the tallest card is not stretched`() {
        val stretch = RouteCardLayout.stretch(head = 120f, rowHeadMax = 120f, rows = 0, rowsHeight = 0f, footer = 52f, rowHeight = 172f)
        assertEquals(CardStretch(headBand = 120f, rowExtra = 0f, footerBand = 52f), stretch)
    }

    @Test
    fun `segments share the width by minutes above their minimums`() {
        val widths = assertNotNull(RouteCardFitting.segmentWidths(listOf(10f, 10f), listOf(1f, 3f), available = 100f))
        assertEquals(25f, widths[0], 0.01f)
        assertEquals(75f, widths[1], 0.01f)
    }

    @Test
    fun `a segment never gets less than its minimum`() {
        val widths = assertNotNull(RouteCardFitting.segmentWidths(listOf(40f, 10f), listOf(1f, 9f), available = 100f))
        assertEquals(40f, widths[0], 0.01f)
        assertEquals(60f, widths[1], 0.01f)
    }

    @Test
    fun `a short route is not stretched across a wide card`() {
        val widths = assertNotNull(RouteCardFitting.segmentWidths(listOf(10f, 10f), listOf(2f, 10f), available = 1000f, maxPerWeight = 30f))
        assertEquals(60f, widths[0], 0.01f)
        assertEquals(300f, widths[1], 0.01f)
    }

    @Test
    fun `segments whose minimums do not fit have no widths`() {
        assertNull(RouteCardFitting.segmentWidths(listOf(60f, 60f), listOf(1f, 1f), available = 100f))
    }

    @Test
    fun `a ride's caption reaches under the walks after it`() {
        val captions =
            RouteCardFitting.captions(
                widths = listOf(50f, 30f, 50f),
                isRide = listOf(true, false, true),
                fullWidths = listOf(80f, 0f, 200f),
                shortWidths = listOf(60f, 0f, 45f),
                gap = 4f,
                minWidth = 40f,
            )
        // The first spans its pill and the walk, 84; the last only its own 50.
        assertEquals(listOf(CaptionChoice.FULL, CaptionChoice.NONE, CaptionChoice.SHORT), captions)
    }

    @Test
    fun `a caption that does not fit whole is left out rather than cut`() {
        val captions =
            RouteCardFitting.captions(
                widths = listOf(60f, 60f),
                isRide = listOf(true, true),
                fullWidths = listOf(120f, 120f),
                shortWidths = listOf(70f, 70f),
                gap = 4f,
                minWidth = 40f,
            )
        assertEquals(listOf(CaptionChoice.NONE, CaptionChoice.NONE), captions)
    }

    @Test
    fun `a timeline drops its walks' minutes before falling back to the chain`() {
        fun fit(available: Float) =
            RouteCardFitting.timeline(
                fullMinWidths = listOf(70f, 50f, 70f, 50f),
                bareMinWidths = listOf(30f, 50f, 30f, 50f),
                weights = listOf(4f, 6f, 3f, 9f),
                isRide = listOf(false, true, false, true),
                fullCaptionWidths = listOf(0f, 60f, 0f, 60f),
                shortCaptionWidths = listOf(0f, 40f, 0f, 40f),
                available = available,
                gap = 4f,
                maxPerWeight = Float.POSITIVE_INFINITY,
                withCaptions = true,
                captionMinWidth = 40f,
            )
        assertEquals(TimelineStyle.FULL, fit(400f).style)
        assertEquals(TimelineStyle.BARE_WALKS, fit(200f).style)
        assertEquals(TimelineStyle.CHAIN, fit(150f).style)
        assertTrue(fit(150f).widths.isEmpty())
    }

    @Test
    fun `a timeline that cannot name the first boarding stop gives way to the chain`() {
        val fit =
            RouteCardFitting.timeline(
                fullMinWidths = listOf(30f, 30f),
                bareMinWidths = listOf(30f, 30f),
                weights = listOf(1f, 1f),
                isRide = listOf(true, true),
                fullCaptionWidths = listOf(200f, 10f),
                shortCaptionWidths = listOf(150f, 10f),
                available = 100f,
                gap = 4f,
                maxPerWeight = Float.POSITIVE_INFINITY,
                withCaptions = true,
                captionMinWidth = 20f,
            )
        assertEquals(TimelineStyle.CHAIN, fit.style)
        // Without captions the same route is a timeline.
        val bare =
            RouteCardFitting.timeline(
                fullMinWidths = listOf(30f, 30f),
                bareMinWidths = listOf(30f, 30f),
                weights = listOf(1f, 1f),
                isRide = listOf(true, true),
                fullCaptionWidths = listOf(200f, 10f),
                shortCaptionWidths = listOf(150f, 10f),
                available = 100f,
                gap = 4f,
                maxPerWeight = Float.POSITIVE_INFINITY,
                withCaptions = false,
                captionMinWidth = 20f,
            )
        assertEquals(TimelineStyle.FULL, bare.style)
    }

    @Test
    fun `a chain drops its walks' minutes only to save a line`() {
        assertTrue(RouteCardFitting.chainShowsWalkMinutes(fullHeight = 40f, bareHeight = 40f))
        assertFalse(RouteCardFitting.chainShowsWalkMinutes(fullHeight = 80f, bareHeight = 40f))
    }

    @Test
    fun `next departures show in the richest detail they all fit`() {
        val widths = listOf(listOf(120f, 120f), listOf(80f, 80f), listOf(50f, 50f))
        assertEquals(NextEntriesFit(detail = 0, count = 2), RouteCardFitting.nextEntries(widths, gap = 10f, available = 250f))
        assertEquals(NextEntriesFit(detail = 1, count = 2), RouteCardFitting.nextEntries(widths, gap = 10f, available = 170f))
        assertEquals(NextEntriesFit(detail = 2, count = 1), RouteCardFitting.nextEntries(widths, gap = 10f, available = 100f))
        assertEquals(NextEntriesFit(detail = 2, count = 0), RouteCardFitting.nextEntries(widths, gap = 10f, available = 40f))
    }

    @Test
    fun `a wide card lists the next departures that fit its height`() {
        val headers = listOf(20f, 48f, 48f, 48f)
        val rows = listOf(48f, 48f, 48f)
        assertEquals(3, RouteCardFitting.nextBlockRows(headers, rows, maxHeight = 200f))
        // Short of one row the header turns into "+1 in details", a touch target.
        assertEquals(2, RouteCardFitting.nextBlockRows(headers, rows, maxHeight = 150f))
        assertEquals(0, RouteCardFitting.nextBlockRows(headers, rows, maxHeight = 40f))
    }

    @Test
    fun `a one-line card shows how soon only beside the whole title`() {
        assertTrue(RouteCardFitting.lineShowsSoon(clockWidth = 60f, titleWidth = 100f, soonWidth = 50f, gap = 10f, available = 230f))
        assertFalse(RouteCardFitting.lineShowsSoon(clockWidth = 60f, titleWidth = 100f, soonWidth = 50f, gap = 10f, available = 229f))
    }

    @Test
    fun `meta parts drop the duration then the arrival to keep their lines`() {
        val widths = listOf(60f, 50f, 70f)
        val order = listOf(1, 2)
        assertEquals(listOf(0, 1, 2), RouteCardFitting.metaParts(widths, gap = 4f, available = 200f, maxLines = 1, dropOrder = order))
        assertEquals(listOf(0, 2), RouteCardFitting.metaParts(widths, gap = 4f, available = 140f, maxLines = 1, dropOrder = order))
        assertEquals(listOf(0), RouteCardFitting.metaParts(widths, gap = 4f, available = 100f, maxLines = 1, dropOrder = order))
        assertEquals(listOf(0, 1, 2), RouteCardFitting.metaParts(widths, gap = 4f, available = 120f, maxLines = 2, dropOrder = order))
        // How soon it leaves is never dropped, even when it alone overflows.
        assertEquals(listOf(0), RouteCardFitting.metaParts(widths, gap = 4f, available = 30f, maxLines = 1, dropOrder = order))
    }

    @Test
    fun `parts wrap between them`() {
        assertEquals(0, RouteCardFitting.flowRows(emptyList(), gap = 4f, available = 100f).size)
        assertEquals(1, RouteCardFitting.flowRows(listOf(48f, 48f), gap = 4f, available = 100f).size)
        assertEquals(2, RouteCardFitting.flowRows(listOf(48f, 49f), gap = 4f, available = 100f).size)
        assertEquals(1, RouteCardFitting.flowRows(listOf(150f), gap = 4f, available = 100f).size)
    }

    @Test
    fun `a card's own band follows what it has`() {
        assertEquals(RouteCardFooter.ACTIONS, RouteCardBands.own(isSearched = true, hasRoute = false, size = RouteCardSize.WIDE, hasAlternatives = false))
        assertEquals(RouteCardFooter.NONE, RouteCardBands.own(isSearched = false, hasRoute = false, size = RouteCardSize.COMPACT, hasAlternatives = true))
        assertEquals(RouteCardFooter.NONE, RouteCardBands.own(isSearched = false, hasRoute = true, size = RouteCardSize.WIDE, hasAlternatives = true))
        assertEquals(RouteCardFooter.TOGGLE, RouteCardBands.own(isSearched = false, hasRoute = true, size = RouteCardSize.REGULAR, hasAlternatives = true))
        assertEquals(RouteCardFooter.NOTE, RouteCardBands.own(isSearched = false, hasRoute = true, size = RouteCardSize.COMPACT, hasAlternatives = false))
    }

    @Test
    fun `parts flow onto lines in order and a wide one has a line of its own`() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), RouteCardFitting.flowRows(listOf(40f, 40f, 40f), 10f, 100f))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), RouteCardFitting.flowRows(listOf(40f, 150f, 40f), 10f, 100f))
        assertEquals(emptyList(), RouteCardFitting.flowRows(emptyList(), 10f, 100f))
        assertEquals(2, RouteCardFitting.flowRows(listOf(40f, 40f, 40f), 10f, 100f).size)
    }
}

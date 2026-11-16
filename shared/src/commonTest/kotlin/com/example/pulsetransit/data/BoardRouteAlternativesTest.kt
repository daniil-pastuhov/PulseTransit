package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the board's route cards show besides the first route, and how many rows fit. */
class BoardRouteAlternativesTest {
    private val minute = 60 * 1000L
    private val now = nowMillis()

    private fun trip(
        startMinutes: Int,
        line: String,
        mode: String = "metro",
    ) = TripInfo(
        destinationName = "Majorstuen",
        expectedStartTime = isoOslo(now + startMinutes * minute),
        lineName = line,
        durationMinutes = 11,
        originName = "Jernbanetorget",
        legs = listOf(TripLeg(mode = "foot", durationMinutes = 3), TripLeg(mode = mode, lineName = line)),
    )

    @Test
    fun `alternatives are the routes after the first that have not left`() {
        val trips = listOf(trip(2, "2"), trip(-1, "3"), trip(6, "3"), trip(9, "5"))

        assertEquals(listOf("3", "5"), BoardRouteAlternatives.alternatives(trips, now).map { it.lineName })
        assertEquals(emptyList(), BoardRouteAlternatives.alternatives(listOf(trip(2, "2")), now))
        assertEquals(emptyList(), BoardRouteAlternatives.alternatives(null, now))
    }

    @Test
    fun `alternatives keep later departures of the same line`() {
        val trips = listOf(trip(2, "2"), trip(6, "2"), trip(9, "2"), trip(12, "2"), trip(15, "2"))

        assertEquals(BoardRouteAlternatives.ALTERNATIVES, BoardRouteAlternatives.alternatives(trips, now).size)
    }

    @Test
    fun `the Then line names the next two by clock and first ride`() {
        val trips = listOf(trip(2, "2"), trip(6, "3"), trip(9, "FLY2", mode = "air"), trip(12, "5"))

        val entries = BoardRouteAlternatives.thenLine(trips, now)

        assertEquals(listOf("3", "FLY2"), entries.map { it.lineName })
        assertEquals(listOf("metro", "air"), entries.map { it.mode })
        assertEquals(isoOslo(now + 6 * minute).substring(11, 16), entries.first().clock)
    }

    @Test
    fun `a Then entry for a walk has no line`() {
        val walk = TripInfo("X", isoOslo(now + 5 * minute), lineName = "?", durationMinutes = 9, originName = "A", legs = listOf(TripLeg(mode = "foot")))

        assertEquals(null, ThenLineEntry(walk).lineName)
        assertEquals("--:--", ThenLineEntry(walk.copy(expectedStartTime = "soon")).clock)
    }

    private val row = 64f
    private val more = 49f

    private fun visible(
        counts: List<Int>,
        spare: Float,
        columns: Int = 1,
    ) = BoardRouteAlternatives.visibleRows(counts, columns = columns, spareHeight = spare, rowHeight = row, moreRowHeight = more)

    @Test
    fun `every row is shown when there is room`() {
        val rows = visible(listOf(3, 0, 2), spare = 400f)

        assertEquals(listOf(VisibleAlternatives(3, 0), VisibleAlternatives(0, 0), VisibleAlternatives(2, 0)), rows)
        assertFalse(rows.any { it.showsMoreRow || it.showsThenLine })
        assertEquals(listOf(true, false, true), rows.map { it.showsRows })
    }

    @Test
    fun `a card short of room shows what fits and ends with the rest in details`() {
        // Room for one alternative and the "+2 in details" row, not for two alternatives and it.
        val rows = visible(listOf(3), spare = row + more + 10f)

        assertEquals(listOf(VisibleAlternatives(shown = 1, hidden = 2)), rows)
        assertTrue(rows.single().showsMoreRow)
    }

    @Test
    fun `the in-details row counts against the room`() {
        // Two alternatives' worth of room: one row and "+2 in details", not two rows and it.
        assertEquals(listOf(VisibleAlternatives(shown = 1, hidden = 2)), visible(listOf(3), spare = 2 * row))
        // Exactly the room for every row needs no "+N" row.
        assertEquals(listOf(VisibleAlternatives(shown = 3, hidden = 0)), visible(listOf(3), spare = 3 * row))
    }

    @Test
    fun `with room for only the in-details row a card shows just that row`() {
        val rows = visible(listOf(3), spare = more + 5f)

        assertEquals(listOf(VisibleAlternatives(shown = 0, hidden = 3)), rows)
        assertTrue(rows.single().showsMoreRow)
        assertTrue(rows.single().showsRows)
        assertFalse(rows.single().showsThenLine)
    }

    @Test
    fun `with no room at all a card shows its Then line instead of rows`() {
        for (spare in listOf(more - 1f, 0f, -50f)) {
            val card = visible(listOf(3), spare = spare).single()

            assertFalse(card.showsRows, "no rows below the banner at $spare")
            assertFalse(card.showsMoreRow)
            assertTrue(card.showsThenLine, "the Then line at $spare")
        }
    }

    @Test
    fun `cards higher up are served first`() {
        // The first takes its three rows; what is left holds the second's "+3 in details" only.
        val rows = visible(listOf(3, 3), spare = 3 * row + more + 10f)

        assertEquals(listOf(VisibleAlternatives(3, 0), VisibleAlternatives(0, 3)), rows)
    }

    @Test
    fun `a card below one that took the room shows its Then line`() {
        val rows = visible(listOf(3, 2), spare = 3 * row + 10f)

        assertEquals(VisibleAlternatives(3, 0), rows[0])
        assertTrue(rows[1].showsThenLine)
        assertFalse(rows[1].showsRows)
    }

    @Test
    fun `cards side by side share their grid row's height`() {
        // Two columns: the first grid row costs its tallest card, three rows; the second gets the rest.
        val rows = visible(listOf(3, 1, 3), spare = 3 * row + row + more, columns = 2)

        assertEquals(listOf(VisibleAlternatives(3, 0), VisibleAlternatives(1, 0), VisibleAlternatives(1, 2)), rows)
    }

    @Test
    fun `a shorter card beside a squeezed one shows what it has when it fits`() {
        // Room for one row and "+N" beside it: the card with one alternative shows it, without "+N".
        val rows = visible(listOf(3, 1), spare = row + more, columns = 2)

        assertEquals(listOf(VisibleAlternatives(1, 2), VisibleAlternatives(1, 0)), rows)
    }

    @Test
    fun `collapsed cards cost nothing and never show a Then line from here`() {
        val rows = visible(listOf(0, 3), spare = 3 * row)

        assertEquals(listOf(VisibleAlternatives(0, 0), VisibleAlternatives(3, 0)), rows)
        assertEquals(listOf(false, false), rows.map { it.showsThenLine })
        assertEquals(listOf(VisibleAlternatives(0, 0)), visible(listOf(0), spare = -10f))
        assertEquals(emptyList(), visible(emptyList(), spare = 0f))
    }

    @Test
    fun `before the cards are measured none shows a row`() {
        val rows = BoardRouteAlternatives.unmeasured(2)

        assertEquals(2, rows.size)
        assertFalse(rows.any { it.showsRows || it.showsThenLine })
    }
}

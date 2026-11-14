package com.example.pulsetransit.ui.layout

import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class PanelRowsTest {
    private fun line(
        line: String,
        count: Int,
        firstMinute: Int = 0,
        notices: List<String> = emptyList(),
    ) = DepartureGroup(
        List(count) {
            Departure(
                line = line,
                destination = "Brenna",
                expectedTime = "2026-11-14T12:${(firstMinute + it * 3).toString().padStart(2, '0')}:00+01:00",
                aimedTime = "2026-11-14T12:00:00+01:00",
                isRealtime = true,
                notices = notices,
            )
        },
    )

    private fun List<DepartureGroup>.sizes() = map { it.departures.size }

    @Test
    fun `a folded line spreads over the free rows and folds the rest into the last`() {
        val rows = PanelRows.fill(listOf(line("73", 15)), rows = 4, showMessages = true)
        assertEquals(listOf(1, 1, 1, 12), rows.sizes())
        assertEquals(line("73", 15).departures, rows.flatMap { it.departures })
    }

    @Test
    fun `a line with room for all its departures folds none`() {
        assertEquals(listOf(1, 1, 1), PanelRows.fill(listOf(line("73", 3)), rows = 6, showMessages = true).sizes())
    }

    @Test
    fun `folded lines take the free rows in turn and keep their order`() {
        val groups = listOf(line("73", 5), line("1", 1, firstMinute = 1), line("70", 4, firstMinute = 2))
        val rows = PanelRows.fill(groups, rows = 6, showMessages = true)
        assertEquals(listOf(1, 1, 3, 1, 1, 3), rows.sizes())
        assertEquals(listOf("73", "73", "73", "1", "70", "70"), rows.map { it.departures.first().line })
        assertEquals(groups.flatMap { it.departures }, rows.flatMap { it.departures })
    }

    @Test
    fun `a full panel or one not measured yet keeps its rows`() {
        val groups = listOf(line("73", 5), line("70", 4))
        assertEquals(groups, PanelRows.fill(groups, rows = 2, showMessages = true))
        assertEquals(groups, PanelRows.fill(groups, rows = 0, showMessages = true))
        assertEquals(emptyList(), PanelRows.fill(emptyList(), rows = 5, showMessages = true))
    }

    @Test
    fun `a line with notices stays folded and its row counts as two`() {
        val noticed = line("73", 5, notices = listOf("Bus for train"))
        val groups = listOf(noticed, line("70", 4, firstMinute = 1))
        // Six rows: the noticed one takes two, the other line gets the remaining three.
        assertEquals(listOf(5, 1, 1, 1, 1), PanelRows.fill(groups, rows = 6, showMessages = true).sizes())
    }

    @Test
    fun `notices that are not shown take no room and split like any line`() {
        val noticed = line("73", 5, notices = listOf("Bus for train"))
        assertEquals(listOf(1, 1, 3), PanelRows.fill(listOf(noticed), rows = 3, showMessages = false).sizes())
    }

    @Test
    fun `a panel packs fewer rows than it asks departures for`() {
        // Ten 52 dp rows and their dividers need more than 520 dp.
        assertEquals(10, BoardLayout.rowCapacity(520f))
        assertEquals(9, PanelRows.capacity(520f))
    }
}

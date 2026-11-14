package com.example.pulsetransit.ui.layout

import com.example.pulsetransit.data.DepartureGroup
import kotlin.math.floor

/**
 * How a stop's departure rows fill its panel: a line folded into one row ("every ~15 min · +14
 * more") spreads over the room the panel has. Shared so both apps split the same rows.
 */
object PanelRows {
    /**
     * About how tall a departure row is with the divider above it when [fill] packs a panel: a
     * two-line row is 52 dp, the divider 1 and a wide panel's larger text a little more. Generous,
     * so the last row, which says how many departures are folded, is never pushed out of view.
     */
    const val ROW_HEIGHT_DP = 56f

    /**
     * How many rows [fill] may lay out in a list this tall: fewer than [BoardLayout.rowCapacity],
     * which sizes how many departures the stop asks for.
     */
    fun capacity(listHeightDp: Float): Int = floor(listHeightDp / ROW_HEIGHT_DP).toInt().coerceAtLeast(0)

    /**
     * [groups] laid out over a list with room for [rows] rows. While there is room, a row that folds
     * a line's departures gives its next one a row of its own, a row from each folded line in turn,
     * so a panel with space shows its departures rather than "every ~15 min · +14 more" over empty
     * space. Only the last row of a line folds what still doesn't fit. Order is kept: a line's
     * departures are consecutive, so its split rows stay where the folded one was.
     *
     * With [showMessages], a row with notices counts as two, and its line stays folded: the notices
     * usually hold for every departure of the line, and split rows would each repeat them.
     */
    fun fill(
        groups: List<DepartureGroup>,
        rows: Int,
        showMessages: Boolean,
    ): List<DepartureGroup> {
        val hasNotices = { group: DepartureGroup -> showMessages && group.departures.any { it.notices.isNotEmpty() } }
        var spare = rows - groups.size - groups.count(hasNotices)
        var result = groups
        while (spare > 0) {
            val next = ArrayList<DepartureGroup>(result.size + spare)
            for (group in result) {
                if (spare > 0 && group.departures.size > 1 && !hasNotices(group)) {
                    next += DepartureGroup(group.departures.take(1))
                    next += DepartureGroup(group.departures.drop(1))
                    spare--
                } else {
                    next += group
                }
            }
            if (next.size == result.size) break
            result = next
        }
        return result
    }
}

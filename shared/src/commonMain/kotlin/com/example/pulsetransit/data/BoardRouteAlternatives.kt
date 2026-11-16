package com.example.pulsetransit.data

import kotlin.math.floor

/**
 * The wall board's route alternatives (board mode only; on the spot lists its own): each saved
 * destination's card shows the first route; its toggle bar names the next ones while collapsed
 * ("Then 08:16 [3] 08:19 [5]"), and, expanded, the next ones are rows below the journey. Which
 * destinations are expanded is a setting ([SettingsRepository.getRouteAlternativesShownIds]).
 * Kept free of UI code so both apps show the same alternatives and cap them by the same rule.
 */
object BoardRouteAlternatives {
    /** How many alternatives after the first route the board fetches and can show. */
    const val ALTERNATIVES = 3

    /** How many routes board mode asks for per destination: the first and its [ALTERNATIVES]. */
    const val ROUTES_PER_DESTINATION = 1 + ALTERNATIVES

    /**
     * The alternatives after the first of [trips] that have not left by [nowMillis], in order. A
     * trip whose time cannot be read is kept. Empty when there is at most one route.
     */
    fun alternatives(
        trips: List<TripInfo>?,
        nowMillis: Long,
    ): List<TripInfo> =
        trips
            .orEmpty()
            .drop(1)
            .filter { trip -> parseIsoToMillis(trip.expectedStartTime)?.let { it >= nowMillis } ?: true }
            .take(ALTERNATIVES)

    /**
     * How many alternative rows each expanded card shows so that the stop panels keep
     * [com.example.pulsetransit.ui.layout.BoardLayout.MIN_PANEL_HEIGHT_DP].
     *
     * [alternativeCounts] is each route card's count of alternatives to show, in board order (0 for
     * a collapsed card), laid out [columns] to a grid row as the board does; a grid row is as tall
     * as its tallest card. [spareHeight] is what the cards may grow by: the space left once the
     * cards without their rows and the panels' minimum are taken. Grid rows are served top to
     * bottom, an alternative row costing [rowHeight] and the "+N in details" row [moreRowHeight].
     * A grid row that cannot show all its rows shows the ones that fit beside a "+N in details"
     * row, or that row alone; when not even that fits, its cards show no rows and their "Then"
     * line instead ([VisibleAlternatives.showsThenLine]), as when collapsed. A card is never
     * collapsed by this, so the choice the user made stays as it is.
     */
    fun visibleRows(
        alternativeCounts: List<Int>,
        columns: Int,
        spareHeight: Float,
        rowHeight: Float,
        moreRowHeight: Float,
    ): List<VisibleAlternatives> {
        val row = rowHeight.coerceAtLeast(0f)
        val more = moreRowHeight.coerceAtLeast(0f)
        var remaining = spareHeight.coerceAtLeast(0f)
        return alternativeCounts.chunked(columns.coerceAtLeast(1)).flatMap { cards ->
            val counts = cards.map { it.coerceAtLeast(0) }
            val wanted = counts.max()
            when {
                wanted * row <= remaining -> {
                    remaining -= wanted * row
                    counts.map { VisibleAlternatives(shown = it, hidden = 0) }
                }
                more <= remaining -> {
                    // Some rows, or none, and "+N in details" for the rest; row > 0 here.
                    val fitting = floor((remaining - more) / row).toInt().coerceIn(0, wanted - 1)
                    remaining -= fitting * row + more
                    counts.map { count ->
                        val shown = minOf(count, fitting)
                        VisibleAlternatives(shown = shown, hidden = count - shown)
                    }
                }
                else -> counts.map { VisibleAlternatives(shown = 0, hidden = it, showsThenLine = it > 0) }
            }
        }
    }

    /**
     * What [visibleRows] gives each card before the cards have been measured: no rows, so the
     * first frames never squeeze the stop panels, and no "Then" line.
     */
    fun unmeasured(count: Int): List<VisibleAlternatives> = List(count) { VisibleAlternatives(shown = 0, hidden = 0) }
}

/**
 * How many of an expanded card's alternatives are shown, and how many are left to its "+N in
 * details" row. [showsThenLine]: not even that row fits, so the card shows no rows and its "Then"
 * line instead, as when collapsed (its control still offers to close it).
 */
data class VisibleAlternatives(
    val shown: Int,
    val hidden: Int,
    val showsThenLine: Boolean = false,
) {
    /** Whether the card ends with "+[hidden] in details". */
    val showsMoreRow: Boolean get() = hidden > 0 && !showsThenLine

    /** Whether the card has anything below its banner: alternatives, or "+N in details". */
    val showsRows: Boolean get() = shown > 0 || showsMoreRow
}

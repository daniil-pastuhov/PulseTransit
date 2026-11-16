package com.example.pulsetransit.data

import kotlin.math.floor

/**
 * The wall board's route alternatives (board mode only; on the spot lists its own): each saved
 * destination's banner shows the first route, a one-line "Then 08:16 [3] · 08:19 [5]" naming the
 * next ones while collapsed, and, expanded, the next ones as rows below it. Which destinations are
 * expanded is a setting ([SettingsRepository.getRouteAlternativesShownIds]). Kept free of UI code
 * so both apps show the same alternatives and cap them by the same rule.
 */
object BoardRouteAlternatives {
    /** How many alternatives after the first route the board fetches and can show. */
    const val ALTERNATIVES = 3

    /** How many routes board mode asks for per destination: the first and its [ALTERNATIVES]. */
    const val ROUTES_PER_DESTINATION = 1 + ALTERNATIVES

    /** How many alternatives the collapsed banner's "Then" line names; more would wrap. */
    const val THEN_LINE_ENTRIES = 2

    /**
     * About how tall an expanded card's alternative row is, with its divider, in dp (points on
     * iOS), before font scaling: as tall as the on-spot rows it reuses. The views use the height
     * they measure once a row has been shown, and this until then.
     */
    const val ROW_HEIGHT_DP = 64f

    /** About how tall the "+N in details" row is, with its divider, as [ROW_HEIGHT_DP]: a touch target. */
    const val MORE_ROW_HEIGHT_DP = 49f

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
     * What a "Then" line names: the next [THEN_LINE_ENTRIES] [alternatives]. Which cards show one
     * is [com.example.pulsetransit.ui.viewmodel.StopBoardViewModel.UiState.routeThenLine]. A view
     * shows as many of them, in order, as fit its width, and none rather than a clock cut short.
     */
    fun thenLine(
        trips: List<TripInfo>?,
        nowMillis: Long,
    ): List<ThenLineEntry> = alternatives(trips, nowMillis).take(THEN_LINE_ENTRIES).map(::ThenLineEntry)

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

/** One alternative on the "Then" line: its departure clock and the line pill of its first ride. */
data class ThenLineEntry(
    val trip: TripInfo,
) {
    /** "08:16", from the ISO time, which is already in Norway's time; "--:--" when unreadable. */
    val clock: String
        get() = trip.expectedStartTime.takeIf { it.length >= ISO_MINUTE_END }?.substring(ISO_HOUR_START, ISO_MINUTE_END) ?: "--:--"

    /** The first ride's line ("3", "FLY2"), or null for a route that only walks. */
    val lineName: String?
        get() = trip.legs.firstOrNull { it.isTransit }?.lineName ?: trip.lineName.takeIf { trip.legs.isEmpty() && it != UNKNOWN_LINE }

    /** The first ride's mode ("metro", "bus"), for the pill's colour. */
    val mode: String?
        get() = trip.legs.firstOrNull { it.isTransit }?.mode

    private companion object {
        const val ISO_HOUR_START = 11
        const val ISO_MINUTE_END = 16

        /** What [TripInfo.lineName] holds when a route has no ride. */
        const val UNKNOWN_LINE = "?"
    }
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

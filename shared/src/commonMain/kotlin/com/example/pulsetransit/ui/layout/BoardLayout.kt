package com.example.pulsetransit.ui.layout

import com.example.pulsetransit.data.DepartureGroup
import kotlin.math.ceil
import kotlin.math.floor

/**
 * How the board fills a screen of a given size: how its stop panels share the space, and which
 * rows have room for more than line, destination and time. A tablet's extra space goes to more
 * panels and more information per row, not larger text. Kept free of UI code so a phone, a tablet
 * and an iPad lay the board out by the same rules. Every size here is in dp (points on iOS).
 */
object BoardLayout {
    /** Below this a panel squeezes the destination too hard to read, so panels stack instead. */
    const val MIN_PANEL_WIDTH_DP = 320f

    /** Below this a panel shows too few rows to be worth a row of its own. */
    const val MIN_PANEL_HEIGHT_DP = 200f

    /**
     * From this width a panel's rows also have columns for the platform and the departure's clock
     * time. Measured on the list, inside the panel's padding: two panels side by side on a
     * 13-inch iPad in portrait have about 470.
     */
    const val WIDE_ROW_MIN_WIDTH_DP = 440f

    /**
     * Below this width a fastest-route banner stacks its title under the departure time and
     * duration, rather than squeezing the title into the gap between them.
     */
    const val COMPACT_BANNER_MAX_WIDTH_DP = 360f

    /** Height of the row under the panels that shows which page of stops is up, when there are pages. */
    const val PAGE_INDICATOR_HEIGHT_DP = 24f

    /** How long each page of stops stays up before the board turns to the next. */
    const val PAGE_INTERVAL_MS = 15_000L

    /** About how tall a departure row is with the gap below it, for [rowCapacity]. */
    const val ROW_HEIGHT_DP = 52f

    /**
     * How many columns of panels [stopCount] stops share a space of this size in, laid out by
     * [columnSizes]. As many columns as fit, then the fewest that stack no more panels in one, so
     * the columns come out even. When that leaves panels too short, it falls back to a single row
     * in a wide space and a single column in a tall one.
     */
    fun columns(
        stopCount: Int,
        widthDp: Float,
        heightDp: Float,
    ): Int {
        if (stopCount <= 1) return 1
        val fitting = floor(widthDp / MIN_PANEL_WIDTH_DP).toInt().coerceIn(1, stopCount)
        val rows = ceilDiv(stopCount, fitting)
        if (rows > 1 && heightDp / rows < MIN_PANEL_HEIGHT_DP) {
            return if (widthDp > heightDp) stopCount else 1
        }
        return ceilDiv(stopCount, rows)
    }

    /**
     * How many panels each of [columns] columns stacks, left to right, filled in favourites order
     * top to bottom. Every panel is a column wide, so none stretches across the board with a gap in
     * each row; where the stops do not divide evenly, the first columns hold one fewer, so the
     * first stop, the one the user put on top, gets the taller panel. 3 stops in 2 columns are
     * the first beside the other two.
     */
    fun columnSizes(
        stopCount: Int,
        columns: Int,
    ): List<Int> {
        if (stopCount <= 0 || columns <= 0) return emptyList()
        val base = stopCount / columns
        val extra = stopCount % columns
        return List(columns) { column -> if (column >= columns - extra) base + 1 else base }
    }

    /**
     * How many panels fit a space of this size without either dropping below
     * [MIN_PANEL_WIDTH_DP] by [MIN_PANEL_HEIGHT_DP]. Always at least one, so a window too small
     * for even one panel still shows a stop at a time.
     */
    fun capacity(
        widthDp: Float,
        heightDp: Float,
    ): Int {
        val columns = floor(widthDp / MIN_PANEL_WIDTH_DP).toInt().coerceAtLeast(1)
        val rows = floor(heightDp / MIN_PANEL_HEIGHT_DP).toInt().coerceAtLeast(1)
        return columns * rows
    }

    /**
     * How many stops each page of the board shows, in favourites order, for a space of this size.
     * One page when they all fit [capacity]. Otherwise the board takes turns showing them, and the
     * page indicator takes [PAGE_INDICATOR_HEIGHT_DP] of the height; the stops then split as evenly
     * as they can, earlier pages holding one more, so no page is left with a lone stop that a fuller
     * one could have shared. Each page is laid out by [columns] for its own count.
     */
    fun pageSizes(
        stopCount: Int,
        widthDp: Float,
        heightDp: Float,
    ): List<Int> {
        if (stopCount <= 0) return emptyList()
        if (stopCount <= capacity(widthDp, heightDp)) return listOf(stopCount)
        val perPage = capacity(widthDp, heightDp - PAGE_INDICATOR_HEIGHT_DP)
        val pages = ceilDiv(stopCount, perPage)
        val base = stopCount / pages
        val extra = stopCount % pages
        return List(pages) { page -> if (page < extra) base + 1 else base }
    }

    /** Whether a fastest-route banner this wide stacks its title, by [COMPACT_BANNER_MAX_WIDTH_DP]. */
    fun isCompactBanner(widthDp: Float): Boolean = widthDp < COMPACT_BANNER_MAX_WIDTH_DP

    /** How many departure rows a list this tall shows without scrolling. */
    fun rowCapacity(listHeightDp: Float): Int = floor(listHeightDp / ROW_HEIGHT_DP).toInt().coerceAtLeast(0)

    /** Whether a panel this wide gives its rows the platform and clock-time columns. */
    fun isWideRow(panelWidthDp: Float): Boolean = panelWidthDp >= WIDE_ROW_MIN_WIDTH_DP

    /**
     * Whether a panel's wide rows show the platform. Only when its departures leave from more than
     * one: at a stop with a single quay the column would repeat the same code on every row.
     */
    fun showsPlatform(groups: List<DepartureGroup>): Boolean =
        groups.mapNotNullTo(HashSet()) { it.departures.firstOrNull()?.platform }.size > 1

    private fun ceilDiv(
        a: Int,
        b: Int,
    ): Int = ceil(a.toDouble() / b).toInt()
}

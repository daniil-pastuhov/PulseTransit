package com.example.pulsetransit.ui.layout

/**
 * Where a chain of items that wraps onto more rows (a trip's legs) breaks, and how many items are
 * left out when it would need more than a given number of rows. The chain always starts at its
 * first item, so what is left out is the end of it, and the last row shown makes room for a marker
 * that says how many items are hidden ("+2 more"). Kept free of UI code so that Compose and SwiftUI
 * break a route the same way. Sizes are in any one unit (pixels on Android, points on iOS).
 */
object WrappedRows {
    /**
     * [rowEnds] holds each row's end index into the items (exclusive), so row `i` is the items from
     * `rowEnds[i - 1]` (or 0) up to `rowEnds[i]`. [hidden] items after the last row are left out;
     * when there are any, the marker goes at the end of the last row, which may then be empty.
     */
    data class Arrangement(
        val rowEnds: List<Int>,
        val hidden: Int,
    ) {
        val shown: Int get() = rowEnds.lastOrNull() ?: 0
    }

    /**
     * Breaks items of the given [widths] into rows no wider than [maxWidth], [spacing] apart.
     * An item wider than a row takes a row of its own; callers narrow it to [maxWidth] first, as it
     * is then drawn. Beyond [maxLines] rows, items are dropped from the end of the last row kept
     * until the marker, [markerWidth] wide, fits after them.
     */
    fun arrange(
        widths: List<Float>,
        maxWidth: Float,
        spacing: Float,
        maxLines: Int,
        markerWidth: Float,
    ): Arrangement {
        require(maxLines >= 1) { "maxLines must be at least 1, was $maxLines" }
        val rowEnds = mutableListOf<Int>()
        var rowStart = 0
        var rowWidth = 0f
        widths.forEachIndexed { index, width ->
            if (index > rowStart && rowWidth + spacing + width > maxWidth) {
                rowEnds += index
                rowStart = index
                rowWidth = width
            } else {
                rowWidth += (if (index > rowStart) spacing else 0f) + width
            }
        }
        if (widths.isNotEmpty()) rowEnds += widths.size
        if (rowEnds.size <= maxLines) return Arrangement(rowEnds, hidden = 0)

        val kept = rowEnds.take(maxLines).toMutableList()
        val lastStart = if (maxLines > 1) kept[maxLines - 2] else 0
        var end = kept.last()
        while (end > lastStart && width(widths, lastStart, end, spacing) + spacing + markerWidth > maxWidth) end--
        kept[kept.lastIndex] = end
        return Arrangement(kept, hidden = widths.size - end)
    }

    /** iOS: Kotlin `Float` lists arrive as `[KotlinFloat]`, so Swift passes plain doubles here. */
    fun arrange(
        widths: DoubleArray,
        maxWidth: Double,
        spacing: Double,
        maxLines: Int,
        markerWidth: Double,
    ): Arrangement = arrange(widths.map { it.toFloat() }, maxWidth.toFloat(), spacing.toFloat(), maxLines, markerWidth.toFloat())

    private fun width(
        widths: List<Float>,
        from: Int,
        to: Int,
        spacing: Float,
    ): Float = (from until to).sumOf { widths[it].toDouble() }.toFloat() + spacing * (to - from - 1).coerceAtLeast(0)
}

package com.example.pulsetransit.data

/**
 * Where a row dragged [offset] px from index [from] would land: past each neighbour once it covers
 * more than half of it. [heights] are the rows' heights, in order.
 */
fun dropIndex(
    from: Int,
    offset: Float,
    heights: List<Int>,
): Int {
    var index = from
    // One direction only: past a neighbour the rest can be negative, which is not a drag back.
    if (offset > 0) {
        var remaining = offset
        while (index < heights.lastIndex && remaining > heights[index + 1] / 2f) {
            remaining -= heights[index + 1]
            index++
        }
    } else {
        var remaining = -offset
        while (index > 0 && remaining > heights[index - 1] / 2f) {
            remaining -= heights[index - 1]
            index--
        }
    }
    return index
}

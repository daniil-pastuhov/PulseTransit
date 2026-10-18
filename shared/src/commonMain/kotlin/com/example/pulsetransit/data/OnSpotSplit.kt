package com.example.pulsetransit.data

/**
 * How the on-spot board shares its space between the routes and the nearby stop, which the user
 * drags. The split is the routes' share, 0..1. A section dragged smaller than its minimum folds to
 * a bar at its edge, like a bottom sheet: [ROUTES_COLLAPSED] and [STOP_COLLAPSED] store that.
 *
 * Sizes are along the split's axis (height in portrait, width in landscape) and in any one unit:
 * dp on Android, points on iOS. Both apps call this so they agree.
 */
object OnSpotSplit {
    const val DEFAULT = 0.62f

    /** The routes are folded to a bar and the stop has the board. */
    const val ROUTES_COLLAPSED = 0f

    /** The stop is folded to a bar and the routes have the board. */
    const val STOP_COLLAPSED = 1f

    /** On a board too short for two minimums, an open section may shrink to 1/this of it. */
    private const val SHORT_BOARD_SECTIONS = 3

    /**
     * The smallest an open section may be on a board [totalSize] long: [minSize], or a third of the
     * board when two of those do not fit (a phone in split screen), so both sections can still open.
     * Callers deciding whether a section shows as folded use this, not [minSize].
     */
    fun minSectionSize(
        totalSize: Float,
        minSize: Float,
    ): Float = minOf(minSize, totalSize / SHORT_BOARD_SECTIONS)

    /**
     * The routes' size for a stored [split] of [totalSize]: [barSize] when folded, the rest less
     * [barSize] when the stop is. An open section keeps at least [minSectionSize], so a share saved
     * on a bigger screen or the other orientation does not leave one too small to use.
     */
    fun routesSize(
        split: Float,
        totalSize: Float,
        barSize: Float,
        minSize: Float,
    ): Float =
        when {
            split <= ROUTES_COLLAPSED -> barSize
            split >= STOP_COLLAPSED -> totalSize - barSize
            else -> {
                val low = minSectionSize(totalSize, minSize)
                (split * totalSize).coerceIn(low, totalSize - low)
            }
        }

    /** The routes' size while dragged by [delta]: never past either section's bar. */
    fun dragRoutesSize(
        routesSize: Float,
        delta: Float,
        totalSize: Float,
        barSize: Float,
    ): Float = (routesSize + delta).coerceIn(barSize, maxOf(barSize, totalSize - barSize))

    /**
     * The split to keep once a drag ends with the routes at [routesSize]: a section left smaller
     * than [minSectionSize] folds to its bar, otherwise the share is kept as dropped.
     */
    fun settle(
        routesSize: Float,
        totalSize: Float,
        minSize: Float,
    ): Float {
        if (totalSize <= 0f) return DEFAULT
        val low = minSectionSize(totalSize, minSize)
        return when {
            routesSize < low -> ROUTES_COLLAPSED
            totalSize - routesSize < low -> STOP_COLLAPSED
            else -> routesSize / totalSize
        }
    }

    /** A stored split made valid: within 0..1, or [DEFAULT] if it is not a number. */
    fun sanitize(split: Float): Float = if (split.isNaN()) DEFAULT else split.coerceIn(ROUTES_COLLAPSED, STOP_COLLAPSED)
}

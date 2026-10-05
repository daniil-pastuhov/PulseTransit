package com.example.pulsetransit.ui.pixelshift

import com.example.pulsetransit.data.BurnInProtectionLevel
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** An offset of the whole board, in dp, that the UI eases towards. */
data class PixelShiftOffset(
    val xDp: Float,
    val yDp: Float,
)

/**
 * When and how far the board moves to spread OLED wear. Kept free of UI code so every platform
 * shifts by the same rules and only the animation differs.
 */
object PixelShiftSchedule {
    /** How often the board picks a new offset. */
    val interval = 1.minutes

    /** How long the move to the new offset takes; slow enough that nobody notices it. */
    val transition = 5.seconds

    // Duration is a value class that reaches Swift as its raw encoding, so Swift reads these.
    val intervalMillis: Long get() = interval.inWholeMilliseconds
    val transitionMillis: Long get() = transition.inWholeMilliseconds

    /** A random offset within [level]'s range. An overload rather than a default, for Swift. */
    fun nextOffset(level: BurnInProtectionLevel): PixelShiftOffset = nextOffset(level, Random.Default)

    /** A random offset within [level]'s range on both axes. */
    fun nextOffset(
        level: BurnInProtectionLevel,
        random: Random,
    ): PixelShiftOffset {
        val max = level.maxOffsetDp
        return PixelShiftOffset(
            xDp = random.nextFloat() * (max * 2) - max,
            yDp = random.nextFloat() * (max * 2) - max,
        )
    }
}

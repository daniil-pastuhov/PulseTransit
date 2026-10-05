package com.example.pulsetransit.ui.pixelshift

import com.example.pulsetransit.data.BurnInProtectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PixelShiftScheduleTest {
    @Test
    fun `offsets stay within the level's range on both axes`() {
        val random = Random(42)
        for (level in BurnInProtectionLevel.entries) {
            repeat(1_000) {
                val offset = PixelShiftSchedule.nextOffset(level, random)
                assertTrue(offset.xDp in -level.maxOffsetDp..level.maxOffsetDp)
                assertTrue(offset.yDp in -level.maxOffsetDp..level.maxOffsetDp)
            }
        }
    }

    @Test
    fun `off never moves the board`() {
        assertEquals(PixelShiftOffset(0f, 0f), PixelShiftSchedule.nextOffset(BurnInProtectionLevel.OFF, Random(1)))
    }
}

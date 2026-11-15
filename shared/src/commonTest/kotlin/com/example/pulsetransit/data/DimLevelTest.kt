package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DimLevelTest {
    @Test
    fun `off leaves a dimmed board all but blank`() {
        assertTrue(DimLevel.OFF.alpha <= 0.05f, "OFF must look like a switched-off OLED screen")
        assertTrue(DimLevel.OFF.alpha > 0f, "OFF still draws the board, faintly")
    }

    @Test
    fun `each level is brighter than the one before and none reaches full brightness`() {
        val alphas = DimLevel.entries.map { it.alpha }
        assertEquals(alphas.sorted(), alphas)
        assertEquals(alphas.size, alphas.toSet().size)
        assertTrue(alphas.all { it < 1f })
    }
}

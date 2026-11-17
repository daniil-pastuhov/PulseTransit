package com.example.pulsetransit.ui.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

class OnSpotRoutePromptLayoutTest {
    private fun rows(
        available: Float,
        count: Int = 10,
    ) = OnSpotRoutePromptLayout.visibleSuggestionRows(available, fixedHeight = 100f, rowHeight = 48f, suggestionCount = count)

    @Test
    fun `whole rows fit below the fixed part`() {
        assertEquals(0, rows(147f))
        assertEquals(1, rows(148f))
        assertEquals(2, rows(243f))
        assertEquals(3, rows(291.9f))
        assertEquals(4, rows(292f))
    }

    @Test
    fun `never more rows than suggestions`() {
        assertEquals(3, rows(10_000f, count = 3))
        assertEquals(3, rows(Float.POSITIVE_INFINITY, count = 3))
    }

    @Test
    fun `nothing fits a height shorter than the fixed part or with no suggestions`() {
        assertEquals(0, rows(50f))
        assertEquals(0, rows(1_000f, count = 0))
        assertEquals(0, OnSpotRoutePromptLayout.visibleSuggestionRows(500f, 100f, 0f, 5))
        assertEquals(0, rows(Float.NaN))
    }
}

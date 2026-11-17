package com.example.pulsetransit.ui.viewmodel

import kotlin.math.floor

/**
 * What the on-spot board's routes section shows in place of route cards when it has none to show
 * (see [StopBoardViewModel.UiState.onSpotRoutePrompt]): a search row that opens the route search,
 * then [StopBoardViewModel.UiState.onSpotRouteSuggestions] to route to with one tap. The cases
 * differ only in their title and in whether "Add destination" is offered.
 */
enum class OnSpotRoutePrompt {
    /** Not on-spot mode, or the section has route cards to show (loading and failed ones included). */
    NONE,

    /** No destination is saved: offer to add one, besides searching. */
    NO_DESTINATIONS,

    /** Destinations are saved, but none is inside its schedule now. */
    NONE_ACTIVE,

    /**
     * Destinations are shown now and every one has been answered for, but none has a route: Entur
     * found none, or the device is already there. Never while one is loading or failed.
     */
    NONE_FOUND,
    ;

    /** Whether the prompt shows at all, i.e. anything but [NONE]. */
    val isShown: Boolean get() = this != NONE

    /** Whether the prompt offers "Add destination", which opens Settings. */
    val offersAddDestination: Boolean get() = this == NO_DESTINATIONS
}

/**
 * How the route prompt is sized, in any one unit (dp on Android, points on iOS), so both apps show
 * the same number of suggestions for a height.
 */
object OnSpotRoutePromptLayout {
    /**
     * How many of [suggestionCount] suggestion rows of [rowHeight] fit [availableHeight] below the
     * prompt's fixed part ([fixedHeight]: search row, title, button, the list's label and padding).
     * Whole rows only, never fewer than 0 or more than there are.
     *
     * Pass the height the section is given (OnSpotSplit's routesSize), not the height it shrinks
     * to around its content, or showing fewer rows would make room for fewer still.
     */
    fun visibleSuggestionRows(
        availableHeight: Float,
        fixedHeight: Float,
        rowHeight: Float,
        suggestionCount: Int,
    ): Int {
        if (suggestionCount <= 0 || rowHeight <= 0f || availableHeight.isNaN()) return 0
        val rows = floor((availableHeight - fixedHeight) / rowHeight)
        if (rows.isNaN() || rows <= 0f) return 0
        return if (rows >= suggestionCount) suggestionCount else rows.toInt()
    }
}

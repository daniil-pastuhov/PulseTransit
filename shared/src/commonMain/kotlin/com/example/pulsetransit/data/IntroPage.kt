package com.example.pulsetransit.data

/**
 * One page of the short intro shown after the first-launch choice of [AppMode]. Each app maps a
 * page to its own title, text and icon; which pages a mode gets, and in what order, is
 * [introPages], so both apps tell the same story.
 */
enum class IntroPage {
    /** On the spot: departures from the nearest stop, and picking another one nearby. */
    NEAREST_STOP,

    /** On the spot: saved destinations get routes from where the user is; search for a one-off trip. */
    ROUTES_FROM_HERE,

    /** On the spot: the bar between routes and departures can be dragged. */
    RESIZE,

    /** Board: stops and their directions are added in Settings. */
    CHOOSE_STOPS,

    /** Board: fastest-route destinations, each with the times it is shown. */
    FASTEST_ROUTES,

    /** Board: tapping a departure or a route opens its details. */
    TAP_FOR_DETAILS,

    /** Board: burn-in protection, dimming, camera wake and the automatic theme. */
    SCREEN_CARE,
}

/** The intro pages for [mode], in the order they are shown. */
fun introPages(mode: AppMode): List<IntroPage> =
    when (mode) {
        AppMode.ON_SPOT ->
            listOf(IntroPage.NEAREST_STOP, IntroPage.ROUTES_FROM_HERE, IntroPage.RESIZE)
        AppMode.BOARD ->
            listOf(IntroPage.CHOOSE_STOPS, IntroPage.FASTEST_ROUTES, IntroPage.TAP_FOR_DETAILS, IntroPage.SCREEN_CARE)
    }

package com.example.pulsetransit.ui.screens.routecards

import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripTime

/** What the route cards can do, wired by [com.example.pulsetransit.ui.screens.MainScreen] to the ViewModel. */
class RouteCardActions(
    /** Opens a route's details: the destination's id and the tapped route. */
    val onOpenTrip: (destinationId: String, TripInfo) -> Unit = { _, _ -> },
    /** Opens or closes a saved destination's alternatives (a setting, by destination id). */
    val onToggleAlternatives: (destinationId: String) -> Unit = {},
    /** "+N in details" and the rail: opens the route search on that destination. */
    val onMoreInDetails: (StopPlace) -> Unit = {},
    /** The searched-for destination's time ("leave at", "arrive by"). */
    val onTimeChange: (TripTime) -> Unit = {},
    /** Saves the searched-for destination. */
    val onSave: () -> Unit = {},
    /** Closes the searched-for destination's card. */
    val onDismissTemporary: () -> Unit = {},
)

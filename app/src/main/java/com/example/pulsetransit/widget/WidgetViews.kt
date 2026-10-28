package com.example.pulsetransit.widget

import kotlinx.serialization.Serializable

/*
 * What a widget draws, with every string already resolved and every time already formatted. A
 * refresh builds one and stores it as JSON in the widget's Glance state; the composables only read
 * it. That way a redraw (a resize, a session restart) never has to load anything, and the network
 * is only asked from WidgetRefresh and the refresh button.
 */

/** Labels the composables need: Compose resources only hand strings out from a suspend call. */
@Serializable
data class WidgetLabels(
    val refresh: String = "",
    val realtime: String = "",
)

/** One departure time in a row. */
@Serializable
data class TimeView(
    val text: String,
    val isRealtime: Boolean,
    val isCancelled: Boolean,
)

/** One line-to-destination row of the departures widget. */
@Serializable
data class RowView(
    val line: String,
    val transportMode: String?,
    val destination: String,
    val platform: String?,
    val times: List<TimeView>,
)

/** The departures widget: a stop's rows, or a [message] in their place. */
@Serializable
data class DeparturesView(
    val title: String,
    val rows: List<RowView> = emptyList(),
    val message: String? = null,
    val status: String,
    /** The status is a warning (offline, failed), drawn in the error colour. */
    val statusIsWarning: Boolean = false,
    val labels: WidgetLabels = WidgetLabels(),
)

/** The route widget: when to leave for a destination, or a [message] in its place. */
@Serializable
data class RouteView(
    val title: String,
    val leaveAt: String? = null,
    val legs: String? = null,
    val boarding: String? = null,
    val arrival: String? = null,
    val message: String? = null,
    val status: String,
    val statusIsWarning: Boolean = false,
    val labels: WidgetLabels = WidgetLabels(),
)

/** The compact widget: the clock is a live TextClock, the rest comes from here. */
@Serializable
data class GlanceView(
    val weatherEmoji: String? = null,
    val temperature: String? = null,
    val stopName: String? = null,
    val next: RowView? = null,
    /** The departures after [next], for a widget tall enough to list them. */
    val later: List<RowView> = emptyList(),
    val message: String? = null,
    val labels: WidgetLabels = WidgetLabels(),
)

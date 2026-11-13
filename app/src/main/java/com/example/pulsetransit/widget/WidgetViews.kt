package com.example.pulsetransit.widget

import androidx.annotation.DrawableRes
import com.example.pulsetransit.R
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

/**
 * The icons a widget draws: the bundled Material Symbols drawables (scripts/gen-icons.py), stored
 * by name so a view saved by one build still draws after an update renumbers the resources.
 */
@Serializable
enum class WidgetIcon(
    @param:DrawableRes val drawable: Int,
) {
    CLEAR_DAY(R.drawable.ic_widget_clear_day),
    CLEAR_NIGHT(R.drawable.ic_widget_clear_night),
    PARTLY_CLOUDY_DAY(R.drawable.ic_widget_partly_cloudy_day),
    PARTLY_CLOUDY_NIGHT(R.drawable.ic_widget_partly_cloudy_night),
    CLOUD(R.drawable.ic_widget_cloud),
    FOG(R.drawable.ic_widget_foggy),
    RAIN_LIGHT(R.drawable.ic_widget_rainy_light),
    RAIN(R.drawable.ic_widget_rainy),
    RAIN_HEAVY(R.drawable.ic_widget_rainy_heavy),
    SLEET(R.drawable.ic_widget_rainy_snow),
    SNOW(R.drawable.ic_widget_weather_snowy),
    SNOW_HEAVY(R.drawable.ic_widget_snowing_heavy),
    THUNDER(R.drawable.ic_widget_thunderstorm),
    OFFLINE(R.drawable.ic_widget_cloud_off),
    ADD_PLACE(R.drawable.ic_widget_add_location_alt),
    SCHEDULE(R.drawable.ic_widget_schedule),
    ROUTE(R.drawable.ic_widget_route),
    LOCATION_OFF(R.drawable.ic_widget_location_off),
    ARRIVED(R.drawable.ic_widget_where_to_vote),
}

/** Where a tap on a widget leads: the board, or the screen that fixes what the widget is missing. */
@Serializable
enum class WidgetTarget { BOARD, ADD_STOP, SETTINGS }

/** A message in place of a widget's data (T5): what happened, what to do about it, and where a tap leads. */
@Serializable
data class MessageView(
    val icon: WidgetIcon,
    val title: String,
    val body: String? = null,
    val target: WidgetTarget = WidgetTarget.BOARD,
)

/** The footer line: how old the data is, a warning when it is not live. */
@Serializable
data class StatusView(
    val text: String,
    val isWarning: Boolean,
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
    val message: MessageView? = null,
    val status: StatusView? = null,
    val labels: WidgetLabels = WidgetLabels(),
)

/** One leg of a route: a ride's line pill, or a walk with its minutes ("6 min"). */
@Serializable
data class LegView(
    val line: String? = null,
    val transportMode: String? = null,
    val walk: String? = null,
)

/** The route widget: when to leave for a destination, or a [message] in its place. */
@Serializable
data class RouteView(
    val title: String,
    val leaveAt: String? = null,
    val legs: List<LegView> = emptyList(),
    /** "Walk all the way", for a route with nothing to ride. */
    val walkOnly: String? = null,
    /** "Departs 19:52", and with the platform for a widget wide enough: "Departs 19:52 · platform 13". */
    val departs: String? = null,
    val departsWithPlatform: String? = null,
    /** "Arrives 20:08"; the duration is separate for the same reason. */
    val arrives: String? = null,
    val duration: String? = null,
    val message: MessageView? = null,
    val status: StatusView? = null,
    val labels: WidgetLabels = WidgetLabels(),
)

/** The compact widget: the clock is a live TextClock, the rest comes from here. */
@Serializable
data class GlanceView(
    val weatherIcon: WidgetIcon? = null,
    val temperature: String? = null,
    val stopName: String? = null,
    val next: RowView? = null,
    /** The departures after [next], for a widget tall enough to list them. */
    val later: List<RowView> = emptyList(),
    val message: MessageView? = null,
    /** Only when the data is not live: offline, or Entur not answering. */
    val status: StatusView? = null,
    val labels: WidgetLabels = WidgetLabels(),
)

package com.example.pulsetransit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.pulsetransit.data.widget.RouteWidgetState
import com.example.pulsetransit.data.widget.WidgetRoute
import com.example.pulsetransit.ui.components.getTransportEmoji
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.getString

/**
 * When to leave for a saved destination by its fastest route, as the board's route banner shows it:
 * leave-by time, the lines ridden, and the arrival. Set to a destination in [WidgetConfigActivity];
 * until then it shows the first destination whose schedule is active.
 */
class RouteWidget : BoardWidget<RouteView>() {
    override suspend fun load(
        context: Context,
        configuredId: String?,
    ): RouteView {
        val labels = labels()
        return when (val state = source.route(configuredId)) {
            RouteWidgetState.NoDestinations ->
                RouteView(title = appName(context), message = getString(Res.string.widget_no_destinations), status = "", labels = labels)
            is RouteWidgetState.NoOrigin ->
                RouteView(
                    title = getString(Res.string.trip_details_title, state.destination.name),
                    message = getString(Res.string.widget_no_origin),
                    status = "",
                    labels = labels,
                )
            is RouteWidgetState.AtDestination ->
                RouteView(
                    title = getString(Res.string.trip_details_title, state.destination.name),
                    message = getString(Res.string.widget_at_destination, state.destination.name),
                    status = "",
                    labels = labels,
                )
            is RouteWidgetState.Route -> {
                val (status, warning) = status(state.freshness)
                val title = getString(Res.string.trip_details_title, state.destination.name)
                val route = state.route
                if (route == null) {
                    val message = if (state.freshness.failed) Res.string.widget_unreachable else Res.string.widget_no_route
                    RouteView(title = title, message = getString(message), status = status, statusIsWarning = warning, labels = labels)
                } else {
                    route.toView(title, status, warning, labels)
                }
            }
        }
    }

    private suspend fun WidgetRoute.toView(
        title: String,
        status: String,
        warning: Boolean,
        labels: WidgetLabels,
    ): RouteView {
        val boardAt = boardAtMillis
        val platform = platform
        val arriveAt = arriveAtMillis
        val duration = getString(Res.string.widget_duration, durationMinutes)
        return RouteView(
            title = title,
            leaveAt = getString(Res.string.widget_leave_at, time(leaveAtMillis)),
            legs =
                if (legs.isEmpty()) {
                    getString(Res.string.widget_walk_only)
                } else {
                    legs.joinToString("  ›  ") { leg -> "${getTransportEmoji(leg.mode)} ${leg.line}" }
                },
            boarding =
                when {
                    boardAt == null -> null
                    platform != null -> getString(Res.string.widget_board_at_platform, time(boardAt), platform)
                    else -> getString(Res.string.widget_board_at, time(boardAt))
                },
            arrival =
                if (arriveAt != null) {
                    "${getString(Res.string.widget_arrive_at, time(arriveAt))} · $duration"
                } else {
                    duration
                },
            status = status,
            statusIsWarning = warning,
            labels = labels,
        )
    }

    override fun encode(view: RouteView) = json.encodeToString(RouteView.serializer(), view)

    override fun decode(json: String) = BoardWidget.json.decodeFromString(RouteView.serializer(), json)

    @Composable
    override fun Content(view: RouteView?) {
        val tall = LocalSize.current.height >= 120.dp
        WidgetCard {
            WidgetHeader(view?.title.orEmpty(), WidgetKind.ROUTE, view?.labels?.refresh.orEmpty())
            VerticalGap(4)
            Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                when {
                    view == null -> Unit
                    view.message != null -> WidgetMessage(view.message)
                    else -> {
                        view.leaveAt?.let {
                            Text(
                                text = it,
                                style =
                                    TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = if (tall) 26.sp else 20.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                maxLines = 1,
                            )
                        }
                        view.legs?.let { SecondaryLine(it, primary = true) }
                        if (tall) view.boarding?.let { SecondaryLine(it) }
                        view.arrival?.let { SecondaryLine(it) }
                    }
                }
            }
            if (!view?.status.isNullOrEmpty()) WidgetStatus(view.status, view.statusIsWarning)
        }
    }
}

@Composable
private fun SecondaryLine(
    text: String,
    primary: Boolean = false,
) {
    Text(
        text = text,
        style =
            TextStyle(
                color = if (primary) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
            ),
        maxLines = 1,
    )
}

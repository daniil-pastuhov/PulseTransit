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
import com.example.pulsetransit.ui.components.tripDurationString
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
                RouteView(
                    title = appName(context),
                    message = message(WidgetIcon.ADD_PLACE, Res.string.widget_no_destinations, Res.string.widget_no_destinations_body, WidgetTarget.SETTINGS),
                    labels = labels,
                )
            is RouteWidgetState.NoOrigin ->
                RouteView(
                    title = getString(Res.string.trip_details_title, state.destination.name),
                    message = message(WidgetIcon.LOCATION_OFF, Res.string.widget_no_origin, target = WidgetTarget.SETTINGS),
                    labels = labels,
                )
            // Not an error: a plain statement, no footer.
            is RouteWidgetState.AtDestination ->
                RouteView(
                    title = getString(Res.string.trip_details_title, state.destination.name),
                    message = MessageView(WidgetIcon.ARRIVED, getString(Res.string.widget_at_destination, state.destination.name)),
                    labels = labels,
                )
            is RouteWidgetState.Route -> {
                val title = getString(Res.string.trip_details_title, state.destination.name)
                val status = status(state.freshness)
                val route = state.route
                when {
                    route != null -> route.toView(title, status, labels)
                    state.freshness.failed ->
                        RouteView(
                            title = title,
                            message = message(WidgetIcon.OFFLINE, Res.string.widget_unreachable, Res.string.widget_unreachable_body),
                            labels = labels,
                        )
                    // Checked just now and nothing runs: the Updated line confirms it.
                    else ->
                        RouteView(
                            title = title,
                            message = message(WidgetIcon.ROUTE, Res.string.widget_no_route, Res.string.widget_no_route_body),
                            status = status,
                            labels = labels,
                        )
                }
            }
        }
    }

    private suspend fun WidgetRoute.toView(
        title: String,
        status: StatusView?,
        labels: WidgetLabels,
    ): RouteView {
        val boardAt = boardAtMillis
        val arriveAt = arriveAtMillis
        return RouteView(
            title = title,
            leaveAt = getString(Res.string.widget_leave_at, time(leaveAtMillis)),
            legs =
                legs.map { leg ->
                    val minutes = leg.walkMinutes
                    if (leg.isWalk && minutes != null) {
                        LegView(walk = getString(Res.string.leg_walk, minutes))
                    } else {
                        LegView(line = leg.line, transportMode = leg.mode)
                    }
                },
            walkOnly = if (isWalkOnly) getString(Res.string.widget_walk_only) else null,
            departs = boardAt?.let { getString(Res.string.widget_board_at, time(it)) },
            departsWithPlatform = boardAt?.let { at -> platform?.let { getString(Res.string.widget_board_at_platform, time(at), it) } },
            arrives = arriveAt?.let { getString(Res.string.widget_arrive_at, time(it)) },
            duration = tripDurationString(durationMinutes),
            status = status,
            labels = labels,
        )
    }

    override fun encode(view: RouteView) = json.encodeToString(RouteView.serializer(), view)

    override fun decode(json: String) = BoardWidget.json.decodeFromString(RouteView.serializer(), json)

    /**
     * Leave-by time first and largest, then the legs, when the ride departs and when it arrives.
     * At 2×2 the title wraps, and platform, duration and footer give way so each line fits.
     */
    @Composable
    override fun Content(view: RouteView?) {
        val size = LocalSize.current
        val small = size.width < SMALL_WIDTH_DP.dp
        WidgetCard(view?.message?.target ?: WidgetTarget.BOARD) {
            WidgetHeader(view?.title.orEmpty(), WidgetKind.ROUTE, view?.labels?.refresh.orEmpty(), titleLines = if (small) 2 else 1)
            VerticalGap(4)
            Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                val message = view?.message
                when {
                    view == null -> Unit
                    message != null -> WidgetMessage(message)
                    else -> {
                        view.leaveAt?.let {
                            Text(
                                text = it,
                                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = if (small) 22.sp else 26.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1,
                            )
                        }
                        VerticalGap(2)
                        if (view.walkOnly != null) {
                            LegChain(listOf(LegView(walk = view.walkOnly)))
                        } else {
                            LegChain(view.legs)
                        }
                        VerticalGap(2)
                        (if (small) view.departs else view.departsWithPlatform ?: view.departs)?.let { SecondaryLine(it) }
                        // Without an arrival time the duration still says how long it takes.
                        when {
                            view.arrives == null -> view.duration?.let { SecondaryLine(it) }
                            !small && view.duration != null -> SecondaryLine("${view.arrives} · ${view.duration}")
                            else -> SecondaryLine(view.arrives)
                        }
                    }
                }
            }
            // At 2×2 the footer gives way, unless the data is not live: then it is the only hint.
            view?.status?.takeIf { !small || it.isWarning || view.message != null }?.let { WidgetStatus(it) }
        }
    }

    private companion object {
        const val SMALL_WIDTH_DP = 200
    }
}

@Composable
private fun SecondaryLine(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        maxLines = 1,
    )
}

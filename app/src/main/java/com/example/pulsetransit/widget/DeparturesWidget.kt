package com.example.pulsetransit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.pulsetransit.data.widget.DeparturesWidgetState
import com.example.pulsetransit.data.widget.WidgetDepartureRow
import com.example.pulsetransit.ui.resources.*

/**
 * The next departures from a favourite stop, one row per line and destination with its next few
 * times, as the board's stop panel shows them. Set to a stop in [WidgetConfigActivity]; until then,
 * or once that stop is removed, it shows the first favourite.
 */
class DeparturesWidget : BoardWidget<DeparturesView>() {
    override suspend fun load(
        context: Context,
        configuredId: String?,
    ): DeparturesView =
        when (val state = source.departures(configuredId, maxRows = MAX_ROWS, timesPerRow = MAX_TIMES)) {
            // T5 inside the widget; the title falls back to the app's name.
            DeparturesWidgetState.NoStops ->
                DeparturesView(
                    title = appName(context),
                    message = message(WidgetIcon.ADD_PLACE, Res.string.widget_no_stops, Res.string.widget_no_stops_body, WidgetTarget.ADD_STOP),
                    labels = labels(),
                )
            is DeparturesWidgetState.Board ->
                DeparturesView(
                    title = state.stop.name,
                    // A cached prediction is no longer live, so it loses the realtime accent.
                    rows = state.rows.map { it.toView(live = state.freshness.staleSinceMillis == null) },
                    message =
                        when {
                            state.rows.isNotEmpty() -> null
                            // Nothing cached to show: the message replaces the rows; refresh retries.
                            state.freshness.failed ->
                                message(WidgetIcon.OFFLINE, Res.string.widget_unreachable, Res.string.widget_unreachable_departures_body)
                            // Empty, not an error: with the normal Updated line, it reads as checked.
                            else -> message(WidgetIcon.SCHEDULE, Res.string.no_departures)
                        },
                    status = status(state.freshness),
                    labels = labels(),
                )
        }

    private fun WidgetDepartureRow.toView(live: Boolean) =
        RowView(
            line = line,
            transportMode = transportMode,
            destination = destination,
            platform = platform,
            times =
                departures.zip(departureMillis).map { (departure, millis) ->
                    TimeView(time(millis), departure.isRealtime && live, departure.isCancelled)
                },
        )

    override fun encode(view: DeparturesView) = json.encodeToString(DeparturesView.serializer(), view)

    override fun decode(json: String) = BoardWidget.json.decodeFromString(DeparturesView.serializer(), json)

    /**
     * One row per line and destination: from 4×2 on one line with the platform and three times, and
     * rules between rows when there is room for them; at 2×2 three rows of two lines (pill and the
     * next time, the destination under it) with the footer giving way.
     */
    @Composable
    override fun Content(view: DeparturesView?) {
        val size = LocalSize.current
        val small = size.width < SMALL_WIDTH_DP.dp
        val labels = view?.labels ?: WidgetLabels()
        WidgetCard(view?.message?.target ?: WidgetTarget.BOARD) {
            WidgetHeader(view?.title.orEmpty(), WidgetKind.DEPARTURES, labels.refresh)
            VerticalGap(6)
            Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                val message = view?.message
                when {
                    view == null -> Unit
                    message != null -> WidgetMessage(message)
                    small ->
                        view.rows.take(SMALL_ROWS).forEach { row ->
                            StackedRow(row, labels.realtime)
                            VerticalGap(6)
                        }
                    else -> {
                        val rows = ((size.height.value - CHROME_DP) / ROW_DP).toInt().coerceIn(1, MAX_ROWS)
                        val ruled = size.height >= RULED_FROM_DP.dp
                        val times = if (size.width < THREE_TIMES_FROM_DP.dp) 2 else MAX_TIMES
                        view.rows.take(rows).forEachIndexed { index, row ->
                            if (index > 0) {
                                if (ruled) Rule() else VerticalGap(4)
                            }
                            DepartureRow(row, times, labels.realtime, showPlatform = size.width >= PLATFORM_FROM_DP.dp)
                        }
                    }
                }
            }
            // At 2×2 the footer gives way, unless the data is not live: then it is the only hint.
            view?.status?.takeIf { !small || it.isWarning || view.message != null }?.let { WidgetStatus(it) }
        }
    }

    private companion object {
        const val MAX_ROWS = 8
        const val MAX_TIMES = 3
        const val SMALL_ROWS = 3
        const val CHROME_DP = 72f
        const val ROW_DP = 28f
        const val SMALL_WIDTH_DP = 200
        const val PLATFORM_FROM_DP = 240
        const val THREE_TIMES_FROM_DP = 280
        const val RULED_FROM_DP = 260
    }
}

@Composable
private fun DepartureRow(
    row: RowView,
    times: Int,
    realtimeDescription: String,
    showPlatform: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val first = row.times.firstOrNull()
        LineBadge(row.line, row.transportMode, cancelled = first?.isCancelled == true)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = row.destination,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 1,
        )
        if (showPlatform && row.platform != null) {
            Text(
                text = row.platform,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        row.times.take(times).forEachIndexed { index, time ->
            if (index > 0) Spacer(GlanceModifier.width(6.dp))
            DepartureTime(time, realtimeDescription, emphasized = index == 0)
        }
    }
}

/** A 2×2's row: the pill and the next time, the destination under them. */
@Composable
private fun StackedRow(
    row: RowView,
    realtimeDescription: String,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val first = row.times.firstOrNull()
            LineBadge(row.line, row.transportMode, cancelled = first?.isCancelled == true)
            Spacer(GlanceModifier.defaultWeight())
            first?.let { DepartureTime(it, realtimeDescription, emphasized = true) }
        }
        Text(
            text = row.destination,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Rule() {
    Box(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GlanceTheme.colors.outline),
    ) {}
}

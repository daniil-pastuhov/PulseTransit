package com.example.pulsetransit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.pulsetransit.data.widget.DeparturesWidgetState
import com.example.pulsetransit.data.widget.WidgetDepartureRow
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.getString

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
            DeparturesWidgetState.NoStops ->
                DeparturesView(
                    title = appName(context),
                    message = getString(Res.string.widget_no_stops),
                    status = "",
                    labels = labels(),
                )
            is DeparturesWidgetState.Board -> {
                val (status, warning) = status(state.freshness)
                DeparturesView(
                    title = state.stop.name,
                    rows = state.rows.map { it.toView() },
                    message =
                        when {
                            state.rows.isNotEmpty() -> null
                            state.freshness.failed -> getString(Res.string.widget_unreachable)
                            else -> getString(Res.string.no_departures)
                        },
                    status = status,
                    statusIsWarning = warning,
                    labels = labels(),
                )
            }
        }

    private fun WidgetDepartureRow.toView() =
        RowView(
            line = line,
            transportMode = transportMode,
            destination = destination,
            platform = platform,
            times =
                departures.zip(departureMillis).map { (departure, millis) ->
                    TimeView(time(millis), departure.isRealtime, departure.isCancelled)
                },
        )

    override fun encode(view: DeparturesView) = json.encodeToString(DeparturesView.serializer(), view)

    override fun decode(json: String) = BoardWidget.json.decodeFromString(DeparturesView.serializer(), json)

    @Composable
    override fun Content(view: DeparturesView?) {
        val size = LocalSize.current
        // Header, footer and padding take about 64dp; each row about 26dp.
        val rows = ((size.height.value - CHROME_DP) / ROW_DP).toInt().coerceIn(1, MAX_ROWS)
        val times =
            when {
                size.width < 200.dp -> 1
                size.width < 280.dp -> 2
                else -> MAX_TIMES
            }
        val labels = view?.labels ?: WidgetLabels()
        WidgetCard {
            WidgetHeader(view?.title.orEmpty(), WidgetKind.DEPARTURES, labels.refresh)
            VerticalGap(6)
            Column(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                when {
                    view == null -> Unit
                    view.message != null -> WidgetMessage(view.message)
                    else ->
                        view.rows.take(rows).forEach { row ->
                            DepartureRow(row, times, labels.realtime, showPlatform = size.width >= 240.dp)
                            VerticalGap(4)
                        }
                }
            }
            if (!view?.status.isNullOrEmpty()) WidgetStatus(view.status, view.statusIsWarning)
        }
    }

    private companion object {
        const val MAX_ROWS = 8
        const val MAX_TIMES = 3
        const val CHROME_DP = 64f
        const val ROW_DP = 26f
    }
}

@Composable
private fun DepartureRow(
    row: RowView,
    times: Int,
    realtimeDescription: String,
    showPlatform: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

package com.example.pulsetransit.widget

import android.content.Context
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.pulsetransit.R
import com.example.pulsetransit.data.Precip
import com.example.pulsetransit.data.Sky
import com.example.pulsetransit.data.parseWeatherSymbol
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.getString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The board's header in a widget: the clock and date, the current weather, and the next departure
 * from the first favourite stop. The clock is a TextClock, so it keeps time between refreshes.
 */
class AtAGlanceWidget : BoardWidget<GlanceView>() {
    override suspend fun load(
        context: Context,
        configuredId: String?,
    ): GlanceView {
        val state = source.glance()
        val next = state.next
        val rows =
            state.upcoming.map { row ->
                RowView(
                    line = row.line,
                    transportMode = row.transportMode,
                    destination = row.destination,
                    platform = row.platform,
                    times = row.departureMillis.take(1).map { TimeView(time(it), row.next.isRealtime, row.next.isCancelled) },
                )
            }
        return GlanceView(
            weatherEmoji = state.weather?.let { weatherEmoji(it.symbolCode) },
            temperature = state.weather?.let { "${it.temperature.roundToInt()}°" },
            stopName = state.stop?.name,
            next = rows.firstOrNull(),
            later = rows.drop(1),
            message =
                when {
                    state.stop == null -> getString(Res.string.widget_no_stops)
                    next == null && state.freshness.failed -> getString(Res.string.widget_unreachable)
                    next == null -> getString(Res.string.no_departures)
                    else -> null
                },
            labels = labels(),
        )
    }

    override fun encode(view: GlanceView) = json.encodeToString(GlanceView.serializer(), view)

    override fun decode(json: String) = BoardWidget.json.decodeFromString(GlanceView.serializer(), json)

    @Composable
    override fun Content(view: GlanceView?) {
        val size = LocalSize.current
        val tall = size.height >= 100.dp
        val wide = size.width >= 300.dp
        WidgetCard {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The host sizes a RemoteViews' box without measuring it, so the clock gets a width
                // with room for "00:00" at its size; left alone it was cut off.
                Column(modifier = GlanceModifier.width(if (tall) LARGE_CLOCK_WIDTH_DP.dp else SMALL_CLOCK_WIDTH_DP.dp)) {
                    AndroidRemoteViews(clockViews(LocalContext.current, large = tall), modifier = GlanceModifier.fillMaxWidth())
                    Text(
                        text = today(),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    // Short but wide: the next departure fits between the clock and the weather.
                    if (!tall && wide) NextDeparture(view)
                }
                if (view?.temperature != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = view.weatherEmoji.orEmpty(), style = TextStyle(fontSize = 24.sp), maxLines = 1)
                        Text(
                            text = view.temperature,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (tall) {
                VerticalGap(8)
                NextDeparture(view)
                // The rest of a tall widget lists what comes after, as many rows as fit.
                val fits = ((size.height - TALL_HEADER_DP.dp - NEXT_ROW_DP.dp) / LATER_ROW_DP.dp).toInt()
                view?.later?.take(fits.coerceAtLeast(0))?.forEach { row ->
                    VerticalGap(6)
                    LaterDeparture(row, view.labels.realtime)
                }
            }
        }
    }
}

@Composable
private fun NextDeparture(view: GlanceView?) {
    if (view == null) return
    val next = view.next
    if (next == null) {
        view.message?.let { WidgetMessage(it) }
        return
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LineBadge(next.line, next.transportMode, cancelled = next.times.firstOrNull()?.isCancelled == true)
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = next.destination,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                maxLines = 1,
            )
            view.stopName?.let {
                Text(text = it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
            }
        }
        next.times.firstOrNull()?.let { DepartureTime(it, view.labels.realtime, emphasized = true) }
    }
}

@Composable
private fun LaterDeparture(
    row: RowView,
    realtimeLabel: String,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LineBadge(row.line, row.transportMode, cancelled = row.times.firstOrNull()?.isCancelled == true)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = row.destination,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 1,
        )
        row.times.firstOrNull()?.let { DepartureTime(it, realtimeLabel, emphasized = false) }
    }
}

/** Heights used to fit the later rows: the clock and date with padding, the next departure, one later row. */
private const val TALL_HEADER_DP = 96
private const val NEXT_ROW_DP = 44
private const val LATER_ROW_DP = 34

private const val LARGE_CLOCK_SP = 40f
private const val LARGE_CLOCK_WIDTH_DP = 150
private const val SMALL_CLOCK_WIDTH_DP = 115
private const val SMALL_CLOCK_SP = 30f

/** The live clock. */
private fun clockViews(
    context: Context,
    large: Boolean,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_clock).apply {
        setTextViewTextSize(R.id.widget_clock_time, TypedValue.COMPLEX_UNIT_SP, if (large) LARGE_CLOCK_SP else SMALL_CLOCK_SP)
    }

/**
 * Today in the locale's own order ("Wed 23 Sep", "Wed, Sep 23"). Drawn at each refresh, so after
 * midnight it may show yesterday for up to one refresh interval.
 */
private fun today(): String {
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
}

/** A MET symbol as one emoji; the board draws its own icons, which a widget cannot. */
@Suppress("CyclomaticComplexMethod") // One case per kind of weather, like parseWeatherSymbol.
internal fun weatherEmoji(symbolCode: String): String {
    val symbol = parseWeatherSymbol(symbolCode)
    return when {
        !symbol.isKnown -> "🌡️"
        symbol.thunder -> "⛈️"
        symbol.precip == Precip.SNOW || symbol.precip == Precip.SLEET -> "🌨️"
        symbol.precip == Precip.RAIN && symbol.showers && !symbol.isNight -> "🌦️"
        symbol.precip == Precip.RAIN -> "🌧️"
        symbol.sky == Sky.FOG -> "🌫️"
        symbol.sky == Sky.CLEAR -> if (symbol.isNight) "🌙" else "☀️"
        symbol.sky == Sky.FAIR -> if (symbol.isNight) "🌙" else "🌤️"
        symbol.sky == Sky.PARTLY_CLOUDY -> if (symbol.isNight) "☁️" else "⛅"
        else -> "☁️"
    }
}

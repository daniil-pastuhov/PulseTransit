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
        return GlanceView(
            weatherEmoji = state.weather?.let { weatherEmoji(it.symbolCode) },
            temperature = state.weather?.let { "${it.temperature.roundToInt()}°" },
            stopName = state.stop?.name,
            next =
                next?.let { row ->
                    RowView(
                        line = row.line,
                        transportMode = row.transportMode,
                        destination = row.destination,
                        platform = row.platform,
                        times = row.departureMillis.take(1).map { TimeView(time(it), row.next.isRealtime, row.next.isCancelled) },
                    )
                },
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
                AndroidRemoteViews(clockViews(LocalContext.current, large = tall))
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

private const val LARGE_CLOCK_SP = 40f
private const val SMALL_CLOCK_SP = 30f

/** The live clock and date, with the date in the locale's own order ("Wed 23 Sep", "Wed, Sep 23"). */
private fun clockViews(
    context: Context,
    large: Boolean,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_clock).apply {
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")
        setCharSequence(R.id.widget_clock_date, "setFormat12Hour", datePattern)
        setCharSequence(R.id.widget_clock_date, "setFormat24Hour", datePattern)
        setTextViewTextSize(R.id.widget_clock_time, TypedValue.COMPLEX_UNIT_SP, if (large) LARGE_CLOCK_SP else SMALL_CLOCK_SP)
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

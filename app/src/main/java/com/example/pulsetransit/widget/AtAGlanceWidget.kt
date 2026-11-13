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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.pulsetransit.R
import com.example.pulsetransit.data.Intensity
import com.example.pulsetransit.data.NORWAY_TIME_ZONE_ID
import com.example.pulsetransit.data.Precip
import com.example.pulsetransit.data.Sky
import com.example.pulsetransit.data.parseWeatherSymbol
import com.example.pulsetransit.ui.resources.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
        val live = state.freshness.staleSinceMillis == null
        val rows =
            state.upcoming.map { row ->
                RowView(
                    line = row.line,
                    transportMode = row.transportMode,
                    destination = row.destination,
                    platform = row.platform,
                    // A cached prediction is no longer live, so it loses the realtime accent.
                    times = row.departureMillis.take(1).map { TimeView(time(it), row.next.isRealtime && live, row.next.isCancelled) },
                )
            }
        return GlanceView(
            weatherIcon = state.weather?.let { weatherWidgetIcon(it.symbolCode) },
            temperature = state.weather?.let { "${it.temperature.roundToInt()}°" },
            stopName = state.stop?.name,
            next = rows.firstOrNull(),
            later = rows.drop(1),
            message =
                when {
                    // T5 inside the widget: the message takes the departure's place; clock and weather still work.
                    state.stop == null -> message(WidgetIcon.ADD_PLACE, Res.string.widget_no_stops, target = WidgetTarget.ADD_STOP)
                    next == null && state.freshness.failed -> message(WidgetIcon.OFFLINE, Res.string.widget_unreachable)
                    next == null -> message(WidgetIcon.SCHEDULE, Res.string.no_departures)
                    else -> null
                },
            // Only a warning earns a footer here: the clock already says the widget is current.
            status = status(state.freshness)?.takeIf { it.isWarning },
            labels = labels(),
        )
    }

    override fun encode(view: GlanceView) = json.encodeToString(GlanceView.serializer(), view)

    override fun decode(json: String) = BoardWidget.json.decodeFromString(GlanceView.serializer(), json)

    /**
     * Four shapes by size: a 4×1 strip (clock, the next departure with the date under it, weather);
     * a 2×2 stack (clock, date and temperature on one line, the next departure in two lines); from
     * 4×2 the clock and date beside the weather, the next departure on a tinted container, and at
     * 4×4 the departures after it, as many as fit.
     */
    @Composable
    override fun Content(view: GlanceView?) {
        val size = LocalSize.current
        WidgetCard(view?.message?.target ?: WidgetTarget.BOARD) {
            when {
                size.height < STRIP_BELOW_DP.dp && size.width >= SMALL_WIDTH_DP.dp -> Strip(view)
                size.width < SMALL_WIDTH_DP.dp -> Small(view)
                else -> {
                    Medium(view)
                    if (size.height >= TALL_FROM_DP.dp) {
                        // The rest of a tall widget lists what comes after, as many rows as fit.
                        val fits = ((size.height - TALL_HEADER_DP.dp - NEXT_ROW_DP.dp) / LATER_ROW_DP.dp).toInt()
                        view?.later?.take(fits.coerceAtLeast(0))?.forEach { row ->
                            VerticalGap(6)
                            LaterDeparture(row, view.labels.realtime)
                        }
                    }
                }
            }
            view?.status?.let {
                Spacer(GlanceModifier.defaultWeight())
                WidgetStatus(it)
            }
        }
    }
}

@Composable
private fun Clock(large: Boolean) {
    // The host sizes a RemoteViews' box without measuring it, so the clock gets a width with room
    // for "00:00" at its size; left alone it was cut off.
    Column(modifier = GlanceModifier.width(if (large) LARGE_CLOCK_WIDTH_DP.dp else SMALL_CLOCK_WIDTH_DP.dp)) {
        AndroidRemoteViews(clockViews(LocalContext.current, large = large), modifier = GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun Weather(view: GlanceView?) {
    val icon = view?.weatherIcon ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        WidgetIconImage(icon, GlanceTheme.colors.onSurface, 22)
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = view.temperature.orEmpty(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
private fun DateText(text: String = today()) {
    Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp), maxLines = 1)
}

/** 4×1: the stop's name gives way to the date, so the strip still says which day it is. */
@Composable
private fun ColumnScope.Strip(view: GlanceView?) {
    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
        Clock(large = false)
        Spacer(GlanceModifier.width(8.dp))
        val next = view?.next
        val message = view?.message
        if (next != null) {
            LineBadge(next.line, next.transportMode, cancelled = next.times.firstOrNull()?.isCancelled == true)
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = next.destination, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1)
                DateText()
            }
            next.times.firstOrNull()?.let { DepartureTime(it, view.labels.realtime, emphasized = true) }
        } else {
            Column(modifier = GlanceModifier.defaultWeight()) {
                DateText()
                message?.let { Text(it.title, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1) }
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Weather(view)
    }
}

/** 2×2: clock; date and temperature on one line; the next departure stacked. */
@Composable
private fun Small(view: GlanceView?) {
    Clock(large = false)
    Row(verticalAlignment = Alignment.CenterVertically) {
        DateText()
        if (view?.weatherIcon != null) {
            Text(" · ", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
            Weather(view)
        }
    }
    VerticalGap(6)
    val next = view?.next
    val message = view?.message
    when {
        next != null ->
            NextContainer {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(next.line, next.transportMode, cancelled = next.times.firstOrNull()?.isCancelled == true)
                    Spacer(GlanceModifier.defaultWeight())
                    next.times.firstOrNull()?.let { DepartureTime(it, view.labels.realtime, emphasized = true) }
                }
                Text(text = next.destination, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
            }
        message != null -> WidgetMessage(message)
    }
}

/** 4×2 and taller: the clock and date beside the weather, then the next departure as the answer. */
@Composable
private fun Medium(view: GlanceView?) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Clock(large = true)
            DateText()
        }
        Weather(view)
    }
    VerticalGap(8)
    val next = view?.next
    val message = view?.message
    when {
        next != null ->
            NextContainer {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(next.line, next.transportMode, cancelled = next.times.firstOrNull()?.isCancelled == true)
                    Spacer(GlanceModifier.width(8.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(text = next.destination, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1)
                        view.stopName?.let {
                            Text(text = it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                        }
                    }
                    next.times.firstOrNull()?.let { DepartureTime(it, view.labels.realtime, emphasized = true) }
                }
            }
        message != null -> WidgetMessage(message)
    }
}

/** The next departure on a tinted container, so it reads as the answer, not one more line of the clock. */
@Composable
private fun NextContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .cornerRadius(10.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun LaterDeparture(
    row: RowView,
    realtimeLabel: String,
) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
private const val NEXT_ROW_DP = 52
private const val LATER_ROW_DP = 30
private const val STRIP_BELOW_DP = 140
private const val SMALL_WIDTH_DP = 200
private const val TALL_FROM_DP = 200

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
 * Today in Norway, in the locale's own order ("Wed 23 Sep", "Wed, Sep 23"). Drawn at each refresh, so after
 * midnight it may show yesterday for up to one refresh interval.
 */
private fun today(): String {
    val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")
    return SimpleDateFormat(pattern, Locale.getDefault())
        .apply { timeZone = TimeZone.getTimeZone(NORWAY_TIME_ZONE_ID) }
        .format(Date())
}

/**
 * A MET symbol as one of the bundled icons, by the same rules as the board's `weatherIcon` in :ui
 * (which draws Compose vectors, which a widget cannot).
 */
@Suppress("CyclomaticComplexMethod") // One case per kind of weather, like parseWeatherSymbol.
internal fun weatherWidgetIcon(symbolCode: String): WidgetIcon {
    val symbol = parseWeatherSymbol(symbolCode)
    return when {
        !symbol.isKnown -> WidgetIcon.CLOUD
        symbol.thunder -> WidgetIcon.THUNDER
        symbol.precip == Precip.SNOW -> if (symbol.intensity == Intensity.HEAVY) WidgetIcon.SNOW_HEAVY else WidgetIcon.SNOW
        symbol.precip == Precip.SLEET -> WidgetIcon.SLEET
        symbol.precip == Precip.RAIN ->
            when (symbol.intensity) {
                Intensity.LIGHT -> WidgetIcon.RAIN_LIGHT
                Intensity.MODERATE -> WidgetIcon.RAIN
                Intensity.HEAVY -> WidgetIcon.RAIN_HEAVY
            }
        symbol.sky == Sky.FOG -> WidgetIcon.FOG
        symbol.sky == Sky.CLEAR -> if (symbol.isNight) WidgetIcon.CLEAR_NIGHT else WidgetIcon.CLEAR_DAY
        symbol.sky == Sky.FAIR || symbol.sky == Sky.PARTLY_CLOUDY -> if (symbol.isNight) WidgetIcon.PARTLY_CLOUDY_NIGHT else WidgetIcon.PARTLY_CLOUDY_DAY
        else -> WidgetIcon.CLOUD
    }
}

package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.NORWAY_TIME_ZONE_ID
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.parseWeatherSymbol
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.theme.type
import com.example.pulsetransit.ui.components.WeatherIcon
import com.example.pulsetransit.ui.components.weatherLabel
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.change_date_style
import com.example.pulsetransit.ui.resources.more_actions
import com.example.pulsetransit.ui.resources.norway_time
import com.example.pulsetransit.ui.resources.search_route
import com.example.pulsetransit.ui.resources.settings_title
import com.example.pulsetransit.ui.resources.weather_now
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// The board's header. On a phone: the clock with the actions beside it, and the weather in a card
// below. From 700 dp (wall, fold, desktop) it is one row: a larger clock, the weather, the actions.

/** Width from which the header is one row with a larger clock. */
internal const val WIDE_HEADER_MIN_WIDTH_DP = 700f

private val ActionSize = 48.dp
private val MinClockWidth = 72.dp
private val WideClockSize = 64.sp
private val NowIconSize = 28.dp
private val HourIconSize = 18.dp
private val HourWidth = 40.dp
private val WideHourWidth = 56.dp

@Composable
internal fun BoardHeader(
    uiState: StopBoardViewModel.UiState,
    wide: Boolean,
    onCycleDateStyle: () -> Unit,
    onSearchRoute: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weather = if (uiState.showWeather) uiState.weather else emptyList()
    if (wide) {
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = FjordDimens.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FjordDimens.space6),
        ) {
            HeaderClock(uiState, wide = true, onClick = onCycleDateStyle)
            Box(modifier = Modifier.weight(1f)) {
                if (weather.isNotEmpty()) WeatherStrip(weather, wide = true)
            }
            HeaderActions(collapsed = false, onSearchRoute = onSearchRoute, onOpenSettings = onOpenSettings)
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = FjordDimens.space1)) {
            // The buttons keep their room; only where even the time would not fit beside them do
            // they fold into one menu button.
            val collapse = maxWidth < ActionSize * 2 + MinClockWidth
            val clockMax = (maxWidth - if (collapse) ActionSize else ActionSize * 2).coerceAtLeast(0.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderClock(uiState, wide = false, onClick = onCycleDateStyle, modifier = Modifier.widthIn(max = clockMax))
                Box(modifier = Modifier.weight(1f))
                HeaderActions(collapsed = collapse, onSearchRoute = onSearchRoute, onOpenSettings = onOpenSettings)
            }
        }
        if (weather.isNotEmpty()) WeatherStrip(weather, wide = false)
    }
}

/** Find a route and Settings; [collapsed], behind one menu button. */
@Composable
private fun HeaderActions(
    collapsed: Boolean,
    onSearchRoute: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onBackground
    val actions =
        listOf(
            Triple(PulseIcons.Search, Res.string.search_route, onSearchRoute),
            Triple(PulseIcons.Settings, Res.string.settings_title, onOpenSettings),
        )
    if (collapsed) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(PulseIcons.MoreVert, contentDescription = stringResource(Res.string.more_actions), tint = tint)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                actions.forEach { (icon, label, action) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        onClick = {
                            expanded = false
                            action()
                        },
                    )
                }
            }
        }
        return
    }
    Row {
        actions.forEach { (icon, label, action) ->
            IconButton(onClick = action) {
                Icon(icon, contentDescription = stringResource(label), tint = tint)
            }
        }
    }
}

/**
 * The clock with the date in the user's chosen [DateDisplayStyle]; tapping it cycles the styles.
 * Where the chosen style doesn't fit, the time alone, then a smaller time, so the date gives way
 * rather than pushing the buttons out of the header.
 */
@Composable
private fun HeaderClock(
    uiState: StopBoardViewModel.UiState,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clock = PulseTheme.type.clock
    val timeStyle =
        (if (wide) clock.copy(fontSize = WideClockSize, lineHeight = WideClockSize) else clock)
            .copy(color = MaterialTheme.colorScheme.primary)
    // The date strings arrive with the first clock tick; until then show the time alone.
    val style = if (uiState.currentDayOfMonth.isEmpty()) DateDisplayStyle.HIDDEN else uiState.dateStyle
    val clickModifier =
        modifier
            .clip(RoundedCornerShape(FjordDimens.radiusSm))
            .clickable(onClickLabel = stringResource(Res.string.change_date_style), onClick = onClick)
    FirstThatFits(
        clickModifier,
        { HeaderClockContent(uiState, style, timeStyle, wide) },
        { HeaderClockContent(uiState, DateDisplayStyle.HIDDEN, timeStyle, wide) },
        { HeaderClockContent(uiState, DateDisplayStyle.HIDDEN, timeStyle.copy(fontSize = timeStyle.fontSize * 0.7f), wide) },
    )
}

/** The clock drawn in one [DateDisplayStyle]; the weekday is the emphasis, the rest context. */
@Composable
private fun HeaderClockContent(
    uiState: StopBoardViewModel.UiState,
    style: DateDisplayStyle,
    timeStyle: TextStyle,
    wide: Boolean,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onBackground
    val dateStyle = if (wide) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyMedium
    // When the device's clock reads otherwise, "Norway time" after the clock says whose time every time is.
    val zoneLabel = if (uiState.isNorwayTimeLabelled) stringResource(Res.string.norway_time) else null
    val zoneStyle =
        MaterialTheme.typography.labelMedium
            .toSpanStyle()
            .copy(color = secondary)
    val time: @Composable (Modifier) -> Unit = { m ->
        Text(
            buildAnnotatedString {
                append(uiState.currentTime)
                if (zoneLabel != null) withStyle(zoneStyle) { append(" $zoneLabel") }
            },
            style = timeStyle,
            maxLines = 1,
            modifier = m,
        )
    }
    when (style) {
        DateDisplayStyle.HIDDEN -> time(Modifier)

        // "19:37  Fri 2 Oct", the date on the clock's baseline.
        DateDisplayStyle.BESIDE ->
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                time(Modifier.alignByBaseline())
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(uiState.currentWeekday) }
                        append(" ${uiState.currentDayOfMonth} ${uiState.currentMonth}")
                    },
                    style = dateStyle,
                    color = secondary,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }

        DateDisplayStyle.STACKED ->
            Column {
                time(Modifier)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(uiState.currentWeekday) }
                        append(" ${uiState.currentDayOfMonth} ${uiState.currentMonth}")
                    },
                    style = dateStyle,
                    color = secondary,
                    maxLines = 1,
                )
            }

        DateDisplayStyle.TILE ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space4)) {
                time(Modifier)
                Column(
                    modifier =
                        Modifier
                            .width(48.dp)
                            .clip(RoundedCornerShape(FjordDimens.radiusSm))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(FjordDimens.radiusSm)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        uiState.currentMonth.uppercase(),
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(uiState.currentDayOfMonth, style = MaterialTheme.typography.headlineSmall, color = strong)
                }
            }

        DateDisplayStyle.LONG ->
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                time(Modifier.alignByBaseline())
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strong)) { append(uiState.currentWeekdayLong) }
                        append(" ${uiState.currentDayOfMonth}. ${uiState.currentMonthLong}")
                    },
                    style = if (wide) dateStyle else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = secondary,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
    }
}

/**
 * The weather now (icon, temperature, condition) and the next hours. On a phone it sits in a card
 * under the clock; wide, it is bare between the clock and the actions. Only whole hours that fit
 * are shown, so a label is never cut ("19:0").
 */
@Composable
private fun WeatherStrip(
    weather: List<WeatherInfo>,
    wide: Boolean,
) {
    val now = weather.first()
    val nowSymbol = remember(now.symbolCode) { parseWeatherSymbol(now.symbolCode) }
    val nowLabel = weatherLabel(nowSymbol)
    val nowTemperature = temperatureText(now.temperature)
    val nowDescription = stringResource(Res.string.weather_now, nowTemperature, nowLabel)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (wide) {
                        Modifier
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(FjordDimens.radiusMd))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = FjordDimens.space3, vertical = FjordDimens.space2)
                    },
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) FjordDimens.space6 else FjordDimens.space3),
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = nowDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2),
        ) {
            WeatherIcon(nowSymbol, modifier = Modifier.size(NowIconSize))
            Column {
                Text(nowTemperature, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(nowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val itemWidth = (if (wide) WideHourWidth else HourWidth) * LocalDensity.current.fontScale
            val count = (maxWidth / itemWidth).toInt().coerceAtMost(weather.size - 1).coerceAtLeast(0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (wide) Arrangement.spacedBy(FjordDimens.space6) else Arrangement.SpaceBetween,
            ) {
                weather.drop(1).take(count).forEach { HourWeather(it, wide) }
            }
        }
    }
}

@Composable
private fun HourWeather(
    weather: WeatherInfo,
    wide: Boolean,
) {
    val symbol = remember(weather.symbolCode) { parseWeatherSymbol(weather.symbolCode) }
    val label = weatherLabel(symbol)
    val hour = localHour(weather.time)
    val temperature = temperatureText(weather.temperature)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$hour:00, $temperature, $label" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            if (wide) "$hour:00" else hour,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        WeatherIcon(symbol, modifier = Modifier.size(HourIconSize))
        Text(
            temperature,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

/** "8°", "−12°" with a true minus sign. */
private fun temperatureText(celsius: Double): String {
    val rounded = celsius.roundToInt()
    return if (rounded < 0) "−${-rounded}°" else "$rounded°"
}

/**
 * The hour of a MET time ("2026-11-13T19:00:00Z") in Norway, two digits. MET sends UTC, so the
 * timestamp is read as UTC and shown in Norway time, like every time the board shows.
 */
private fun localHour(time: String): String =
    try {
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        utc.parse(time.substring(0, MET_TIME_LENGTH))?.let { SimpleDateFormat("HH", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(NORWAY_TIME_ZONE_ID) }.format(it) } ?: "??"
    } catch (_: Exception) {
        "??"
    }

private const val MET_TIME_LENGTH = 19

/**
 * The first of [contents] that fits the width offered at its natural size, or the last one, held to
 * that width, when none does. Each is measured before it is chosen, so keep them few and cheap.
 */
@Composable
private fun FirstThatFits(
    modifier: Modifier,
    vararg contents: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val natural = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        var chosen: List<Placeable>? = null
        for ((index, content) in contents.withIndex()) {
            val placeables = subcompose(index, content).map { it.measure(natural) }
            if ((placeables.maxOfOrNull { it.width } ?: 0) <= constraints.maxWidth) {
                chosen = placeables
                break
            }
        }
        val placeables =
            chosen ?: subcompose(contents.size, contents.last()).map { it.measure(constraints.copy(minWidth = 0)) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

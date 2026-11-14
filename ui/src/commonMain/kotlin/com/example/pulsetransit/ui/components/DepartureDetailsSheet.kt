package com.example.pulsetransit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureCountdown
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.clockTimeText
import com.example.pulsetransit.data.departureClock
import com.example.pulsetransit.data.departureCountdown
import com.example.pulsetransit.data.minutesBetween
import com.example.pulsetransit.data.minutesUntil
import com.example.pulsetransit.data.nextRunningDeparture
import com.example.pulsetransit.data.typicalHeadwayMinutes
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.theme.type
import com.example.pulsetransit.ui.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.minutes

/** How long the details stay open untouched: a wall display left showing them would never return to the board. */
internal val AUTO_CLOSE_AFTER = 2.minutes

internal const val CLOCK_TICK_MILLIS = 1000L
private const val MINUTES_PER_HOUR = 60L

// Size of the "+1" next-day marker relative to the time it follows, as on the board.
private const val DAY_OFFSET_FONT_SCALE = 0.6f

/** Wide enough for the departures and the selected trip's stops side by side. */
private val TWO_COLUMN_MIN_WIDTH = 520.dp

/**
 * Every loaded departure of one line to one destination at [stopName], opened by tapping its row:
 * the board shows only the next few. The header names the line, who runs it and how often; each
 * departure has its platform and countdown; and the selected one's remaining stops are fetched
 * with [loadJourney] while the sheet is open. Filtering the line out lives here too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartureDetailsSheet(
    stopName: String,
    departures: List<Departure>,
    showDelays: Boolean,
    showMessages: Boolean,
    onFilterOut: (String) -> Unit,
    loadJourney: suspend (Departure) -> List<JourneyStop>,
    onDismiss: () -> Unit,
) {
    val first = departures.firstOrNull() ?: return
    // Kept by trip rather than position, so a refresh that drops the departures already gone
    // leaves the same one selected.
    var selectedJourneyId by rememberSaveable { mutableStateOf(departures.firstOrNull { !it.isCancelled }?.serviceJourneyId) }
    val selected = departures.firstOrNull { it.serviceJourneyId == selectedJourneyId } ?: first

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // Restarted by picking a departure, so only an untouched sheet closes itself.
    LaunchedEffect(selectedJourneyId) {
        delay(AUTO_CLOSE_AFTER)
        currentOnDismiss()
    }
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    // Null while loading. Keyed by trip, so a refresh of the same departure keeps its stops; "Try
    // again" bumps the attempt to ask once more.
    var stopsAttempt by remember { mutableIntStateOf(0) }
    val stops by produceState<List<JourneyStop>?>(null, selected.serviceJourneyId, stopsAttempt) {
        value = null
        value = loadJourney(selected)
    }

    AdaptiveSheet(onDismiss = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            DetailsHeader(first, selected, departures, stopName, nowMillis, showMessages)
            if (selected.isCancelled) {
                Spacer(modifier = Modifier.height(FjordDimens.space4))
                CancelledCallout(selected, departures, nowMillis) { next -> selectedJourneyId = next.serviceJourneyId }
            }
            Spacer(modifier = Modifier.height(FjordDimens.space4))
            BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
                val departuresList: @Composable (Modifier) -> Unit = { modifier ->
                    DeparturesColumn(departures, selected, nowMillis, showDelays, modifier) {
                        selectedJourneyId = it.serviceJourneyId
                    }
                }
                val route: @Composable (Modifier) -> Unit = { modifier ->
                    RouteColumn(selected, stops, nowMillis, modifier, onRetry = { stopsAttempt++ })
                }
                if (maxWidth >= TWO_COLUMN_MIN_WIDTH) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        departuresList(Modifier.weight(1f))
                        route(Modifier.weight(1f))
                    }
                } else {
                    Column {
                        departuresList(Modifier.weight(1f, fill = false))
                        Spacer(modifier = Modifier.height(16.dp))
                        route(Modifier.weight(1f, fill = false))
                    }
                }
            }
            Spacer(modifier = Modifier.height(FjordDimens.space4))
            // Hiding acts at once: the sheet closes and the board offers Undo.
            OutlinedButton(
                onClick = {
                    onFilterOut(first.line)
                    onDismiss()
                },
            ) {
                Icon(PulseIcons.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(FjordDimens.space2))
                Text(stringResource(Res.string.filter_out_line, first.line))
            }
            Text(
                stringResource(Res.string.filter_out_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = FjordDimens.space1),
            )
        }
    }
}

/**
 * Line pill and destination (wrapping: details are where cut board text is read in full), where it
 * leaves from, then the selected departure large: its countdown, clock time, whether it is live and
 * how often the line runs. The line's full name and operator, and any notices, follow.
 */
@Composable
private fun DetailsHeader(
    first: Departure,
    selected: Departure,
    departures: List<Departure>,
    stopName: String,
    nowMillis: Long,
    showMessages: Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
        LinePill(line = first.line, mode = first.transportMode, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = first.destination, style = MaterialTheme.typography.headlineSmall)
            val from = stringResource(Res.string.details_from, stopName)
            val platform = selected.platform?.let { stringResource(Res.string.details_platform, it) }
            Text(text = listOfNotNull(from, platform).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    }
    Spacer(modifier = Modifier.height(FjordDimens.space3))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
        val countdown =
            when {
                selected.isCancelled -> stringResource(Res.string.cancelled)
                departureCountdown(selected.expectedTime, nowMillis) == DepartureCountdown.Now -> stringResource(Res.string.now)
                else -> minutesUntil(selected.expectedTime, nowMillis)?.let { stringResource(Res.string.countdown_minutes, it.toInt()) }.orEmpty()
            }
        Text(
            countdown,
            style = PulseTheme.type.countdown.copy(fontSize = HEADER_TIME_SIZE, lineHeight = HEADER_TIME_SIZE),
            color = if (selected.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            clockText(selected.expectedTime, nowMillis),
            style = MaterialTheme.typography.titleLarge,
            color = muted,
            textDecoration = if (selected.isCancelled) TextDecoration.LineThrough else null,
        )
        if (selected.isRealtime && !selected.isCancelled) LiveChip()
        typicalHeadwayMinutes(departures)?.let {
            Text(stringResource(Res.string.details_every, it), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    }
    val line = listOfNotNull(first.lineName, first.authority, first.operator).distinct().joinToString(" · ")
    if (line.isNotEmpty()) {
        Text(text = line, style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = FjordDimens.space1))
    }
    val notices = departures.flatMap { it.notices }.distinct()
    if (showMessages && notices.isNotEmpty()) {
        Spacer(modifier = Modifier.height(FjordDimens.space2))
        notices.forEach { notice ->
            NoticeText(notice)
        }
    }
}

private val HEADER_TIME_SIZE = 32.sp

/** "Live", on the success colours: the time comes from the vehicle, not the timetable. */
@Composable
internal fun LiveChip() {
    Text(
        stringResource(Res.string.details_live),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = PulseTheme.colors.onSuccessContainer,
        modifier =
            Modifier
                .clip(RoundedCornerShape(FjordDimens.radiusSm))
                .background(PulseTheme.colors.successContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A cancelled departure says so, with a way to the next one that runs (T4). */
@Composable
private fun CancelledCallout(
    selected: Departure,
    departures: List<Departure>,
    nowMillis: Long,
    onShow: (Departure) -> Unit,
) {
    val cancelledAt = clockText(selected.expectedTime, nowMillis).text
    val next = nextRunningDeparture(departures, selected)
    if (next == null) {
        Callout(
            title = stringResource(Res.string.details_cancelled_title),
            body = stringResource(Res.string.details_cancelled_none, cancelledAt),
            tone = CalloutTone.ERROR,
        )
        return
    }
    val nextAt = clockText(next.expectedTime, nowMillis).text
    val nextIn = minutesUntil(next.expectedTime, nowMillis)?.let { inTimeText(it) }.orEmpty()
    Callout(
        title = stringResource(Res.string.details_cancelled_title),
        body = stringResource(Res.string.details_cancelled_body, cancelledAt, nextAt, nextIn),
        tone = CalloutTone.ERROR,
    ) {
        TextButton(onClick = { onShow(next) }) { Text(stringResource(Res.string.details_show_departure, nextAt)) }
    }
}

@Composable
internal fun SectionTitle(
    text: String,
    subtitle: String? = null,
) {
    Text(text = text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    if (subtitle != null) {
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun DeparturesColumn(
    departures: List<Departure>,
    selected: Departure,
    nowMillis: Long,
    showDelays: Boolean,
    modifier: Modifier,
    onSelect: (Departure) -> Unit,
) {
    Column(modifier = modifier) {
        SectionTitle(stringResource(Res.string.details_departures))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(departures) { _, departure ->
                DepartureDetailRow(
                    departure = departure,
                    isSelected = departure === selected,
                    nowMillis = nowMillis,
                    showDelays = showDelays,
                    onClick = { onSelect(departure) },
                )
            }
        }
    }
}

@Composable
private fun DepartureDetailRow(
    departure: Departure,
    isSelected: Boolean,
    nowMillis: Long,
    showDelays: Boolean,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val isDelayed = showDelays && !departure.isCancelled && departure.delayMinutes > 0
    val nowText = stringResource(Res.string.now)
    val countdown =
        when {
            departure.isCancelled -> stringResource(Res.string.cancelled)
            departureCountdown(departure.expectedTime, nowMillis) == DepartureCountdown.Now -> nowText
            else -> minutesUntil(departure.expectedTime, nowMillis)?.let { inTimeText(it) }.orEmpty()
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = clockText(departure.expectedTime, nowMillis),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (departure.isCancelled) TextDecoration.LineThrough else null,
                            color = if (departure.isCancelled) muted else MaterialTheme.colorScheme.onSurface,
                        ),
                )
                if (isDelayed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = clockText(departure.aimedTime, nowMillis),
                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough, color = muted),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    DelayChip(departure.delayMinutes)
                }
            }
            // Only live ones are marked: departures further out have no live data yet, as is normal.
            val platform = departure.platform?.let { stringResource(Res.string.details_platform, it) }
            val isLive = departure.isRealtime && !departure.isCancelled
            if (platform != null || isLive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                    if (isLive) LiveChip()
                    platform?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                }
            }
        }
        Text(
            text = countdown,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color =
                        when {
                            departure.isCancelled -> MaterialTheme.colorScheme.error
                            countdown == nowText -> PulseTheme.colors.now
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                ),
        )
    }
}

/**
 * The selected departure's stops from here to the end of the line, on a rail in the line's colour.
 * Only this section waits for Entur: a skeleton while it loads, a note with "Try again" when Entur
 * sends no stops (T4: the rest of the sheet is fine).
 */
@Composable
private fun RouteColumn(
    departure: Departure,
    stops: List<JourneyStop>?,
    nowMillis: Long,
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    Column(modifier = modifier) {
        val last = stops?.lastOrNull()
        val ride =
            stops?.firstOrNull()?.let { from ->
                last?.let { minutesBetween(from.expectedTime, it.expectedTime) }?.takeIf { stops.size > 1 }?.let {
                    stringResource(Res.string.details_ride, it.toInt(), last.name)
                }
            }
        SectionTitle(stringResource(Res.string.details_route, clockText(departure.expectedTime, nowMillis).text), ride)
        when {
            stops == null -> StopsSkeleton()
            stops.isEmpty() ->
                Callout(body = stringResource(Res.string.details_stops_failed), tone = CalloutTone.WARNING) {
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.try_again)) }
                }
            else -> {
                val rail = PulseTheme.colors.lines.forMode(departure.transportMode)
                val boardHere =
                    listOfNotNull(departure.platform?.let { stringResource(Res.string.details_platform, it) }, stringResource(Res.string.details_board_here))
                        .joinToString(" · ")
                val lastStop = stringResource(Res.string.details_last_stop)
                LazyColumn {
                    itemsIndexed(stops) { index, stop ->
                        RouteStopRow(
                            stop,
                            nowMillis,
                            isFirst = index == 0,
                            isLast = index == stops.lastIndex,
                            railColor = rail,
                            label =
                                when (index) {
                                    0 -> boardHere
                                    stops.lastIndex -> lastStop
                                    else -> null
                                },
                        )
                    }
                }
            }
        }
    }
}

/** Rows of the stop list while Entur sends it: a time, a dot and a name each. */
@Composable
private fun StopsSkeleton() {
    val alpha = rememberPlaceholderPulse()
    Column(verticalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
        repeat(STOPS_SKELETON_ROWS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                PlaceholderBlock(alpha, Modifier.width(40.dp).height(14.dp))
                PlaceholderBlock(alpha, Modifier.size(10.dp), cornerRadius = 5.dp)
                PlaceholderBlock(alpha, Modifier.weight(1f).height(14.dp))
            }
        }
    }
}

private const val STOPS_SKELETON_ROWS = 5

@Composable
internal fun RouteStopRow(
    stop: JourneyStop,
    nowMillis: Long,
    isFirst: Boolean,
    isLast: Boolean,
    railColor: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
) {
    val isEnd = isFirst || isLast
    Row(modifier = Modifier.height(IntrinsicSize.Min).heightIn(min = 36.dp)) {
        Text(
            text = clockText(stop.expectedTime, nowMillis),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isEnd) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (stop.isCancelled) TextDecoration.LineThrough else null,
                ),
            modifier = Modifier.width(56.dp).padding(top = 8.dp),
        )
        // The timeline: a rail through every stop, with a larger dot at both ends.
        Box(modifier = Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(3.dp).height(14.dp).background(if (isFirst) Color.Transparent else railColor))
                Box(Modifier.width(3.dp).weight(1f).background(if (isLast) Color.Transparent else railColor))
            }
            Box(
                Modifier
                    .padding(top = if (isEnd) 8.dp else 10.dp)
                    .size(if (isEnd) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (isEnd) railColor else MaterialTheme.colorScheme.surface)
                    .border(2.dp, railColor, CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                text = stop.name,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isEnd) FontWeight.SemiBold else FontWeight.Normal,
                        textDecoration = if (stop.isCancelled) TextDecoration.LineThrough else null,
                    ),
            )
            label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** "Now" when it is leaving, else "in 14 min" / "in 1 h 5 min"; null when the time can't be read. */
@Composable
internal fun leavesInText(
    time: String,
    nowMillis: Long,
): String? =
    if (departureCountdown(time, nowMillis) == DepartureCountdown.Now) {
        stringResource(Res.string.now)
    } else {
        minutesUntil(time, nowMillis)?.let { inTimeText(it) }
    }

/** "in 14 min", or "in 1 h 5 min" from an hour out. */
@Composable
internal fun inTimeText(minutes: Long): String =
    if (minutes < MINUTES_PER_HOUR) {
        stringResource(Res.string.details_in_minutes, minutes.toInt())
    } else {
        stringResource(Res.string.details_in_hours, (minutes / MINUTES_PER_HOUR).toInt(), (minutes % MINUTES_PER_HOUR).toInt())
    }

/** "HH:mm", with the board's small raised "+1" when it falls on a later day. */
internal fun clockText(
    time: String,
    nowMillis: Long,
): AnnotatedString {
    val clock = departureClock(time, nowMillis) ?: return AnnotatedString("?")
    return buildAnnotatedString {
        append(clockTimeText(clock.epochMillis))
        if (clock.dayOffset > 0) {
            withStyle(SpanStyle(fontSize = DAY_OFFSET_FONT_SCALE.em, baselineShift = BaselineShift.Superscript)) {
                append("+${clock.dayOffset}")
            }
        }
    }
}

package com.example.pulsetransit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureCountdown
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.departureClock
import com.example.pulsetransit.data.departureCountdown
import com.example.pulsetransit.data.minutesBetween
import com.example.pulsetransit.data.minutesUntil
import com.example.pulsetransit.data.typicalHeadwayMinutes
import com.example.pulsetransit.ui.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    var selectedJourneyId by remember { mutableStateOf(departures.firstOrNull { !it.isCancelled }?.serviceJourneyId) }
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
    // Null while loading. Keyed by trip, so a refresh of the same departure keeps its stops.
    val stops by produceState<List<JourneyStop>?>(null, selected.serviceJourneyId) {
        value = null
        value = loadJourney(selected)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            DetailsHeader(first, departures, stopName, showMessages)
            Spacer(modifier = Modifier.height(16.dp))
            BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
                val departuresList: @Composable (Modifier) -> Unit = { modifier ->
                    DeparturesColumn(departures, selected, nowMillis, showDelays, modifier) {
                        selectedJourneyId = it.serviceJourneyId
                    }
                }
                val route: @Composable (Modifier) -> Unit = { modifier ->
                    RouteColumn(selected, stops, nowMillis, modifier)
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
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    onFilterOut(first.line)
                    onDismiss()
                },
            ) {
                Text(stringResource(Res.string.filter_out_line, first.line))
            }
        }
    }
}

/** Line badge and destination, then the line's full name, who runs it, the stop and how often. */
@Composable
private fun DetailsHeader(
    first: Departure,
    departures: List<Departure>,
    stopName: String,
    showMessages: Boolean,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = first.line,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
            maxLines = 1,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(getTransportColor(first.transportMode))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${getTransportEmoji(first.transportMode)}  ${first.destination}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line = listOfNotNull(first.lineName, first.authority, first.operator).distinct().joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium.copy(color = muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val headway = typicalHeadwayMinutes(departures)?.let { stringResource(Res.string.details_every, it) }
            Text(
                text = listOfNotNull(stopName, headway).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.copy(color = muted),
            )
        }
    }
    val notices = departures.flatMap { it.notices }.distinct()
    if (showMessages && notices.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        notices.forEach { notice ->
            Text(text = "⚠️ $notice", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
        }
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
                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+${departure.delayMinutes}",
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            ),
                    )
                }
            }
            // Only live ones are marked: departures further out have no live data yet, as is normal.
            val platform = departure.platform?.let { stringResource(Res.string.details_platform, it) }
            val isLive = departure.isRealtime && !departure.isCancelled
            if (platform != null || isLive) {
                val liveText = stringResource(Res.string.details_live)
                val liveColor = MaterialTheme.colorScheme.primary
                Text(
                    text =
                        buildAnnotatedString {
                            if (isLive) {
                                withStyle(SpanStyle(color = liveColor, fontWeight = FontWeight.Bold)) { append("● $liveText") }
                                if (platform != null) append(" · ")
                            }
                            if (platform != null) append(platform)
                        },
                    style = MaterialTheme.typography.bodySmall.copy(color = muted),
                )
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
                            countdown == nowText -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                ),
        )
    }
}

/** The selected departure's stops from here to the end of the line, as a timeline. */
@Composable
private fun RouteColumn(
    departure: Departure,
    stops: List<JourneyStop>?,
    nowMillis: Long,
    modifier: Modifier,
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
            stops == null ->
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            stops.isEmpty() ->
                Text(
                    text = stringResource(Res.string.details_route_unavailable),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            else ->
                LazyColumn {
                    itemsIndexed(stops) { index, stop ->
                        RouteStopRow(stop, nowMillis, isFirst = index == 0, isLast = index == stops.lastIndex)
                    }
                }
        }
    }
}

@Composable
internal fun RouteStopRow(
    stop: JourneyStop,
    nowMillis: Long,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val isEnd = isFirst || isLast
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
        Text(
            text = clockText(stop.expectedTime, nowMillis),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isEnd) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (stop.isCancelled) TextDecoration.LineThrough else null,
                ),
            modifier = Modifier.width(56.dp),
        )
        // The timeline: a rail through every stop, with a larger dot at both ends.
        Box(modifier = Modifier.width(20.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(2.dp).weight(1f).background(if (isFirst) Color.Transparent else lineColor))
                Box(Modifier.width(2.dp).weight(1f).background(if (isLast) Color.Transparent else lineColor))
            }
            Box(
                Modifier
                    .size(if (isEnd) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(lineColor),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stop.name,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isEnd) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (stop.isCancelled) TextDecoration.LineThrough else null,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
        append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(clock.epochMillis)))
        if (clock.dayOffset > 0) {
            withStyle(SpanStyle(fontSize = DAY_OFFSET_FONT_SCALE.em, baselineShift = BaselineShift.Superscript)) {
                append("+${clock.dayOffset}")
            }
        }
    }
}

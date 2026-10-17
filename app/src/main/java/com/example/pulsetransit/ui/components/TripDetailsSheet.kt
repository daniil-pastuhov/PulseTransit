package com.example.pulsetransit.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.R
import com.example.pulsetransit.data.DepartureCountdown
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.departureCountdown
import com.example.pulsetransit.data.isShown
import com.example.pulsetransit.data.isTransit
import com.example.pulsetransit.data.legDelayMinutes
import com.example.pulsetransit.data.legEndName
import com.example.pulsetransit.data.minutesUntil
import com.example.pulsetransit.data.transferCount
import com.example.pulsetransit.data.tripKey
import com.example.pulsetransit.data.waitMinutesBefore
import com.example.pulsetransit.data.walkDistanceMeters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A route's every leg, opened by tapping a fastest route: the board fits it on one line. Each ride
 * has its line, which way it heads, the platform, live times and delays; walks have their time and
 * distance, and waits between rides are shown. The stops each ride passes are fetched with
 * [loadLegStops] while the sheet is open, and listed on request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailsSheet(
    trip: TripInfo,
    showDelays: Boolean,
    showMessages: Boolean,
    loadLegStops: suspend (TripLeg) -> List<JourneyStop>,
    onDismiss: () -> Unit,
) {
    // Bumped by every tap inside, so only an untouched sheet closes itself.
    var interactions by remember { mutableIntStateOf(0) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(interactions) {
        delay(AUTO_CLOSE_AFTER)
        currentOnDismiss()
    }
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    // By leg index; a leg missing from it is still loading. Keyed by the journeys ridden, so a
    // refresh of the same route keeps its stops.
    val legStops by produceState(emptyMap<Int, List<JourneyStop>>(), tripKey(trip)) {
        value = emptyMap()
        trip.legs.forEachIndexed { index, leg ->
            if (leg.serviceJourneyId != null) {
                launch {
                    // Loaded before the map is read: legs load in parallel, and reading it first
                    // would let the last to finish drop the others.
                    val stops = loadLegStops(leg)
                    value = value + (index to stops)
                }
            }
        }
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
            TripHeader(trip, nowMillis)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trip.legs.indices.filter { trip.legs[it].isShown }) { index ->
                    val leg = trip.legs[index]
                    Column {
                        waitMinutesBefore(trip, index)?.let { WaitRow(it) }
                        if (leg.isTransit) {
                            RideCard(
                                leg = leg,
                                endName = legEndName(trip, index),
                                stops = legStops[index],
                                nowMillis = nowMillis,
                                showDelays = showDelays,
                                showMessages = showMessages,
                                onInteract = { interactions++ },
                            )
                        } else {
                            WalkRow(leg, legEndName(trip, index))
                        }
                    }
                }
            }
        }
    }
}

/** Where to, when it leaves and arrives, how long it takes, how often it changes and how far it walks. */
@Composable
private fun TripHeader(
    trip: TripInfo,
    nowMillis: Long,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(R.string.trip_details_title, trip.destinationName),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text =
            buildAnnotatedString {
                append(clockText(trip.expectedStartTime, nowMillis))
                trip.expectedEndTime?.let {
                    append(" → ")
                    append(clockText(it, nowMillis))
                }
                append(" · ")
                append(stringResource(R.string.duration_min, trip.durationMinutes))
            },
        style = MaterialTheme.typography.titleMedium,
    )
    val changes = transferCount(trip)
    val walked = walkDistanceMeters(trip)
    val countdown =
        if (departureCountdown(trip.expectedStartTime, nowMillis) == DepartureCountdown.Now) {
            stringResource(R.string.now)
        } else {
            minutesUntil(trip.expectedStartTime, nowMillis)?.let { inTimeText(it) }
        }
    Text(
        text =
            listOfNotNull(
                if (changes ==
                    0
                ) {
                    stringResource(R.string.trip_details_direct)
                } else {
                    pluralStringResource(R.plurals.trip_details_changes, changes, changes)
                },
                walked.takeIf { it > 0 }?.let { stringResource(R.string.trip_details_walk_total, it) },
                countdown,
            ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium.copy(color = muted),
    )
}

@Composable
private fun WaitRow(minutes: Int) {
    Text(
        text = "⏱  ${stringResource(R.string.trip_details_wait, minutes)}",
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun WalkRow(
    leg: TripLeg,
    endName: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🚶", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text =
                    listOfNotNull(
                        stringResource(R.string.trip_details_walk, leg.durationMinutes),
                        leg.distanceMeters.takeIf { it > 0 }?.let { "$it m" },
                    ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
            endName?.let {
                Text(
                    text = stringResource(R.string.trip_details_to_place, it),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One ride: line badge and heading, who runs it, then where it boards and gets off. Once its stops
 * have loaded, a toggle swaps the two ends for every stop it passes.
 */
@Composable
private fun RideCard(
    leg: TripLeg,
    endName: String?,
    stops: List<JourneyStop>?,
    nowMillis: Long,
    showDelays: Boolean,
    showMessages: Boolean,
    onInteract: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .animateContentSize()
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = leg.lineName.orEmpty(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(getTransportColor(leg.mode))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${getTransportEmoji(leg.mode)}  ${leg.destination ?: leg.lineFullName ?: leg.lineName.orEmpty()}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = listOfNotNull(leg.lineFullName, leg.authority).distinct().joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(color = muted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        LegFacts(leg)
        Spacer(modifier = Modifier.height(8.dp))
        if (expanded && !stops.isNullOrEmpty()) {
            stops.forEachIndexed { index, stop ->
                RouteStopRow(stop, nowMillis, isFirst = index == 0, isLast = index == stops.lastIndex)
            }
        } else {
            EndpointRow(
                leg.expectedStartTime,
                leg.fromName,
                nowMillis,
                delayFrom =
                    leg.aimedStartTime.takeIf {
                        showDelays &&
                            legDelayMinutes(leg) > 0
                    },
            )
            EndpointRow(leg.expectedEndTime, endName, nowMillis)
        }
        StopsToggle(leg, stops, expanded) {
            expanded = !expanded
            onInteract()
        }
        if (showMessages) {
            leg.notices.forEach { notice ->
                Text(text = "⚠️ $notice", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error))
            }
        }
    }
}

/** "● Live · Platform 2", or whichever of the two the leg has. */
@Composable
private fun LegFacts(leg: TripLeg) {
    val platform = leg.platform?.let { stringResource(R.string.details_platform, it) }
    if (platform == null && !leg.isRealtime) return
    val liveText = stringResource(R.string.details_live)
    val liveColor = MaterialTheme.colorScheme.primary
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text =
            buildAnnotatedString {
                if (leg.isRealtime) {
                    withStyle(SpanStyle(color = liveColor, fontWeight = FontWeight.Bold)) { append("● $liveText") }
                    if (platform != null) append(" · ")
                }
                if (platform != null) append(platform)
            },
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

/** A time and a stop name; with [delayFrom], the timetabled time struck through and the delay. */
@Composable
private fun EndpointRow(
    time: String?,
    name: String?,
    nowMillis: Long,
    delayFrom: String? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
        Text(
            text = time?.let { clockText(it, nowMillis) } ?: buildAnnotatedString { append("--:--") },
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = name.orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (delayFrom != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = clockText(delayFrom, nowMillis),
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough, color = muted),
            )
        }
    }
}

/** "6 min · 5 stops", which shows or hides them once loaded; a spinner while they load. */
@Composable
private fun StopsToggle(
    leg: TripLeg,
    stops: List<JourneyStop>?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val canExpand = (stops?.size ?: 0) > 1
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = canExpand, onClick = onToggle)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ridden = stops?.let { it.size - 1 }?.takeIf { it > 0 }
        Text(
            text =
                listOfNotNull(
                    stringResource(R.string.duration_min, leg.durationMinutes),
                    ridden?.let { pluralStringResource(R.plurals.trip_details_stops, it, it) },
                ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium.copy(color = muted),
        )
        Spacer(modifier = Modifier.width(4.dp))
        when {
            stops == null && leg.serviceJourneyId != null ->
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            canExpand ->
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription =
                        stringResource(
                            if (expanded) R.string.trip_details_hide_stops else R.string.trip_details_show_stops,
                        ),
                    tint = muted,
                )
        }
    }
}

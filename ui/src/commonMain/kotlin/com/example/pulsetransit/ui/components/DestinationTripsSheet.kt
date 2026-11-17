package com.example.pulsetransit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.DestinationTrips
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.clockTimeText
import com.example.pulsetransit.data.currentTrip
import com.example.pulsetransit.data.tripKey
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.ui.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One destination's routes on their own, opened by tapping its card's title: the next
 * [DestinationTrips.FIRST_PAGE] in departure order, each with when it leaves, its legs, how long
 * it takes and when it arrives, and "Later routes" at the end for the ones after. The list follows
 * the board's refreshes (a thin bar while one runs) and has a refresh button of its own; departed
 * routes drop out as the clock passes them. Offline it keeps the routes found last and says so.
 * Tapping a route opens its [TripDetailsSheet], which follows the list's refreshes.
 */
@Composable
fun DestinationTripsSheet(
    list: DestinationTrips,
    originLabel: String,
    isOnline: Boolean,
    showDelays: Boolean,
    showMessages: Boolean,
    onRefresh: () -> Unit,
    onLoadLater: () -> Unit,
    loadLegStops: suspend (TripLeg) -> List<JourneyStop>,
    onFilterOutLine: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var openTrip by rememberSaveable(stateSaver = TripSaver) { mutableStateOf<TripInfo?>(null) }
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(LIST_CLOCK_TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        DestinationTripsContent(
            list = list,
            originLabel = originLabel,
            isOnline = isOnline,
            nowMillis = nowMillis,
            onRefresh = onRefresh,
            onLoadLater = onLoadLater,
            onOpenTrip = { openTrip = it },
        )
    }
    openTrip?.let { selected ->
        TripDetailsSheet(
            trip = currentTrip(list.trips, selected),
            showDelays = showDelays,
            showMessages = showMessages,
            loadLegStops = loadLegStops,
            onDismiss = { openTrip = null },
            onFilterOutLine = onFilterOutLine,
        )
    }
}

private const val LIST_CLOCK_TICK_MILLIS = 15_000L
private const val SKELETON_ROWS = 6

/**
 * What [DestinationTripsSheet] shows inside its sheet, apart from it so the gallery can draw it
 * without a window: the header, then the routes, or why there are none.
 */
@Composable
internal fun DestinationTripsContent(
    list: DestinationTrips,
    originLabel: String,
    isOnline: Boolean,
    nowMillis: Long,
    onRefresh: () -> Unit,
    onLoadLater: () -> Unit,
    onOpenTrip: (TripInfo) -> Unit,
) {
    val trips = list.upcoming(nowMillis)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.route_to, list.destination.name),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(originLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRefresh, enabled = !list.isLoading) {
                Icon(PulseIcons.Refresh, contentDescription = stringResource(Res.string.refresh))
            }
        }
        // A refresh of routes already shown: a thin bar, the list stays where it is.
        Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            if (list.isLoading && trips.isNotEmpty()) RefreshingIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (trips.isNotEmpty()) {
            Text(
                pluralStringResource(
                    Res.plurals.destination_trips_span,
                    trips.size,
                    trips.size,
                    clockText(trips.first().expectedStartTime, nowMillis).text,
                    clockText(trips.last().expectedStartTime, nowMillis).text,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = FjordDimens.space2),
            )
            StaleNote(list = list, isOnline = isOnline, onRetry = onRefresh)
        }
        when {
            trips.isNotEmpty() ->
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(trips, key = { _, trip -> tripKey(trip) }) { index, trip ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DestinationTripRow(trip = trip, isFirst = index == 0, nowMillis = nowMillis) { onOpenTrip(trip) }
                    }
                    item(key = "later") { LaterRow(list = list, onLoadLater = onLoadLater) }
                }
            list.failed ->
                ScreenMessage(
                    title = stringResource(if (isOnline) Res.string.stale_entur_title else Res.string.stale_no_internet_title),
                    body = stringResource(Res.string.destination_trips_failed_body),
                    icon = loadFailureIcon(isOnline),
                ) { OutlinedButton(onClick = onRefresh) { Text(stringResource(Res.string.try_again)) } }
            list.hasNoRoutes ->
                ScreenMessage(
                    title = stringResource(Res.string.no_route_to, list.destination.name),
                    body = stringResource(Res.string.destination_trips_none_body),
                    icon = PulseIcons.SearchOff,
                ) { OutlinedButton(onClick = onRefresh) { Text(stringResource(Res.string.try_again)) } }
            else -> TripSkeletons()
        }
    }
}

/** Routes kept from before Entur stopped answering, or served from the cache: says from when. */
@Composable
private fun StaleNote(
    list: DestinationTrips,
    isOnline: Boolean,
    onRetry: () -> Unit,
) {
    val cachedAt = list.cachedAtMillis
    if (!list.failed && cachedAt == null) return
    Callout(
        title = stringResource(if (isOnline) Res.string.stale_entur_title else Res.string.stale_no_internet_title),
        body =
            if (cachedAt != null) {
                stringResource(Res.string.destination_trips_cached_body, clockTimeText(cachedAt))
            } else {
                stringResource(Res.string.destination_trips_offline_body)
            },
        tone = CalloutTone.WARNING,
        icon = loadFailureIcon(isOnline),
        modifier = Modifier.padding(top = FjordDimens.space2),
    ) { TextButton(onClick = onRetry, enabled = !list.isLoading) { Text(stringResource(Res.string.try_again)) } }
}

/** The end of the list: "Later routes", the next page loading, or that there are none later. */
@Composable
private fun LaterRow(
    list: DestinationTrips,
    onLoadLater: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = FjordDimens.touch).padding(vertical = FjordDimens.space2),
        contentAlignment = Alignment.Center,
    ) {
        when {
            list.isLoadingLater -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            list.canLoadLater ->
                OutlinedButton(onClick = onLoadLater, enabled = !list.isLoading) {
                    Icon(PulseIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(FjordDimens.space2))
                    Text(stringResource(Res.string.destination_trips_later))
                }
            // Offline there may well be later routes; the note above says why none are fetched.
            list.hasLoaded && !list.isLoading && !list.failed && list.cachedAtMillis == null ->
                Text(
                    stringResource(Res.string.route_no_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

/**
 * One route: "19:41 in 4 min", its legs, then how long it takes and when it arrives. The first,
 * the one to catch, has its time in the accent colour as on the card.
 */
@Composable
private fun DestinationTripRow(
    trip: TripInfo,
    isFirst: Boolean,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = FjordDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        Column {
            Text(
                clockText(trip.expectedStartTime, nowMillis),
                style = MaterialTheme.typography.titleMedium,
                color = if (isFirst) PulseTheme.colors.now else MaterialTheme.colorScheme.onSurface,
            )
            leavesInText(trip.expectedStartTime, nowMillis)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TripLegs(trip = trip, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(tripDurationText(trip.durationMinutes), style = MaterialTheme.typography.labelLarge)
            trip.expectedEndTime?.let {
                Text(
                    stringResource(Res.string.route_arrival, clockText(it, nowMillis).text),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Skeleton rows while the first routes load. */
@Composable
private fun TripSkeletons() {
    val alpha = rememberPlaceholderPulse()
    Column(modifier = Modifier.padding(vertical = FjordDimens.space3), verticalArrangement = Arrangement.spacedBy(FjordDimens.space5)) {
        repeat(SKELETON_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                PlaceholderBlock(alpha, Modifier.width(48.dp).height(28.dp))
                PlaceholderBlock(alpha, Modifier.weight(1f).height(14.dp))
                PlaceholderBlock(alpha, Modifier.width(40.dp).height(14.dp))
            }
        }
    }
}

package com.example.pulsetransit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.PlaceKind
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.TripTime
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.resources.stale_entur_title
import com.example.pulsetransit.ui.viewmodel.BoardUndo
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Finds a route to a place that isn't saved, and keeps the answer in the sheet: the alternatives
 * from [originLabel] at the chosen travel time, then "Save destination". Before typing it offers
 * the saved and recent places. Picking a place also shows its route on the board, until dismissed
 * there or saved. A place with no route says what to change; an "arrive by" no route can meet gets
 * the earliest arrival instead (T4), and a failed search a way to try again (T5).
 */
@Composable
fun RouteSearchSheet(
    state: StopBoardViewModel.UiState,
    originLabel: String,
    onQueryChange: (String) -> Unit,
    onTripTimeChange: (TripTime) -> Unit,
    onPick: (StopPlace) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    loadLegStops: suspend (TripLeg) -> List<JourneyStop>,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var picked by remember { mutableStateOf<StopPlace?>(null) }
    var openTrip by remember { mutableStateOf<TripInfo?>(null) }
    var editingTime by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(SEARCH_CLOCK_TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    // A new time searches the picked place again: a saved destination routed for now is not the
    // board's searched place until it has a time of its own.
    val changeTime: (TripTime) -> Unit = { time ->
        onTripTimeChange(time)
        picked?.let(onPick)
    }

    AdaptiveSheet(onDismiss = onDismiss) {
        // Read inside the sheet: it is its own window, with its own focus manager. Clearing focus,
        // not just hiding the keyboard, leaves no cursor in the picked name.
        val focusManager = LocalFocusManager.current
        val pick: (StopPlace) -> Unit = { place ->
            focusManager.clearFocus()
            picked = place
            onPick(place)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(stringResource(Res.string.search_route), style = MaterialTheme.typography.headlineSmall)
            Text(originLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(FjordDimens.space3))
            OutlinedTextField(
                value = picked?.name ?: state.routeSearchQuery,
                onValueChange = {
                    picked = null
                    onQueryChange(it)
                },
                label = { Text(stringResource(Res.string.route_to_label)) },
                placeholder = { Text(stringResource(Res.string.search_destination_hint)) },
                leadingIcon = { Icon(PulseIcons.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
            TripTimeChip(time = state.temporaryTripTime, onTimeChange = changeTime, modifier = Modifier.padding(top = 4.dp))
            val place = picked
            if (place == null) {
                PlaceResults(state, pick)
            } else {
                RouteResults(
                    state = state,
                    place = place,
                    nowMillis = nowMillis,
                    onOpenTrip = { openTrip = it },
                    onSave = onSave,
                    onUndo = onUndo,
                    onRetry = onRetry,
                    onChangeTime = { editingTime = true },
                )
            }
        }
    }
    if (editingTime) {
        TripTimeDialog(
            initial = state.temporaryTripTime,
            onConfirm = {
                editingTime = false
                changeTime(it)
            },
            onDismiss = { editingTime = false },
        )
    }
    openTrip?.let { trip ->
        TripDetailsSheet(
            trip = trip,
            showDelays = state.showDelays,
            showMessages = state.showMessages,
            loadLegStops = loadLegStops,
            onDismiss = { openTrip = null },
        )
    }
}

private const val SEARCH_CLOCK_TICK_MILLIS = 15_000L
private const val SKELETON_ROWS = 4

/** Before a place is picked: the saved and recent places, the matches, or that there are none. */
@Composable
private fun PlaceResults(
    state: StopBoardViewModel.UiState,
    onPick: (StopPlace) -> Unit,
) {
    val query = state.routeSearchQuery
    val results = state.routeSearchResults
    when {
        query.isBlank() -> {
            val saved = state.destinationStations
            val recent = state.recentDestinations.filter { place -> saved.none { it.id == place.id } }
            LazyColumn(modifier = Modifier.padding(top = FjordDimens.space2)) {
                if (saved.isNotEmpty()) {
                    item { SearchSectionLabel(stringResource(Res.string.route_saved_destinations)) }
                    items(saved, key = { "saved_${it.id}" }) { PlaceRow(it, query, onPick) }
                }
                if (recent.isNotEmpty()) {
                    item { SearchSectionLabel(stringResource(Res.string.route_recent)) }
                    items(recent, key = { "recent_${it.id}" }) { PlaceRow(it, query, onPick, recent = true) }
                }
            }
        }
        state.isSearchingRoutePlaces && results.isEmpty() -> RowSkeletons()
        results.isEmpty() ->
            Column(modifier = Modifier.padding(vertical = FjordDimens.space4)) {
                Text(stringResource(Res.string.search_route_no_results), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.route_no_places_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        else ->
            LazyColumn(modifier = Modifier.padding(top = FjordDimens.space2)) {
                items(results, key = { it.id }) { PlaceRow(it, query, onPick) }
            }
    }
}

@Composable
private fun SearchSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = FjordDimens.space3, bottom = FjordDimens.space1),
    )
}

/** A place with its kind's icon, the part matching [query] in bold, and where it is. */
@Composable
private fun PlaceRow(
    place: StopPlace,
    query: String,
    onPick: (StopPlace) -> Unit,
    recent: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .clip(RoundedCornerShape(FjordDimens.radiusMd))
                .clickable { onPick(place) }
                .padding(horizontal = FjordDimens.space2, vertical = FjordDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        if (recent) {
            Icon(PulseIcons.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            PlaceKindIcon(place.kind)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(highlighted(place.name, query), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            place.locality?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** [text] with its first match of [query] in bold, ignoring case. */
private fun highlighted(
    text: String,
    query: String,
): AnnotatedString {
    val at = query.trim().takeIf { it.isNotEmpty() }?.let { text.indexOf(it, ignoreCase = true) } ?: -1
    if (at < 0) return AnnotatedString(text)
    val end = at + query.trim().length
    return buildAnnotatedString {
        append(text.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(at, end)) }
        append(text.substring(end))
    }
}

/** Skeleton rows while places or routes are searched for. */
@Composable
private fun RowSkeletons() {
    val alpha = rememberPlaceholderPulse()
    Column(modifier = Modifier.padding(vertical = FjordDimens.space3), verticalArrangement = Arrangement.spacedBy(FjordDimens.space4)) {
        repeat(SKELETON_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                PlaceholderBlock(alpha, Modifier.width(48.dp).height(28.dp))
                PlaceholderBlock(alpha, Modifier.weight(1f).height(14.dp))
                PlaceholderBlock(alpha, Modifier.width(40.dp).height(14.dp))
            }
        }
    }
}

/**
 * The routes to [place]: the alternatives, each opening its details; or why there are none, with
 * the earliest arrival when an "arrive by" can't be met. Then "Save destination", or "Saved" with
 * Undo just after saving.
 */
@Composable
private fun RouteResults(
    state: StopBoardViewModel.UiState,
    place: StopPlace,
    nowMillis: Long,
    onOpenTrip: (TripInfo) -> Unit,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onChangeTime: () -> Unit,
) {
    val trips = state.fastestTrips[place.id].orEmpty()
    val earliest = state.earliestArrivals[place.id]
    Column(modifier = Modifier.padding(top = FjordDimens.space3)) {
        Text(
            stringResource(Res.string.trip_details_title, place.name),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = FjordDimens.space2),
        )
        when {
            trips.isNotEmpty() ->
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(trips) { index, trip ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TripOptionRow(trip, nowMillis) { onOpenTrip(trip) }
                    }
                }
            place.id in state.loadingTripDestinationIds -> RowSkeletons()
            place.id in state.failedTripDestinationIds ->
                ScreenMessage(
                    title = stringResource(if (state.isOnline) Res.string.stale_entur_title else Res.string.stale_no_internet_title),
                    body = stringResource(Res.string.route_offline_body),
                    icon = loadFailureIcon(state.isOnline),
                ) { OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.try_again)) } }
            earliest != null -> {
                val time = state.temporaryTripTime
                Callout(
                    title =
                        if (time is TripTime.ArriveBy) {
                            stringResource(Res.string.no_route_to_arriving, place.name, tripClockText(time.atMillis))
                        } else {
                            stringResource(Res.string.no_route_to, place.name)
                        },
                    body =
                        stringResource(
                            Res.string.route_earliest_body,
                            earliest.expectedEndTime?.let { clockText(it, nowMillis).text }.orEmpty(),
                            clockText(earliest.expectedStartTime, nowMillis).text,
                        ),
                    tone = CalloutTone.WARNING,
                    icon = PulseIcons.Schedule,
                ) { TextButton(onClick = onChangeTime) { Text(stringResource(Res.string.trip_time_change)) } }
                SearchSectionLabel(stringResource(Res.string.route_earliest_title))
                TripOptionRow(earliest, nowMillis) { onOpenTrip(earliest) }
            }
            place.id in state.noRouteDestinationIds ->
                ScreenMessage(
                    title = stringResource(Res.string.no_route_to, place.name),
                    body = stringResource(Res.string.route_no_route_body),
                    icon = PulseIcons.SearchOff,
                ) { OutlinedButton(onClick = onChangeTime) { Text(stringResource(Res.string.trip_time_change)) } }
            else -> RowSkeletons()
        }
        Spacer(modifier = Modifier.height(FjordDimens.space3))
        val justSaved = (state.pendingUndo as? BoardUndo.DestinationSaved)?.takeIf { it.place.id == place.id }
        when {
            justSaved != null ->
                Callout(
                    body = stringResource(Res.string.destination_saved, place.name),
                    tone = CalloutTone.SUCCESS,
                ) { TextButton(onClick = onUndo) { Text(stringResource(Res.string.undo)) } }
            state.destinationStations.any { it.id == place.id } ->
                OutlinedButton(onClick = {}, enabled = false) {
                    Icon(PulseIcons.BookmarkAddedFilled, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(FjordDimens.space2))
                    Text(stringResource(Res.string.route_saved))
                }
            state.canSaveTemporaryDestination && state.temporaryDestination?.id == place.id ->
                Button(onClick = onSave) {
                    Icon(PulseIcons.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(FjordDimens.space2))
                    Text(stringResource(Res.string.save_destination))
                }
        }
    }
}

/** One alternative: "19:41 in 4 min", its legs, then how long it takes and when it arrives. */
@Composable
private fun TripOptionRow(
    trip: TripInfo,
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
            Text(clockText(trip.expectedStartTime, nowMillis), style = MaterialTheme.typography.titleMedium)
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

/**
 * The board's temporary destination's time, save and close buttons; save is left out when it
 * can't be.
 */
@Composable
fun TemporaryRouteActions(
    time: TripTime,
    onTimeChange: (TripTime) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
        TripTimeButton(time = time, onTimeChange = onTimeChange, tint = tint)
        if (canSave) {
            IconButton(onClick = onSave) {
                Icon(PulseIcons.BookmarkAdd, contentDescription = stringResource(Res.string.save_destination), tint = tint)
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(PulseIcons.Close, contentDescription = stringResource(Res.string.dismiss_route), tint = tint)
        }
    }
}

/** Tells a stop apart from an address or place in destination search, where they sit side by side. */
@Composable
fun PlaceKindIcon(kind: PlaceKind) {
    Icon(
        imageVector =
            when (kind) {
                PlaceKind.STOP -> PulseIcons.DirectionsBus
                PlaceKind.ADDRESS -> PulseIcons.Home
                PlaceKind.POI -> PulseIcons.LocationOn
                PlaceKind.PLACE -> PulseIcons.Map
            },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

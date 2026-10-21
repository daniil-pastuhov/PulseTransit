package com.example.pulsetransit.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.OnSpotSplit
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.autoTripOptionCount
import com.example.pulsetransit.data.currentTrip
import com.example.pulsetransit.data.tripArrival
import com.example.pulsetransit.data.visibleTripOptionCount
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.StopBoardPanel
import com.example.pulsetransit.ui.components.TemporaryRouteActions
import com.example.pulsetransit.ui.components.TripDetailsSheet
import com.example.pulsetransit.ui.platform.rememberLocationAccess
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.NearbyStop
import com.example.pulsetransit.ui.viewmodel.OnSpotLocation
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

// Heights the auto route count is worked out from: one alternative's row, and a destination
// card's title and padding. Both grow with the font scale.
private const val TRIP_ROW_HEIGHT_DP = 64f
private const val DESTINATION_HEADER_HEIGHT_DP = 72f

// The routes/stop split: the bar a folded section keeps, the size below which a dropped section
// folds to it, and the grip between the two.
private val SPLIT_BAR_SIZE = 56.dp
private val SPLIT_MIN_SECTION_SIZE = 120.dp

// Route rows need more width than height.
private val SPLIT_MIN_SECTION_WIDTH = 240.dp
private val SPLIT_HANDLE_SIZE = 20.dp

private const val METRES_PER_KILOMETRE = 1000

/**
 * The on-spot board below the header: route alternatives to each destination from where the
 * device is, taking most of the space, and the nearest stop's departures beside or below them.
 */
@Composable
fun OnSpotContent(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    isLandscape: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onRetryLocation = rememberLocationAccess(onGranted = viewModel::retryLocation)

    when (val location = uiState.location) {
        is OnSpotLocation.Located -> LocatedContent(uiState, viewModel, isLandscape, onNavigateToSettings, modifier)
        else -> LocationStatus(location, onRetry = onRetryLocation, modifier = modifier)
    }
}

@Composable
private fun LocatedContent(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    isLandscape: Boolean,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier.padding(6.dp)) {
        // Sizes along the split's axis, in dp, as OnSpotSplit takes them.
        val total = ((if (isLandscape) maxWidth else maxHeight) - SPLIT_HANDLE_SIZE).value
        val bar = SPLIT_BAR_SIZE.value
        val min = OnSpotSplit.minSectionSize(total, (if (isLandscape) SPLIT_MIN_SECTION_WIDTH else SPLIT_MIN_SECTION_SIZE).value)
        val target = OnSpotSplit.routesSize(uiState.onSpotSplit, total, bar, min)
        // A new size (rotation, a window resize) jumps there; a new split animates to it.
        val routesSize = remember(total) { Animatable(target) }
        LaunchedEffect(target) { routesSize.animateTo(target) }

        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val dragState =
            rememberDraggableState { deltaPx ->
                val delta = with(density) { deltaPx.toDp().value }
                scope.launch { routesSize.snapTo(OnSpotSplit.dragRoutesSize(routesSize.value, delta, total, bar)) }
            }
        var isDragging by remember { mutableStateOf(false) }

        // A folded bar opens when dragged, but the section turns back into its content on the way,
        // so the drag sits on a wrapper that stays and is kept enabled until the drag ends.
        fun drag(enabled: Boolean = true) =
            Modifier.draggable(
                state = dragState,
                orientation = if (isLandscape) Orientation.Horizontal else Orientation.Vertical,
                enabled = enabled || isDragging,
                // In a right-to-left row the routes are on the right, so dragging right shrinks them.
                reverseDirection = isLandscape && layoutDirection == LayoutDirection.Rtl,
                onDragStarted = { isDragging = true },
                onDragStopped = {
                    isDragging = false
                    val split = OnSpotSplit.settle(routesSize.value, total, min)
                    viewModel.setOnSpotSplit(split)
                    routesSize.animateTo(OnSpotSplit.routesSize(split, total, bar, min))
                },
            )
        val onExpand = { viewModel.setOnSpotSplit(OnSpotSplit.DEFAULT) }

        // A section dragged below its minimum shows the bar it will fold to.
        val routesFolded = routesSize.value < min
        val stopFolded = total - routesSize.value < min
        val routes: @Composable (Modifier) -> Unit = { sectionModifier ->
            Box(modifier = sectionModifier.then(drag(enabled = routesFolded))) {
                if (routesFolded) {
                    FoldedSection(
                        label = stringResource(Res.string.on_spot_routes),
                        icon = Icons.Default.Directions,
                        expandLabel = stringResource(Res.string.on_spot_show_routes),
                        edge = if (isLandscape) FoldedEdge.START else FoldedEdge.TOP,
                        onExpand = onExpand,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    RoutesSection(uiState, viewModel, onNavigateToSettings, Modifier.fillMaxSize())
                }
            }
        }
        val stop: @Composable (Modifier) -> Unit = { sectionModifier ->
            Box(modifier = sectionModifier.then(drag(enabled = stopFolded))) {
                if (stopFolded) {
                    FoldedSection(
                        label = uiState.favorites.firstOrNull()?.name ?: stringResource(Res.string.details_departures),
                        icon = Icons.Default.DirectionsBus,
                        expandLabel = stringResource(Res.string.on_spot_show_departures),
                        edge = if (isLandscape) FoldedEdge.END else FoldedEdge.BOTTOM,
                        onExpand = onExpand,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    NearbyStopSection(uiState, viewModel, Modifier.fillMaxSize())
                }
            }
        }
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                routes(Modifier.width(routesSize.value.dp).fillMaxHeight())
                SplitHandle(isVertical = true, modifier = Modifier.width(SPLIT_HANDLE_SIZE).fillMaxHeight().then(drag()))
                stop(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                routes(Modifier.height(routesSize.value.dp).fillMaxWidth())
                SplitHandle(isVertical = false, modifier = Modifier.height(SPLIT_HANDLE_SIZE).fillMaxWidth().then(drag()))
                stop(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** The grip between the routes and the stop, dragged to share the board between them. */
@Composable
private fun SplitHandle(
    isVertical: Boolean,
    modifier: Modifier,
) {
    val description = stringResource(Res.string.on_spot_resize)
    Box(modifier = modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(width = if (isVertical) 4.dp else 32.dp, height = if (isVertical) 32.dp else 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

/** The board edge a folded section sits at, which its arrow points away from. */
private enum class FoldedEdge { TOP, BOTTOM, START, END }

/**
 * A section folded to a bar at its [edge], like a bottom sheet: tapped, it opens to the default
 * split; dragged, it opens as far as it is pulled. Side bars, in landscape, are too narrow for the label.
 */
@Composable
private fun FoldedSection(
    label: String,
    icon: ImageVector,
    expandLabel: String,
    edge: FoldedEdge,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    val arrow =
        when (edge) {
            FoldedEdge.TOP -> Icons.Default.KeyboardArrowDown
            FoldedEdge.BOTTOM -> Icons.Default.KeyboardArrowUp
            FoldedEdge.START -> Icons.AutoMirrored.Filled.KeyboardArrowRight
            FoldedEdge.END -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        }
    Card(
        modifier =
            modifier
                .padding(6.dp)
                .clickable(onClickLabel = expandLabel, onClick = onExpand),
    ) {
        if (edge == FoldedEdge.START || edge == FoldedEdge.END) {
            Column(
                modifier = Modifier.fillMaxSize().semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Icon(icon, contentDescription = null)
                Icon(arrow, contentDescription = null)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(arrow, contentDescription = null)
            }
        }
    }
}

/** Finding the device, or why it could not be found, filling the board until it is. */
@Composable
private fun LocationStatus(
    location: OnSpotLocation,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val failed = location is OnSpotLocation.Unavailable || location is OnSpotLocation.PermissionDenied
            if (failed) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                CircularProgressIndicator()
            }
            Text(
                text =
                    stringResource(
                        when (location) {
                            OnSpotLocation.PermissionDenied -> Res.string.location_denied
                            OnSpotLocation.Unavailable -> Res.string.location_unavailable
                            is OnSpotLocation.Locating ->
                                if (location.accuracy == LocationAccuracy.PRECISE) {
                                    Res.string.locating_precise
                                } else {
                                    Res.string.locating_approximate
                                }
                            else -> Res.string.locating_precise
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (failed) {
                Button(onClick = onRetry) {
                    Text(
                        stringResource(
                            if (location is OnSpotLocation.PermissionDenied) Res.string.location_allow else Res.string.retry,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutesSection(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier,
) {
    if (uiState.routeDestinations.isEmpty()) {
        Card(modifier = modifier.padding(6.dp)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = stringResource(Res.string.routes_add_destination),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onNavigateToSettings) { Text(stringResource(Res.string.add_destination)) }
            }
        }
        return
    }

    // A destination with no route is left out once it has loaded, as on the wall board.
    val destinations =
        uiState.routeDestinations.filter {
            it.id in uiState.fastestTrips ||
                it.id in uiState.loadingTripDestinationIds ||
                it.id in uiState.failedTripDestinationIds
        }
    // The tapped alternative, with its destination's id; the details follow its refreshes.
    var detailsTrip by remember { mutableStateOf<Pair<String, TripInfo>?>(null) }
    detailsTrip?.let { (destinationId, selected) ->
        TripDetailsSheet(
            trip = currentTrip(uiState.fastestTrips[destinationId], selected),
            showDelays = uiState.showDelays,
            showMessages = uiState.showMessages,
            loadLegStops = viewModel::tripLegStops,
            onDismiss = { detailsTrip = null },
        )
    }
    BoxWithConstraints(modifier = modifier) {
        val fontScale = LocalDensity.current.fontScale
        val autoCount =
            autoTripOptionCount(
                availableHeight = maxHeight.value,
                destinationCount = destinations.size,
                rowHeight = TRIP_ROW_HEIGHT_DP * fontScale,
                headerHeight = DESTINATION_HEADER_HEIGHT_DP * fontScale,
            )
        val count = visibleTripOptionCount(uiState.tripOptionsCount, autoCount)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            destinations.forEach { destination ->
                key(destination.id) {
                    DestinationRoutesCard(
                        destination = destination,
                        trips = uiState.fastestTrips[destination.id],
                        count = count,
                        isRefreshing = destination.id in uiState.loadingTripDestinationIds,
                        isFailed = destination.id in uiState.failedTripDestinationIds,
                        isOnline = uiState.isOnline,
                        onTripClick = { detailsTrip = destination.id to it },
                        actions =
                            if (destination.id == uiState.temporaryDestination?.id) {
                                {
                                    TemporaryRouteActions(
                                        canSave = uiState.canSaveTemporaryDestination,
                                        onSave = { viewModel.saveTemporaryDestination() },
                                        onDismiss = { viewModel.dismissTemporaryDestination() },
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
            }
        }
    }
}

/** One destination's next [count] route alternatives, best first, with [actions] beside its title. */
@Composable
private fun DestinationRoutesCard(
    destination: StopPlace,
    trips: List<TripInfo>?,
    count: Int,
    isRefreshing: Boolean,
    isFailed: Boolean,
    isOnline: Boolean,
    onTripClick: (TripInfo) -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        when {
            trips == null ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (isFailed) {
                            FastestTripUnavailable(destinationName = destination.name, isOnline = isOnline, isRetrying = isRefreshing)
                        } else {
                            FastestTripPlaceholder(destinationName = destination.name)
                        }
                    }
                    actions?.invoke()
                }
            else -> {
                Box {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(Res.string.route_to, destination.name),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            actions?.invoke()
                        }
                        trips.take(count).forEachIndexed { index, trip ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                                )
                            }
                            TripOptionRow(trip = trip, isFirst = index == 0, onClick = { onTripClick(trip) })
                        }
                    }
                    if (isRefreshing) {
                        RefreshingIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

/** One alternative: when to leave, the legs, when it arrives and how long it takes. */
@Composable
private fun TripOptionRow(
    trip: TripInfo,
    isFirst: Boolean,
    onClick: () -> Unit,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = trip.expectedStartTime.takeIf { it.length >= 16 }?.substring(11, 16) ?: "--:--",
            style =
                if (isFirst) {
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleLarge
                },
            color = onContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tripRouteText(trip),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            tripArrival(trip)?.let { arrival ->
                val time = if (arrival.dayOffset > 0) "${arrival.time} +${arrival.dayOffset}" else arrival.time
                Text(
                    text = stringResource(Res.string.route_arrival, time),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            text = stringResource(Res.string.duration_min, trip.durationMinutes),
            style = MaterialTheme.typography.titleMedium,
            color = onContainer,
        )
    }
}

@Composable
private fun NearbyStopSection(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    modifier: Modifier,
) {
    val stop = uiState.favorites.firstOrNull()
    if (stop == null) {
        Card(modifier = modifier.padding(6.dp)) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                if (uiState.isSearchingNearby || uiState.nearbyStops.isNotEmpty()) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(Res.string.no_stops_nearby), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        return
    }
    StopBoardPanel(
        stopPlace = stop,
        departureGroups = uiState.departures.find { it.stopId == stop.id }?.groups,
        showDelays = uiState.showDelays,
        showMessages = uiState.showMessages,
        isLoading = stop.id in uiState.loadingStopIds,
        isExtended = stop.id in uiState.extendedStopIds,
        isFailed = stop.id in uiState.failedStopIds,
        isOnline = uiState.isOnline,
        canLoadMore = stop.id in uiState.canLoadMoreStopIds,
        isLoadingMore = stop.id in uiState.loadingMoreStopIds,
        onFilterOut = viewModel::filterOutLine,
        onSearchFurtherAhead = { viewModel.searchFurtherAhead(stop.id) },
        onRetry = viewModel::refreshData,
        onLoadMore = { viewModel.loadMoreDepartures(stop.id) },
        loadJourney = { departure -> viewModel.journeyStopsAhead(departure, stop.id) },
        modifier = modifier,
        header = {
            StopPicker(
                selected = stop,
                nearbyStops = uiState.nearbyStops,
                isPinned = uiState.isStopPinned,
                onSelect = viewModel::selectStop,
                onFollowNearest = viewModel::followNearest,
            )
        },
    )
}

/** The shown stop's name, opening a menu of the other stops nearby; and a way back to the nearest. */
@Composable
private fun RowScope.StopPicker(
    selected: StopPlace,
    nearbyStops: List<NearbyStop>,
    isPinned: Boolean,
    onSelect: (String) -> Unit,
    onFollowNearest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val distance = nearbyStops.firstOrNull { it.stop.id == selected.id }?.distanceMeters
    Box(modifier = Modifier.weight(1f)) {
        Row(
            modifier = Modifier.clickable(enabled = nearbyStops.size > 1) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = selected.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(distance?.let { distanceText(it) }, selected.locality).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (nearbyStops.size > 1) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(Res.string.choose_stop))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp),
        ) {
            nearbyStops.forEach { nearby ->
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = nearby.stop.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (nearby.stop.id == selected.id) FontWeight.Bold else FontWeight.Normal,
                            )
                            nearby.distanceMeters?.let {
                                Text(
                                    text = distanceText(it),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(nearby.stop.id)
                    },
                )
            }
        }
    }
    if (isPinned) {
        AssistChip(
            onClick = onFollowNearest,
            label = { Text(stringResource(Res.string.back_to_nearest)) },
            leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
}

@Composable
private fun distanceText(meters: Int): String =
    if (meters < METRES_PER_KILOMETRE) {
        stringResource(Res.string.distance_meters, meters)
    } else {
        val kilometres = meters / METRES_PER_KILOMETRE.toFloat()
        stringResource(Res.string.distance_kilometers, String.format(Locale.getDefault(), "%.1f", kilometres))
    }

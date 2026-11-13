package com.example.pulsetransit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.OnSpotSplit
import com.example.pulsetransit.data.SamplePlace
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripTime
import com.example.pulsetransit.data.autoTripOptionCount
import com.example.pulsetransit.data.currentTrip
import com.example.pulsetransit.data.tripArrival
import com.example.pulsetransit.data.visibleTripOptionCount
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.theme.type
import com.example.pulsetransit.ui.components.AdaptiveSheet
import com.example.pulsetransit.ui.components.Callout
import com.example.pulsetransit.ui.components.LoadingPlaceholderList
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.ScreenMessage
import com.example.pulsetransit.ui.components.StopBoardPanel
import com.example.pulsetransit.ui.components.TemporaryRouteActions
import com.example.pulsetransit.ui.components.TripDetailsSheet
import com.example.pulsetransit.ui.components.TripLegs
import com.example.pulsetransit.ui.components.leavesInText
import com.example.pulsetransit.ui.components.tripDurationText
import com.example.pulsetransit.ui.platform.rememberLocationAccess
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.NearbyStop
import com.example.pulsetransit.ui.viewmodel.OnSpotLocation
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.time.Clock

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

    val location = uiState.location
    when {
        // Nothing near to show, and routes from abroad are no use: offer a place in Norway instead.
        uiState.isOutsideNorway -> OutsideNorway(onTrySamplePlace = viewModel::useSamplePlace, modifier = modifier)
        location is OnSpotLocation.Located -> {
            val samplePlace = uiState.samplePlace
            if (samplePlace == null) {
                LocatedContent(uiState, viewModel, isLandscape, onNavigateToSettings, modifier)
            } else {
                Column(modifier = modifier) {
                    SamplePlaceCallout(
                        place = samplePlace,
                        onUseDeviceLocation = viewModel::useDeviceLocation,
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp),
                    )
                    LocatedContent(uiState, viewModel, isLandscape, onNavigateToSettings, Modifier.weight(1f))
                }
            }
        }
        else ->
            LocationStatus(
                location,
                uiState.routeDestinations,
                onRetry = onRetryLocation,
                onTrySamplePlace = viewModel::useSamplePlace,
                modifier = modifier,
            )
    }
}

/**
 * The device is outside Norway, where Entur has nothing: says so, and offers to try the board as
 * if standing at one of the [SamplePlace]s (for testers and store reviewers abroad).
 */
@Composable
private fun OutsideNorway(
    onTrySamplePlace: (SamplePlace) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        ScreenMessage(
            title = stringResource(Res.string.outside_norway_title),
            body = stringResource(Res.string.outside_norway_body),
            icon = PulseIcons.Map,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                SamplePlace.entries.forEachIndexed { index, place ->
                    val label = stringResource(Res.string.sample_place_try, place.displayName)
                    if (index == 0) {
                        Button(onClick = { onTrySamplePlace(place) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { onTrySamplePlace(place) }) { Text(label) }
                    }
                }
            }
        }
    }
}

/** Says the board is at a sample place, not where the device is, with the way back. */
@Composable
private fun SamplePlaceCallout(
    place: SamplePlace,
    onUseDeviceLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Callout(
        title = stringResource(Res.string.sample_place_active_title),
        body = stringResource(Res.string.sample_place_active, place.displayName),
        icon = PulseIcons.EditLocationAlt,
        modifier = modifier,
    ) {
        TextButton(onClick = onUseDeviceLocation) { Text(stringResource(Res.string.sample_place_use_mine)) }
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
        // How tall the routes' cards are, measured; null while they show something else.
        var routesContentHeight by remember { mutableStateOf<Float?>(null) }

        // The routes get no more than their cards need, the stop taking the rest.
        fun fitted(split: Float) =
            OnSpotSplit.fittedRoutesSize(
                split = split,
                totalSize = total,
                barSize = bar,
                minSize = min,
                contentSize = routesContentHeight?.takeUnless { isLandscape } ?: Float.POSITIVE_INFINITY,
            )
        val target = fitted(uiState.onSpotSplit)
        var isDragging by remember { mutableStateOf(false) }
        // A new size (rotation, a window resize) jumps there; a new split or content animates to
        // it, though not while dragged, so the grip follows the finger.
        val routesSize = remember(total) { Animatable(target) }
        LaunchedEffect(target, isDragging) { if (!isDragging) routesSize.animateTo(target) }

        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val dragState =
            rememberDraggableState { deltaPx ->
                val delta = with(density) { deltaPx.toDp().value }
                scope.launch { routesSize.snapTo(OnSpotSplit.dragRoutesSize(routesSize.value, delta, total, bar)) }
            }

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
                    routesSize.animateTo(fitted(split))
                },
            )
        val onExpand = { viewModel.setOnSpotSplit(OnSpotSplit.DEFAULT) }

        // A section dragged below its minimum shows the bar it will fold to.
        val routesFolded = routesSize.value < min
        val stopFolded = total - routesSize.value < min
        // How many alternatives show follows the routes' share, not the fitted size, or showing
        // fewer would leave room for fewer still. Landscape counts the full height.
        val countHeight =
            when {
                isLandscape -> null
                isDragging -> routesSize.value.dp
                else -> OnSpotSplit.routesSize(uiState.onSpotSplit, total, bar, min).dp
            }
        val routes: @Composable (Modifier) -> Unit = { sectionModifier ->
            Box(modifier = sectionModifier.then(drag(enabled = routesFolded))) {
                Crossfade(targetState = routesFolded, label = "routesFolded") { folded ->
                    if (folded) {
                        FoldedSection(
                            // Says what it hides: "Show routes · 2 destinations".
                            label =
                                listOf(
                                    stringResource(Res.string.on_spot_show_routes),
                                    pluralStringResource(Res.plurals.on_spot_destinations, uiState.routeDestinations.size, uiState.routeDestinations.size),
                                ).joinToString(" · "),
                            icon = PulseIcons.Directions,
                            expandLabel = stringResource(Res.string.on_spot_show_routes),
                            edge = if (isLandscape) FoldedEdge.START else FoldedEdge.TOP,
                            onExpand = onExpand,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        RoutesSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToSettings = onNavigateToSettings,
                            countHeight = countHeight,
                            onContentHeight = { routesContentHeight = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        val stop: @Composable (Modifier) -> Unit = { sectionModifier ->
            Box(modifier = sectionModifier.then(drag(enabled = stopFolded))) {
                Crossfade(targetState = stopFolded, label = "stopFolded") { folded ->
                    if (folded) {
                        val shown = uiState.favorites.firstOrNull()
                        val distance = uiState.nearbyStops.firstOrNull { it.stop.id == shown?.id }?.distanceMeters
                        FoldedSection(
                            // Says what it hides: "Show departures · Oslo S · 83 m".
                            label =
                                listOfNotNull(
                                    stringResource(Res.string.on_spot_show_departures),
                                    shown?.name,
                                    distance?.let { distanceText(it) },
                                ).joinToString(" · "),
                            icon = PulseIcons.DirectionsBus,
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
            FoldedEdge.TOP -> PulseIcons.KeyboardArrowDown
            FoldedEdge.BOTTOM -> PulseIcons.KeyboardArrowUp
            FoldedEdge.START -> PulseIcons.ChevronRight
            FoldedEdge.END -> PulseIcons.ChevronLeft
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(FjordDimens.radiusLg))
                .background(MaterialTheme.colorScheme.surfaceContainer)
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

/**
 * Before a fix: "Finding your location…" over the routes and stop as skeletons, so the layout
 * doesn't jump when it comes. Without one: why (denied, T5, or unavailable, T5 with Try again),
 * leaving the header so search and Settings stay in reach.
 */
@Composable
private fun LocationStatus(
    location: OnSpotLocation,
    destinations: List<StopPlace>,
    onRetry: () -> Unit,
    onTrySamplePlace: (SamplePlace) -> Unit,
    modifier: Modifier,
) {
    // Without a location the device may well be abroad (a tester, a store reviewer): offer a place in Norway.
    val trySample: @Composable () -> Unit = {
        val place = SamplePlace.entries.first()
        TextButton(onClick = { onTrySamplePlace(place) }) {
            Text(stringResource(Res.string.sample_place_hint) + " " + stringResource(Res.string.sample_place_try, place.displayName))
        }
    }
    when (location) {
        OnSpotLocation.PermissionDenied ->
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                ScreenMessage(
                    title = stringResource(Res.string.on_spot_denied_title),
                    body = stringResource(Res.string.location_denied),
                    icon = PulseIcons.LocationOff,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                        Button(onClick = onRetry) { Text(stringResource(Res.string.location_allow)) }
                        Text(
                            stringResource(Res.string.on_spot_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        trySample()
                    }
                }
            }
        OnSpotLocation.Unavailable ->
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                ScreenMessage(
                    title = stringResource(Res.string.location_unavailable),
                    body = stringResource(Res.string.on_spot_unavailable_body),
                    icon = PulseIcons.WrongLocation,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.try_again)) }
                        trySample()
                    }
                }
            }
        else -> {
            val approximate = (location as? OnSpotLocation.Locating)?.accuracy == LocationAccuracy.APPROXIMATE
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                    Icon(PulseIcons.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (approximate) Res.string.locating_approximate else Res.string.locating_precise),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                destinations.forEach { FastestTripPlaceholder(destinationName = it.name) }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(FjordDimens.radiusLg))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(FjordDimens.space4),
                ) { LoadingPlaceholderList() }
            }
        }
    }
}

/**
 * The route alternatives, as many per destination as fit [countHeight] (or the section's own
 * height when null). The height of the cards goes to [onContentHeight], null while there are none.
 */
@Composable
private fun RoutesSection(
    uiState: StopBoardViewModel.UiState,
    viewModel: StopBoardViewModel,
    onNavigateToSettings: () -> Unit,
    countHeight: Dp?,
    onContentHeight: (Float?) -> Unit,
    modifier: Modifier,
) {
    DisposableEffect(Unit) { onDispose { onContentHeight(null) } }
    if (uiState.routeDestinations.isEmpty()) {
        LaunchedEffect(Unit) { onContentHeight(null) }
        // First use: teach the next step; the nearest stop's departures already work.
        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(FjordDimens.radiusLg))
                    .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            ScreenMessage(
                title = stringResource(Res.string.on_spot_no_destinations_title),
                body = stringResource(Res.string.routes_add_destination),
                icon = PulseIcons.Flag,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                    Button(onClick = onNavigateToSettings) { Text(stringResource(Res.string.add_destination)) }
                    Text(
                        stringResource(Res.string.on_spot_one_off_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    // A destination with no route is left out once it has loaded, as on the wall board, except
    // the searched-for one, which says so and keeps its buttons.
    val destinations =
        uiState.routeDestinations.filter {
            it.id in uiState.fastestTrips ||
                it.id in uiState.loadingTripDestinationIds ||
                it.id in uiState.failedTripDestinationIds ||
                it.id == uiState.temporaryDestination?.id
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
        val density = LocalDensity.current
        val fontScale = density.fontScale
        val autoCount =
            autoTripOptionCount(
                availableHeight = (countHeight ?: maxHeight).value,
                destinationCount = destinations.size,
                rowHeight = TRIP_ROW_HEIGHT_DP * fontScale,
                headerHeight = DESTINATION_HEADER_HEIGHT_DP * fontScale,
            )
        val count = visibleTripOptionCount(uiState.tripOptionsCount, autoCount)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .wrapContentHeight(Alignment.Top)
                    .onSizeChanged { onContentHeight(with(density) { it.height.toDp().value }) },
        ) {
            if ((uiState.location as? OnSpotLocation.Located)?.accuracy == LocationAccuracy.APPROXIMATE) {
                // Distances and walks come from a rough fix until a precise one replaces it (T4).
                ApproximateTag(modifier = Modifier.padding(bottom = FjordDimens.space2))
            }
            destinations.forEach { destination ->
                key(destination.id) {
                    DestinationRoutesCard(
                        destination = destination,
                        trips = uiState.fastestTrips[destination.id],
                        count = count,
                        isRefreshing = destination.id in uiState.loadingTripDestinationIds,
                        isFailed = destination.id in uiState.failedTripDestinationIds,
                        noRouteTime = uiState.noRouteTime(destination.id),
                        isOnline = uiState.isOnline,
                        onTripClick = { detailsTrip = destination.id to it },
                        actions =
                            if (destination.id == uiState.temporaryDestination?.id) {
                                {
                                    TemporaryRouteActions(
                                        time = uiState.temporaryTripTime,
                                        onTimeChange = { viewModel.setTripTime(it) },
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
    /** Set when Entur found no route: the time it was searched for. */
    noRouteTime: TripTime?,
    isOnline: Boolean,
    onTripClick: (TripInfo) -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = FjordDimens.space2)
                .clip(RoundedCornerShape(FjordDimens.radiusLg))
                .background(if (trips == null) MaterialTheme.colorScheme.surfaceContainer else PulseTheme.colors.route),
    ) {
        when {
            trips == null ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (isFailed) {
                            FastestTripUnavailable(destinationName = destination.name, isOnline = isOnline, isRetrying = isRefreshing)
                        } else if (noRouteTime != null) {
                            FastestTripNone(destinationName = destination.name, time = noRouteTime)
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
                                style = MaterialTheme.typography.titleMedium,
                                color = PulseTheme.colors.onRoute,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            actions?.invoke()
                        }
                        // Alternatives come and go as the split or the setting changes.
                        trips.forEachIndexed { index, trip ->
                            AnimatedVisibility(visible = index < count) {
                                Column {
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = PulseTheme.colors.onRoute.copy(alpha = 0.15f),
                                        )
                                    }
                                    TripOptionRow(trip = trip, isFirst = index == 0, onClick = { onTripClick(trip) })
                                }
                            }
                        }
                    }
                    if (isRefreshing) {
                        RefreshingIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
                            color = PulseTheme.colors.onRoute.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

/** One alternative: "19:41 in 4 min", the legs, then how long it takes and when it arrives. */
@Composable
private fun TripOptionRow(
    trip: TripInfo,
    isFirst: Boolean,
    onClick: () -> Unit,
) {
    val onRoute = PulseTheme.colors.onRoute
    // Ticks on its own, so "in 4 min" counts down between refreshes of the trips.
    val nowMillis by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(TRIP_ROW_CLOCK_TICK_MILLIS)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        Column {
            Text(
                text = trip.expectedStartTime.takeIf { it.length >= 16 }?.substring(11, 16) ?: "--:--",
                style = if (isFirst) PulseTheme.type.countdown else MaterialTheme.typography.titleMedium,
                color = if (isFirst) PulseTheme.colors.routeAccent else onRoute,
            )
            leavesInText(trip.expectedStartTime, nowMillis)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = onRoute)
            }
        }
        TripLegs(trip = trip, color = onRoute, maxLines = 2, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(tripDurationText(trip.durationMinutes), style = MaterialTheme.typography.labelLarge, color = onRoute)
            tripArrival(trip)?.let { arrival ->
                val time = if (arrival.dayOffset > 0) "${arrival.time} +${arrival.dayOffset}" else arrival.time
                Text(stringResource(Res.string.route_arrival, time), style = MaterialTheme.typography.labelSmall, color = onRoute)
            }
        }
    }
}

/** "Approximate location": distances and walks are from a rough fix for now. */
@Composable
private fun ApproximateTag(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(FjordDimens.radiusSm))
                .background(PulseTheme.colors.warningContainer)
                .padding(horizontal = FjordDimens.space2, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1),
    ) {
        Icon(PulseIcons.NearMe, contentDescription = null, tint = PulseTheme.colors.onWarningContainer, modifier = Modifier.size(14.dp))
        Text(stringResource(Res.string.on_spot_approximate), style = MaterialTheme.typography.labelSmall, color = PulseTheme.colors.onWarningContainer)
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
        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(FjordDimens.radiusLg))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(FjordDimens.space4),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isSearchingNearby || uiState.nearbyStops.isNotEmpty()) {
                LoadingPlaceholderList()
            } else {
                // Empty, not an error: routes still work, as the planner includes the walk.
                ScreenMessage(
                    title = stringResource(Res.string.no_stops_nearby),
                    body = stringResource(Res.string.on_spot_no_stops_body),
                    icon = PulseIcons.NearMeDisabled,
                )
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
        onLoadMore = { viewModel.loadMoreDepartures(stop.id) },
        loadJourney = { departure -> viewModel.journeyStopsAhead(departure, stop.id) },
        // Asks for enough departures to fill the panel, as on the wall board.
        onRowCapacity = { rows -> viewModel.setPanelRowCapacity(stop.id, rows) },
        modifier = modifier,
        header = {
            StopPicker(
                selected = stop,
                approximate = (uiState.location as? OnSpotLocation.Located)?.accuracy == LocationAccuracy.APPROXIMATE,
                isSearching = uiState.isSearchingNearby,
                nearbyStops = uiState.nearbyStops,
                isPinned = uiState.isStopPinned,
                onSelect = viewModel::selectStop,
                onFollowNearest = viewModel::followNearest,
            )
        },
    )
}

/**
 * The shown stop's name and distance ("83 m · Oslo", or "About 300 m" from a rough fix), opening a
 * sheet of the stops within 1 km, nearest first; and "Back to nearest" while another is chosen.
 */
@Composable
private fun RowScope.StopPicker(
    selected: StopPlace,
    approximate: Boolean,
    isSearching: Boolean,
    nearbyStops: List<NearbyStop>,
    isPinned: Boolean,
    onSelect: (String) -> Unit,
    onFollowNearest: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    val distance = nearbyStops.firstOrNull { it.stop.id == selected.id }?.distanceMeters
    val distanceLabel = distance?.let { distanceText(it) }?.let { if (approximate) stringResource(Res.string.distance_about, it) else it }
    Row(
        modifier =
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(FjordDimens.radiusSm))
                .clickable(onClickLabel = stringResource(Res.string.choose_stop)) { choosing = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(text = selected.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(distanceLabel, selected.locality).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(PulseIcons.ExpandMore, contentDescription = null)
    }
    if (isPinned) {
        AssistChip(
            onClick = onFollowNearest,
            label = { Text(stringResource(Res.string.back_to_nearest)) },
            leadingIcon = { Icon(PulseIcons.MyLocation, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
    }
    if (choosing) {
        StopChooserSheet(
            selectedId = selected.id,
            nearbyStops = nearbyStops,
            isSearching = isSearching,
            approximate = approximate,
            onSelect = {
                choosing = false
                onSelect(it)
            },
            onDismiss = { choosing = false },
        )
    }
}

/** The stops within 1 km, nearest first with a comparable distance; the shown one checked. */
@Composable
private fun StopChooserSheet(
    selectedId: String,
    nearbyStops: List<NearbyStop>,
    isSearching: Boolean,
    approximate: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(stringResource(Res.string.on_spot_stops_nearby), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(FjordDimens.space3))
            when {
                nearbyStops.isEmpty() && isSearching -> LoadingPlaceholderList()
                nearbyStops.isEmpty() ->
                    ScreenMessage(
                        title = stringResource(Res.string.no_stops_nearby),
                        body = stringResource(Res.string.on_spot_search_hint),
                        icon = PulseIcons.NearMeDisabled,
                    )
                else ->
                    LazyColumn {
                        items(nearbyStops, key = { it.stop.id }) { nearby ->
                            val selected = nearby.stop.id == selectedId
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = FjordDimens.touch)
                                        .clickable { onSelect(nearby.stop.id) }
                                        .semantics { this.selected = selected }
                                        .padding(vertical = FjordDimens.space2),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nearby.stop.name, style = MaterialTheme.typography.bodyLarge)
                                    nearby.stop.locality?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                nearby.distanceMeters?.let {
                                    val text = distanceText(it)
                                    Text(
                                        if (approximate) stringResource(Res.string.distance_about, text) else text,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selected) Icon(PulseIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
            }
        }
    }
}

@Composable
internal fun distanceText(meters: Int): String =
    if (meters < METRES_PER_KILOMETRE) {
        stringResource(Res.string.distance_meters, meters)
    } else {
        val kilometres = meters / METRES_PER_KILOMETRE.toFloat()
        stringResource(Res.string.distance_kilometers, String.format(Locale.getDefault(), "%.1f", kilometres))
    }

private const val TRIP_ROW_CLOCK_TICK_MILLIS = 1_000L

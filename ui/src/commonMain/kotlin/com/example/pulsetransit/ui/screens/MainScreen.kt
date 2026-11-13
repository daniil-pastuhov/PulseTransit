package com.example.pulsetransit.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DirectionSummary
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.StopDepartures
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripTime
import com.example.pulsetransit.data.currentTrip
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.FollowedJourneyBar
import com.example.pulsetransit.ui.components.JourneyAlertsEffects
import com.example.pulsetransit.ui.components.JourneyNotifyButton
import com.example.pulsetransit.ui.components.LocalTripDetailsActions
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.RouteSearchSheet
import com.example.pulsetransit.ui.components.StopBoardPanel
import com.example.pulsetransit.ui.components.TemporaryRouteActions
import com.example.pulsetransit.ui.components.TripDetailsSheet
import com.example.pulsetransit.ui.layout.BoardLayout
import com.example.pulsetransit.ui.pixelshift.pixelShift
import com.example.pulsetransit.ui.platform.CameraMotionEffect
import com.example.pulsetransit.ui.platform.rememberAppUpdateAction
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.JourneyAlertsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Suppress("ParamsComparedByRef")
@Composable
fun MainScreen(
    viewModel: StopBoardViewModel,
    updateViewModel: AppUpdateViewModel,
    journeyViewModel: JourneyAlertsViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.uiState.collectAsState()
    val journeyState by journeyViewModel.uiState.collectAsState()
    val windowSize = LocalWindowInfo.current.containerSize
    val isLandscape = windowSize.width > windowSize.height

    CameraMotionEffect(viewModel, key = uiState.mode)

    val startAppUpdate = rememberAppUpdateAction()

    // On-spot mode follows the device only while the board is showing, like the camera.
    LifecycleStartEffect(Unit) {
        viewModel.startLocationTracking()
        onStopOrDispose { viewModel.stopLocationTracking() }
    }

    // The fastest route whose details are open, with its destination's id.
    var detailsTrip by remember { mutableStateOf<Pair<String, TripInfo>?>(null) }
    var showRouteSearch by remember { mutableStateOf(false) }
    // The followed journey's details, opened from its bar or a tap on a notification.
    var showFollowedJourney by remember { mutableStateOf(false) }

    JourneyAlertsEffects(journeyViewModel, onOpenJourney = { showFollowedJourney = true })
    // A journey that ends while its details are open closes them, so the next one does not open them.
    LaunchedEffect(journeyState.activeJourney == null) {
        if (journeyState.activeJourney == null) showFollowedJourney = false
    }
    val journeyActions: @Composable (TripInfo) -> Unit = { trip ->
        JourneyNotifyButton(
            state = journeyState,
            trip = trip,
            onNotify = { journeyViewModel.onNotifyClick(trip) },
            onStop = journeyViewModel::onStopClick,
        )
    }

    // A dimmed wall board goes dark and drops to about half brightness: on OLED black that keeps it
    // readable from across the room without lighting it.
    val effectiveTheme = if (uiState.isScreenDimmed) ThemeMode.DARK else uiState.activeTheme
    val screenAlpha = if (uiState.isScreenDimmed) DIMMED_ALPHA else 1f
    val snackbarHostState = remember { SnackbarHostState() }
    BoardUndoSnackbar(
        pending = uiState.pendingUndo,
        hostState = snackbarHostState,
        onUndo = viewModel::undo,
        onDismiss = viewModel::dismissUndo,
    )
    val insets = if (uiState.isFullScreenMode) WindowInsets(0, 0, 0, 0) else WindowInsets.systemBars

    CompositionLocalProvider(LocalTripDetailsActions provides journeyActions) {
        PulseTransitTheme(themeMode = effectiveTheme) {
            BoxWithConstraints(
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .pixelShift(uiState.burnInLevel),
            ) {
                val wide = maxWidth.value >= WIDE_HEADER_MIN_WIDTH_DP
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    val gap = if (wide) FjordDimens.space4 else FjordDimens.space3
                    val routeColumns = if (wide) (maxWidth.value / ROUTE_CARD_MIN_WIDTH_DP).toInt().coerceAtLeast(1) else 1
                    PullToRefreshBox(
                        isRefreshing = uiState.isManualRefreshing,
                        onRefresh = viewModel::refreshNow,
                        modifier = Modifier.fillMaxSize().graphicsLayer(alpha = screenAlpha),
                        // A thin bar along the top while it runs; the content stays where it is.
                        indicator = {
                            if (uiState.isManualRefreshing) {
                                RefreshingIndicator(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .windowInsetsPadding(insets.only(WindowInsetsSides.Top))
                                            .fillMaxWidth()
                                            .height(3.dp),
                                )
                            }
                        },
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(insets)
                                    .padding(
                                        horizontal = if (wide) FjordDimens.space6 else FjordDimens.space3,
                                        vertical = if (wide) FjordDimens.space4 else FjordDimens.space3,
                                    ),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            BoardHeader(
                                uiState = uiState,
                                wide = wide,
                                onCycleDateStyle = viewModel::cycleDateStyle,
                                onSearchRoute = { showRouteSearch = true },
                                onOpenSettings = onNavigateToSettings,
                            )

                            BoardNoticeSlot(
                                uiState = uiState,
                                updateAvailable = updateState.update != null,
                                onRetry = viewModel::refreshNow,
                                onUpdate = startAppUpdate,
                                onDismissUpdate = updateViewModel::dismiss,
                            )

                            journeyState.activeJourney?.let { journey ->
                                FollowedJourneyBar(
                                    journey = journey,
                                    onOpen = { showFollowedJourney = true },
                                    onStop = journeyViewModel::onStopClick,
                                )
                                if (showFollowedJourney) {
                                    TripDetailsSheet(
                                        trip = journey.trip,
                                        showDelays = uiState.showDelays,
                                        showMessages = uiState.showMessages,
                                        loadLegStops = viewModel::tripLegStops,
                                        onDismiss = { showFollowedJourney = false },
                                    )
                                }
                            }

                            if (uiState.mode == AppMode.ON_SPOT) {
                                OnSpotContent(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    isLandscape = isLandscape,
                                    onNavigateToSettings = onNavigateToSettings,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            } else {
                                // The tapped route, captured at the tap; the details follow its refreshes.
                                detailsTrip?.let { (destinationId, selected) ->
                                    TripDetailsSheet(
                                        trip = currentTrip(uiState.fastestTrips[destinationId], selected),
                                        showDelays = uiState.showDelays,
                                        showMessages = uiState.showMessages,
                                        loadLegStops = viewModel::tripLegStops,
                                        onDismiss = { detailsTrip = null },
                                    )
                                }
                                RouteCards(
                                    uiState = uiState,
                                    columns = routeColumns,
                                    onOpenTrip = { destinationId, trip -> detailsTrip = destinationId to trip },
                                    onTimeChange = viewModel::setTripTime,
                                    onSave = viewModel::saveTemporaryDestination,
                                    onDismissTemporary = viewModel::dismissTemporaryDestination,
                                )

                                if (uiState.favorites.isEmpty()) {
                                    BoardEmptyState(
                                        onAddStops = onNavigateToSettings,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                    )
                                } else {
                                    ReorderableFavoriteList(
                                        favorites = uiState.favorites,
                                        departures = uiState.departures,
                                        directionSummaries = uiState.directionSummaries,
                                        showDelays = uiState.showDelays,
                                        showMessages = uiState.showMessages,
                                        loadingStopIds = uiState.loadingStopIds,
                                        onReorder = { from, to -> viewModel.reorderFavorites(from, to) },
                                        onFilterOut = { line -> viewModel.filterOutLine(line) },
                                        extendedStopIds = uiState.extendedStopIds,
                                        failedStopIds = uiState.failedStopIds,
                                        isOnline = uiState.isOnline,
                                        onSearchFurtherAhead = { stopId -> viewModel.searchFurtherAhead(stopId) },
                                        canLoadMoreStopIds = uiState.canLoadMoreStopIds,
                                        loadingMoreStopIds = uiState.loadingMoreStopIds,
                                        onLoadMore = { stopId -> viewModel.loadMoreDepartures(stopId) },
                                        loadJourney = viewModel::journeyStopsAhead,
                                        onRowCapacity = viewModel::setPanelRowCapacity,
                                        rotationPaused = uiState.isScreenDimmed,
                                        gap = gap,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                    )
                                }
                            }

                            BoardFooter(uiState)
                        }
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(insets)
                                .padding(FjordDimens.space4)
                                .widthIn(max = SNACKBAR_MAX_WIDTH),
                    )
                }

                if (showRouteSearch) {
                    RouteSearchSheet(
                        query = uiState.routeSearchQuery,
                        results = uiState.routeSearchResults,
                        isSearching = uiState.isSearchingRoutePlaces,
                        tripTime = uiState.temporaryTripTime,
                        onQueryChange = { viewModel.onRouteSearchQueryChanged(it) },
                        onTripTimeChange = { viewModel.setTripTime(it) },
                        onPick = { place ->
                            showRouteSearch = false
                            viewModel.showTemporaryDestination(place)
                        },
                        onDismiss = {
                            showRouteSearch = false
                            viewModel.clearRouteSearch()
                        },
                    )
                }

                if (uiState.isScreenDimmed) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { viewModel.wakeScreen() },
                    )
                }
            }
        }
    }
}

/** How bright a dimmed board is drawn, over black. */
private const val DIMMED_ALPHA = 0.55f

/** Narrowest a route card gets before wide boards put fewer of them side by side. */
private const val ROUTE_CARD_MIN_WIDTH_DP = 320f

private val SNACKBAR_MAX_WIDTH = 560.dp

/**
 * One card per destination routes are shown for (the one searched for from the board first), each
 * appearing as soon as its own route arrives, [columns] side by side. A destination whose route
 * loaded as nothing is left out, except the searched-for one, which says it has no route and keeps
 * its buttons.
 */
@Composable
private fun RouteCards(
    uiState: StopBoardViewModel.UiState,
    columns: Int,
    onOpenTrip: (destinationId: String, TripInfo) -> Unit,
    onTimeChange: (TripTime) -> Unit,
    onSave: () -> Unit,
    onDismissTemporary: () -> Unit,
) {
    val destinations =
        uiState.routeDestinations.filter {
            it.id in uiState.fastestTrips ||
                it.id in uiState.loadingTripDestinationIds ||
                it.id in uiState.failedTripDestinationIds ||
                it.id == uiState.temporaryDestination?.id
        }
    if (destinations.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
        destinations.chunked(columns.coerceAtMost(destinations.size)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                row.forEach { destination ->
                    key(destination.id) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            val trip = uiState.fastestTrips[destination.id]?.firstOrNull()
                            val noRouteTime = uiState.noRouteTime(destination.id)
                            AnimatedContent(
                                targetState = trip,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "fastestTrip",
                                contentKey = { it == null },
                                modifier = Modifier.weight(1f),
                            ) { loaded ->
                                when {
                                    loaded == null && destination.id in uiState.failedTripDestinationIds ->
                                        FastestTripUnavailable(
                                            destinationName = destination.name,
                                            isOnline = uiState.isOnline,
                                            isRetrying = destination.id in uiState.loadingTripDestinationIds,
                                        )
                                    loaded == null && noRouteTime != null ->
                                        FastestTripNone(destinationName = destination.name, time = noRouteTime)
                                    loaded == null -> FastestTripPlaceholder(destinationName = destination.name)
                                    else ->
                                        FastestTripBanner(
                                            trip = loaded,
                                            onClick = { onOpenTrip(destination.id, loaded) },
                                            isRefreshing = destination.id in uiState.loadingTripDestinationIds,
                                        )
                                }
                            }
                            if (destination.id == uiState.temporaryDestination?.id) {
                                TemporaryRouteActions(
                                    time = uiState.temporaryTripTime,
                                    onTimeChange = onTimeChange,
                                    canSave = uiState.canSaveTemporaryDestination,
                                    onSave = onSave,
                                    onDismiss = onDismissTemporary,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The favourite stops' panels, as many at once as [BoardLayout.pageSizes] lets fit. When they do
 * not all fit, the board takes turns: a pager turns to the next page every
 * [BoardLayout.PAGE_INTERVAL_MS], and [BoardPageIndicator] below says which stops are up, how many
 * there are and when the next page comes. The turning holds while [rotationPaused] (the dimmed
 * screen), while a panel is dragged and while a departure's details are open, since turning the
 * page would close them; a swipe to another page starts its full interval again.
 */
@Composable
fun ReorderableFavoriteList(
    favorites: List<StopPlace>,
    departures: List<StopDepartures>,
    directionSummaries: Map<String, DirectionSummary>,
    showDelays: Boolean,
    showMessages: Boolean,
    loadingStopIds: Set<String>,
    onReorder: (Int, Int) -> Unit,
    onFilterOut: (String) -> Unit,
    extendedStopIds: Set<String>,
    failedStopIds: Set<String>,
    isOnline: Boolean,
    onSearchFurtherAhead: (String) -> Unit,
    canLoadMoreStopIds: Set<String>,
    loadingMoreStopIds: Set<String>,
    onLoadMore: (String) -> Unit,
    loadJourney: suspend (Departure, String) -> List<JourneyStop>,
    modifier: Modifier = Modifier,
    rotationPaused: Boolean = false,
    gap: Dp = FjordDimens.space3,
    onRowCapacity: (stopId: String, rows: Int) -> Unit = { _, _ -> },
) {
    var isDragging by remember { mutableStateOf(false) }
    var openDetailsStopIds by remember { mutableStateOf(emptySet<String>()) }

    @Composable
    fun grid(
        first: Int,
        count: Int,
        gridModifier: Modifier,
    ) = StopPanelGrid(
        stops = favorites.subList(first, first + count),
        departures = departures,
        directionSummaries = directionSummaries,
        gap = gap,
        showDelays = showDelays,
        showMessages = showMessages,
        loadingStopIds = loadingStopIds,
        onReorder = { from, to -> onReorder(first + from, first + to) },
        onDraggingChange = { isDragging = it },
        onDetailsOpenChange = { stopId, open ->
            openDetailsStopIds = if (open) openDetailsStopIds + stopId else openDetailsStopIds - stopId
        },
        onFilterOut = onFilterOut,
        extendedStopIds = extendedStopIds,
        failedStopIds = failedStopIds,
        isOnline = isOnline,
        onSearchFurtherAhead = onSearchFurtherAhead,
        canLoadMoreStopIds = canLoadMoreStopIds,
        loadingMoreStopIds = loadingMoreStopIds,
        onLoadMore = onLoadMore,
        loadJourney = loadJourney,
        onRowCapacity = onRowCapacity,
        modifier = gridModifier,
    )

    BoxWithConstraints(modifier = modifier) {
        val pageSizes = BoardLayout.pageSizes(favorites.size, maxWidth.value, maxHeight.value)
        if (pageSizes.size <= 1) {
            grid(first = 0, count = favorites.size, gridModifier = Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val pageStarts = pageSizes.runningFold(0) { start, size -> start + size }
        val pagerState = rememberPagerState { pageSizes.size }
        // Tapping the page dots holds the current page until they are tapped again.
        var heldByUser by remember { mutableStateOf(false) }
        val paused = rotationPaused || heldByUser || isDragging || openDetailsStopIds.isNotEmpty()
        LaunchedEffect(pagerState.settledPage, pageSizes.size, paused) {
            if (paused) return@LaunchedEffect
            delay(BoardLayout.PAGE_INTERVAL_MS.milliseconds)
            pagerState.animateScrollToPage((pagerState.settledPage + 1) % pageSizes.size)
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // A panel dragged to reorder moves within its page; the pager must not take the drag.
                userScrollEnabled = !isDragging,
                key = { page -> favorites.getOrNull(pageStarts[page])?.id ?: page },
            ) { page ->
                grid(first = pageStarts[page], count = pageSizes[page], gridModifier = Modifier.fillMaxSize())
            }
            val page = pagerState.currentPage.coerceIn(pageSizes.indices)
            BoardPageIndicator(
                page = page,
                pageCount = pageSizes.size,
                firstStop = pageStarts[page] + 1,
                lastStop = pageStarts[page] + pageSizes[page],
                stopCount = favorites.size,
                paused = paused,
                onTogglePause = { heldByUser = !heldByUser },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(BoardLayout.PAGE_INDICATOR_HEIGHT_DP.dp),
            )
        }
    }
}

/**
 * Which stops the page shows ("Stops 3–4 of 6") beside a dot per page, the current one long. The
 * board turns by itself every [BoardLayout.PAGE_INTERVAL_MS]; screen readers hear when.
 */
@Composable
private fun BoardPageIndicator(
    page: Int,
    pageCount: Int,
    firstStop: Int,
    lastStop: Int,
    stopCount: Int,
    paused: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        if (paused) {
            stringResource(Res.string.board_page_paused, page + 1, pageCount)
        } else {
            stringResource(Res.string.board_page_description, page + 1, pageCount, (BoardLayout.PAGE_INTERVAL_MS / 1000).toInt())
        }
    val off = MaterialTheme.colorScheme.outlineVariant
    val on = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            modifier
                .clickable(onClick = onTogglePause)
                .clearAndSetSemantics {
                    contentDescription = description
                    onClick {
                        onTogglePause()
                        true
                    }
                },
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                if (firstStop == lastStop) {
                    stringResource(Res.string.board_page_stop, firstStop, stopCount)
                } else {
                    stringResource(Res.string.board_page_stops, firstStop, lastStop, stopCount)
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .size(width = if (index == page) 20.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (index == page) on else off),
            )
        }
    }
}

/**
 * [stops] laid out by [BoardLayout.columns] over the space they have, a long press dragging a panel
 * to reorder them. [onReorder] takes indices into [stops].
 */
@Composable
private fun StopPanelGrid(
    stops: List<StopPlace>,
    departures: List<StopDepartures>,
    directionSummaries: Map<String, DirectionSummary>,
    gap: Dp,
    showDelays: Boolean,
    showMessages: Boolean,
    loadingStopIds: Set<String>,
    onReorder: (Int, Int) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onDetailsOpenChange: (stopId: String, open: Boolean) -> Unit,
    onFilterOut: (String) -> Unit,
    extendedStopIds: Set<String>,
    failedStopIds: Set<String>,
    isOnline: Boolean,
    onSearchFurtherAhead: (String) -> Unit,
    canLoadMoreStopIds: Set<String>,
    loadingMoreStopIds: Set<String>,
    onLoadMore: (String) -> Unit,
    loadJourney: suspend (Departure, String) -> List<JourneyStop>,
    onRowCapacity: (stopId: String, rows: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier) {
        val itemCount = stops.size
        val columnSizes = BoardLayout.columnSizes(itemCount, BoardLayout.columns(itemCount, maxWidth.value, maxHeight.value))
        val grid = PanelGrid(columnSizes, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        // The slot the dragged panel is over: the one whose centre is nearest its own.
        val currentTargetIndex =
            draggingIndex?.let { start ->
                val center = grid.center(start) + dragOffset
                (0 until itemCount).minBy { (grid.center(it) - center).getDistanceSquared() }
            }

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            columnSizes.forEachIndexed { column, size ->
                val first = columnSizes.take(column).sum()
                // zIndex orders siblings only, so the dragged panel's column is raised too, or the
                // panel would pass under the columns after it.
                val holdsDragged = draggingIndex?.let { it in first until first + size } == true
                Column(
                    modifier = Modifier.fillMaxHeight().weight(1f).zIndex(if (holdsDragged) 1f else 0f),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    stops.subList(first, first + size).forEachIndexed { inColumn, stop ->
                        val index = first + inColumn
                        key(stop.id) {
                            val isDragging = draggingIndex == index

                            // Panels between the dragged one and its target move one slot towards
                            // where it came from, making room for it.
                            val visualOffset =
                                remember(index, draggingIndex, currentTargetIndex, grid) {
                                    val start = draggingIndex
                                    if (start == null || currentTargetIndex == null || isDragging) {
                                        Offset.Zero
                                    } else if (index in (minOf(start, currentTargetIndex)..maxOf(start, currentTargetIndex))) {
                                        val direction = if (start < currentTargetIndex) -1 else 1
                                        grid.topLeft(index + direction) - grid.topLeft(index)
                                    } else {
                                        Offset.Zero
                                    }
                                }

                            val animatedOffset by animateOffsetAsState(
                                targetValue = if (isDragging) dragOffset else visualOffset,
                                animationSpec = spring(stiffness = if (isDragging) 2500f else 400f),
                                label = "itemOffset",
                            )

                            // The gesture below outlives this composition (pointerInput(Unit) is
                            // never restarted), so it reads these through State: captured plainly,
                            // the drop would always see the null target of the first composition
                            // and never reorder, and a moved panel would drag from its old slot.
                            val latestIndex by rememberUpdatedState(index)
                            val latestTarget by rememberUpdatedState(currentTargetIndex)

                            // A panel paged out of view cannot have its details open any more.
                            DisposableEffect(stop.id) { onDispose { onDetailsOpenChange(stop.id, false) } }

                            val onDragStart = { _: Offset ->
                                draggingIndex = latestIndex
                                onDraggingChange(true)
                            }
                            val onDragEnd = {
                                val from = draggingIndex
                                latestTarget?.let { target ->
                                    if (from != null && target != from) onReorder(from, target)
                                }
                                draggingIndex = null
                                dragOffset = Offset.Zero
                                onDraggingChange(false)
                            }
                            val onDragCancel = {
                                draggingIndex = null
                                dragOffset = Offset.Zero
                                onDraggingChange(false)
                            }
                            val onDrag = { change: PointerInputChange, dragAmount: Offset ->
                                change.consume()
                                dragOffset += dragAmount
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .offset { IntOffset(animatedOffset.x.roundToInt(), animatedOffset.y.roundToInt()) }
                                        // Anywhere on the panel after a long press, so a tap still opens a row.
                                        .pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(onDragStart, onDragEnd, onDragCancel, onDrag)
                                        },
                            ) {
                                StopBoardPanel(
                                    stopPlace = stop,
                                    directions = directionSummaries[stop.id],
                                    departureGroups = departures.find { it.stopId == stop.id }?.groups,
                                    showDelays = showDelays,
                                    showMessages = showMessages,
                                    isLoading = stop.id in loadingStopIds,
                                    isExtended = stop.id in extendedStopIds,
                                    isFailed = stop.id in failedStopIds,
                                    isOnline = isOnline,
                                    onFilterOut = onFilterOut,
                                    onSearchFurtherAhead = { onSearchFurtherAhead(stop.id) },
                                    canLoadMore = stop.id in canLoadMoreStopIds,
                                    isLoadingMore = stop.id in loadingMoreStopIds,
                                    onLoadMore = { onLoadMore(stop.id) },
                                    loadJourney = { departure -> loadJourney(departure, stop.id) },
                                    onRowCapacity = { rows -> onRowCapacity(stop.id, rows) },
                                    onDetailsOpenChange = { open -> onDetailsOpenChange(stop.id, open) },
                                    // The handle shows the panels can be moved, and drags at once.
                                    dragHandle =
                                        if (itemCount > 1) {
                                            Modifier.pointerInput(Unit) {
                                                detectDragGestures(onDragStart, onDragEnd, onDragCancel, onDrag)
                                            }
                                        } else {
                                            null
                                        },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Where each panel sits, in px, when [columnSizes] stack them column by column over a [width] by
 * [height] space, each column's panels sharing its height. Used to follow a drag across the board.
 */
private data class PanelGrid(
    val columnSizes: List<Int>,
    val width: Float,
    val height: Float,
) {
    private val columnWidth = width / columnSizes.size.coerceAtLeast(1)

    private fun rect(index: Int): Rect {
        var first = 0
        columnSizes.forEachIndexed { column, size ->
            if (index < first + size) {
                val panelHeight = height / size
                return Rect(Offset(column * columnWidth, (index - first) * panelHeight), Size(columnWidth, panelHeight))
            }
            first += size
        }
        return Rect.Zero
    }

    fun topLeft(index: Int): Offset = rect(index).topLeft

    fun center(index: Int): Offset = rect(index).center
}

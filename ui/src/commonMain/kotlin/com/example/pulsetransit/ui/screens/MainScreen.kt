package com.example.pulsetransit.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.StopDepartures
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.currentTrip
import com.example.pulsetransit.data.parseWeatherSymbol
import com.example.pulsetransit.data.tripArrival
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.BlurredPlaceholder
import com.example.pulsetransit.ui.components.FollowedJourneyBar
import com.example.pulsetransit.ui.components.JourneyAlertsEffects
import com.example.pulsetransit.ui.components.JourneyNotifyButton
import com.example.pulsetransit.ui.components.LocalTripDetailsActions
import com.example.pulsetransit.ui.components.PlaceholderBlock
import com.example.pulsetransit.ui.components.PrecipitationBar
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.RouteSearchSheet
import com.example.pulsetransit.ui.components.StopBoardPanel
import com.example.pulsetransit.ui.components.TemporaryRouteActions
import com.example.pulsetransit.ui.components.TripDetailsSheet
import com.example.pulsetransit.ui.components.UpdateBanner
import com.example.pulsetransit.ui.components.WeatherIcon
import com.example.pulsetransit.ui.components.getTransportEmoji
import com.example.pulsetransit.ui.components.loadFailureIcon
import com.example.pulsetransit.ui.components.loadFailureReason
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
import com.example.pulsetransit.ui.components.temperatureColor
import com.example.pulsetransit.ui.components.weatherLabel
import com.example.pulsetransit.ui.layout.BoardLayout
import com.example.pulsetransit.ui.pixelshift.pixelShift
import com.example.pulsetransit.ui.platform.CameraMotionEffect
import com.example.pulsetransit.ui.platform.rememberAppUpdateAction
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.JourneyAlertsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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

    val effectiveTheme = if (uiState.isScreenDimmed) ThemeMode.DARK else uiState.activeTheme
    val screenAlpha = if (uiState.isScreenDimmed) 0.05f else 1f

    CompositionLocalProvider(LocalTripDetailsActions provides journeyActions) {
        PulseTransitTheme(themeMode = effectiveTheme) {
            val backgroundColor = if (uiState.isScreenDimmed) Color.Black else MaterialTheme.colorScheme.background
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .pixelShift(uiState.burnInLevel),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = screenAlpha),
                ) {
                    // Header Bar
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .windowInsetsPadding(if (uiState.isFullScreenMode) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        // The buttons keep their room: the clock gets what they leave, stepping its date
                        // down to fit, and only where even the time would not fit do they fold into a menu.
                        val collapseActions = maxWidth < HEADER_ACTIONS_WIDTH + HEADER_MIN_CLOCK_WIDTH
                        val clockMaxWidth =
                            (maxWidth - if (collapseActions) HEADER_ACTION_SIZE else HEADER_ACTIONS_WIDTH).coerceAtLeast(
                                0.dp,
                            )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Left: Digital Clock + date; tapping cycles the date style.
                            HeaderClock(
                                uiState = uiState,
                                onClick = { viewModel.cycleDateStyle() },
                                modifier = Modifier.widthIn(max = clockMaxWidth),
                            )

                            // Middle: Weather, unless turned off in Settings
                            if (uiState.showWeather) {
                                BoxWithConstraints(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Fit only as many columns as the width allows: a forced minimum
                                    // squeezed "19:00" to "19:0" in a phone's portrait header. The labels
                                    // grow with the system font scale, so the column budget does too.
                                    // With no room for one, the forecast is left out.
                                    val itemWidth = 56.dp * LocalDensity.current.fontScale
                                    val maxItems = (maxWidth / itemWidth).toInt().coerceAtMost(8)
                                    val totalAvailable = uiState.weather.size
                                    val step = if (totalAvailable > maxItems) totalAvailable / maxItems.coerceAtLeast(1) else 1

                                    if (maxItems > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                        ) {
                                            uiState.weather
                                                .filterIndexed { index, _ -> index % step == 0 }
                                                .take(maxItems)
                                                .forEach { w ->
                                                    // Equal-width columns: a long condition label ("Partly
                                                    // cloudy") must wrap inside its column rather than stretch
                                                    // it and squeeze the neighbours.
                                                    WeatherItem(w, modifier = Modifier.weight(1f))
                                                }
                                        }
                                    }
                                }
                            }

                            // Right: Control Buttons
                            HeaderActions(
                                collapsed = collapseActions,
                                onSearchRoute = { showRouteSearch = true },
                                onRefresh = { viewModel.refreshData() },
                                onOpenSettings = onNavigateToSettings,
                            )
                        }
                    }

                    if (updateState.update != null) {
                        UpdateBanner(
                            onUpdate = startAppUpdate,
                            onDismiss = { updateViewModel.dismiss() },
                        )
                    }

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
                            modifier = Modifier.fillMaxSize().weight(1f),
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
                        // Fastest route banners (one per destination station set in Settings, after the
                        // one searched for from the board), each appearing as soon as its own route
                        // arrives. A destination with no route is left out once it has loaded. They
                        // share one background colour, so without a rule between them consecutive
                        // banners read as a single block.
                        uiState.routeDestinations
                            .filter {
                                it.id in uiState.fastestTrips ||
                                    it.id in uiState.loadingTripDestinationIds ||
                                    it.id in uiState.failedTripDestinationIds
                            }.forEachIndexed { index, destination ->
                                key(destination.id) {
                                    if (index > 0) {
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                                        )
                                    }
                                    val trip = uiState.fastestTrips[destination.id]?.firstOrNull()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AnimatedContent(
                                            targetState = trip,
                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                            label = "fastestTrip",
                                            contentKey = { it == null },
                                            modifier = Modifier.weight(1f),
                                        ) { loaded ->
                                            if (loaded == null && destination.id in uiState.failedTripDestinationIds) {
                                                FastestTripUnavailable(
                                                    destinationName = destination.name,
                                                    isOnline = uiState.isOnline,
                                                    isRetrying = destination.id in uiState.loadingTripDestinationIds,
                                                )
                                            } else if (loaded == null) {
                                                FastestTripPlaceholder(destinationName = destination.name)
                                            } else {
                                                FastestTripBanner(
                                                    trip = loaded,
                                                    onClick = { detailsTrip = destination.id to loaded },
                                                    isRefreshing = destination.id in uiState.loadingTripDestinationIds,
                                                )
                                            }
                                        }
                                        if (destination.id == uiState.temporaryDestination?.id) {
                                            TemporaryRouteActions(
                                                canSave = uiState.canSaveTemporaryDestination,
                                                onSave = { viewModel.saveTemporaryDestination() },
                                                onDismiss = { viewModel.dismissTemporaryDestination() },
                                            )
                                        }
                                    }
                                }
                            }

                        // Body Area (Equal screen division between favorite stops)
                        if (uiState.favorites.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.no_stops_configured),
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                    Button(onClick = onNavigateToSettings) {
                                        Text(stringResource(Res.string.open_settings_to_add))
                                    }
                                }
                            }
                        } else {
                            ReorderableFavoriteList(
                                favorites = uiState.favorites,
                                departures = uiState.departures,
                                showDelays = uiState.showDelays,
                                showMessages = uiState.showMessages,
                                loadingStopIds = uiState.loadingStopIds,
                                onReorder = { from, to -> viewModel.reorderFavorites(from, to) },
                                onFilterOut = { line -> viewModel.filterOutLine(line) },
                                extendedStopIds = uiState.extendedStopIds,
                                failedStopIds = uiState.failedStopIds,
                                isOnline = uiState.isOnline,
                                onSearchFurtherAhead = { stopId -> viewModel.searchFurtherAhead(stopId) },
                                onRetry = { viewModel.refreshData() },
                                canLoadMoreStopIds = uiState.canLoadMoreStopIds,
                                loadingMoreStopIds = uiState.loadingMoreStopIds,
                                onLoadMore = { stopId -> viewModel.loadMoreDepartures(stopId) },
                                loadJourney = viewModel::journeyStopsAhead,
                                onRowCapacity = viewModel::setPanelRowCapacity,
                                rotationPaused = uiState.isScreenDimmed,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                        .padding(6.dp),
                            )
                        }
                    }

                    // Subtle Last Updated and Attribution at the bottom
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    if (uiState.isFullScreenMode) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
                                ).padding(top = 2.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        uiState.staleAgeMinutes?.let { ageMinutes ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = loadFailureIcon(uiState.isOnline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text =
                                        stringResource(
                                            if (uiState.isOnline) {
                                                Res.string.offline_stale_data
                                            } else {
                                                Res.string.offline_no_internet_stale_data
                                            },
                                            ageMinutes,
                                        ),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error,
                                        ),
                                )
                            }
                        }
                        Text(
                            text = stringResource(Res.string.last_updated, uiState.lastUpdated),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                ),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.attribution_entur),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    ),
                            )
                            if (uiState.showWeather) {
                                Text(
                                    text = stringResource(Res.string.attribution_met),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        ),
                                )
                            }
                        }
                    }
                }

                if (showRouteSearch) {
                    RouteSearchSheet(
                        query = uiState.routeSearchQuery,
                        results = uiState.routeSearchResults,
                        isSearching = uiState.isSearchingRoutePlaces,
                        onQueryChange = { viewModel.onRouteSearchQueryChanged(it) },
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

@Composable
fun FastestTripBanner(
    trip: TripInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
) {
    val departureTime =
        try {
            trip.expectedStartTime.substring(11, 16)
        } catch (_: Exception) {
            "--:--"
        }

    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val timeText: @Composable () -> Unit = {
        Text(
            text = departureTime,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = onContainer,
            maxLines = 1,
        )
    }
    val durationText: @Composable () -> Unit = {
        Text(
            text = stringResource(Res.string.duration_min, trip.durationMinutes),
            style = MaterialTheme.typography.titleMedium,
            color = onContainer,
            maxLines = 1,
        )
    }
    val titleAndRoute: @Composable () -> Unit = {
        Text(
            text = fastestTripTitle(trip),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = onContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = tripRouteText(trip),
            style = MaterialTheme.typography.bodyMedium,
            color = onContainer.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val contentModifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        if (BoardLayout.isCompactBanner(maxWidth.value)) {
            // Too narrow for the title between the time and the duration: it goes under them.
            Column(modifier = contentModifier) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    timeText()
                    durationText()
                }
                titleAndRoute()
            }
        } else {
            Row(
                modifier = contentModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                timeText()
                Column(modifier = Modifier.weight(1f)) { titleAndRoute() }
                durationText()
            }
        }
        if (isRefreshing) {
            RefreshingIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
            )
        }
    }
}

/** A trip's legs as "🚇 2 from Oslo S  →  🚶 4 min", or its first line and origin if Entur sent no legs. */
@Composable
internal fun tripRouteText(trip: TripInfo): String =
    if (trip.legs.isNotEmpty()) {
        trip.legs
            .mapNotNull { leg ->
                when {
                    leg.lineName != null -> {
                        val prefix = "${getTransportEmoji(leg.mode)} ${leg.lineName}"
                        leg.fromName?.let { "$prefix ${stringResource(Res.string.leg_from, it)}" } ?: prefix
                    }
                    leg.durationMinutes > 0 ->
                        stringResource(Res.string.leg_walk, leg.durationMinutes)
                    else -> null // skip 0-minute foot legs (quay transfers)
                }
            }.joinToString("  →  ")
    } else {
        stringResource(Res.string.fastest_route_info, trip.lineName, trip.originName)
    }

// Size of the "+1" next-day marker on an arrival time, as on the board's departure times.
private const val ARRIVAL_DAY_OFFSET_FONT_SCALE = 0.6f

/** "Fastest to Majorstuen 18:31", with a raised "+1" when the trip arrives after midnight. */
@Composable
private fun fastestTripTitle(trip: TripInfo): AnnotatedString {
    val arrival = tripArrival(trip) ?: return AnnotatedString(stringResource(Res.string.fastest_route_to, trip.destinationName))
    val title = stringResource(Res.string.fastest_route_to_arriving, trip.destinationName, arrival.time)
    if (arrival.dayOffset <= 0) return AnnotatedString(title)
    // The marker follows the time wherever the translation puts it.
    val timeEnd = title.lastIndexOf(arrival.time) + arrival.time.length
    return buildAnnotatedString {
        append(title.substring(0, timeEnd))
        withStyle(SpanStyle(fontSize = ARRIVAL_DAY_OFFSET_FONT_SCALE.em, baselineShift = BaselineShift.Superscript)) {
            append("+${arrival.dayOffset}")
        }
        append(title.substring(timeEnd))
    }
}

/** Keeps a destination's slot when its route failed to load, rather than silently dropping it. */
@Composable
fun FastestTripUnavailable(
    destinationName: String,
    isOnline: Boolean,
    isRetrying: Boolean,
    modifier: Modifier = Modifier,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = loadFailureIcon(isOnline),
                contentDescription = null,
                tint = onContainer.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.fastest_route_unavailable, destinationName),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = loadFailureReason(isOnline),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.8f),
                )
            }
        }
        if (isRetrying) {
            RefreshingIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                color = onContainer.copy(alpha = 0.6f),
            )
        }
    }
}

/** Stand-in for a [FastestTripBanner] whose route hasn't arrived yet, blurred until it does. */
@Composable
fun FastestTripPlaceholder(
    destinationName: String,
    modifier: Modifier = Modifier,
) {
    val alpha = rememberPlaceholderPulse()
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    BlurredPlaceholder(
        label = stringResource(Res.string.loading_fastest_route, destinationName),
        contentColor = onContainer,
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        // Mirrors the banner's layout: departure time, title over route, duration.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlaceholderBlock(alpha, Modifier.size(width = 64.dp, height = 28.dp), color = onContainer)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaceholderBlock(alpha, Modifier.fillMaxWidth(0.5f).height(18.dp), color = onContainer)
                PlaceholderBlock(alpha, Modifier.fillMaxWidth(0.8f).height(14.dp), color = onContainer)
            }
            PlaceholderBlock(alpha, Modifier.size(width = 48.dp, height = 20.dp), color = onContainer)
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
    showDelays: Boolean,
    showMessages: Boolean,
    loadingStopIds: Set<String>,
    onReorder: (Int, Int) -> Unit,
    onFilterOut: (String) -> Unit,
    extendedStopIds: Set<String>,
    failedStopIds: Set<String>,
    isOnline: Boolean,
    onSearchFurtherAhead: (String) -> Unit,
    onRetry: () -> Unit,
    canLoadMoreStopIds: Set<String>,
    loadingMoreStopIds: Set<String>,
    onLoadMore: (String) -> Unit,
    loadJourney: suspend (Departure, String) -> List<JourneyStop>,
    modifier: Modifier = Modifier,
    rotationPaused: Boolean = false,
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
        onRetry = onRetry,
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
        val paused = rotationPaused || isDragging || openDetailsStopIds.isNotEmpty()
        // How far the current page is through its interval, read only when the indicator draws.
        val progress = remember { Animatable(0f) }
        LaunchedEffect(pagerState.settledPage, pageSizes.size, paused) {
            progress.snapTo(0f)
            if (paused) return@LaunchedEffect
            progress.animateTo(1f, tween(BoardLayout.PAGE_INTERVAL_MS.toInt(), easing = LinearEasing))
            pagerState.animateScrollToPage((pagerState.settledPage + 1) % pageSizes.size)
        }

        Column(modifier = Modifier.fillMaxSize()) {
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
                progress = { progress.value },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(BoardLayout.PAGE_INDICATOR_HEIGHT_DP.dp),
            )
        }
    }
}

/**
 * Which stops the page shows ("Stops 3–4 of 6") beside a segment per page. The current page's
 * segment fills over its interval, so it reads as the board turning by itself and when it will.
 */
@Composable
private fun BoardPageIndicator(
    page: Int,
    pageCount: Int,
    firstStop: Int,
    lastStop: Int,
    stopCount: Int,
    paused: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val description =
        if (paused) {
            stringResource(Res.string.board_page_paused, page + 1, pageCount)
        } else {
            stringResource(Res.string.board_page_description, page + 1, pageCount, (BoardLayout.PAGE_INTERVAL_MS / 1000).toInt())
        }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val fill = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(pageCount) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(width = 20.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(track)
                            .drawBehind {
                                if (index == page) drawRect(fill, size = size.copy(width = size.width * progress()))
                            },
                )
            }
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
    onRetry: () -> Unit,
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

        Row(modifier = Modifier.fillMaxSize()) {
            columnSizes.forEachIndexed { column, size ->
                val first = columnSizes.take(column).sum()
                // zIndex orders siblings only, so the dragged panel's column is raised too, or the
                // panel would pass under the columns after it.
                val holdsDragged = draggingIndex?.let { it in first until first + size } == true
                Column(modifier = Modifier.fillMaxHeight().weight(1f).zIndex(if (holdsDragged) 1f else 0f)) {
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
                                    departureGroups = departures.find { it.stopId == stop.id }?.groups,
                                    showDelays = showDelays,
                                    showMessages = showMessages,
                                    isLoading = stop.id in loadingStopIds,
                                    isExtended = stop.id in extendedStopIds,
                                    isFailed = stop.id in failedStopIds,
                                    isOnline = isOnline,
                                    onFilterOut = onFilterOut,
                                    onSearchFurtherAhead = { onSearchFurtherAhead(stop.id) },
                                    onRetry = onRetry,
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

@Composable
fun WeatherItem(
    weather: WeatherInfo,
    modifier: Modifier = Modifier,
) {
    // MET returns times in UTC (trailing `Z`); the display formatter uses the device's local
    // zone, so the parser must read the wall-clock as UTC or the hour comes out offset.
    val locale = SimpleDateFormat("HH", Locale.getDefault())
    val hour =
        try {
            val sdf =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            val date = sdf.parse(weather.time.substring(0, 19))
            if (date != null) {
                locale.format(date)
            } else {
                "??"
            }
        } catch (_: Exception) {
            "??"
        }

    val symbol = remember(weather.symbolCode) { parseWeatherSymbol(weather.symbolCode) }
    val label = weatherLabel(symbol)
    val precipitationDescription =
        stringResource(Res.string.weather_precipitation, weather.precipitation.toString())

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "$hour:00",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        WeatherIcon(
            symbol = symbol,
            modifier =
                Modifier
                    .size(26.dp)
                    .semantics { contentDescription = label },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "${weather.temperature.roundToInt()}°",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                color = temperatureColor(weather.temperature),
            )
            PrecipitationBar(
                millimetres = weather.precipitation,
                modifier =
                    Modifier
                        .size(width = 4.dp, height = 16.dp)
                        .semantics {
                            contentDescription = precipitationDescription
                        },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

// The header's three buttons, at their touch-target size and spacing, and the least the clock needs
// beside them before they fold into one menu button.
private val HEADER_ACTION_SIZE = 48.dp
private val HEADER_ACTIONS_WIDTH = HEADER_ACTION_SIZE * 3 + 8.dp * 2
private val HEADER_MIN_CLOCK_WIDTH = 72.dp

/**
 * Search, refresh and Settings, side by side; [collapsed], behind one menu button, for a header
 * too narrow to give them their room and still show the time.
 */
@Composable
private fun HeaderActions(
    collapsed: Boolean,
    onSearchRoute: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    if (collapsed) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(Res.string.more_actions), tint = tint)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(
                    Triple(Icons.Default.Search, Res.string.search_route, onSearchRoute),
                    Triple(Icons.Default.Refresh, Res.string.refresh, onRefresh),
                    Triple(Icons.Default.Settings, Res.string.settings_title, onOpenSettings),
                ).forEach { (icon, label, action) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onSearchRoute) {
            Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(Res.string.search_route), tint = tint)
        }
        IconButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(Res.string.refresh), tint = tint)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(Res.string.settings_title), tint = tint)
        }
    }
}

/**
 * The header clock with the date drawn in the user's chosen [DateDisplayStyle]. Every visible style
 * gives the day of month the emphasis; weekday and month are secondary context.
 */
@Composable
private fun HeaderClock(
    uiState: StopBoardViewModel.UiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeStyle =
        MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    val dayStyle = timeStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    // The date strings arrive with the first clock tick; until then show the time alone.
    val style = if (uiState.currentDayOfMonth.isEmpty()) DateDisplayStyle.HIDDEN else uiState.dateStyle
    val clickModifier =
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(Res.string.change_date_style), onClick = onClick)

    // The chosen style where it fits; in a narrower header the time alone, then a smaller time,
    // so the date gives way rather than pushing the buttons out of the header.
    FirstThatFits(
        clickModifier,
        { HeaderClockContent(uiState, style, timeStyle, dayStyle, secondary) },
        { HeaderClockContent(uiState, DateDisplayStyle.HIDDEN, timeStyle, dayStyle, secondary) },
        { HeaderClockContent(uiState, DateDisplayStyle.HIDDEN, timeStyle.copy(fontSize = 22.sp), dayStyle, secondary) },
    )
}

/** The header clock drawn in one [DateDisplayStyle]. */
@Composable
private fun HeaderClockContent(
    uiState: StopBoardViewModel.UiState,
    style: DateDisplayStyle,
    timeStyle: TextStyle,
    dayStyle: TextStyle,
    secondary: Color,
) {
    when (style) {
        DateDisplayStyle.HIDDEN -> {
            Text(text = uiState.currentTime, style = timeStyle, maxLines = 1)
        }

        DateDisplayStyle.BESIDE -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = uiState.currentTime, style = timeStyle)
                Text(
                    text = uiState.currentDayOfMonth,
                    modifier = Modifier.padding(start = 10.dp),
                    style = dayStyle,
                )
                Column {
                    Text(text = uiState.currentWeekday, style = MaterialTheme.typography.labelMedium, color = secondary)
                    Text(text = uiState.currentMonth, style = MaterialTheme.typography.labelMedium, color = secondary)
                }
            }
        }

        DateDisplayStyle.STACKED -> {
            Column {
                Text(text = uiState.currentTime, style = timeStyle)
                Text(
                    text =
                        buildAnnotatedString {
                            append(uiState.currentWeekday)
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) { append(uiState.currentDayOfMonth) }
                            append(" ")
                            append(uiState.currentMonth)
                        },
                    style = MaterialTheme.typography.labelLarge,
                    color = secondary,
                )
            }
        }

        DateDisplayStyle.TILE -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = uiState.currentTime, style = timeStyle)
                Column(
                    modifier =
                        Modifier
                            .width(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.currentMonth.uppercase(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Text(
                        text = uiState.currentDayOfMonth,
                        style = dayStyle.copy(fontSize = 22.sp),
                    )
                }
            }
        }

        DateDisplayStyle.LONG -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = uiState.currentTime, modifier = Modifier.alignByBaseline(), style = timeStyle)
                Text(
                    text = uiState.currentWeekdayLong,
                    modifier = Modifier.alignByBaseline().padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = secondary,
                )
                Text(
                    text = uiState.currentDayOfMonth,
                    modifier = Modifier.alignByBaseline(),
                    style = dayStyle.copy(fontSize = 26.sp),
                )
                Text(
                    text = uiState.currentMonthLong,
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium,
                    color = secondary,
                )
            }
        }
    }
}

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

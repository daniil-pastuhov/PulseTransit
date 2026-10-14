package com.example.pulsetransit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.pulsetransit.R
import com.example.pulsetransit.data.DateDisplayStyle
import com.example.pulsetransit.data.StopDepartures
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.data.parseWeatherSymbol
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.BlurredPlaceholder
import com.example.pulsetransit.ui.components.PlaceholderBlock
import com.example.pulsetransit.ui.components.PrecipitationBar
import com.example.pulsetransit.ui.components.RefreshingIndicator
import com.example.pulsetransit.ui.components.StopBoardPanel
import com.example.pulsetransit.ui.components.WeatherIcon
import com.example.pulsetransit.ui.components.getTransportEmoji
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
import com.example.pulsetransit.ui.components.temperatureColor
import com.example.pulsetransit.ui.components.weatherLabel
import com.example.pulsetransit.ui.pixelshift.pixelShift
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@Suppress("ParamsComparedByRef")
@Composable
fun MainScreen(
    viewModel: StopBoardViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.startCameraMotion()
            }
        }

    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!granted && viewModel.isCameraMotionEnabled()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // The camera runs only while the board is visible: it is released when the app goes to the
    // background or the board leaves the screen, and reopened when it comes back.
    LifecycleStartEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCameraMotion()
        }
        onStopOrDispose { viewModel.stopCameraMotion() }
    }

    val effectiveTheme = if (uiState.isScreenDimmed) ThemeMode.DARK else uiState.activeTheme
    val screenAlpha = if (uiState.isScreenDimmed) 0.05f else 1f

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
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .windowInsetsPadding(if (uiState.isFullScreenMode) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left: Digital Clock + date; tapping cycles the date style.
                    HeaderClock(uiState = uiState, onClick = { viewModel.cycleDateStyle() })

                    // Middle: Weather
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
                        val itemWidth = 56.dp * LocalDensity.current.fontScale
                        val maxItems = (maxWidth / itemWidth).toInt().coerceIn(1, 8)
                        val totalAvailable = uiState.weather.size
                        val step = if (totalAvailable > maxItems) totalAvailable / maxItems else 1

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

                    // Right: Control Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        IconButton(onClick = { viewModel.refreshData() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Fastest route banners (one per destination station set in Settings), each
                // appearing as soon as its own route arrives. A destination with no route is
                // left out once it has loaded. They share one background colour, so without a
                // rule between them consecutive banners read as a single block.
                uiState.destinationStations
                    .filter { it.id in uiState.fastestTrips || it.id in uiState.loadingTripDestinationIds }
                    .forEachIndexed { index, destination ->
                        key(destination.id) {
                            if (index > 0) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                                )
                            }
                            val trip = uiState.fastestTrips[destination.id]
                            AnimatedContent(
                                targetState = trip,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "fastestTrip",
                                contentKey = { it == null },
                            ) { loaded ->
                                if (loaded == null) {
                                    FastestTripPlaceholder(destinationName = destination.name)
                                } else {
                                    FastestTripBanner(
                                        trip = loaded,
                                        isRefreshing = destination.id in uiState.loadingTripDestinationIds,
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
                                text = stringResource(R.string.no_stops_configured),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Button(onClick = onNavigateToSettings) {
                                Text(stringResource(R.string.open_settings_to_add))
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
                        isLandscape = isLandscape,
                        onReorder = { from, to -> viewModel.reorderFavorites(from, to) },
                        onFilterOut = { line -> viewModel.filterOutLine(line) },
                        extendedStopIds = uiState.extendedStopIds,
                        onSearchFurtherAhead = { stopId -> viewModel.searchFurtherAhead(stopId) },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .padding(6.dp),
                    )
                }

                // Subtle Last Updated and Attribution at the bottom
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(if (uiState.isFullScreenMode) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars)
                            .padding(top = 2.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    uiState.staleAgeMinutes?.let { ageMinutes ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = stringResource(R.string.offline_stale_data, ageMinutes),
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
                        text = stringResource(R.string.last_updated, uiState.lastUpdated),
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
                            text = stringResource(R.string.attribution_entur),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                ),
                        )
                        Text(
                            text = stringResource(R.string.attribution_met),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                ),
                        )
                    }
                }
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

@Composable
fun FastestTripBanner(
    trip: TripInfo,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
) {
    val departureTime =
        try {
            trip.expectedStartTime.substring(11, 16)
        } catch (_: Exception) {
            "--:--"
        }

    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    val canExpand = hasOverflow || expanded

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = canExpand) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = departureTime,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fastest_route_to, trip.destinationName),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val routeText =
                    if (trip.legs.isNotEmpty()) {
                        trip.legs
                            .mapNotNull { leg ->
                                when {
                                    leg.lineName != null -> {
                                        val prefix = "${getTransportEmoji(leg.mode)} ${leg.lineName}"
                                        leg.fromName?.let { "$prefix ${stringResource(R.string.leg_from, it)}" } ?: prefix
                                    }
                                    leg.durationMinutes > 0 ->
                                        stringResource(R.string.leg_walk, leg.durationMinutes)
                                    else -> null // skip 0-minute foot legs (quay transfers)
                                }
                            }.joinToString("  →  ")
                    } else {
                        stringResource(R.string.fastest_route_info, trip.lineName, trip.originName)
                    }
                Text(
                    text = routeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!expanded) hasOverflow = result.hasVisualOverflow
                    },
                )
            }
            Text(
                text = stringResource(R.string.duration_min, trip.durationMinutes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (canExpand) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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

/** Stand-in for a [FastestTripBanner] whose route hasn't arrived yet, blurred until it does. */
@Composable
fun FastestTripPlaceholder(
    destinationName: String,
    modifier: Modifier = Modifier,
) {
    val alpha = rememberPlaceholderPulse()
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    BlurredPlaceholder(
        label = stringResource(R.string.loading_fastest_route, destinationName),
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

@Composable
fun ReorderableFavoriteList(
    favorites: List<StopPlace>,
    departures: List<StopDepartures>,
    showDelays: Boolean,
    showMessages: Boolean,
    loadingStopIds: Set<String>,
    isLandscape: Boolean,
    onReorder: (Int, Int) -> Unit,
    onFilterOut: (String) -> Unit,
    extendedStopIds: Set<String>,
    onSearchFurtherAhead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier) {
        val itemCount = favorites.size

        val currentTargetIndex =
            draggingIndex?.let { startIdx ->
                if (isLandscape && itemSize.width > 0) {
                    (startIdx + (dragOffset.x / itemSize.width).roundToInt()).coerceIn(0, itemCount - 1)
                } else if (!isLandscape && itemSize.height > 0) {
                    (startIdx + (dragOffset.y / itemSize.height).roundToInt()).coerceIn(0, itemCount - 1)
                } else {
                    startIdx
                }
            }

        if (isLandscape) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            itemSize = IntSize(width = size.width / maxOf(1, itemCount), height = size.height)
                        },
            ) {
                favorites.forEachIndexed { index, stop ->
                    key(stop.id) {
                        val isDragging = draggingIndex == index

                        val visualOffset =
                            remember(index, draggingIndex, currentTargetIndex) {
                                if (draggingIndex == null || currentTargetIndex == null || isDragging) {
                                    Offset.Zero
                                } else {
                                    val start = draggingIndex!!
                                    if (index in (minOf(start, currentTargetIndex)..maxOf(start, currentTargetIndex))) {
                                        val direction = if (start < currentTargetIndex) -1f else 1f
                                        Offset(x = direction * itemSize.width.toFloat(), y = 0f)
                                    } else {
                                        Offset.Zero
                                    }
                                }
                            }

                        val animatedOffset by animateOffsetAsState(
                            targetValue = if (isDragging) dragOffset else visualOffset,
                            animationSpec = spring(stiffness = if (isDragging) 2500f else 400f),
                            label = "itemOffset",
                        )

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .offset { IntOffset(animatedOffset.x.roundToInt(), animatedOffset.y.roundToInt()) }
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggingIndex = index },
                                            onDragEnd = {
                                                currentTargetIndex?.let { target ->
                                                    if (target != index) onReorder(index, target)
                                                }
                                                draggingIndex = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                            },
                                        )
                                    },
                        ) {
                            StopBoardPanel(
                                stopPlace = stop,
                                departureGroups = departures.find { it.stopId == stop.id }?.groups,
                                showDelays = showDelays,
                                showMessages = showMessages,
                                isLoading = stop.id in loadingStopIds,
                                isExtended = stop.id in extendedStopIds,
                                onFilterOut = onFilterOut,
                                onSearchFurtherAhead = { onSearchFurtherAhead(stop.id) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            itemSize = IntSize(width = size.width, height = size.height / maxOf(1, itemCount))
                        },
            ) {
                favorites.forEachIndexed { index, stop ->
                    key(stop.id) {
                        val isDragging = draggingIndex == index

                        val visualOffset =
                            remember(index, draggingIndex, currentTargetIndex) {
                                if (draggingIndex == null || currentTargetIndex == null || isDragging) {
                                    Offset.Zero
                                } else {
                                    val start = draggingIndex!!
                                    if (index in (minOf(start, currentTargetIndex)..maxOf(start, currentTargetIndex))) {
                                        val direction = if (start < currentTargetIndex) -1f else 1f
                                        Offset(x = 0f, y = direction * itemSize.height.toFloat())
                                    } else {
                                        Offset.Zero
                                    }
                                }
                            }

                        val animatedOffset by animateOffsetAsState(
                            targetValue = if (isDragging) dragOffset else visualOffset,
                            animationSpec = spring(stiffness = if (isDragging) 2500f else 400f),
                            label = "itemOffset",
                        )

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .offset { IntOffset(animatedOffset.x.roundToInt(), animatedOffset.y.roundToInt()) }
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggingIndex = index },
                                            onDragEnd = {
                                                currentTargetIndex?.let { target ->
                                                    if (target != index) onReorder(index, target)
                                                }
                                                draggingIndex = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                            },
                                        )
                                    },
                        ) {
                            StopBoardPanel(
                                stopPlace = stop,
                                departureGroups = departures.find { it.stopId == stop.id }?.groups,
                                showDelays = showDelays,
                                showMessages = showMessages,
                                isLoading = stop.id in loadingStopIds,
                                isExtended = stop.id in extendedStopIds,
                                onFilterOut = onFilterOut,
                                onSearchFurtherAhead = { onSearchFurtherAhead(stop.id) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherItem(
    weather: WeatherInfo,
    modifier: Modifier = Modifier,
) {
    // MET returns times in UTC (trailing `Z`); the display formatter uses the device's local
    // zone, so the parser must read the wall-clock as UTC or the hour comes out offset.
    val locale = SimpleDateFormat("HH", LocalLocale.current.platformLocale)
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
        stringResource(R.string.weather_precipitation, weather.precipitation.toString())

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

/**
 * The header clock with the date drawn in the user's chosen [DateDisplayStyle]. Every visible style
 * gives the day of month the emphasis; weekday and month are secondary context.
 */
@Composable
private fun HeaderClock(
    uiState: StopBoardViewModel.UiState,
    onClick: () -> Unit,
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
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(R.string.change_date_style), onClick = onClick)

    when (style) {
        DateDisplayStyle.HIDDEN -> {
            Text(text = uiState.currentTime, modifier = clickModifier, style = timeStyle)
        }

        DateDisplayStyle.BESIDE -> {
            Row(
                modifier = clickModifier,
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
            Column(modifier = clickModifier) {
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
                modifier = clickModifier,
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
                modifier = clickModifier,
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

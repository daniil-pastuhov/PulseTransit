package com.example.pulsetransit.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pulsetransit.R
import com.example.pulsetransit.data.*
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.StopBoardPanel
import com.example.pulsetransit.ui.components.getTransportEmoji
import com.example.pulsetransit.ui.pixelshift.pixelShift
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Suppress("ParamsComparedByRef")
@Composable
fun MainScreen(
    viewModel: StopBoardViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.bindCameraMotion(lifecycleOwner)
        }
    }

    LaunchedEffect(uiState.isScreenDimmed) {
        // Just to ensure we're observing
    }

    LaunchedEffect(lifecycleOwner) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.bindCameraMotion(lifecycleOwner)
        } else if (viewModel.isCameraMotionEnabled()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val effectiveTheme = if (uiState.isScreenDimmed) ThemeMode.DARK else uiState.activeTheme
    val screenAlpha = if (uiState.isScreenDimmed) 0.05f else 1f

    PulseTransitTheme(themeMode = effectiveTheme) {
        val backgroundColor = if (uiState.isScreenDimmed) Color.Black else MaterialTheme.colorScheme.background
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor)
                .pixelShift(uiState.burnInLevel)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = screenAlpha)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(if (uiState.isFullScreenMode) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Digital Clock
                    Text(
                        text = uiState.currentTime,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Middle: Weather
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val maxItems = (maxWidth.value / 60).toInt().coerceIn(4, 12)
                        val totalAvailable = uiState.weather.size
                        val step = if (totalAvailable > maxItems) totalAvailable / maxItems else 1

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            uiState.weather.filterIndexed { index, _ -> index % step == 0 }
                                .take(maxItems)
                                .forEach { w ->
                                    WeatherItem(w)
                                }
                        }
                    }

                    // Right: Control Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        IconButton(onClick = { viewModel.refreshData() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Fastest route banners (one per destination station set in Settings)
                uiState.fastestTrips.forEach { trip ->
                    key(trip.destinationName) {
                        FastestTripBanner(trip = trip)
                    }
                }

                // Body Area (Equal screen division between favorite stops)
                if (uiState.favorites.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.no_stops_configured),
                                style = MaterialTheme.typography.headlineSmall
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
                        isLoading = uiState.isLoading,
                        isLandscape = isLandscape,
                        onReorder = { from, to -> viewModel.reorderFavorites(from, to) },
                        onFilterOut = { line -> viewModel.filterOutLine(line) },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(6.dp)
                    )
                }

                // Subtle Last Updated and Attribution at the bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(if (uiState.isFullScreenMode) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars)
                        .padding(top = 2.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.last_updated, uiState.lastUpdated),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.attribution_entur),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )
                        Text(
                            text = stringResource(R.string.attribution_met),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }

            if (uiState.isScreenDimmed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.wakeScreen() }
                )
            }
        }
    }
}

@Composable
fun FastestTripBanner(
    trip: TripInfo,
    modifier: Modifier = Modifier
) {
    val departureTime = try {
        trip.expectedStartTime.substring(11, 16)
    } catch (_: Exception) { "--:--" }

    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }
    val canExpand = hasOverflow || expanded

    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = canExpand) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = departureTime,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.fastest_route_to, trip.destinationName),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            val routeText = if (trip.legs.isNotEmpty()) {
                trip.legs.mapNotNull { leg ->
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
                }
            )
        }
        Text(
            text = stringResource(R.string.duration_min, trip.durationMinutes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (canExpand) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun ReorderableFavoriteList(
    favorites: List<StopPlace>,
    departures: List<StopDepartures>,
    showDelays: Boolean,
    showMessages: Boolean,
    isLoading: Boolean,
    isLandscape: Boolean,
    onReorder: (Int, Int) -> Unit,
    onFilterOut: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier) {
        val itemCount = favorites.size
        
        val currentTargetIndex = draggingIndex?.let { startIdx ->
            if (isLandscape && itemSize.width > 0) {
                (startIdx + (dragOffset.x / itemSize.width).roundToInt()).coerceIn(0, itemCount - 1)
            } else if (!isLandscape && itemSize.height > 0) {
                (startIdx + (dragOffset.y / itemSize.height).roundToInt()).coerceIn(0, itemCount - 1)
            } else startIdx
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size -> 
                        itemSize = IntSize(width = size.width / maxOf(1, itemCount), height = size.height) 
                    }
            ) {
                favorites.forEachIndexed { index, stop ->
                    key(stop.id) {
                        val isDragging = draggingIndex == index
                        
                        val visualOffset = remember(index, draggingIndex, currentTargetIndex) {
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
                            label = "itemOffset"
                        )

                        Box(
                            modifier = Modifier
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
                                        }
                                    )
                                }
                        ) {
                            StopBoardPanel(
                                stopPlace = stop,
                                departureGroups = departures.find { it.stopId == stop.id }?.groups ?: emptyList(),
                                showDelays = showDelays,
                                showMessages = showMessages,
                                isLoading = isLoading,
                                onFilterOut = onFilterOut,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size -> 
                        itemSize = IntSize(width = size.width, height = size.height / maxOf(1, itemCount)) 
                    }
            ) {
                favorites.forEachIndexed { index, stop ->
                    key(stop.id) {
                        val isDragging = draggingIndex == index
                        
                        val visualOffset = remember(index, draggingIndex, currentTargetIndex) {
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
                            label = "itemOffset"
                        )

                        Box(
                            modifier = Modifier
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
                                        }
                                    )
                                }
                        ) {
                            StopBoardPanel(
                                stopPlace = stop,
                                departureGroups = departures.find { it.stopId == stop.id }?.groups ?: emptyList(),
                                showDelays = showDelays,
                                showMessages = showMessages,
                                isLoading = isLoading,
                                onFilterOut = onFilterOut,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherItem(weather: WeatherInfo) {
    val locale = SimpleDateFormat("HH", LocalLocale.current.platformLocale)
    val hour = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = sdf.parse(weather.time.substring(0, 19))
        if (date != null) {
            locale.format(date)
        } else "??"
    } catch (_: Exception) { "??" }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$hour:00", 
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            text = "${weather.temperature.toInt()}°", 
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (weather.precipitation > 0) {
            Text(
                text = "${weather.precipitation}mm",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.primary, 
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        // Note: In a real app we'd map symbolCode to actual icons
        Text(
            text = weather.symbolCode.split("_")[0], 
            style = MaterialTheme.typography.labelSmall, 
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

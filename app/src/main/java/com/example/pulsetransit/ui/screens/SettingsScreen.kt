package com.example.pulsetransit.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.R
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.CameraSensitivity
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    var manualLat by remember { mutableStateOf("") }
    var manualLon by remember { mutableStateOf("") }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineGranted || coarseGranted) {
                viewModel.requestLocationAndNearbyStops()
            }
        }

    PulseTransitTheme(themeMode = uiState.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.navigationBars,
        ) { innerPadding ->
            val containerSizePx = LocalWindowInfo.current.containerSize
            val containerWidthDp = with(LocalDensity.current) { containerSizePx.width.toDp() }
            val isCompact = containerWidthDp < 600.dp

            // Section groups shared by the compact (phone) and wide (tablet) layouts
            val configSections: @Composable ColumnScope.() -> Unit = {
                // Section 1: Theme Settings
                SettingsCard(title = stringResource(R.string.theme_settings)) {
                    Text(
                        stringResource(R.string.day_night_mode),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val isSelected = uiState.themeMode == mode
                            val labelId =
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                    ThemeMode.AUTO -> R.string.theme_auto
                                }
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateThemeMode(mode) },
                                label = { Text(stringResource(labelId)) },
                            )
                        }
                    }

                    if (uiState.themeMode == ThemeMode.AUTO) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.auto_theme_thresholds),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(stringResource(R.string.auto_theme_dark), style = MaterialTheme.typography.labelSmall)
                        val darkThresholds = listOf(10f, 30f, 50f, 100f)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            darkThresholds.forEach { lux ->
                                FilterChip(
                                    selected = uiState.autoThemeDarkLux == lux,
                                    onClick = { viewModel.updateAutoThemeDarkLux(lux) },
                                    label = { Text(stringResource(R.string.lux_value, lux)) },
                                )
                            }
                        }

                        uiState.currentLux?.let { lux ->
                            Spacer(modifier = Modifier.height(12.dp))
                            LuxScaleIndicator(
                                currentLux = lux,
                                darkLux = uiState.autoThemeDarkLux,
                                lightLux = uiState.autoThemeLightLux,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(stringResource(R.string.auto_theme_light), style = MaterialTheme.typography.labelSmall)
                        val lightThresholds = listOf(100f, 150f, 200f, 250f)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            lightThresholds.forEach { lux ->
                                FilterChip(
                                    selected = uiState.autoThemeLightLux == lux,
                                    onClick = { viewModel.updateAutoThemeLightLux(lux) },
                                    label = { Text(stringResource(R.string.lux_value, lux)) },
                                )
                            }
                        }
                    }
                }

                // Section 2: Refresh Interval
                SettingsCard(title = stringResource(R.string.refresh_interval)) {
                    Text(
                        stringResource(R.string.auto_update_schedule),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val intervals =
                        listOf(
                            R.string.refresh_none to 0,
                            R.string.refresh_30s to 30,
                            R.string.refresh_1m to 60,
                            R.string.refresh_2m to 120,
                            R.string.refresh_5m to 300,
                        )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        intervals.forEach { (labelId, seconds) ->
                            val isSelected = uiState.refreshIntervalSeconds == seconds
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateRefreshInterval(seconds) },
                                label = { Text(stringResource(labelId)) },
                            )
                        }
                    }
                }

                // Section 3: OLED Screen Care
                SettingsCard(title = stringResource(R.string.oled_screen_care)) {
                    Text(
                        stringResource(R.string.pixel_shift_level),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BurnInProtectionLevel.entries.forEach { level ->
                            val isSelected = uiState.burnInLevel == level
                            val labelId =
                                when (level) {
                                    BurnInProtectionLevel.OFF -> R.string.burn_in_off
                                    BurnInProtectionLevel.LOW -> R.string.burn_in_low
                                    BurnInProtectionLevel.MEDIUM -> R.string.burn_in_medium
                                    BurnInProtectionLevel.HIGH -> R.string.burn_in_high
                                }
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateBurnInLevel(level) },
                                label = { Text(stringResource(labelId)) },
                            )
                        }
                    }
                }

                // Section: Power & Refresh
                SettingsCard(title = stringResource(R.string.power_refresh)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.cameraMotionWake, onCheckedChange = { viewModel.updateCameraMotionWake(it) })
                        Text(stringResource(R.string.wake_camera))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.dynamicRefresh, onCheckedChange = { viewModel.updateDynamicRefresh(it) })
                        Text(stringResource(R.string.dynamic_refresh))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.isFullScreenMode, onCheckedChange = { viewModel.updateFullScreenMode(it) })
                        Text(stringResource(R.string.full_screen_mode))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.isAlwaysOnEnabled, onCheckedChange = { viewModel.updateAlwaysOnMode(it) })
                        Text(stringResource(R.string.always_on_display))
                    }

                    if (uiState.cameraMotionWake) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.auto_dim_delay),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            stringResource(R.string.auto_dim_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val dimIntervals =
                            listOf(
                                R.string.dim_15s to 15,
                                R.string.dim_30s to 30,
                                R.string.dim_1m to 60,
                                R.string.dim_2m to 120,
                                R.string.dim_never to 0,
                            )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            dimIntervals.forEach { (labelId, seconds) ->
                                val isSelected = uiState.autoDimSeconds == seconds
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateAutoDimSeconds(seconds) },
                                    label = { Text(stringResource(labelId)) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.camera_sensitivity),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Text(
                            stringResource(R.string.camera_sensitivity_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CameraSensitivity.entries.forEach { sensitivity ->
                                val labelId =
                                    when (sensitivity) {
                                        CameraSensitivity.LOW -> R.string.sensitivity_low
                                        CameraSensitivity.MEDIUM -> R.string.sensitivity_medium
                                        CameraSensitivity.HIGH -> R.string.sensitivity_high
                                    }
                                FilterChip(
                                    selected = uiState.cameraSensitivity == sensitivity,
                                    onClick = { viewModel.updateCameraSensitivity(sensitivity) },
                                    label = { Text(stringResource(labelId)) },
                                )
                            }
                        }
                    }
                }

                // Section: Transport Info
                SettingsCard(title = stringResource(R.string.transport_info)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.showDelays, onCheckedChange = { viewModel.updateShowDelays(it) })
                        Text(stringResource(R.string.show_delays))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.showMessages, onCheckedChange = { viewModel.updateShowMessages(it) })
                        Text(stringResource(R.string.show_messages))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.showCancelledLines, onCheckedChange = { viewModel.updateShowCancelledLines(it) })
                        Text(stringResource(R.string.show_cancelled))
                    }
                }

                // Section: Filtered Lines
                SettingsCard(title = stringResource(R.string.filtered_lines)) {
                    if (uiState.filteredLines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_filtered_lines),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 16.sp,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            uiState.filteredLines.sorted().forEach { line ->
                                InputChip(
                                    selected = false,
                                    onClick = { },
                                    label = { Text(line) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeFilteredLine(line) },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.remove),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Section: Fastest Route Destinations
                SettingsCard(title = stringResource(R.string.fastest_route_destination)) {
                    val destinations = uiState.destinationStations
                    if (destinations.isNotEmpty()) {
                        destinations.forEach { destination ->
                            key(destination.id) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(destination.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        destination.locality?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.removeDestinationStation(destination.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.remove),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.no_destination_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (destinations.size >= SettingsRepository.MAX_DESTINATION_STATIONS) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            pluralStringResource(
                                R.plurals.max_destinations_reached,
                                SettingsRepository.MAX_DESTINATION_STATIONS,
                                SettingsRepository.MAX_DESTINATION_STATIONS,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.destinationQuery,
                            onValueChange = { viewModel.onDestinationQueryChanged(it) },
                            label = { Text(stringResource(R.string.search_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        if (uiState.isSearchingDestination && uiState.destinationResults.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else if (uiState.destinationResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            uiState.destinationResults
                                .filter { result -> destinations.none { it.id == result.id } }
                                .forEach { stop ->
                                    key(stop.id) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(stop.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                stop.locality?.let {
                                                    Text(
                                                        it,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(onClick = { viewModel.addDestinationStation(stop) }) {
                                                Text(stringResource(R.string.select), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }

                // Section 4: Location Settings
                SettingsCard(title = stringResource(R.string.location_config)) {
                    Text(
                        stringResource(R.string.current_coords),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val latVal = uiState.latitude?.toString() ?: stringResource(R.string.not_set)
                    val lonVal = uiState.longitude?.toString() ?: stringResource(R.string.not_set)
                    Text("${stringResource(R.string.latitude_val, latVal)}, ${stringResource(R.string.longitude_val, lonVal)}")

                    val statusText =
                        if (uiState.locationStatus.arg != null) {
                            stringResource(
                                uiState.locationStatus.resId,
                                uiState.locationStatus.arg!!,
                            )
                        } else {
                            stringResource(uiState.locationStatus.resId)
                        }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.detect_location))
                    }

                    if (uiState.showManualCoordinatesPrompt) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.enter_manual),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = manualLat,
                                onValueChange = { manualLat = it },
                                label = { Text(stringResource(R.string.latitude_label)) },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = manualLon,
                                onValueChange = { manualLon = it },
                                label = { Text(stringResource(R.string.longitude_label)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.setManualCoordinates(manualLat, manualLon)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.save_coords))
                        }
                    }
                }
            }

            // Search & Favorites Management
            val stopsSections: @Composable ColumnScope.() -> Unit = {
                // Section: Search Stops
                SettingsCard(title = stringResource(R.string.add_stop_places)) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onQueryChanged(it) },
                        label = { Text(stringResource(R.string.search_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val showNearby = uiState.query.isBlank()
                    val listData = if (showNearby) uiState.nearbyStops else uiState.searchResults
                    val isLoadingList = if (showNearby) uiState.isSearchingNearby else uiState.isSearching

                    if (!showNearby || listData.isNotEmpty()) {
                        val listTitle =
                            if (showNearby) {
                                stringResource(
                                    R.string.nearby_stops,
                                )
                            } else {
                                stringResource(R.string.search_results)
                            }
                        Text(listTitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))

                        if (isLoadingList && listData.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (listData.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.no_stops_found),
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                listData.forEach { stop ->
                                    key(stop.id) {
                                        StopSearchResultRow(
                                            stop = stop,
                                            isFav = uiState.favorites.any { it.id == stop.id },
                                            onAdd = { viewModel.addFavorite(stop) },
                                            onRemove = { viewModel.removeFavorite(stop.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Current Favorites
                SettingsCard(title = stringResource(R.string.favorite_stops, uiState.favorites.size)) {
                    if (uiState.favorites.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.no_favorites),
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            uiState.favorites.forEach { stop ->
                                key(stop.id) {
                                    FavoriteStopRow(
                                        stop = stop,
                                        stopWithQuays = uiState.favoriteStopsWithQuays[stop.id],
                                        selectedQuays = uiState.selectedQuays[stop.id] ?: emptySet(),
                                        onToggleQuay = { viewModel.toggleQuay(stop.id, it) },
                                        onRemove = { viewModel.removeFavorite(stop.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isCompact) {
                // Phone: single scrollable column
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    configSections()
                    stopsSections()
                }
            } else {
                // Tablet / wide: two columns side by side
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        configSections()
                    }
                    Column(
                        modifier =
                            Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        stopsSections()
                    }
                }
            }
        }
    }
}

/**
 * A horizontal scale showing the live ambient-light reading against the two auto-theme thresholds.
 * The filled bar tracks the current lux; the two ticks (and labels below) mark the "dark below" and
 * "light above" thresholds, so the user can see where the current reading falls relative to both.
 */
@Composable
private fun LuxScaleIndicator(
    currentLux: Float,
    darkLux: Float,
    lightLux: Float,
    modifier: Modifier = Modifier,
) {
    val scaleMax = maxOf(lightLux, currentLux, 1f) * 1.15f
    val curFrac = (currentLux / scaleMax).coerceIn(0f, 1f)
    val darkFrac = (darkLux / scaleMax).coerceIn(0f, 1f)
    val lightFrac = (lightLux / scaleMax).coerceIn(0f, 1f)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val darkMarkColor = MaterialTheme.colorScheme.onSurface
    val lightMarkColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.current_lux, currentLux),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val fullWidth = maxWidth
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor),
            )
            Box(
                modifier =
                    Modifier
                        .width(fullWidth * curFrac)
                        .height(8.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(fillColor),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = fullWidth * darkFrac - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(darkMarkColor),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = fullWidth * lightFrac - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(lightMarkColor),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val fullWidth = maxWidth
            val labelWidth = 48.dp
            Text(
                text = stringResource(R.string.lux_value, darkLux),
                style = MaterialTheme.typography.labelSmall,
                color = darkMarkColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .width(labelWidth)
                        .offset(x = (fullWidth * darkFrac - labelWidth / 2).coerceIn(0.dp, fullWidth - labelWidth)),
            )
            Text(
                text = stringResource(R.string.lux_value, lightLux),
                style = MaterialTheme.typography.labelSmall,
                color = lightMarkColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .width(labelWidth)
                        .offset(x = (fullWidth * lightFrac - labelWidth / 2).coerceIn(0.dp, fullWidth - labelWidth)),
            )
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun StopSearchResultRow(
    stop: StopPlace,
    isFav: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stop.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            stop.locality?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isFav) {
            Button(
                onClick = onRemove,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.remove), fontSize = 12.sp)
            }
        } else {
            Button(onClick = onAdd) {
                Text(stringResource(R.string.add), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FavoriteStopRow(
    stop: StopPlace,
    stopWithQuays: StopPlace?,
    selectedQuays: Set<String>,
    onToggleQuay: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stop.name, fontWeight = FontWeight.SemiBold)
                stop.locality?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        if ((stopWithQuays != null) && stopWithQuays.quays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.directions_quays), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                stopWithQuays.quays.forEach { quay ->
                    val isSelected = selectedQuays.isEmpty() || selectedQuays.contains(quay.id)
                    val platform = quay.publicCode?.takeIf { it.isNotBlank() }
                    val destination = quay.towards?.takeIf { it.isNotBlank() }

                    val labelText =
                        when {
                            platform != null && destination != null -> stringResource(R.string.platform_destination, platform, destination)
                            destination != null -> stringResource(R.string.towards_destination, destination)
                            platform != null -> stringResource(R.string.platform_label, platform)
                            !quay.description.isNullOrBlank() -> quay.description
                            !quay.name.isNullOrBlank() && quay.name != stop.name -> quay.name
                            else -> stringResource(R.string.direction_id, quay.id.takeLast(3))
                        }

                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleQuay(quay.id) },
                        label = { Text(labelText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            if (selectedQuays.isEmpty()) {
                Text(
                    stringResource(R.string.showing_all_directions),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

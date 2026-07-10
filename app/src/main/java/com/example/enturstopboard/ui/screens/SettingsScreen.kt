package com.example.enturstopboard.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.enturstopboard.R
import com.example.enturstopboard.data.BurnInProtectionLevel
import com.example.enturstopboard.data.StopPlace
import com.example.enturstopboard.data.ThemeMode
import com.example.enturstopboard.theme.EnturStopBoardTheme
import com.example.enturstopboard.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var manualLat by remember { mutableStateOf("") }
    var manualLon by remember { mutableStateOf("") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.requestLocationAndNearbyStops()
        }
    }

    EnturStopBoardTheme(themeMode = uiState.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    windowInsets = WindowInsets.statusBars
                )
            },
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.navigationBars
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Column: Configuration & Location
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Section 1: Theme Settings
                    SettingsCard(title = stringResource(R.string.theme_settings)) {
                        Text(stringResource(R.string.day_night_mode), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ThemeMode.values().forEach { mode ->
                                val isSelected = uiState.themeMode == mode
                                val labelId = when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                    ThemeMode.AUTO -> R.string.theme_auto
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateThemeMode(mode) },
                                    label = { Text(stringResource(labelId)) }
                                )
                            }
                        }
                    }

                    // Section 2: Refresh Interval
                    SettingsCard(title = stringResource(R.string.refresh_interval)) {
                        Text(stringResource(R.string.auto_update_schedule), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Spacer(modifier = Modifier.height(8.dp))
                        val intervals = listOf(
                            R.string.refresh_30s to 30,
                            R.string.refresh_1m to 60,
                            R.string.refresh_2m to 120,
                            R.string.refresh_5m to 300
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            intervals.forEach { (labelId, seconds) ->
                                val isSelected = uiState.refreshIntervalSeconds == seconds
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateRefreshInterval(seconds) },
                                    label = { Text(stringResource(labelId)) }
                                )
                            }
                        }
                    }

                    // Section 3: OLED Screen Care
                    SettingsCard(title = stringResource(R.string.oled_screen_care)) {
                        Text(stringResource(R.string.pixel_shift_level), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BurnInProtectionLevel.values().forEach { level ->
                                val isSelected = uiState.burnInLevel == level
                                val labelId = when (level) {
                                    BurnInProtectionLevel.OFF -> R.string.burn_in_off
                                    BurnInProtectionLevel.LOW -> R.string.burn_in_low
                                    BurnInProtectionLevel.MEDIUM -> R.string.burn_in_medium
                                    BurnInProtectionLevel.HIGH -> R.string.burn_in_high
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateBurnInLevel(level) },
                                    label = { Text(stringResource(labelId)) }
                                )
                            }
                        }
                    }

                    // Section: Power & Refresh
                    SettingsCard(title = stringResource(R.string.power_refresh)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = uiState.proximityWake, 
                                onCheckedChange = { viewModel.updateProximityWake(it) },
                                enabled = uiState.isProximityAvailable
                            )
                            Column {
                                Text(stringResource(R.string.wake_proximity))
                                if (!uiState.isProximityAvailable) {
                                    Text(stringResource(R.string.not_available), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = uiState.cameraMotionWake, onCheckedChange = { viewModel.updateCameraMotionWake(it) })
                            Column {
                                Text(stringResource(R.string.wake_camera))
                                if (!uiState.isProximityAvailable) {
                                    Text(stringResource(R.string.recommended_alt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = uiState.dynamicRefresh, onCheckedChange = { viewModel.updateDynamicRefresh(it) })
                            Text(stringResource(R.string.dynamic_refresh))
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
                    }

                    // Section 4: Location Settings
                    SettingsCard(title = stringResource(R.string.location_config)) {
                        Text(stringResource(R.string.current_coords), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Spacer(modifier = Modifier.height(4.dp))
                        val latVal = uiState.latitude?.toString() ?: stringResource(R.string.not_set)
                        val lonVal = uiState.longitude?.toString() ?: stringResource(R.string.not_set)
                        Text("${stringResource(R.string.latitude_val, latVal)}, ${stringResource(R.string.longitude_val, lonVal)}")
                        
                        val statusText = if (uiState.locationStatus.arg != null) {
                            stringResource(uiState.locationStatus.resId,
                                uiState.locationStatus.arg!!
                            )
                        } else {
                            stringResource(uiState.locationStatus.resId)
                        }
                        Text(statusText, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.detect_location))
                        }

                        if (uiState.showManualCoordinatesPrompt) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.enter_manual), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = manualLat,
                                    onValueChange = { manualLat = it },
                                    label = { Text(stringResource(R.string.latitude_label)) },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = manualLon,
                                    onValueChange = { manualLon = it },
                                    label = { Text(stringResource(R.string.longitude_label)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.setManualCoordinates(manualLat, manualLon)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.save_coords))
                            }
                        }
                    }
                }

                // Right Column: Search & Favorites Management
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Section: Search Stops
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.add_stop_places), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = uiState.query,
                                onValueChange = { viewModel.onQueryChanged(it) },
                                label = { Text(stringResource(R.string.search_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val showNearby = uiState.query.isBlank()
                            val listTitle = if (showNearby) stringResource(R.string.nearby_stops) else stringResource(R.string.search_results)
                            val listData = if (showNearby) uiState.nearbyStops else uiState.searchResults

                            Text(listTitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(6.dp))

                            if (uiState.isSearching || uiState.isSearchingNearby) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (listData.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_stops_found), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    listData.forEach { stop ->
                                        StopSearchResultRow(
                                            stop = stop,
                                            isFav = uiState.favorites.any { it.id == stop.id },
                                            onAdd = { viewModel.addFavorite(stop) },
                                            onRemove = { viewModel.removeFavorite(stop.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section: Current Favorites
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.favorite_stops, uiState.favorites.size), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.favorites.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_favorites), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    uiState.favorites.forEach { stop ->
                                        FavoriteStopRow(
                                            stop = stop,
                                            stopWithQuays = uiState.favoriteStopsWithQuays[stop.id],
                                            selectedQuays = uiState.selectedQuays[stop.id] ?: emptySet(),
                                            onToggleQuay = { viewModel.toggleQuay(stop.id, it) },
                                            onRemove = { viewModel.removeFavorite(stop.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StopSearchResultRow(
    stop: StopPlace,
    isFav: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        
        if (stopWithQuays != null && stopWithQuays.quays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.directions_quays), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stopWithQuays.quays.forEach { quay ->
                    val isSelected = selectedQuays.isEmpty() || selectedQuays.contains(quay.id)
                    val platform = quay.publicCode?.takeIf { it.isNotBlank() }
                    val destination = quay.towards?.takeIf { it.isNotBlank() }
                    
                    val labelText = when {
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
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            if (selectedQuays.isEmpty()) {
                Text(stringResource(R.string.showing_all_directions), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

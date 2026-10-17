package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.R
import com.example.pulsetransit.data.RouteModeGroup
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel

/** "Travel by": which kinds of transport route searches may use, one card per group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteModesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    PulseTransitTheme(themeMode = uiState.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.route_modes)) },
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
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.route_modes_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RouteTransportMode.entries.groupBy { it.group }.forEach { (group, modes) ->
                    SettingsCard(title = stringResource(group.labelId())) {
                        // The ViewModel refuses to exclude the last allowed kind, so that box stays ticked.
                        modes.forEach { mode ->
                            RouteModeRow(
                                label = stringResource(mode.labelId()),
                                allowed = mode !in uiState.excludedRouteModes,
                                onToggle = { viewModel.toggleRouteMode(mode) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteModeRow(
    label: String,
    allowed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .toggleable(value = allowed, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = allowed, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun RouteModeGroup.labelId(): Int =
    when (this) {
        RouteModeGroup.BUS -> R.string.route_group_bus
        RouteModeGroup.TRAIN -> R.string.route_group_train
        RouteModeGroup.BOAT -> R.string.route_group_boat
        RouteModeGroup.OTHER -> R.string.route_group_other
    }

// One branch per kind, which detekt counts as complexity.
@Suppress("CyclomaticComplexMethod")
private fun RouteTransportMode.labelId(): Int =
    when (this) {
        RouteTransportMode.LOCAL_BUS -> R.string.route_mode_local_bus
        RouteTransportMode.EXPRESS_BUS -> R.string.route_mode_express_bus
        RouteTransportMode.AIRPORT_BUS -> R.string.route_mode_airport_bus
        RouteTransportMode.RAIL_REPLACEMENT_BUS -> R.string.route_mode_rail_replacement_bus
        RouteTransportMode.LOCAL_TRAIN -> R.string.route_mode_local_train
        RouteTransportMode.REGIONAL_TRAIN -> R.string.route_mode_regional_train
        RouteTransportMode.LONG_DISTANCE_TRAIN -> R.string.route_mode_long_distance_train
        RouteTransportMode.EXPRESS_TRAIN -> R.string.route_mode_express_train
        RouteTransportMode.CAR_FERRY -> R.string.route_mode_car_ferry
        RouteTransportMode.PASSENGER_FERRY -> R.string.route_mode_passenger_ferry
        RouteTransportMode.EXPRESS_BOAT -> R.string.route_mode_express_boat
        RouteTransportMode.METRO -> R.string.route_mode_metro
        RouteTransportMode.TRAM -> R.string.route_mode_tram
        RouteTransportMode.PLANE -> R.string.route_mode_plane
        RouteTransportMode.ON_DEMAND -> R.string.route_mode_on_demand
        RouteTransportMode.CABLE_CAR -> R.string.route_mode_cable_car
    }

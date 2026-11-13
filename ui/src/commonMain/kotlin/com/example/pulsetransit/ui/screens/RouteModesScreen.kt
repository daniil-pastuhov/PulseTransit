package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.RouteModeGroup
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
                    title = { Text(stringResource(Res.string.route_modes)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = PulseIcons.ArrowBack, contentDescription = stringResource(Res.string.back))
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
                        .padding(FjordDimens.space4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = SINGLE_COLUMN_MAX_WIDTH).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FjordDimens.space4),
                ) {
                    Text(
                        stringResource(Res.string.route_modes_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RouteTransportMode.entries.groupBy { it.group }.forEach { (group, modes) ->
                        val allowed = modes.count { it !in uiState.excludedRouteModes }
                        // Excluding this group would leave nothing allowed, which the ViewModel refuses: locked instead.
                        val onlyGroupLeft = allowed == modes.size && uiState.excludedRouteModes.size == RouteTransportMode.entries.size - modes.size
                        val groupName = stringResource(group.labelId())
                        Surface(
                            shape = RoundedCornerShape(FjordDimens.radiusLg),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(vertical = FjordDimens.space1)) {
                                // The group's box: ticked, mixed with "3 of 4", or clear; it sets them all.
                                val toggleLabel = stringResource(Res.string.travel_by_group_toggle, groupName)
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = FjordDimens.touch)
                                            .triStateToggleable(
                                                state =
                                                    when (allowed) {
                                                        modes.size -> ToggleableState.On
                                                        0 -> ToggleableState.Off
                                                        else -> ToggleableState.Indeterminate
                                                    },
                                                enabled = !onlyGroupLeft,
                                                role = Role.Checkbox,
                                                onClick = { viewModel.toggleRouteModeGroup(group) },
                                            ).semantics { contentDescription = toggleLabel }
                                            .padding(start = FjordDimens.space4, end = FjordDimens.space1),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
                                ) {
                                    Icon(group.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        groupName,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f).semantics { heading() },
                                    )
                                    Text(
                                        if (allowed == modes.size) {
                                            stringResource(Res.string.route_modes_all)
                                        } else {
                                            stringResource(Res.string.route_modes_count, allowed, modes.size)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TriStateCheckbox(
                                        state =
                                            when (allowed) {
                                                modes.size -> ToggleableState.On
                                                0 -> ToggleableState.Off
                                                else -> ToggleableState.Indeterminate
                                            },
                                        onClick = null,
                                        enabled = !onlyGroupLeft,
                                        modifier = Modifier.padding(FjordDimens.space2),
                                    )
                                }
                                SettingsDivider()
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
                .heightIn(min = FjordDimens.touch)
                .toggleable(value = allowed, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(start = 52.dp, end = FjordDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Checkbox(checked = allowed, onCheckedChange = null, modifier = Modifier.padding(FjordDimens.space2))
    }
}

private fun RouteModeGroup.icon(): ImageVector =
    when (this) {
        RouteModeGroup.BUS -> PulseIcons.DirectionsBus
        RouteModeGroup.TRAIN -> PulseIcons.Train
        RouteModeGroup.BOAT -> PulseIcons.DirectionsBoat
        RouteModeGroup.OTHER -> PulseIcons.Tram
    }

private fun RouteModeGroup.labelId(): StringResource =
    when (this) {
        RouteModeGroup.BUS -> Res.string.route_group_bus
        RouteModeGroup.TRAIN -> Res.string.route_group_train
        RouteModeGroup.BOAT -> Res.string.route_group_boat
        RouteModeGroup.OTHER -> Res.string.route_group_other
    }

// One branch per kind, which detekt counts as complexity.
@Suppress("CyclomaticComplexMethod")
private fun RouteTransportMode.labelId(): StringResource =
    when (this) {
        RouteTransportMode.LOCAL_BUS -> Res.string.route_mode_local_bus
        RouteTransportMode.EXPRESS_BUS -> Res.string.route_mode_express_bus
        RouteTransportMode.AIRPORT_BUS -> Res.string.route_mode_airport_bus
        RouteTransportMode.RAIL_REPLACEMENT_BUS -> Res.string.route_mode_rail_replacement_bus
        RouteTransportMode.LOCAL_TRAIN -> Res.string.route_mode_local_train
        RouteTransportMode.REGIONAL_TRAIN -> Res.string.route_mode_regional_train
        RouteTransportMode.LONG_DISTANCE_TRAIN -> Res.string.route_mode_long_distance_train
        RouteTransportMode.EXPRESS_TRAIN -> Res.string.route_mode_express_train
        RouteTransportMode.CAR_FERRY -> Res.string.route_mode_car_ferry
        RouteTransportMode.PASSENGER_FERRY -> Res.string.route_mode_passenger_ferry
        RouteTransportMode.EXPRESS_BOAT -> Res.string.route_mode_express_boat
        RouteTransportMode.METRO -> Res.string.route_mode_metro
        RouteTransportMode.TRAM -> Res.string.route_mode_tram
        RouteTransportMode.PLANE -> Res.string.route_mode_plane
        RouteTransportMode.ON_DEMAND -> Res.string.route_mode_on_demand
        RouteTransportMode.CABLE_CAR -> Res.string.route_mode_cable_car
    }

package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.RoutePreference
import com.example.pulsetransit.data.RouteTransportMode
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.journey.JourneyAlertKind
import com.example.pulsetransit.data.quaySections
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.DestinationScheduleEditor
import com.example.pulsetransit.ui.components.PlaceKindIcon
import com.example.pulsetransit.ui.components.ReorderableColumn
import com.example.pulsetransit.ui.components.scheduleSummary
import com.example.pulsetransit.ui.platform.appVersion
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel.SettingsUndo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings: what the board shows and how the device behaves, every change applied at once. Only
 * what applies to the mode is shown. Up to 700 dp a full-width column; then a centred one; from
 * 1000 dp in board mode two columns, what the board shows beside how the device behaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenRouteModes: () -> Unit,
    onOpenJourneyAlerts: () -> Unit,
    onOpenDataSources: () -> Unit,
    onOpenAddStop: () -> Unit,
    onOpenDirections: (stopId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Only what is undone here: adding is undone on Add stop, where it happened.
    SettingsUndoSnackbar(uiState.pendingUndo as? SettingsUndo.StopRemoved, snackbarHostState, viewModel::undo, viewModel::dismissUndo)

    PulseTransitTheme(themeMode = uiState.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = PulseIcons.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier.fillMaxSize(),
            // Edge-to-edge means adjustResize no longer shrinks the window for the keyboard, so the
            // content has to make room for it itself or it covers the text fields.
            contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
        ) { innerPadding ->
            val isBoard = uiState.appMode == AppMode.BOARD
            val sections =
                SettingsSections(
                    viewModel = viewModel,
                    state = uiState,
                    navigation = SettingsNavigation(onOpenRouteModes, onOpenJourneyAlerts, onOpenDataSources, onOpenAddStop, onOpenDirections),
                )
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                val twoColumns = isBoard && maxWidth >= TWO_COLUMNS_FROM
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space4),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (twoColumns) {
                        Row(
                            modifier = Modifier.widthIn(max = TWO_COLUMN_MAX_WIDTH).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FjordDimens.space6),
                        ) {
                            SectionColumn(Modifier.weight(1f)) { sections.whatTheBoardShows(this) }
                            SectionColumn(Modifier.weight(1f)) { sections.howTheDeviceBehaves(this) }
                        }
                    } else {
                        SectionColumn(Modifier.widthIn(max = SINGLE_COLUMN_MAX_WIDTH).fillMaxWidth()) {
                            if (isBoard) {
                                sections.whatTheBoardShows(this)
                                sections.howTheDeviceBehaves(this)
                            } else {
                                sections.onTheSpot(this)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionColumn(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FjordDimens.space6), content = content)

/** Where Settings' rows lead: the screens of their own. */
private class SettingsNavigation(
    val onOpenRouteModes: () -> Unit,
    val onOpenJourneyAlerts: () -> Unit,
    val onOpenDataSources: () -> Unit,
    val onOpenAddStop: () -> Unit,
    val onOpenDirections: (String) -> Unit,
)

/** Each section of Settings, and the order each mode shows them in. */
private class SettingsSections(
    val viewModel: SettingsViewModel,
    val state: SettingsViewModel.UiState,
    navigation: SettingsNavigation,
) {
    private val onOpenRouteModes = navigation.onOpenRouteModes
    private val onOpenJourneyAlerts = navigation.onOpenJourneyAlerts
    private val onOpenDataSources = navigation.onOpenDataSources
    private val onOpenAddStop = navigation.onOpenAddStop
    private val onOpenDirections = navigation.onOpenDirections

    /** Board mode, left: stops, destinations, routes, lines and what departures show. */
    val whatTheBoardShows: @Composable ColumnScope.() -> Unit = {
        ModeSection(state, viewModel)
        StopsSection(state, viewModel, onOpenAddStop, onOpenDirections)
        DestinationsSection(state, viewModel, isBoard = true)
        RouteSearchSection(state, viewModel, onOpenRouteModes, showsAlternatives = false)
        if (state.journeyAlertsSupported) JourneyAlertsSection(state, onOpenJourneyAlerts)
        FilteredLinesSection(state, viewModel)
        TransportSection(state, viewModel)
    }

    /** Board mode, right: theme, power, waking and dimming, the screen's care, and location. */
    val howTheDeviceBehaves: @Composable ColumnScope.() -> Unit = {
        ThemeSection(state, viewModel)
        PowerSection(state, viewModel, isBoard = true)
        WakeAndDimSection(state, viewModel)
        OledSection(state, viewModel)
        LocationSection(state, viewModel)
        AboutSection(onOpenDataSources)
    }

    /** On the spot: only what applies when following the device. */
    val onTheSpot: @Composable ColumnScope.() -> Unit = {
        ModeSection(state, viewModel)
        DestinationsSection(state, viewModel, isBoard = false)
        RouteSearchSection(state, viewModel, onOpenRouteModes, showsAlternatives = true)
        if (state.journeyAlertsSupported) JourneyAlertsSection(state, onOpenJourneyAlerts)
        ThemeSection(state, viewModel)
        TransportSection(state, viewModel)
        FilteredLinesSection(state, viewModel)
        PowerSection(state, viewModel, isBoard = false)
        AboutSection(onOpenDataSources)
    }
}

/**
 * The mode, as a two-way choice. Switching asks first (T6): it changes what the whole app does,
 * though nothing is lost either way.
 */
@Composable
private fun ModeSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
) {
    var confirming by rememberSaveable { mutableStateOf<AppMode?>(null) }
    SettingsSection(title = stringResource(Res.string.settings_mode)) {
        SettingSegmented(
            title = null,
            options = listOf(AppMode.ON_SPOT to stringResource(Res.string.mode_on_spot), AppMode.BOARD to stringResource(Res.string.mode_board)),
            selected = state.appMode,
            onSelect = { if (it != state.appMode) confirming = it },
        )
        Text(
            stringResource(if (state.appMode == AppMode.BOARD) Res.string.mode_board_description else Res.string.mode_on_spot_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = FjordDimens.space4, end = FjordDimens.space4, bottom = FjordDimens.space3),
        )
    }
    confirming?.let { target ->
        val toBoard = target == AppMode.BOARD
        val stops = state.favorites.size
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (toBoard) Res.string.set_switch_to_board_title else Res.string.set_switch_to_spot_title)) },
            text = {
                Text(
                    when {
                        !toBoard -> pluralStringResource(Res.plurals.set_switch_to_spot_body, stops, stops)
                        stops == 0 -> stringResource(Res.string.set_switch_to_board_empty)
                        else -> pluralStringResource(Res.plurals.set_switch_to_board_body, stops, stops)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    viewModel.updateAppMode(target)
                }) {
                    Text(stringResource(if (toBoard) Res.string.set_switch_to_board_confirm else Res.string.set_switch_to_spot_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text(stringResource(if (toBoard) Res.string.set_switch_to_board_keep else Res.string.set_switch_to_spot_keep))
                }
            },
        )
    }
}

/**
 * The board's stops, "3 of 6": tap one for its directions, remove one at once (with Undo). In
 * Reorder, drag handles and Move up / Move down set the order the board shows them in.
 */
@Composable
private fun StopsSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
    onOpenAddStop: () -> Unit,
    onOpenDirections: (String) -> Unit,
) {
    var reordering by rememberSaveable { mutableStateOf(false) }
    val favorites = state.favorites
    if (favorites.size < 2) reordering = false
    val max = SettingsRepository.MAX_FAVORITE_STOPS
    SettingsSection(
        title = stringResource(Res.string.set_stops_title, favorites.size, max),
        hint =
            when {
                favorites.isEmpty() -> stringResource(Res.string.set_no_stops)
                reordering -> stringResource(Res.string.set_reorder_hint)
                else -> stringResource(Res.string.set_stops_hint)
            },
        action =
            if (favorites.size >= 2) {
                {
                    TextButton(onClick = { reordering = !reordering }) {
                        Text(stringResource(if (reordering) Res.string.set_done else Res.string.reorder))
                    }
                }
            } else {
                null
            },
    ) {
        if (reordering) {
            ReorderableColumn(items = favorites, key = { it.id }, onMove = viewModel::moveFavorite) { stop, dragHandle, isDragging ->
                val index = favorites.indexOf(stop)
                Surface(
                    color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = FjordDimens.touch).padding(end = FjordDimens.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = dragHandle.size(FjordDimens.touch), contentAlignment = Alignment.Center) {
                            Icon(PulseIcons.DragIndicator, contentDescription = stringResource(Res.string.drag_to_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StopText(stop, state, Modifier.weight(1f))
                        IconButton(onClick = { viewModel.moveFavorite(index, index - 1) }, enabled = index > 0) {
                            Icon(PulseIcons.ArrowUpward, contentDescription = stringResource(Res.string.move_up))
                        }
                        IconButton(onClick = { viewModel.moveFavorite(index, index + 1) }, enabled = index < favorites.lastIndex) {
                            Icon(PulseIcons.ArrowDownward, contentDescription = stringResource(Res.string.move_down))
                        }
                    }
                }
            }
        } else {
            favorites.forEach { stop ->
                key(stop.id) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = FjordDimens.touch)
                                .clickable(role = Role.Button) { onOpenDirections(stop.id) }
                                .padding(start = FjordDimens.space4, end = FjordDimens.space1, top = FjordDimens.space1, bottom = FjordDimens.space1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StopText(stop, state, Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeFavorite(stop.id) }) {
                            Icon(PulseIcons.Delete, contentDescription = stringResource(Res.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SettingsDivider()
                }
            }
            val full = favorites.size >= max
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = FjordDimens.touch)
                        .clickable(enabled = !full, role = Role.Button, onClick = onOpenAddStop)
                        .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
            ) {
                // Stays visible when full, with the reason, rather than disappearing.
                val tint = if (full) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                Icon(PulseIcons.Add, contentDescription = null, tint = tint)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.set_add_stop), style = MaterialTheme.typography.bodyLarge, color = tint)
                    if (full) {
                        Text(
                            stringResource(Res.string.set_stops_max, max),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!full) Icon(PulseIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A stop's name and which of its directions the board shows. */
@Composable
private fun StopText(
    stop: StopPlace,
    state: SettingsViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = FjordDimens.space2)) {
        Text(stop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            directionsSummary(stop.id, state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "All directions", or the chosen platforms: "Platform 2, Platform 4". */
@Composable
private fun directionsSummary(
    stopId: String,
    state: SettingsViewModel.UiState,
): String {
    val selected = state.selectedQuays[stopId].orEmpty()
    if (selected.isEmpty()) return stringResource(Res.string.set_all_directions)
    val stop = state.favoriteStopsWithQuays[stopId]
    val labels =
        stop
            ?.let { quaySections(it) }
            ?.flatMap { section -> section.quays.filter { it.id in selected }.map { quayLabel(it, setOfNotNull(stop.name, section.title)) } }
            .orEmpty()
    // Before the platforms load, the count says as much.
    return labels.joinToString(", ").ifEmpty { stringResource(Res.string.directions_showing_some, selected.size, stop?.quays?.size ?: selected.size) }
}

/**
 * The saved destinations: tap one for when it shows, drag to reorder, remove; then the field to
 * add another, up to the limit.
 */
@Composable
private fun DestinationsSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
    isBoard: Boolean,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val destinations = state.destinationStations
    var editingSchedule by remember { mutableStateOf<StopPlace?>(null) }
    editingSchedule?.let { destination ->
        DestinationScheduleEditor(
            destination = destination,
            schedule = state.destinationSchedules[destination.id],
            onSave = { schedule ->
                viewModel.setDestinationSchedule(destination.id, schedule)
                editingSchedule = null
            },
            onDismiss = { editingSchedule = null },
        )
    }
    val max = SettingsRepository.MAX_DESTINATION_STATIONS
    SettingsSection(
        title = stringResource(if (isBoard) Res.string.set_destinations_board else Res.string.set_destinations_spot),
        hint = stringResource(if (isBoard) Res.string.set_destinations_board_hint else Res.string.set_destinations_spot_hint),
    ) {
        ReorderableColumn(items = destinations, key = { it.id }, onMove = viewModel::moveDestinationStation) { destination, dragHandle, isDragging ->
            Surface(
                color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                shadowElevation = if (isDragging) 4.dp else 0.dp,
            ) {
                val schedule = state.destinationSchedules[destination.id]
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = FjordDimens.touch)
                            .clickable(onClickLabel = stringResource(Res.string.schedule_edit, destination.name)) { editingSchedule = destination }
                            .padding(end = FjordDimens.space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (destinations.size > 1) {
                        Box(modifier = dragHandle.size(FjordDimens.touch), contentAlignment = Alignment.Center) {
                            Icon(PulseIcons.DragIndicator, contentDescription = stringResource(Res.string.drag_to_reorder), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Spacer(Modifier.width(FjordDimens.space4))
                    }
                    PlaceKindIcon(destination.kind)
                    Spacer(Modifier.width(FjordDimens.space3))
                    Column(modifier = Modifier.weight(1f).padding(vertical = FjordDimens.space2)) {
                        Text(destination.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
                            val color = if (schedule != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(PulseIcons.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
                            Text(scheduleSummary(schedule), style = MaterialTheme.typography.bodyMedium, color = color)
                        }
                    }
                    IconButton(onClick = { viewModel.removeDestinationStation(destination.id) }) {
                        Icon(PulseIcons.Delete, contentDescription = stringResource(Res.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (destinations.isNotEmpty()) SettingsDivider()
        Column(modifier = Modifier.padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3)) {
            if (destinations.size < max) {
                OutlinedTextField(
                    value = state.destinationQuery,
                    onValueChange = viewModel::onDestinationQueryChanged,
                    label = { Text(stringResource(Res.string.set_add_destination)) },
                    placeholder = { Text(stringResource(Res.string.search_destination_hint)) },
                    leadingIcon = { Icon(PulseIcons.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                )
                if (state.isSearchingDestination && state.destinationResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(FjordDimens.space3), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                state.destinationResults
                    .filter { result -> destinations.none { it.id == result.id } }
                    .forEach { place ->
                        key(place.id) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = FjordDimens.touch)
                                        .clickable(onClickLabel = stringResource(Res.string.add)) {
                                            keyboardController?.hide()
                                            viewModel.addDestinationStation(place)
                                        }.padding(vertical = FjordDimens.space2),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
                            ) {
                                PlaceKindIcon(place.kind)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(place.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    place.locality?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Icon(PulseIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                Text(
                    stringResource(Res.string.set_destinations_limit, max),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = FjordDimens.space2),
                )
            } else {
                Text(
                    pluralStringResource(Res.plurals.max_destinations_reached, max, max),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How routes are searched: preference, transport, walking speed and, on the spot, how many. */
@Composable
private fun RouteSearchSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
    onOpenRouteModes: () -> Unit,
    showsAlternatives: Boolean,
) {
    SettingsSection(title = stringResource(Res.string.route_settings_title)) {
        SettingSegmented(
            title = stringResource(Res.string.route_preference),
            options = RoutePreference.entries.map { it to stringResource(it.labelId()) },
            selected = state.routePreference,
            onSelect = viewModel::updateRoutePreference,
        )
        SettingsDivider()
        val total = RouteTransportMode.entries.size
        SettingLink(
            title = stringResource(Res.string.route_modes),
            hint = stringResource(Res.string.set_travel_by_hint),
            value =
                if (state.excludedRouteModes.isEmpty()) {
                    stringResource(Res.string.route_modes_all)
                } else {
                    stringResource(Res.string.route_modes_count, total - state.excludedRouteModes.size, total)
                },
            onClick = onOpenRouteModes,
        )
        SettingsDivider()
        SettingSegmented(
            title = stringResource(Res.string.walking_speed),
            options = WalkingSpeed.entries.map { it to stringResource(it.labelId()) },
            selected = state.walkingSpeed,
            onSelect = viewModel::updateWalkingSpeed,
        )
        if (showsAlternatives) {
            SettingsDivider()
            SettingSegmented(
                title = stringResource(Res.string.trip_options_title),
                hint = stringResource(Res.string.trip_options_hint),
                options =
                    (SettingsRepository.TRIP_OPTIONS_AUTO..SettingsRepository.MAX_TRIP_OPTIONS).map { count ->
                        count to if (count == SettingsRepository.TRIP_OPTIONS_AUTO) stringResource(Res.string.trip_options_auto) else count.toString()
                    },
                selected = state.tripOptionsCount,
                onSelect = viewModel::updateTripOptionsCount,
            )
        }
    }
}

/** How "Notify me about this journey" follows a trip; the choices are on a screen of their own. */
@Composable
private fun JourneyAlertsSection(
    state: SettingsViewModel.UiState,
    onOpenJourneyAlerts: () -> Unit,
) {
    SettingsSection(title = stringResource(Res.string.journey_rationale_title), hint = stringResource(Res.string.set_journey_hint)) {
        val total = JourneyAlertKind.entries.size
        SettingLink(
            icon = PulseIcons.Notifications,
            title = stringResource(Res.string.journey_rationale_title),
            hint = stringResource(state.journeyMode?.let(::journeyModeLabel) ?: Res.string.not_set),
            value =
                if (state.disabledJourneyAlerts.isEmpty()) {
                    stringResource(Res.string.journey_alerts_all)
                } else {
                    stringResource(Res.string.journey_alerts_count, total - state.disabledJourneyAlerts.size, total)
                },
            onClick = onOpenJourneyAlerts,
        )
    }
}

/** Lines hidden from the board, each with a way to show it again. */
@Composable
private fun FilteredLinesSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
) {
    SettingsSection(title = stringResource(Res.string.set_filtered_title), hint = stringResource(Res.string.filtered_lines_hint)) {
        Column(modifier = Modifier.padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3)) {
            if (state.filteredLines.isEmpty()) {
                Text(stringResource(Res.string.set_filtered_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2), verticalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                    state.filteredLines.sorted().forEach { line ->
                        val description = stringResource(Res.string.set_unfilter_line, line)
                        Surface(
                            shape = RoundedCornerShape(FjordDimens.radiusMd),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            onClick = { viewModel.removeFilteredLine(line) },
                            modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = description },
                        ) {
                            Row(
                                modifier = Modifier.padding(start = FjordDimens.space3, end = FjordDimens.space2),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1),
                            ) {
                                Text(line, style = MaterialTheme.typography.labelLarge)
                                Icon(PulseIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Text(
                    stringResource(Res.string.set_filtered_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = FjordDimens.space3),
                )
            }
        }
    }
}

/** What departures show besides their times. */
@Composable
private fun TransportSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
) {
    SettingsSection(title = stringResource(Res.string.set_transport_title)) {
        SettingSwitch(
            title = stringResource(Res.string.show_delays),
            hint = stringResource(Res.string.set_delays_hint),
            checked = state.showDelays,
            onCheckedChange = viewModel::updateShowDelays,
        )
        SettingsDivider()
        SettingSwitch(
            title = stringResource(Res.string.show_messages),
            hint = stringResource(Res.string.set_messages_hint),
            checked = state.showMessages,
            onCheckedChange = viewModel::updateShowMessages,
        )
        SettingsDivider()
        SettingSwitch(stringResource(Res.string.show_cancelled), state.showCancelledLines, viewModel::updateShowCancelledLines)
        SettingsDivider()
        SettingSwitch(stringResource(Res.string.show_weather), state.showWeather, viewModel::updateShowWeather)
    }
}

/** Where the data comes from, and the app's version. */
@Composable
private fun AboutSection(onOpenDataSources: () -> Unit) {
    SettingsSection(title = stringResource(Res.string.set_about_title)) {
        SettingLink(
            icon = PulseIcons.Apartment,
            title = stringResource(Res.string.data_sources_row),
            hint = stringResource(Res.string.data_sources_row_hint),
            onClick = onOpenDataSources,
        )
        SettingsDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
        ) {
            Icon(PulseIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.set_version), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.set_version_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(appVersion(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Shows the Settings [SettingsUndo] as a snackbar (T3) with Undo, and clears it when the snackbar
 * goes, by Undo, a swipe or its timeout, or the screen is left.
 */
@Composable
internal fun SettingsUndoSnackbar(
    pending: SettingsUndo?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Leaving the screen ends the offer, so it doesn't come back on the next visit when the
    // ViewModel outlives the screen (the desktop app keeps one for good).
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(Unit) { onDispose { currentOnDismiss() } }
    LaunchedEffect(pending?.serial) {
        val undo = pending ?: return@LaunchedEffect
        val message =
            when (undo) {
                is SettingsUndo.StopRemoved -> getString(Res.string.set_stop_removed, undo.stop.name)
                is SettingsUndo.StopAdded -> getString(Res.string.set_stop_added, undo.stop.name)
            }
        val result = hostState.showSnackbar(message = message, actionLabel = getString(Res.string.undo), duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onUndo() else onDismiss()
    }
}

private fun WalkingSpeed.labelId(): StringResource =
    when (this) {
        WalkingSpeed.SLOW -> Res.string.walking_speed_slow
        WalkingSpeed.NORMAL -> Res.string.walking_speed_normal
        WalkingSpeed.FAST -> Res.string.walking_speed_fast
    }

private fun RoutePreference.labelId(): StringResource =
    when (this) {
        RoutePreference.FASTEST -> Res.string.route_pref_fastest
        RoutePreference.FEWER_TRANSFERS -> Res.string.route_pref_fewer_transfers
        RoutePreference.LESS_WALKING -> Res.string.route_pref_less_walking
    }

/** One column of settings, centred, up to this width. */
internal val SINGLE_COLUMN_MAX_WIDTH = 640.dp

/** From this width the board's settings split in two columns. */
private val TWO_COLUMNS_FROM = 1000.dp
private val TWO_COLUMN_MAX_WIDTH = 1280.dp

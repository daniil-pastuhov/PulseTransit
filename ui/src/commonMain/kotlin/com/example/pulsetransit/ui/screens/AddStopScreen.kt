package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.Callout
import com.example.pulsetransit.ui.components.CalloutTone
import com.example.pulsetransit.ui.components.PlaceKindIcon
import com.example.pulsetransit.ui.components.PlaceholderBlock
import com.example.pulsetransit.ui.components.ScreenMessage
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Adding a stop to the board: the stops near the board's location, nearest first, or a search
 * anywhere in Norway. Stops already on the board say Added. Adding acts at once, with Undo; once
 * the board is full (T4) the Add buttons are disabled and a callout says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStopScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Only what is undone here: a removal is undone in Settings, where it happened.
    SettingsUndoSnackbar(state.pendingUndo as? SettingsViewModel.SettingsUndo.StopAdded, snackbarHostState, viewModel::undo, viewModel::dismissUndo)
    val max = SettingsRepository.MAX_FAVORITE_STOPS
    val full = state.favorites.size >= max
    PulseTransitTheme(themeMode = state.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.add_stop_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(PulseIcons.ArrowBack, contentDescription = stringResource(Res.string.back)) }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
            modifier = modifier.fillMaxSize(),
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
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FjordDimens.space3),
                ) {
                    if (full) {
                        Callout(
                            title = stringResource(Res.string.add_stop_max_title, max),
                            body = stringResource(Res.string.add_stop_max_body),
                            tone = CalloutTone.INFO,
                            icon = PulseIcons.Info,
                        )
                    }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        label = { Text(stringResource(Res.string.add_stop_search)) },
                        leadingIcon = { Icon(PulseIcons.Search, contentDescription = null) },
                        trailingIcon =
                            if (state.query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                        Icon(PulseIcons.Close, contentDescription = stringResource(Res.string.add_stop_clear))
                                    }
                                }
                            } else {
                                null
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val added = state.favorites.map { it.id }.toSet()
                    if (state.query.isBlank()) {
                        NearbyList(state, added, full, viewModel)
                    } else {
                        ResultsList(state, added, full, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyList(
    state: SettingsViewModel.UiState,
    added: Set<String>,
    full: Boolean,
    viewModel: SettingsViewModel,
) {
    when {
        state.latitude == null || state.longitude == null ->
            Text(stringResource(Res.string.add_stop_no_location), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.nearbyStops.isEmpty() && state.isSearchingNearby -> StopListSkeleton(stringResource(Res.string.add_stop_nearby))
        state.nearbyStops.isEmpty() ->
            ScreenMessage(
                title = stringResource(Res.string.add_stop_none_nearby),
                body = stringResource(Res.string.add_stop_none_nearby_body),
                icon = PulseIcons.NearMeDisabled,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        else ->
            StopList(stringResource(Res.string.add_stop_nearby)) {
                state.nearbyStops.forEach { nearby ->
                    key(nearby.stop.id) {
                        AddStopRow(nearby.stop, nearby.distanceMeters, nearby.stop.id in added, full) { viewModel.addFavorite(nearby.stop) }
                    }
                }
            }
    }
}

@Composable
private fun ResultsList(
    state: SettingsViewModel.UiState,
    added: Set<String>,
    full: Boolean,
    viewModel: SettingsViewModel,
) {
    when {
        state.searchResults.isEmpty() && state.isSearching -> StopListSkeleton(stringResource(Res.string.add_stop_results))
        // An empty result, not an error: it says what to try next.
        state.searchResults.isEmpty() ->
            ScreenMessage(
                title = stringResource(Res.string.no_stops_found),
                body = stringResource(Res.string.add_stop_no_results_body),
                icon = PulseIcons.SearchOff,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        else ->
            StopList(stringResource(Res.string.add_stop_results)) {
                state.searchResults.forEach { stop ->
                    key(stop.id) { AddStopRow(stop, null, stop.id in added, full) { viewModel.addFavorite(stop) } }
                }
            }
    }
}

@Composable
private fun StopList(
    title: String,
    rows: @Composable () -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = FjordDimens.space2).semantics { heading() },
        )
        rows()
    }
}

/** Rows shaped like the loaded ones, while the list loads; the field stays usable. */
@Composable
private fun StopListSkeleton(title: String) {
    val alpha = rememberPlaceholderPulse()
    StopList(title) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = FjordDimens.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
            ) {
                PlaceholderBlock(alpha, Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceholderBlock(alpha, Modifier.width(160.dp).height(14.dp))
                    PlaceholderBlock(alpha, Modifier.width(90.dp).height(12.dp))
                }
                PlaceholderBlock(alpha, Modifier.width(64.dp).height(32.dp))
            }
        }
    }
}

/** A stop to add: its name, "Oslo · 60 m", and Add, or Added when it is on the board already. */
@Composable
private fun AddStopRow(
    stop: StopPlace,
    distanceMeters: Int?,
    isAdded: Boolean,
    full: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = FjordDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
    ) {
        PlaceKindIcon(stop.kind)
        Column(modifier = Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(stop.locality, distanceMeters?.let { distanceText(it) }).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isAdded) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
                Icon(PulseIcons.CheckCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.add_stop_added), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val description = stringResource(Res.string.add_stop_add_named, stop.name)
            FilledTonalButton(onClick = onAdd, enabled = !full, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(Res.string.add))
            }
        }
    }
}

private const val SKELETON_ROWS = 4

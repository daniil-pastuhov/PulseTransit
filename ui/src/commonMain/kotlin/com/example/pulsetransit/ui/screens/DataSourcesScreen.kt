package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.DataSource
import com.example.pulsetransit.data.DataSourceMode
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.DataSourcesViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings' "Data sources": every transport company whose timetables reach Entur, searchable, so
 * people can check whether one they travel with is covered. A row opens the company's website.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(
    viewModel: DataSourcesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    PulseTransitTheme(themeMode = uiState.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.data_sources_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.navigationBars,
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = SINGLE_COLUMN_MAX_WIDTH).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item(key = "intro") {
                        Text(
                            stringResource(Res.string.data_sources_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    item(key = "search") {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(Res.string.data_sources_search)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    when {
                        uiState.isLoading ->
                            item(key = "loading") {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        uiState.failed ->
                            item(key = "failed") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(stringResource(Res.string.data_sources_failed), style = MaterialTheme.typography.bodyLarge)
                                    OutlinedButton(onClick = viewModel::retry) { Text(stringResource(Res.string.retry)) }
                                }
                            }
                        else -> {
                            if (uiState.query.isBlank()) {
                                item(key = "count") {
                                    Text(
                                        stringResource(Res.string.data_sources_count, uiState.all.size),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                            if (uiState.noMatches) {
                                item(key = "noMatch") {
                                    Text(
                                        stringResource(Res.string.data_sources_no_match, uiState.query.trim()),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(vertical = 16.dp),
                                    )
                                }
                            }
                            items(uiState.shown, key = { it.name }) { source ->
                                DataSourceRow(
                                    source = source,
                                    onOpen = source.url?.let { url -> { runCatching { uriHandler.openUri(url) } } },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    item(key = "footer") {
                        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            Text(
                                stringResource(Res.string.data_sources_check),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(Res.string.data_sources_weather),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSourceRow(
    source: DataSource,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val modes = source.modes.map { stringResource(it.labelId()) }.joinToString(", ")
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
                .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(source.name, style = MaterialTheme.typography.bodyLarge)
        Text(modes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DataSourceMode.labelId(): StringResource =
    when (this) {
        DataSourceMode.BUS -> Res.string.route_group_bus
        DataSourceMode.COACH -> Res.string.route_mode_express_bus
        DataSourceMode.TRAIN -> Res.string.route_group_train
        DataSourceMode.METRO -> Res.string.route_mode_metro
        DataSourceMode.TRAM -> Res.string.route_mode_tram
        DataSourceMode.BOAT -> Res.string.route_group_boat
        DataSourceMode.PLANE -> Res.string.route_mode_plane
        DataSourceMode.CABLE_CAR -> Res.string.route_mode_cable_car
        DataSourceMode.ON_DEMAND -> Res.string.route_mode_on_demand
    }

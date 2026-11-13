package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.PlaceholderBlock
import com.example.pulsetransit.ui.components.ScreenMessage
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
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
                            Icon(imageVector = PulseIcons.ArrowBack, contentDescription = stringResource(Res.string.back))
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
                    verticalArrangement = Arrangement.spacedBy(0.dp),
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
                            leadingIcon = { Icon(PulseIcons.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    when {
                        // The intro is local text, so only the list is a skeleton.
                        uiState.isLoading ->
                            item(key = "loading") { SourcesSkeleton() }
                        // T5 in the list's place: the list is the content and nothing is cached.
                        uiState.failed ->
                            item(key = "failed") {
                                ScreenMessage(
                                    title = stringResource(Res.string.data_sources_failed),
                                    body = stringResource(Res.string.data_sources_failed_body),
                                    icon = PulseIcons.CloudOff,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Button(onClick = viewModel::retry) { Text(stringResource(Res.string.try_again)) } }
                            }
                        // A real answer, not an error: the company isn't covered.
                        uiState.noMatches ->
                            item(key = "noMatch") {
                                ScreenMessage(
                                    title = stringResource(Res.string.data_sources_no_match, uiState.query.trim()),
                                    icon = PulseIcons.SearchOff,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        else -> {
                            if (uiState.query.isBlank()) {
                                item(key = "count") {
                                    Text(
                                        stringResource(Res.string.data_sources_count, uiState.all.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = FjordDimens.space2),
                                    )
                                }
                            }
                            itemsIndexed(uiState.shown, key = { _, source -> source.name }) { index, source ->
                                DataSourceRow(
                                    source = source,
                                    first = index == 0,
                                    last = index == uiState.shown.lastIndex,
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

/** One company and what it runs, from Entur; a card's row, the first and last rounding its corners. */
@Composable
private fun DataSourceRow(
    source: DataSource,
    first: Boolean,
    last: Boolean,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val modes = source.modes.map { stringResource(it.labelId()) }.joinToString(", ")
    val corner = FjordDimens.radiusLg
    val shape =
        RoundedCornerShape(
            topStart = if (first) corner else 0.dp,
            topEnd = if (first) corner else 0.dp,
            bottomStart = if (last) corner else 0.dp,
            bottomEnd = if (last) corner else 0.dp,
        )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier),
    ) {
        Column(modifier = Modifier.heightIn(min = FjordDimens.touch).padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3)) {
            Text(source.name, style = MaterialTheme.typography.bodyLarge)
            if (modes.isNotEmpty()) {
                Text(modes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!last) SettingsDivider()
    }
}

@Composable
private fun SourcesSkeleton() {
    val alpha = rememberPlaceholderPulse()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = FjordDimens.space2),
        verticalArrangement = Arrangement.spacedBy(FjordDimens.space4),
    ) {
        repeat(SKELETON_ROWS) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaceholderBlock(alpha, Modifier.width(140.dp).height(14.dp))
                PlaceholderBlock(alpha, Modifier.width(200.dp).height(12.dp))
            }
        }
    }
}

private const val SKELETON_ROWS = 6

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

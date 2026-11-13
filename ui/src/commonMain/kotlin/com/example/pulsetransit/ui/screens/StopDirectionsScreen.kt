package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.Quay
import com.example.pulsetransit.data.quaySections
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.components.LinePill
import com.example.pulsetransit.ui.components.PlaceholderBlock
import com.example.pulsetransit.ui.components.ScreenMessage
import com.example.pulsetransit.ui.components.rememberPlaceholderPulse
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Which directions of a stop the board shows: tick platforms, and with none ticked it shows all.
 * The list is the screen's only content, so a failed load (T5) fills it, with Try again; the top
 * bar stays, so Back still works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDirectionsScreen(
    viewModel: SettingsViewModel,
    stopId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val stop = state.favorites.firstOrNull { it.id == stopId }
    val withQuays = state.favoriteStopsWithQuays[stopId]
    val name = stop?.name ?: withQuays?.name.orEmpty()
    PulseTransitTheme(themeMode = state.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(Res.string.directions_title))
                            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(PulseIcons.ArrowBack, contentDescription = stringResource(Res.string.back)) }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
            },
            contentWindowInsets = WindowInsets.navigationBars,
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
                    when {
                        withQuays != null && withQuays.quays.isNotEmpty() -> {
                            Text(stringResource(Res.string.directions_intro, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            QuayPicker(
                                stopName = name,
                                withQuays = withQuays,
                                selected = state.selectedQuays[stopId].orEmpty(),
                                onToggle = { viewModel.toggleQuay(stopId, it) },
                                onShowAll = { viewModel.showAllQuays(stopId) },
                            )
                        }
                        withQuays != null ->
                            Text(stringResource(Res.string.directions_none, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        stopId in state.quaysFailed ->
                            ScreenMessage(
                                title = stringResource(Res.string.directions_failed_title),
                                body = stringResource(Res.string.directions_failed_body, name),
                                icon = PulseIcons.CloudOff,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Button(onClick = { viewModel.retryQuays(stopId) }) { Text(stringResource(Res.string.try_again)) } }
                        else -> {
                            Text(stringResource(Res.string.directions_intro, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            QuaySkeleton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuayPicker(
    stopName: String,
    withQuays: com.example.pulsetransit.data.StopPlace,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onShowAll: () -> Unit,
) {
    val all = withQuays.quays.size
    // A saved choice can hold platforms Entur no longer lists; only the listed ones count.
    val chosen = withQuays.quays.count { it.id in selected }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
        if (selected.isEmpty()) {
            Icon(PulseIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.showing_all_directions), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(PulseIcons.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                stringResource(Res.string.directions_showing_some, chosen, all),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowAll) { Text(stringResource(Res.string.directions_show_all)) }
        }
    }
    Surface(
        shape = RoundedCornerShape(FjordDimens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = FjordDimens.space1)) {
            quaySections(withQuays).forEachIndexed { sectionIndex, section ->
                section.title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = FjordDimens.space4, top = FjordDimens.space3, bottom = FjordDimens.space1).semantics { heading() },
                    )
                }
                section.quays.forEachIndexed { index, quay ->
                    if (index > 0 || (sectionIndex > 0 && section.title == null)) SettingsDivider()
                    QuayRow(
                        quay = quay,
                        label = quayLabel(quay, setOfNotNull(stopName, section.title)),
                        // With none chosen, every platform is shown, but none is ticked: ticking
                        // one then chooses just that one, as the intro says.
                        checked = quay.id in selected,
                        onToggle = { onToggle(quay.id) },
                    )
                }
            }
        }
    }
}

/** One platform: its tick, name, the lines it serves and where they go. */
@Composable
private fun QuayRow(
    quay: Quay,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FjordDimens.touch)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(end = FjordDimens.space4, top = FjordDimens.space2, bottom = FjordDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = FjordDimens.space3))
        Column(verticalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (quay.lines.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1), verticalArrangement = Arrangement.spacedBy(FjordDimens.space1)) {
                    quay.lines.take(MAX_LINE_PILLS).forEach { LinePill(line = it, mode = null, small = true) }
                    if (quay.lines.size > MAX_LINE_PILLS) {
                        Text("+${quay.lines.size - MAX_LINE_PILLS}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (quay.destinations.isNotEmpty()) {
                Text(
                    stringResource(Res.string.directions_towards, quay.destinations.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuaySkeleton() {
    val alpha = rememberPlaceholderPulse()
    Column(verticalArrangement = Arrangement.spacedBy(FjordDimens.space4), modifier = Modifier.padding(vertical = FjordDimens.space2)) {
        repeat(SKELETON_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                PlaceholderBlock(alpha, Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceholderBlock(alpha, Modifier.width(100.dp).height(14.dp))
                    PlaceholderBlock(alpha, Modifier.width(180.dp).height(12.dp))
                }
            }
        }
    }
}

/**
 * A platform's name: "Platform 2", else its description or name, unless that only repeats the
 * stop's or section's name ([redundantNames]); else the end of its id.
 */
@Composable
internal fun quayLabel(
    quay: Quay,
    redundantNames: Set<String>,
): String {
    val platform = quay.publicCode?.takeIf { it.isNotBlank() }
    // Copied to locals: Quay comes from :shared, and Kotlin does not smart-cast another module's properties.
    val description = quay.description?.takeIf { it.isNotBlank() }
    val name = quay.name?.takeIf { it.isNotBlank() && it !in redundantNames }
    return when {
        platform != null -> stringResource(Res.string.platform_label, platform)
        description != null -> description
        name != null -> name
        else -> stringResource(Res.string.direction_id, quay.id.takeLast(3))
    }
}

private const val MAX_LINE_PILLS = 8
private const val SKELETON_ROWS = 4

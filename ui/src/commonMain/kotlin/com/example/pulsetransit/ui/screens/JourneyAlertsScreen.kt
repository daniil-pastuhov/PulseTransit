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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.journey.JourneyAlertKind
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings' journey alerts: how "Notify me about this journey" follows a trip, and which of its
 * alerts to show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyAlertsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    PulseTransitTheme(themeMode = uiState.themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.journey_rationale_title)) },
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
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(Res.string.settings_journey_alerts_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Radio and check boxes get the 48 dp square the clickable checkboxes in Settings
                // have, so these rows line up with them.
                SettingsCard(title = stringResource(Res.string.journey_tracking_title)) {
                    JourneyTrackingMode.entries.forEach { mode ->
                        val selected = uiState.journeyMode == mode
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { viewModel.updateJourneyMode(mode) },
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                modifier = Modifier.padding(12.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(stringResource(journeyModeLabel(mode)), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(journeyModeDetail(mode)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                SettingsCard(title = stringResource(Res.string.journey_alert_kinds_title)) {
                    JourneyAlertKind.entries.forEach { kind ->
                        val enabled = kind !in uiState.disabledJourneyAlerts
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .toggleable(
                                        value = enabled,
                                        role = Role.Checkbox,
                                        onValueChange = { viewModel.updateJourneyAlert(kind, it) },
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = enabled,
                                onCheckedChange = null,
                                modifier = Modifier.padding(12.dp),
                            )
                            Text(stringResource(journeyAlertKindLabel(kind)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

/** What each way of following a journey is called, in Settings and in its summary there. */
internal fun journeyModeLabel(mode: JourneyTrackingMode): StringResource =
    when (mode) {
        JourneyTrackingMode.TIME_ONLY -> Res.string.journey_mode_time
        JourneyTrackingMode.LOCATION -> Res.string.journey_mode_location
    }

private fun journeyModeDetail(mode: JourneyTrackingMode): StringResource =
    when (mode) {
        JourneyTrackingMode.TIME_ONLY -> Res.string.journey_mode_time_detail
        JourneyTrackingMode.LOCATION -> Res.string.journey_mode_location_detail
    }

/** What each kind of journey alert is called in Settings. */
private fun journeyAlertKindLabel(kind: JourneyAlertKind): StringResource =
    when (kind) {
        JourneyAlertKind.DEPARTING -> Res.string.journey_alert_kind_departing
        JourneyAlertKind.NEXT_STOP -> Res.string.journey_alert_kind_next_stop
        JourneyAlertKind.TRANSFER -> Res.string.journey_alert_kind_transfer
        JourneyAlertKind.WALK -> Res.string.journey_alert_kind_walk
        JourneyAlertKind.FASTER_ROUTE -> Res.string.journey_alert_kind_faster_route
        JourneyAlertKind.MISSED -> Res.string.journey_alert_kind_missed
        JourneyAlertKind.ARRIVED -> Res.string.journey_alert_kind_arrived
    }

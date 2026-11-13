package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.journey.JourneyAlertKind
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.data.journey.appliesTo
import com.example.pulsetransit.data.journey.journeyAlertCount
import com.example.pulsetransit.theme.FjordDimens
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
                        .padding(FjordDimens.space4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = SINGLE_COLUMN_MAX_WIDTH).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FjordDimens.space6),
                ) {
                    Text(
                        stringResource(Res.string.settings_journey_alerts_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsSection(title = stringResource(Res.string.journey_tracking_title)) {
                        JourneyTrackingMode.entries.forEachIndexed { index, mode ->
                            if (index > 0) SettingsDivider()
                            val selected = uiState.journeyMode == mode
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = FjordDimens.touch)
                                        .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.updateJourneyMode(mode) })
                                        .padding(end = FjordDimens.space4, top = FjordDimens.space2, bottom = FjordDimens.space2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = FjordDimens.space3))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(journeyModeLabel(mode)), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(journeyModeDetail(mode)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        SettingsDivider()
                        // The privacy answer, next to the choice it is about.
                        Row(
                            modifier = Modifier.padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
                            horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PulseIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                stringResource(Res.string.journey_rationale_privacy),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val count = journeyAlertCount(uiState.journeyMode, uiState.disabledJourneyAlerts)
                    SettingsSection(
                        title = stringResource(Res.string.journey_alert_kinds_title),
                        action = {
                            Text(
                                if (count.isAll) {
                                    stringResource(Res.string.journey_alerts_all)
                                } else {
                                    stringResource(Res.string.journey_alerts_count, count.enabled, count.total)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        JourneyAlertKind.entries.forEachIndexed { index, kind ->
                            if (index > 0) SettingsDivider()
                            // A missed ride needs location, so it is off and disabled, with the reason, until then.
                            val applies = kind.appliesTo(uiState.journeyMode)
                            SettingSwitch(
                                title = stringResource(journeyAlertKindLabel(kind)),
                                hint = if (applies) null else stringResource(Res.string.journey_missed_needs_location),
                                checked = applies && kind !in uiState.disabledJourneyAlerts,
                                onCheckedChange = { viewModel.updateJourneyAlert(kind, it) },
                                enabled = applies,
                            )
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

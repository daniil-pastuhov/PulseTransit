package com.example.pulsetransit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.ui.platform.rememberJourneyPermissionRequest
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.JourneyAlertsViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * "Notify me about this journey" in a route's details, or "Stop following" while it is the journey
 * being followed. Absent where alerts are unavailable or notifications are denied.
 */
@Composable
fun JourneyNotifyButton(
    state: JourneyAlertsViewModel.UiState,
    trip: TripInfo,
    onNotify: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isFollowing(trip)) {
        OutlinedButton(onClick = onStop, modifier = modifier.fillMaxWidth()) {
            Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.journey_stop_button), modifier = Modifier.padding(start = 8.dp))
        }
    } else if (state.available) {
        FilledTonalButton(onClick = onNotify, modifier = modifier.fillMaxWidth()) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.journey_notify_button), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * Why journey alerts want notifications and location, and the choice between alerts from live
 * times only and, with the location, also noticing a missed ride. Says plainly that the location
 * never leaves the device.
 */
@Composable
fun JourneyRationaleDialog(
    onChoice: (JourneyTrackingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
        title = { Text(stringResource(Res.string.journey_rationale_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.journey_rationale_body), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(Res.string.journey_rationale_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ModeChoice(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(Res.string.journey_mode_location),
                    detail = stringResource(Res.string.journey_mode_location_detail),
                    onClick = { onChoice(JourneyTrackingMode.LOCATION) },
                )
                ModeChoice(
                    icon = Icons.Default.Schedule,
                    title = stringResource(Res.string.journey_mode_time),
                    detail = stringResource(Res.string.journey_mode_time_detail),
                    onClick = { onChoice(JourneyTrackingMode.TIME_ONLY) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.journey_rationale_cancel)) }
        },
    )
}

@Composable
private fun ModeChoice(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The journey being followed, under the board's header: tap to see it, or stop following it. */
@Composable
fun FollowedJourneyBar(
    journey: TrackedJourney,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = stringResource(Res.string.journey_following, journey.trip.destinationName),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onStop) { Text(stringResource(Res.string.journey_stop_button), color = tint) }
    }
}

/**
 * The parts of journey alerts that outlive a route's details: the rationale, the system prompts
 * after it, re-reading the permissions when the app comes back (they may have changed in system
 * settings), and a tap on a notification, which calls [onOpenJourney].
 */
@Composable
fun JourneyAlertsEffects(
    viewModel: JourneyAlertsViewModel,
    onOpenJourney: (TrackedJourney) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }

    if (state.rationaleFor != null) {
        JourneyRationaleDialog(onChoice = viewModel::onRationaleChoice, onDismiss = viewModel::onRationaleDismiss)
    }

    val requestPermissions = rememberJourneyPermissionRequest(onResult = viewModel::onPermissionsResult)
    val request = state.permissionRequest
    LaunchedEffect(request) {
        if (request != null) requestPermissions(request.notifications, request.location)
    }

    val active = state.activeJourney
    LaunchedEffect(state.openJourneyRequested, active) {
        if (state.openJourneyRequested) {
            if (active != null) onOpenJourney(active)
            viewModel.onJourneyOpened()
        }
    }
}

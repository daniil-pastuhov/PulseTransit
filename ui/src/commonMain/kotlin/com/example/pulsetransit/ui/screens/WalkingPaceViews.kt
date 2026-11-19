package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.pace.MeasuredWalkingPace
import com.example.pulsetransit.data.pace.WalkingSpeedProposal
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.components.Callout
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

// The walking pace the app measured on the device, as the on-spot board proposes it and Settings
// shows it. What to propose is decided in the shared WalkingPace; these only pick the strings.

private const val TENTHS = 10.0

/** "5.4" (nb "5,4"): the pace in km/h with the locale's decimal separator. */
internal fun kmhText(kmhTenths: Int): String = String.format(Locale.getDefault(), "%.1f", kmhTenths / TENTHS)

private fun WalkingSpeed.useLabelId(): StringResource =
    when (this) {
        WalkingSpeed.SLOW -> Res.string.walking_pace_use_slow
        WalkingSpeed.NORMAL -> Res.string.walking_pace_use_normal
        WalkingSpeed.FAST -> Res.string.walking_pace_use_fast
    }

private fun WalkingSpeed.proposalBodyId(): StringResource =
    when (this) {
        WalkingSpeed.SLOW -> Res.string.walking_pace_proposal_body_slow
        WalkingSpeed.NORMAL -> Res.string.walking_pace_proposal_body_normal
        WalkingSpeed.FAST -> Res.string.walking_pace_proposal_body_fast
    }

/**
 * The on-spot board's proposal to use [proposal]'s walking speed for routes: "You walk faster than
 * the app assumes", the measured pace, then "Not now" ([onDismiss]) and "Use Fast" ([onAccept]).
 */
@Composable
internal fun WalkingPaceProposalCallout(
    proposal: WalkingSpeedProposal,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Callout(
        title =
            stringResource(
                if (proposal.isFaster) Res.string.walking_pace_proposal_title_faster else Res.string.walking_pace_proposal_title_slower,
            ),
        body = stringResource(proposal.speed.proposalBodyId(), kmhText(proposal.kmhTenths)),
        icon = PulseIcons.DirectionsWalk,
        modifier = modifier,
    ) {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.walking_pace_not_now)) }
        TextButton(onClick = onAccept) { Text(stringResource(proposal.speed.useLabelId())) }
    }
}

/**
 * Under Settings' walking speed picker: the pace measured on the device (or that none is yet), the
 * proposal's "Use Fast" when there is one, where the pace comes from, and "Forget measured pace"
 * while anything is kept.
 */
@Composable
internal fun WalkingPaceRow(
    measured: MeasuredWalkingPace?,
    proposal: WalkingSpeedProposal?,
    hasData: Boolean,
    onAccept: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActions = proposal != null || hasData
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // A text button brings its own space below; text alone needs a row's.
                .padding(start = FjordDimens.space4, end = FjordDimens.space4, bottom = if (hasActions) FjordDimens.space1 else FjordDimens.space3),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text =
                if (measured != null) {
                    stringResource(Res.string.walking_pace_measured, kmhText(measured.kmhTenths))
                } else {
                    stringResource(Res.string.walking_pace_not_measured)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.walking_pace_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasActions) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                if (proposal != null) {
                    TextButton(onClick = onAccept) { Text(stringResource(proposal.speed.useLabelId())) }
                }
                if (hasData) {
                    TextButton(onClick = onForget) { Text(stringResource(Res.string.walking_pace_forget)) }
                }
            }
        }
    }
}

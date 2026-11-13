package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTheme
import com.example.pulsetransit.ui.components.Callout
import com.example.pulsetransit.ui.components.CalloutTone
import com.example.pulsetransit.ui.components.loadFailureIcon
import com.example.pulsetransit.ui.resources.Res
import com.example.pulsetransit.ui.resources.attribution_entur
import com.example.pulsetransit.ui.resources.attribution_met
import com.example.pulsetransit.ui.resources.board_empty_action
import com.example.pulsetransit.ui.resources.board_empty_body
import com.example.pulsetransit.ui.resources.board_empty_title
import com.example.pulsetransit.ui.resources.destination_saved
import com.example.pulsetransit.ui.resources.dismiss_update
import com.example.pulsetransit.ui.resources.footer_updated
import com.example.pulsetransit.ui.resources.line_hidden
import com.example.pulsetransit.ui.resources.retry
import com.example.pulsetransit.ui.resources.stale_entur_body
import com.example.pulsetransit.ui.resources.stale_entur_title
import com.example.pulsetransit.ui.resources.stale_no_internet_body
import com.example.pulsetransit.ui.resources.stale_no_internet_title
import com.example.pulsetransit.ui.resources.undo
import com.example.pulsetransit.ui.resources.update_app
import com.example.pulsetransit.ui.resources.update_available
import com.example.pulsetransit.ui.resources.updating
import com.example.pulsetransit.ui.viewmodel.BoardUndo
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The one lasting notice under the header (T4), chosen by priority so they never stack: stale data
 * (no internet, or Entur not answering) before a new version. [extra] is a notice of higher
 * priority than the update, from the caller (on-spot's "Location off"); it shows when there is no
 * stale data. Nothing shows while the board is fresh.
 */
@Composable
internal fun BoardNoticeSlot(
    uiState: StopBoardViewModel.UiState,
    updateAvailable: Boolean,
    onRetry: () -> Unit,
    onUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    val age = uiState.staleAgeMinutes
    when {
        uiState.showStaleNotice && age != null ->
            if (uiState.isOnline) {
                Callout(
                    title = stringResource(Res.string.stale_entur_title),
                    body = stringResource(Res.string.stale_entur_body, age),
                    tone = CalloutTone.ERROR,
                    icon = loadFailureIcon(isOnline = true),
                    modifier = modifier,
                ) { TextButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) } }
            } else {
                Callout(
                    title = stringResource(Res.string.stale_no_internet_title),
                    body = stringResource(Res.string.stale_no_internet_body, age),
                    tone = CalloutTone.WARNING,
                    icon = loadFailureIcon(isOnline = false),
                    modifier = modifier,
                )
            }
        extra != null -> extra()
        updateAvailable ->
            Callout(
                body = stringResource(Res.string.update_available),
                tone = CalloutTone.INFO,
                icon = PulseIcons.SystemUpdate,
                modifier = modifier,
            ) {
                TextButton(onClick = onDismissUpdate) { Text(stringResource(Res.string.dismiss_update)) }
                TextButton(onClick = onUpdate) { Text(stringResource(Res.string.update_app)) }
            }
    }
}

/** "Updated 19:37 · Data from Entur · Weather from MET Norway", or "Updating…" during a pull. */
@Composable
internal fun BoardFooter(
    uiState: StopBoardViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    val parts =
        listOfNotNull(
            if (uiState.isManualRefreshing) stringResource(Res.string.updating) else stringResource(Res.string.footer_updated, uiState.lastUpdated),
            stringResource(Res.string.attribution_entur),
            if (uiState.showWeather && uiState.weather.isNotEmpty()) stringResource(Res.string.attribution_met) else null,
        )
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A board with no stops yet: what it is for, and the way to Settings. Clock and weather still run. */
@Composable
internal fun BoardEmptyState(
    onAddStops: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = FjordDimens.space6, vertical = FjordDimens.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FjordDimens.space3, Alignment.CenterVertically),
    ) {
        EmptyBoardArt()
        Text(
            stringResource(Res.string.board_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.board_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Button(onClick = onAddStops) { Text(stringResource(Res.string.board_empty_action)) }
    }
}

/** A small board with three line rows and a plus, drawn from the theme (the design's empty art). */
@Composable
private fun EmptyBoardArt() {
    val container = MaterialTheme.colorScheme.surfaceContainer
    val bar = MaterialTheme.colorScheme.outlineVariant
    val lines = PulseTheme.colors.lines
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = Modifier.size(width = 160.dp, height = 120.dp)) {
        val unit = size.width / ART_WIDTH

        fun r(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
        ) = Offset(x * unit, y * unit) to Size(w * unit, h * unit)
        val (boardAt, boardSize) = r(20f, 24f, 120f, 76f)
        drawRoundRect(container, boardAt, boardSize, CornerRadius(12 * unit))
        listOf(lines.bus to 56f, lines.metro to 44f, lines.train to 50f).forEachIndexed { index, (color, width) ->
            val y = 40f + index * 20f
            val (pillAt, pillSize) = r(32f, y, 22f, 12f)
            drawRoundRect(color, pillAt, pillSize, CornerRadius(3 * unit))
            val (barAt, barSize) = r(60f, y + 2f, width, 8f)
            drawRoundRect(bar, barAt, barSize, CornerRadius(4 * unit))
        }
        val center = Offset(128 * unit, 26 * unit)
        drawCircle(primary, 16 * unit, center)
        val arm = 7 * unit
        drawLine(onPrimary, center - Offset(arm, 0f), center + Offset(arm, 0f), 3 * unit, StrokeCap.Round)
        drawLine(onPrimary, center - Offset(0f, arm), center + Offset(0f, arm), 3 * unit, StrokeCap.Round)
    }
}

private const val ART_WIDTH = 160f

/**
 * Shows the board's [BoardUndo] as a snackbar (T3) with Undo, and clears it when the snackbar goes,
 * whether by Undo, a swipe or its timeout.
 */
@Composable
internal fun BoardUndoSnackbar(
    pending: BoardUndo?,
    hostState: SnackbarHostState,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(pending?.serial) {
        val undo = pending ?: return@LaunchedEffect
        val message =
            when (undo) {
                is BoardUndo.LineHidden -> getString(Res.string.line_hidden, undo.line)
                is BoardUndo.DestinationSaved -> getString(Res.string.destination_saved, undo.place.name)
            }
        val result =
            hostState.showSnackbar(
                message = message,
                actionLabel = getString(Res.string.undo),
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) onUndo() else onDismiss()
    }
}

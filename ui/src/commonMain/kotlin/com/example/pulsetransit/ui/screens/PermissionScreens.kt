package com.example.pulsetransit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.platform.AppPermission
import com.example.pulsetransit.ui.platform.SystemBackHandler
import com.example.pulsetransit.ui.platform.rememberOpenAppSettings
import com.example.pulsetransit.ui.platform.rememberPermissionGranted
import com.example.pulsetransit.ui.platform.rememberPermissionRequest
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * On the spot's ask for location, after its intro: why it is needed before the system prompt,
 * then, if refused or put off, what still works (T5): the system settings, carrying on without
 * it, or the wall board, which needs no location. Ends with [onDone] once access is granted,
 * however that happens, or when the user carries on without it.
 */
@Composable
fun LocationAccessScreen(
    onDone: () -> Unit,
    onUseBoard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var refused by rememberSaveable { mutableStateOf(false) }
    val granted = rememberPermissionGranted(AppPermission.LOCATION)
    // Granted in the prompt or, coming back, in the system settings.
    LaunchedEffect(granted) { if (granted) onDone() }
    val request = rememberPermissionRequest(AppPermission.LOCATION) { ok -> if (ok) onDone() else refused = true }
    val openSettings = rememberOpenAppSettings()
    SystemBackHandler(enabled = refused) { refused = false }
    PulseTransitTheme {
        if (!refused) {
            PermissionPage(
                art = { PermissionArt(PulseIcons.LocationOn, refused = false) },
                title = stringResource(Res.string.location_rationale_title),
                body = stringResource(Res.string.location_rationale_body),
                points =
                    listOf(
                        PulseIcons.NearMe to stringResource(Res.string.location_rationale_precise),
                        PulseIcons.Settings to stringResource(Res.string.location_rationale_turn_off),
                    ),
                modifier = modifier,
            ) {
                PrimaryAction(PulseIcons.MyLocation, stringResource(Res.string.location_allow), request)
                TextAction(stringResource(Res.string.not_now)) { refused = true }
            }
        } else {
            PermissionPage(
                art = { PermissionArt(PulseIcons.LocationOff, refused = true) },
                title = stringResource(Res.string.location_needed_title),
                body = stringResource(Res.string.location_needed_body),
                modifier = modifier,
            ) {
                PrimaryAction(PulseIcons.OpenInNew, stringResource(Res.string.open_app_settings), openSettings)
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = FjordDimens.touch)) {
                    Text(stringResource(Res.string.location_continue_without))
                }
                TextAction(stringResource(Res.string.use_as_board_instead), onUseBoard)
            }
        }
    }
}

/**
 * Settings' ask for the camera, shown where camera wake is turned on: privacy is the whole
 * question, so it is answered before the system prompt. [onGranted] turns the setting on; refused,
 * it says what the board does without it, and [onDismiss] leaves the setting off.
 */
@Composable
fun CameraAccessDialog(
    onGranted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var refused by rememberSaveable { mutableStateOf(false) }
    val granted = rememberPermissionGranted(AppPermission.CAMERA)
    // Granted in the system settings, once the user comes back from there.
    LaunchedEffect(granted) { if (granted && refused) onGranted() }
    val request = rememberPermissionRequest(AppPermission.CAMERA) { ok -> if (ok) onGranted() else refused = true }
    val openSettings = rememberOpenAppSettings()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PulseTransitTheme {
            val topBar: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(PulseIcons.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                    Text(
                        stringResource(Res.string.wake_camera),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!refused) {
                PermissionPage(
                    art = { PermissionArt(PulseIcons.PhotoCamera, refused = false) },
                    title = stringResource(Res.string.camera_rationale_title),
                    body = stringResource(Res.string.camera_rationale_body),
                    points =
                        listOf(
                            PulseIcons.Lock to stringResource(Res.string.camera_rationale_on_device),
                            PulseIcons.VideocamOff to stringResource(Res.string.camera_rationale_nothing_kept),
                            PulseIcons.Tune to stringResource(Res.string.camera_rationale_sensitivity),
                        ),
                    topBar = topBar,
                ) {
                    PrimaryAction(PulseIcons.PhotoCamera, stringResource(Res.string.camera_allow), request)
                    TextAction(stringResource(Res.string.not_now), onDismiss)
                }
            } else {
                PermissionPage(
                    art = { PermissionArt(PulseIcons.VideocamOff, refused = true) },
                    title = stringResource(Res.string.camera_denied_title),
                    body = stringResource(Res.string.camera_denied_body),
                    topBar = topBar,
                ) {
                    PrimaryAction(PulseIcons.OpenInNew, stringResource(Res.string.open_app_settings), openSettings)
                    TextAction(stringResource(Res.string.camera_keep_off), onDismiss)
                }
            }
        }
    }
}

/**
 * The layout every permission screen shares, in the onboarding column: an optional top bar, the
 * art, the title, why, the points that answer the usual worries, and the actions at the bottom.
 */
@Composable
private fun PermissionPage(
    art: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    points: List<Pair<ImageVector, String>> = emptyList(),
    topBar: (@Composable () -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.widthIn(max = ONBOARDING_COLUMN_WIDTH).fillMaxWidth().heightIn(min = FjordDimens.touch)) {
                topBar?.invoke()
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(max = ONBOARDING_COLUMN_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = FjordDimens.space4),
                verticalArrangement = Arrangement.spacedBy(FjordDimens.space4, Alignment.CenterVertically),
            ) {
                art()
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                points.forEach { (icon, text) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3)) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Column(
                modifier = Modifier.widthIn(max = ONBOARDING_COLUMN_WIDTH).fillMaxWidth().padding(vertical = FjordDimens.space4),
                verticalArrangement = Arrangement.spacedBy(FjordDimens.space2),
                content = actions,
            )
        }
    }
}

/** A tile with the feature's symbol: in the primary colours to ask, muted once refused. */
@Composable
private fun PermissionArt(
    icon: ImageVector,
    refused: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(FjordDimens.radiusXl))
                .background(colors.surfaceContainer)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(124.dp).clip(CircleShape).background(if (refused) colors.surface else colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (refused) colors.outline else colors.primary, modifier = Modifier.size(60.dp))
        }
    }
}

@Composable
private fun PrimaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = FjordDimens.touch)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

@Composable
private fun TextAction(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = FjordDimens.touch)) { Text(label) }
}

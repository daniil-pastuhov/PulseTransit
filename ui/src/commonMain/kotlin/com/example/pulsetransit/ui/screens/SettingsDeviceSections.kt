package com.example.pulsetransit.ui.screens

// Settings' sections about how the device behaves: theme, power, waking and dimming, the
// screen's care and the board's location.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.data.BurnInProtectionLevel
import com.example.pulsetransit.data.CameraSensitivity
import com.example.pulsetransit.data.CoordinateProblem
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.theme.FjordDimens
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.components.Callout
import com.example.pulsetransit.ui.components.CalloutTone
import com.example.pulsetransit.ui.platform.AppPermission
import com.example.pulsetransit.ui.platform.rememberLocationPermissionRequest
import com.example.pulsetransit.ui.platform.rememberOpenAppSettings
import com.example.pulsetransit.ui.platform.rememberPermissionGranted
import com.example.pulsetransit.ui.resources.*
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel.LocationStatus
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Light, dark, the system's, or by the room's light, with that mode's two thresholds. */
@Composable
internal fun ThemeSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
) {
    SettingsSection(title = stringResource(Res.string.set_theme_title)) {
        SettingSegmented(
            title = stringResource(Res.string.set_theme_mode),
            hint = stringResource(Res.string.set_theme_hint),
            options =
                listOf(
                    ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
                    ThemeMode.LIGHT to stringResource(Res.string.theme_light),
                    ThemeMode.DARK to stringResource(Res.string.theme_dark),
                    ThemeMode.AUTO to stringResource(Res.string.theme_auto),
                ),
            selected = state.themeMode,
            onSelect = viewModel::updateThemeMode,
        )
        if (state.themeMode == ThemeMode.AUTO) {
            SettingsDivider()
            Column(
                modifier = Modifier.padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
                verticalArrangement = Arrangement.spacedBy(FjordDimens.space2),
            ) {
                Text(stringResource(Res.string.set_thresholds_title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.set_thresholds_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.currentLux?.let { LuxScaleIndicator(currentLux = it, darkLux = state.autoThemeDarkLux, lightLux = state.autoThemeLightLux) }
            }
            SettingSegmented(
                title = stringResource(Res.string.set_thresholds_dark),
                options = DARK_THRESHOLDS.map { it to stringResource(Res.string.lux_value, it.roundToInt()) },
                selected = state.autoThemeDarkLux,
                onSelect = viewModel::updateAutoThemeDarkLux,
            )
            SettingSegmented(
                title = stringResource(Res.string.set_thresholds_light),
                options = LIGHT_THRESHOLDS.map { it to stringResource(Res.string.lux_value, it.roundToInt()) },
                selected = state.autoThemeLightLux,
                onSelect = viewModel::updateAutoThemeLightLux,
            )
        }
    }
}

/** How often things update; on the board also the power-related switches. */
@Composable
internal fun PowerSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
    isBoard: Boolean,
) {
    SettingsSection(title = stringResource(if (isBoard) Res.string.set_power_title else Res.string.set_refresh_title)) {
        SettingSegmented(
            title = stringResource(Res.string.set_refresh_interval),
            hint = stringResource(if (isBoard) Res.string.set_refresh_hint_board else Res.string.set_refresh_hint_spot),
            options =
                listOf(
                    0 to stringResource(Res.string.refresh_none),
                    30 to stringResource(Res.string.refresh_30s),
                    60 to stringResource(Res.string.refresh_1m),
                    120 to stringResource(Res.string.refresh_2m),
                    300 to stringResource(Res.string.refresh_5m),
                ),
            selected = state.refreshIntervalSeconds,
            onSelect = viewModel::updateRefreshInterval,
        )
        if (isBoard) {
            SettingsDivider()
            SettingSwitch(
                title = stringResource(Res.string.set_dynamic_refresh),
                hint = stringResource(Res.string.set_dynamic_refresh_hint),
                checked = state.dynamicRefresh,
                onCheckedChange = viewModel::updateDynamicRefresh,
            )
            SettingsDivider()
            SettingSwitch(
                title = stringResource(Res.string.set_full_screen),
                hint = stringResource(Res.string.set_full_screen_hint),
                checked = state.isFullScreenMode,
                onCheckedChange = viewModel::updateFullScreenMode,
            )
            SettingsDivider()
            SettingSwitch(
                title = stringResource(Res.string.set_always_on),
                hint = stringResource(Res.string.set_always_on_hint),
                checked = state.isAlwaysOnEnabled,
                onCheckedChange = viewModel::updateAlwaysOnMode,
            )
        }
    }
}

/**
 * Camera wake, and the two settings that only matter with it on: sensitivity and auto-dim, shown
 * disabled with the reason while it is off, so the rows don't shift. Turning it on without camera
 * access explains first ([CameraAccessDialog]).
 */
@Composable
internal fun WakeAndDimSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
) {
    val cameraGranted = rememberPermissionGranted(AppPermission.CAMERA)
    val openAppSettings = rememberOpenAppSettings()
    var askingCamera by remember { mutableStateOf(false) }
    if (askingCamera) {
        CameraAccessDialog(
            onGranted = {
                askingCamera = false
                viewModel.updateCameraMotionWake(true)
            },
            onDismiss = { askingCamera = false },
        )
    }
    val on = state.cameraMotionWake
    SettingsSection(title = stringResource(Res.string.set_wake_title)) {
        SettingSwitch(
            title = stringResource(Res.string.wake_camera),
            hint = stringResource(Res.string.set_camera_hint),
            checked = on,
            onCheckedChange = { enable ->
                // Turned on without camera access: why first, then the prompt.
                if (enable && !cameraGranted) askingCamera = true else viewModel.updateCameraMotionWake(enable)
            },
        )
        if (on && !cameraGranted) {
            // On from before, with access since taken away: say why it does nothing (T4).
            Callout(
                body = stringResource(Res.string.camera_denied_title),
                tone = CalloutTone.WARNING,
                icon = PulseIcons.VideocamOff,
                modifier = Modifier.padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space2),
            ) { TextButton(onClick = openAppSettings) { Text(stringResource(Res.string.open_app_settings)) } }
        }
        SettingsDivider()
        SettingSegmented(
            title = stringResource(Res.string.camera_sensitivity),
            hint = stringResource(if (on) Res.string.camera_sensitivity_hint else Res.string.set_sensitivity_off_hint),
            options =
                listOf(
                    CameraSensitivity.LOW to stringResource(Res.string.sensitivity_low),
                    CameraSensitivity.MEDIUM to stringResource(Res.string.sensitivity_medium),
                    CameraSensitivity.HIGH to stringResource(Res.string.sensitivity_high),
                ),
            selected = state.cameraSensitivity,
            onSelect = viewModel::updateCameraSensitivity,
            enabled = on,
        )
        SettingsDivider()
        SettingSegmented(
            title = stringResource(Res.string.auto_dim_delay),
            hint = stringResource(if (on) Res.string.set_auto_dim_hint else Res.string.set_auto_dim_off_hint),
            options =
                listOf(
                    15 to stringResource(Res.string.dim_15s),
                    30 to stringResource(Res.string.dim_30s),
                    60 to stringResource(Res.string.dim_1m),
                    120 to stringResource(Res.string.dim_2m),
                    0 to stringResource(Res.string.dim_never),
                ),
            selected = state.autoDimSeconds,
            onSelect = viewModel::updateAutoDimSeconds,
            enabled = on,
        )
    }
}

@Composable
internal fun OledSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
) {
    SettingsSection(title = stringResource(Res.string.set_oled_title)) {
        SettingSegmented(
            title = stringResource(Res.string.set_pixel_shift),
            hint = stringResource(Res.string.set_pixel_shift_hint),
            options =
                listOf(
                    BurnInProtectionLevel.OFF to stringResource(Res.string.burn_in_off),
                    BurnInProtectionLevel.LOW to stringResource(Res.string.burn_in_low),
                    BurnInProtectionLevel.MEDIUM to stringResource(Res.string.burn_in_medium),
                    BurnInProtectionLevel.HIGH to stringResource(Res.string.burn_in_high),
                ),
            selected = state.burnInLevel,
            onSelect = viewModel::updateBurnInLevel,
        )
    }
}

/**
 * The board's location: where it stands, finding it (with progress, T4 callouts when refused or
 * failed) and typing it, with each field's own error (T1).
 */
@Composable
internal fun LocationSection(
    state: SettingsViewModel.UiState,
    viewModel: SettingsViewModel,
) {
    val requestLocation = rememberLocationPermissionRequest(onGranted = viewModel::requestLocationAndNearbyStops)
    val openAppSettings = rememberOpenAppSettings()
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    val status = state.locationStatus
    val requesting = status == LocationStatus.RequestingPrecise || status == LocationStatus.RequestingApproximate
    SettingsSection(title = stringResource(Res.string.set_location_title), hint = stringResource(Res.string.set_location_hint)) {
        Column(
            modifier = Modifier.padding(horizontal = FjordDimens.space4, vertical = FjordDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FjordDimens.space3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space3), verticalAlignment = Alignment.CenterVertically) {
                Icon(PulseIcons.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.set_current_coordinates), style = MaterialTheme.typography.bodyLarge)
                    val lat = state.latitude
                    val lon = state.longitude
                    Text(
                        if (lat != null && lon != null) "${coordinateText(lat)}, ${coordinateText(lon)}" else stringResource(Res.string.not_set),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (status) {
                LocationStatus.Denied ->
                    Callout(
                        title = stringResource(Res.string.set_location_denied_title),
                        body = stringResource(Res.string.set_location_denied_body),
                        tone = CalloutTone.WARNING,
                        icon = PulseIcons.LocationOff,
                    ) { TextButton(onClick = openAppSettings) { Text(stringResource(Res.string.open_app_settings)) } }
                LocationStatus.Failed, is LocationStatus.Error ->
                    Callout(
                        title = stringResource(Res.string.set_location_failed_title),
                        body = stringResource(Res.string.set_location_failed_body),
                        tone = CalloutTone.ERROR,
                        icon = PulseIcons.Error,
                    ) { TextButton(onClick = requestLocation) { Text(stringResource(Res.string.try_again)) } }
                else -> LocationStatusLine(status)
            }
            Button(onClick = requestLocation, enabled = !requesting, modifier = Modifier.fillMaxWidth().heightIn(min = FjordDimens.touch)) {
                if (requesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(PulseIcons.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(FjordDimens.space2))
                Text(stringResource(Res.string.set_detect_location))
            }
            Text(stringResource(Res.string.set_enter_coordinates), style = MaterialTheme.typography.bodyLarge)
            val errors = state.coordinatesError
            Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2)) {
                CoordinateField(
                    value = latitude,
                    onValueChange = {
                        latitude = it
                        viewModel.clearCoordinatesError()
                    },
                    label = stringResource(Res.string.set_latitude),
                    error = errors?.latitude?.let { coordinateErrorText(it, isLatitude = true) },
                    modifier = Modifier.weight(1f),
                )
                CoordinateField(
                    value = longitude,
                    onValueChange = {
                        longitude = it
                        viewModel.clearCoordinatesError()
                    },
                    label = stringResource(Res.string.set_longitude),
                    error = errors?.longitude?.let { coordinateErrorText(it, isLatitude = false) },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = { viewModel.setManualCoordinates(latitude, longitude) },
                modifier = Modifier.fillMaxWidth().heightIn(min = FjordDimens.touch),
            ) { Text(stringResource(Res.string.set_save_coordinates)) }
        }
    }
}

@Composable
private fun CoordinateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

/** What a coordinate field says is wrong, with an example of what to type (T1). */
@Composable
private fun coordinateErrorText(
    problem: CoordinateProblem,
    isLatitude: Boolean,
): String {
    val example = if (isLatitude) "59.9111" else "10.7528"
    return when (problem) {
        CoordinateProblem.MISSING -> stringResource(Res.string.set_coord_missing, example)
        CoordinateProblem.NOT_A_NUMBER -> stringResource(Res.string.set_coord_not_number, example)
        CoordinateProblem.OUT_OF_RANGE -> stringResource(if (isLatitude) Res.string.set_latitude_range else Res.string.set_longitude_range)
    }
}

/** The lookup's progress or outcome, as one line; refused and failed get callouts instead. */
@Composable
private fun LocationStatusLine(status: LocationStatus) {
    val (icon, text) =
        when (status) {
            LocationStatus.NotRequested -> PulseIcons.Info to stringResource(Res.string.loc_not_requested)
            LocationStatus.RequestingPrecise -> null to stringResource(Res.string.loc_requesting_gps)
            LocationStatus.RequestingApproximate -> null to stringResource(Res.string.loc_requesting_network)
            is LocationStatus.Found ->
                PulseIcons.CheckCircle to
                    stringResource(
                        Res.string.loc_success,
                        stringResource(if (status.accuracy == LocationAccuracy.PRECISE) Res.string.gps_source else Res.string.network_source),
                    )
            LocationStatus.Manual -> PulseIcons.CheckCircle to stringResource(Res.string.loc_manual)
            else -> return
        }
    Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space2), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A coordinate to four decimals, about 10 m, as typed: "59.9111". */
private fun coordinateText(value: Double): String {
    val scaled = (value * COORDINATE_SCALE).roundToInt()
    val sign = if (scaled < 0) "-" else ""
    val abs = kotlin.math.abs(scaled)
    return "$sign${abs / COORDINATE_SCALE.toInt()}.${(abs % COORDINATE_SCALE.toInt()).toString().padStart(COORDINATE_DECIMALS, '0')}"
}

/**
 * A horizontal scale showing the live ambient-light reading against the two auto-theme thresholds.
 * The filled bar tracks the current lux; the two ticks (and labels below) mark the "dark below" and
 * "light above" thresholds, so the user can see where the current reading falls relative to both.
 */
@Composable
private fun LuxScaleIndicator(
    currentLux: Float,
    darkLux: Float,
    lightLux: Float,
    modifier: Modifier = Modifier,
) {
    val scaleMax = maxOf(lightLux, currentLux, 1f) * LUX_SCALE_HEADROOM
    val curFrac = (currentLux / scaleMax).coerceIn(0f, 1f)
    val darkFrac = (darkLux / scaleMax).coerceIn(0f, 1f)
    val lightFrac = (lightLux / scaleMax).coerceIn(0f, 1f)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val darkMarkColor = MaterialTheme.colorScheme.onSurface
    val lightMarkColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(FjordDimens.space1), verticalAlignment = Alignment.CenterVertically) {
            Icon(PulseIcons.LightMode, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = stringResource(Res.string.current_lux, currentLux.roundToInt()), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(modifier = Modifier.height(6.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val fullWidth = maxWidth
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor),
            )
            Box(
                modifier =
                    Modifier
                        .width(fullWidth * curFrac)
                        .height(8.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(fillColor),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = fullWidth * darkFrac - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(darkMarkColor),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = fullWidth * lightFrac - 1.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(lightMarkColor),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val fullWidth = maxWidth
            val labelWidth = 48.dp
            Text(
                text = stringResource(Res.string.lux_value, darkLux.roundToInt()),
                style = MaterialTheme.typography.labelSmall,
                color = darkMarkColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(labelWidth).offset(x = (fullWidth * darkFrac - labelWidth / 2).coerceIn(0.dp, fullWidth - labelWidth)),
            )
            Text(
                text = stringResource(Res.string.lux_value, lightLux.roundToInt()),
                style = MaterialTheme.typography.labelSmall,
                color = lightMarkColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(labelWidth).offset(x = (fullWidth * lightFrac - labelWidth / 2).coerceIn(0.dp, fullWidth - labelWidth)),
            )
        }
    }
}

private val DARK_THRESHOLDS = listOf(10f, 30f, 50f, 100f)
private val LIGHT_THRESHOLDS = listOf(100f, 150f, 200f, 250f)
private const val COORDINATE_SCALE = 10_000.0
private const val COORDINATE_DECIMALS = 4

/** How far the lux scale reaches past its highest mark, so the mark isn't at the very end. */
private const val LUX_SCALE_HEADROOM = 1.15f

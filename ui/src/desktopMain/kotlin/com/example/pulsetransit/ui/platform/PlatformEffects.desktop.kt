package com.example.pulsetransit.ui.platform

import androidx.compose.runtime.Composable
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) = Unit

/** No camera on the desktop app. */
@Composable
actual fun CameraMotionEffect(
    viewModel: StopBoardViewModel,
    key: Any?,
) = Unit

/** Never called: the desktop app has no store to update from. */
@Composable
actual fun rememberAppUpdateAction(): () -> Unit = {}

/** The desktop app's location is fixed and needs no permission, so retrying just retries. */
@Composable
actual fun rememberLocationAccess(onGranted: () -> Unit): () -> Unit = onGranted

/** The desktop's location needs no permission. */
@Composable
actual fun rememberLocationPermissionRequest(onGranted: () -> Unit): () -> Unit = onGranted

/** Never called: the desktop app has no journey alerts, so their button is hidden. */
@Composable
actual fun rememberJourneyPermissionRequest(
    onResult: (notificationsGranted: Boolean, locationGranted: Boolean) -> Unit,
): (notifications: Boolean, location: Boolean) -> Unit = { _, _ -> onResult(false, false) }

/** From the locale's short time format: "HH:mm" is 24-hour, "h:mm a" is not. */
@Composable
actual fun is24HourClock(): Boolean =
    (DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern()?.contains('H') ?: true

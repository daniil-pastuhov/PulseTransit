package com.example.pulsetransit.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
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
actual fun rememberJourneyPermissionRequest(onResult: (notificationsGranted: Boolean, locationGranted: Boolean) -> Unit): (notifications: Boolean, location: Boolean) -> Unit = { _, _ -> onResult(false, false) }

/** From the locale's short time format: "HH:mm" is 24-hour, "h:mm a" is not. */
@Composable
actual fun is24HourClock(): Boolean = (DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()) as? SimpleDateFormat)?.toPattern()?.contains('H') ?: true

/** The desktop has no map app of its own, so Google Maps' walking directions open in the browser. */
@Composable
actual fun rememberWalkingDirections(): (latitude: Double, longitude: Double) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { latitude, longitude ->
        runCatching {
            uriHandler.openUri("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude&travelmode=walking")
        }
    }
}

/** The desktop app has no permission settings to open. */
@Composable
actual fun rememberOpenAppSettings(): () -> Unit = {}

/** The desktop app needs no permissions: its location is fixed and it has no camera. */
@Composable
actual fun rememberPermissionGranted(permission: AppPermission): Boolean = permission == AppPermission.LOCATION

/** Nothing to ask on the desktop; location counts as granted, the camera as refused. */
@Composable
actual fun rememberPermissionRequest(
    permission: AppPermission,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit = { onResult(permission == AppPermission.LOCATION) }

/** The desktop window has no system back. */
@Composable
actual fun SystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit

/** The desktop app is a development tool with no release of its own. */
@Composable
actual fun appVersion(): String = "dev"

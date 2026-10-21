package com.example.pulsetransit.ui.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.pulsetransit.platform.isInstallable
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable
actual fun CameraMotionEffect(
    viewModel: StopBoardViewModel,
    key: Any?,
) {
    val context = LocalContext.current
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.startCameraMotion()
            }
        }

    LaunchedEffect(Unit) {
        if (!context.hasCameraPermission() && viewModel.isCameraMotionEnabled()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // The camera runs only while the board is visible: it is released when the app goes to the
    // background or the board leaves the screen, and reopened when it comes back.
    // Keyed on the mode so a switch made in Settings starts or stops the camera here too.
    LifecycleStartEffect(key) {
        if (context.hasCameraPermission()) {
            viewModel.startCameraMotion()
        }
        onStopOrDispose { viewModel.stopCameraMotion() }
    }
}

@Composable
actual fun rememberAppUpdateAction(): () -> Unit {
    val context = LocalContext.current
    // Play's update screen reports back here. Its result needs no handling: a declined or failed
    // update just leaves the notice up, and a finished one restarts the app.
    val updateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    val scope = rememberCoroutineScope()
    return { scope.launch { startPlayUpdate(context, updateLauncher) } }
}

@Composable
actual fun rememberLocationAccess(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var askedByUser by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                onGranted()
            } else if (askedByUser) {
                // Android stops showing the prompt once the user has refused it for good; the
                // app's system settings page is then the only place access can be granted.
                val activity = context.findActivity()
                if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSIONS[0])) {
                    context.openAppSettings()
                }
            }
        }

    LaunchedEffect(Unit) {
        if (!context.hasLocationPermission()) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    return {
        if (context.hasLocationPermission()) {
            onGranted()
        } else {
            askedByUser = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }
}

@Composable
actual fun rememberLocationPermissionRequest(onGranted: () -> Unit): () -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) onGranted()
        }
    return { launcher.launch(LOCATION_PERMISSIONS) }
}

@Composable
actual fun is24HourClock(): Boolean = DateFormat.is24HourFormat(LocalContext.current)

/**
 * Installs the update in place through Play's update screen, which restarts the app once it is
 * done, and resumes one that was interrupted. Opens the app's Play Store page instead when Play
 * can no longer offer it in place.
 */
@Suppress("TooGenericExceptionCaught") // Play fails with a plain Exception; the store page is the fallback.
private suspend fun startPlayUpdate(
    context: Context,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
) {
    val manager = AppUpdateManagerFactory.create(context)
    val info =
        try {
            manager.requestAppUpdateInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    if (info?.isInstallable == true) {
        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
    } else {
        openPlayStorePage(context)
    }
}

private fun openPlayStorePage(context: Context) {
    val packageName = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.hasLocationPermission(): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

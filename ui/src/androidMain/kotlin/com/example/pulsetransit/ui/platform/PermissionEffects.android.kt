package com.example.pulsetransit.ui.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

// The permissions onboarding and Settings explain before asking: whether each is granted, and
// the system prompt for it.

private fun AppPermission.manifestNames(): Array<String> =
    when (this) {
        AppPermission.LOCATION -> LOCATION_PERMISSIONS
        AppPermission.CAMERA -> arrayOf(Manifest.permission.CAMERA)
    }

/** Location counts as granted with either precise or approximate access. */
private fun Context.isGranted(permission: AppPermission): Boolean = permission.manifestNames().any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@Composable
actual fun rememberPermissionGranted(permission: AppPermission): Boolean {
    val context = LocalContext.current
    var granted by remember(permission) { mutableStateOf(context.isGranted(permission)) }
    // Coming back from the system settings, or from the prompt, which pauses the activity.
    LifecycleResumeEffect(permission) {
        granted = context.isGranted(permission)
        onPauseOrDispose {}
    }
    return granted
}

@Composable
actual fun rememberPermissionRequest(
    permission: AppPermission,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    // Read back from the system, which also covers a prompt Android no longer shows once refused.
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            currentOnResult(context.isGranted(permission))
        }
    return { launcher.launch(permission.manifestNames()) }
}

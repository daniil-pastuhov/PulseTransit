package com.example.pulsetransit.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.pulsetransit.R
import com.example.pulsetransit.platform.isInstallable
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException

/** Tells the user that Play has a newer version of the app, under the board's header. */
@Composable
fun UpdateBanner(
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.update_available),
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        Row {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss_update), color = tint) }
            TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_app)) }
        }
    }
}

/**
 * Installs the update in place through Play's update screen, which restarts the app once it is
 * done, and resumes one that was interrupted. Opens the app's Play Store page instead when Play
 * can no longer offer it in place.
 */
@Suppress("TooGenericExceptionCaught") // Play fails with a plain Exception; the store page is the fallback.
suspend fun startPlayUpdate(
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

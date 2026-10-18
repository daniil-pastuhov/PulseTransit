package com.example.pulsetransit.platform

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException

/**
 * The Android [AppUpdateChecker], on Play's In-App Updates. The app installs the update through
 * Play's own flow (`startPlayUpdate` in `:app`), so there is no store URL.
 *
 * Builds not installed from Play (debug builds, sideloads) get an error from Play, which is
 * reported as no update.
 */
class PlayAppUpdateChecker(
    context: Context,
) : AppUpdateChecker {
    private val manager = AppUpdateManagerFactory.create(context)

    @Suppress("TooGenericExceptionCaught") // Play fails with a plain Exception; no update is the answer to any failure.
    override suspend fun availableUpdate(): AppUpdate? =
        try {
            manager
                .requestAppUpdateInfo()
                .takeIf { it.isInstallable }
                ?.let { AppUpdate(version = it.availableVersionCode().toString(), storeUrl = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}

/**
 * Whether Play can install an update in place: one is available, or an install the app started
 * earlier was interrupted and can be resumed.
 */
val AppUpdateInfo.isInstallable: Boolean
    get() =
        when (updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> true
            else -> false
        }

package com.example.pulsetransit.journey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pulsetransit.platform.JourneyPermissions
import com.example.pulsetransit.platform.PermissionState
import com.russhwolf.settings.Settings

/**
 * Journey alerts' permissions on Android. The system reports only granted or not, so whether the
 * user was ever asked is remembered here: not granted and never asked reads as [PermissionState.NOT_ASKED].
 */
class AndroidJourneyPermissions(
    private val context: Context,
    private val settings: Settings,
) : JourneyPermissions {
    override val isSupported = true

    override fun notificationState(): PermissionState =
        when {
            NotificationManagerCompat.from(context).areNotificationsEnabled() -> PermissionState.GRANTED
            // Before Android 13 there is no prompt: notifications were turned off in system settings.
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> PermissionState.DENIED
            settings.getBoolean(ASKED_NOTIFICATIONS, false) -> PermissionState.DENIED
            else -> PermissionState.NOT_ASKED
        }

    override fun locationState(): PermissionState =
        when {
            LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ->
                PermissionState.GRANTED
            settings.getBoolean(ASKED_LOCATION, false) -> PermissionState.DENIED
            else -> PermissionState.NOT_ASKED
        }

    override fun markAsked(
        notifications: Boolean,
        location: Boolean,
    ) {
        if (notifications) settings.putBoolean(ASKED_NOTIFICATIONS, true)
        if (location) settings.putBoolean(ASKED_LOCATION, true)
    }

    private companion object {
        const val ASKED_NOTIFICATIONS = "journey_asked_notifications"
        const val ASKED_LOCATION = "journey_asked_location"
        val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

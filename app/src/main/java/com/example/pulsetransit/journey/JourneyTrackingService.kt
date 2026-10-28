package com.example.pulsetransit.journey

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.JourneyTracker
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.platform.JourneyPermissions
import com.example.pulsetransit.platform.PermissionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/**
 * Keeps the followed journey's [JourneyTracker] running while the app is in the background, as a
 * foreground service with the journey's progress as its notification. Of type `location` when the
 * journey reads the location, which lets a while-in-use grant keep working with the app closed;
 * otherwise `specialUse`, for live transit times. Stops itself when the journey ends.
 */
class JourneyTrackingService : Service() {
    private val store: JourneyStore by inject()
    private val tracker: JourneyTracker by inject()
    private val notifier: AndroidJourneyNotifier by inject()
    private val permissions: JourneyPermissions by inject()

    private val scope = MainScope()
    private var tracking: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val journey = store.active.value
        // Started with startForegroundService, so startForeground must come before anything stops
        // the service, even when the journey ended meanwhile. Built synchronously for that: the
        // strings are local resources, read in a few milliseconds.
        val notification = runBlocking { notifier.buildInitial(journey) }
        val withLocation = journey?.mode == JourneyTrackingMode.LOCATION && permissions.locationState() == PermissionState.GRANTED
        // Android refuses a location service started from the background (a sticky restart after the
        // process was killed), and on 12+ any service started from there: then without location, and
        // failing that not at all. MainActivity starts it again when the app opens.
        val started =
            (if (withLocation) listOf(true, false) else listOf(false)).any { location ->
                tryStartForeground(notification, location)
            }
        if (journey == null || !started) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (tracking?.isActive != true) {
            tracking =
                scope.launch {
                    tracker.run()
                    ServiceCompat.stopForeground(this@JourneyTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
        }
        // Restarted after the process is killed, to pick the saved journey up again.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun tryStartForeground(
        notification: Notification,
        withLocation: Boolean,
    ): Boolean =
        try {
            ServiceCompat.startForeground(this, AndroidJourneyNotifier.PROGRESS_ID, notification, serviceType(withLocation))
            true
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            Log.w(TAG, "Foreground start refused (location: $withLocation)", e)
            false
        }

    private fun serviceType(withLocation: Boolean): Int =
        when {
            withLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else -> 0
        }

    private companion object {
        const val TAG = "JourneyTrackingService"
    }

    /** [JourneyLauncher] on Android: this service. */
    class Launcher(
        private val context: Context,
    ) : JourneyLauncher {
        override fun start() {
            ContextCompat.startForegroundService(context, Intent(context, JourneyTrackingService::class.java))
        }

        override fun stop() {
            context.stopService(Intent(context, JourneyTrackingService::class.java))
        }
    }
}

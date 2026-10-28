package com.example.pulsetransit.platform

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The device services a desktop has no hardware or store for. Location and connectivity are real:
// see IpLocationProvider and NetworkInterfaceConnectivityMonitor.

/** [ClockFormatter] in the JVM's default locale and time zone, as Android's `SystemClockFormatter`. */
class DesktopClockFormatter : ClockFormatter {
    override fun clockText(epochMillis: Long): ClockText {
        val date = Date(epochMillis)

        fun format(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        return ClockText(
            time = format("HH:mm"),
            dayOfMonth = format("d"),
            weekdayShort = format("EEE"),
            monthShort = format("LLL"),
            weekdayLong = format("EEEE"),
            monthLong = format("LLLL"),
        )
    }

    override fun timeWithSeconds(epochMillis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
}

/** A desktop build is never installed from a store, so there is nothing to update to. */
class NoAppUpdates : AppUpdateChecker {
    override suspend fun availableUpdate(): AppUpdate? = null
}

/** No light sensor: the `AUTO` theme keeps whichever theme it starts in. */
class NoLightSensor : SystemSensorManager {
    override fun getLightFlow(): Flow<Float> = emptyFlow()
}

/** No camera wake: the board is never dimmed by an empty room. */
class NoCameraMotion : CameraMotionDetector {
    override fun motionEvents(): Flow<Unit> = emptyFlow()
}

/** No notifications to show journey alerts with: the details hide the button. */
class NoJourneyAlerts :
    JourneyNotifier,
    JourneyPermissions,
    JourneyLauncher {
    override val isSupported = false

    override fun notificationState() = PermissionState.DENIED

    override fun locationState() = PermissionState.DENIED

    override fun markAsked(
        notifications: Boolean,
        location: Boolean,
    ) = Unit

    override fun show(
        journey: TrackedJourney,
        alert: JourneyAlert,
    ) = Unit

    override fun updateProgress(progress: JourneyProgress) = Unit

    override fun scheduleUpcoming(
        journey: TrackedJourney,
        upcoming: List<ScheduledAlert>,
    ) = Unit

    override fun end(journeyId: String) = Unit

    override fun start() = Unit

    override fun stop() = Unit
}

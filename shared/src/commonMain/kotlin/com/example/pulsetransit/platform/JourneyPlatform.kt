package com.example.pulsetransit.platform

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney

/**
 * Shows a followed journey's notifications: alerts as they come, the progress in an ongoing
 * notification (Android) or Live Activity (iOS). Calls come from the main thread.
 */
interface JourneyNotifier {
    fun show(
        journey: TrackedJourney,
        alert: JourneyAlert,
    )

    fun updateProgress(progress: JourneyProgress)

    /**
     * The alerts still to come, re-sent whenever times change. A platform that may be suspended
     * before they are due schedules them with the system (iOS); one that keeps running ignores this.
     */
    fun scheduleUpcoming(
        journey: TrackedJourney,
        upcoming: List<ScheduledAlert>,
    )

    /**
     * [journey] is over or no longer followed: take its ongoing notification down, and its alerts
     * not yet shown. With [clearAlerts], because another journey took its place, the alerts it has
     * already shown go too: only the followed journey's alerts are ever in sight.
     */
    fun end(
        journey: TrackedJourney,
        clearAlerts: Boolean,
    )
}

enum class PermissionState {
    NOT_ASKED,
    GRANTED,
    DENIED,
}

/** The permissions journey alerts need, as the platform reports them. */
interface JourneyPermissions {
    /** False where journey alerts do not exist (the desktop app). */
    val isSupported: Boolean

    fun notificationState(): PermissionState

    fun locationState(): PermissionState

    /**
     * Records that the system prompts were shown, where the platform cannot tell "never asked"
     * from "denied" on its own (Android).
     */
    fun markAsked(
        notifications: Boolean,
        location: Boolean,
    )
}

/** Starts and stops what follows the store's active journey on this platform: a service, or a coroutine. */
interface JourneyLauncher {
    fun start()

    fun stop()
}

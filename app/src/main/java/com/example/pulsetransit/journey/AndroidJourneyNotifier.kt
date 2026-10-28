package com.example.pulsetransit.journey

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.example.pulsetransit.MainActivity
import com.example.pulsetransit.R
import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyPhase
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.ui.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Journey alerts as Android notifications: each alert on a high-importance channel, and the
 * progress as the ongoing notification of [JourneyTrackingService]. Strings are Compose resources,
 * which load asynchronously, so notifications are built in order on one queue.
 */
class AndroidJourneyNotifier(
    private val context: Context,
    clockFormatter: ClockFormatter,
) : JourneyNotifier {
    private val manager = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val content = JourneyProgressContent(clockFormatter)

    init {
        scope.launch {
            for (task in queue) {
                ensureChannels()
                task()
            }
        }
    }

    private var channelsCreated = false

    /** Creates the channels, named in the current language, before anything is posted to them. */
    private suspend fun ensureChannels() {
        if (channelsCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(ALERTS_CHANNEL, getString(Res.string.journey_channel_alerts), NotificationManager.IMPORTANCE_HIGH),
        )
        system.createNotificationChannel(
            NotificationChannel(PROGRESS_CHANNEL, getString(Res.string.journey_channel_progress), NotificationManager.IMPORTANCE_LOW),
        )
        channelsCreated = true
    }

    override fun show(
        journey: TrackedJourney,
        alert: JourneyAlert,
    ) {
        queue.trySend { post(alertId(alert), buildAlert(alert)) }
    }

    override fun updateProgress(progress: JourneyProgress) {
        queue.trySend { post(PROGRESS_ID, buildProgress(progress)) }
    }

    // Android keeps the service, and so the alerts' timing, running: nothing is scheduled ahead.
    override fun scheduleUpcoming(
        journey: TrackedJourney,
        upcoming: List<ScheduledAlert>,
    ) = Unit

    override fun end(journeyId: String) {
        queue.trySend { manager.cancel(PROGRESS_ID) }
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled is checked; without it nothing is posted.
    private fun post(
        id: Int,
        notification: Notification,
    ) {
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    /**
     * The ongoing notification the service starts with, before the first progress is known; without
     * a journey (it ended before the service started), just the channel's name.
     */
    suspend fun buildInitial(journey: TrackedJourney?): Notification {
        ensureChannels()
        val title =
            if (journey == null) {
                getString(Res.string.journey_channel_progress)
            } else {
                getString(Res.string.journey_progress_title, journey.trip.destinationName)
            }
        return baseProgress().setContentTitle(title).build()
    }

    private fun baseProgress() =
        NotificationCompat
            .Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_journey)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Otherwise the header shows when the notification was built, which reads as a journey time.
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(showJourneyIntent())

    /**
     * The ongoing notification: what to do now as the title, the facts for this step under it (see
     * [JourneyProgress.details]), the journey as a progress bar, and the steps still to come when
     * expanded. On Android 16 it is a Live Update, with the minutes to the next moment in its chip.
     */
    private suspend fun buildProgress(progress: JourneyProgress): Notification {
        val details = content.details(progress)
        val builder =
            baseProgress()
                .setContentTitle(content.headline(progress))
                .setContentText(details)
                .setSubText(getString(Res.string.journey_progress_title, progress.destinationName))
                .setRequestPromotedOngoing(true)
        // The header shows the next moment as a clock time, the one the text names. Not a
        // chronometer: its "16:35" countdown reads as a time of day.
        val next = progress.nextAtMillis?.takeIf { progress.phase != JourneyPhase.ARRIVED }
        if (next != null) builder.setWhen(next).setShowWhen(true) else builder.setShowWhen(false)
        progress.minutesToNext?.let { builder.setShortCriticalText(getString(Res.string.journey_progress_in_minutes, it)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setStyle(JourneyProgressContent.progressStyle(progress))
        } else {
            val expanded = (listOf(details) + content.upcomingLines(progress)).joinToString("\n")
            val max = JourneyProgressContent.PROGRESS_MAX
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
                .setProgress(max, (progress.elapsedFraction * max).toInt(), false)
        }
        if (!progress.muted) builder.addAction(0, getString(Res.string.journey_action_skip), skipIntent())
        builder.addAction(0, getString(Res.string.journey_action_show), showJourneyIntent())
        return builder.build()
    }

    private suspend fun buildAlert(alert: JourneyAlert): Notification {
        val (title, text) = alertText(alert)
        val builder =
            NotificationCompat
                .Builder(context, ALERTS_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_journey)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(showJourneyIntent())
        if (alert !is JourneyAlert.MissedJourney && alert !is JourneyAlert.Arrived) {
            builder.addAction(0, getString(Res.string.journey_action_skip), skipIntent())
        }
        builder.addAction(0, getString(Res.string.journey_action_show), showJourneyIntent())
        if (alert is JourneyAlert.WalkTo && alert.canNavigate) {
            navigateIntent(alert.latitude!!, alert.longitude!!)?.let { navigate ->
                builder.addAction(0, getString(Res.string.journey_action_navigate), navigate)
            }
        }
        return builder.build()
    }

    private suspend fun alertText(alert: JourneyAlert): Pair<String, String?> =
        when (alert) {
            is JourneyAlert.DepartingSoon ->
                getString(Res.string.journey_alert_departing, alert.lineName, clockTime(alert.departsAtMillis)) to
                    listOfNotNull(
                        alert.stopName?.let { getString(Res.string.journey_alert_from, it) },
                        alert.platform?.let { getString(Res.string.journey_alert_platform, it) },
                        alert.destination?.let { getString(Res.string.journey_alert_towards, it) },
                    ).joinToString(" · ")
            is JourneyAlert.NextStopIsYours ->
                getString(Res.string.journey_alert_next_stop, alert.stopName) to getString(Res.string.journey_alert_next_stop_detail)
            is JourneyAlert.Transfer ->
                getString(Res.string.journey_alert_transfer, alert.lineName) to
                    listOfNotNull(
                        getString(Res.string.journey_alert_leaves_at, clockTime(alert.departsAtMillis)),
                        alert.platform?.let { getString(Res.string.journey_alert_platform, it) },
                    ).joinToString(" · ")
            is JourneyAlert.WalkTo ->
                getString(Res.string.journey_alert_walk, alert.name) to getString(Res.string.journey_alert_walk_detail, alert.minutes)
            JourneyAlert.MissedJourney ->
                getString(Res.string.journey_alert_missed) to getString(Res.string.journey_alert_missed_detail)
            is JourneyAlert.Arrived -> getString(Res.string.journey_alert_arrived, alert.destinationName) to null
        }

    /**
     * A departure as a clock time: an alert is posted once and may be read minutes later, when
     * "in 9 minutes" would no longer be true.
     */
    private fun clockTime(epochMillis: Long) = clockFormatter.clockText(epochMillis).time

    private fun showJourneyIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_JOURNEY, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun skipIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_SKIP,
            Intent(context, JourneyActionReceiver::class.java).setAction(JourneyActionReceiver.ACTION_SKIP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Walking directions in Google Maps, or else a map app that opens `geo:` links; null without
     * either. An activity, not a receiver: Android does not let a notification's broadcast open an
     * app. The intent names the app it resolved to, so no other app can step in to receive it.
     */
    private fun navigateIntent(
        latitude: Double,
        longitude: Double,
    ): PendingIntent? {
        val intent =
            listOf(
                "google.navigation:q=$latitude,$longitude&mode=w",
                "geo:$latitude,$longitude?q=$latitude,$longitude",
            ).firstNotNullOfOrNull { uri ->
                val candidate = Intent(Intent.ACTION_VIEW, uri.toUri())
                candidate.resolveActivity(context.packageManager)?.let { candidate.setComponent(it) }
            } ?: return null
        return PendingIntent.getActivity(
            context,
            REQUEST_NAVIGATE,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alertId(alert: JourneyAlert) = ALERT_ID_BASE + (alert.id.hashCode() and ALERT_ID_MASK)

    companion object {
        const val PROGRESS_ID = 4_100
        private const val ALERT_ID_BASE = 4_200
        private const val ALERT_ID_MASK = 0xFFFF
        private const val ALERTS_CHANNEL = "journey_alerts"
        const val PROGRESS_CHANNEL = "journey_progress"
        private const val REQUEST_SHOW = 1
        private const val REQUEST_SKIP = 2
        private const val REQUEST_NAVIGATE = 3
    }
}

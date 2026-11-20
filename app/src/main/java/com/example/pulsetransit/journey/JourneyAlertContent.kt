package com.example.pulsetransit.journey

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.getString

/**
 * The words of a journey alert: its title and the line under it. The notification and the watch
 * say the same.
 */
internal class JourneyAlertContent(
    private val clockFormatter: ClockFormatter,
) {
    suspend fun text(alert: JourneyAlert): Pair<String, String?> =
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
            is JourneyAlert.FasterRoute -> fasterRouteText(alert)
            is JourneyAlert.WalkTo ->
                getString(Res.string.journey_alert_walk, alert.name) to getString(Res.string.journey_alert_walk_detail, alert.minutes)
            JourneyAlert.MissedJourney ->
                getString(Res.string.journey_alert_missed) to getString(Res.string.journey_alert_missed_detail)
            is JourneyAlert.Arrived -> getString(Res.string.journey_alert_arrived, alert.destinationName) to null
        }

    /** "Faster: 19 at 09:16", with when it arrives, how much sooner, and where to board. */
    private suspend fun fasterRouteText(alert: JourneyAlert.FasterRoute): Pair<String, String?> {
        val line = alert.lineName
        val departs = alert.departsAtMillis
        val title =
            if (line != null && departs != null) {
                getString(Res.string.journey_alert_faster, line, clockTime(departs))
            } else {
                getString(Res.string.journey_alert_faster_walk)
            }
        return title to
            listOfNotNull(
                getString(Res.string.journey_alert_faster_detail, clockTime(alert.arrivesAtMillis), alert.minutesSooner),
                alert.stopName?.takeIf { line != null }?.let { getString(Res.string.journey_alert_from, it) },
                alert.platform?.let { getString(Res.string.journey_alert_platform, it) },
            ).joinToString(" · ")
    }

    /**
     * A departure as a clock time: an alert is posted once and may be read minutes later, when
     * "in 9 minutes" would no longer be true.
     */
    private fun clockTime(epochMillis: Long) = clockFormatter.clockText(epochMillis).time
}

package com.example.pulsetransit.journey

import androidx.core.app.NotificationCompat
import com.example.pulsetransit.data.journey.JourneyDetail
import com.example.pulsetransit.data.journey.JourneyPhase
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.JourneyTimelineSegment
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * The words and bar of the ongoing journey notification, from a [JourneyProgress]. What to show
 * is decided in the shared code ([JourneyProgress.details], [JourneyProgress.timeline]); this only
 * words it, as the iOS Live Activity does.
 */
internal class JourneyProgressContent(
    private val clockFormatter: ClockFormatter,
) {
    /** What to do now: "5 towards Vestli", "On 5 to Majorstuen", "Walking to Jernbanetorget". */
    suspend fun headline(progress: JourneyProgress): String {
        val line = progress.lineName.orEmpty()
        val target = progress.targetName.orEmpty()
        return when (progress.phase) {
            JourneyPhase.WAITING ->
                progress.towards?.let { getString(Res.string.journey_progress_towards, line, it) }
                    ?: getString(Res.string.journey_progress_waiting, line, target)
            JourneyPhase.RIDING -> getString(Res.string.journey_progress_riding, line, target)
            JourneyPhase.WALKING -> getString(Res.string.journey_progress_walking, target)
            JourneyPhase.ARRIVED -> getString(Res.string.journey_progress_arrived, progress.destinationName)
        }
    }

    /** The step's facts on one line: "Leaves 09:08, 2 min late · Platform 2 · From Jernbanetorget". */
    suspend fun details(progress: JourneyProgress): String = progress.details.map { detailText(it) }.joinToString(SEPARATOR)

    /** The expanded view's timetable: each step still to come, then the arrival. */
    suspend fun upcomingLines(progress: JourneyProgress): List<String> {
        val steps =
            progress.upcomingSteps.map { step ->
                val at = time(step.startMillis)
                val to = step.toName.orEmpty()
                val line = step.lineName.orEmpty()
                if (!step.isRide) {
                    getString(Res.string.journey_progress_step_walk, at, to)
                } else {
                    step.platform?.let { getString(Res.string.journey_progress_step_ride_platform, at, line, to, it) }
                        ?: getString(Res.string.journey_progress_step_ride, at, line, to)
                }
            }
        val arrival =
            progress.arrivalAtMillis
                ?.takeIf { progress.phase != JourneyPhase.ARRIVED }
                ?.let { getString(Res.string.journey_progress_step_arrive, time(it), progress.destinationName) }
        return steps + listOfNotNull(arrival)
    }

    private fun time(atMillis: Long) = clockFormatter.clockText(atMillis).time

    private suspend fun detailText(detail: JourneyDetail): String =
        when (detail) {
            is JourneyDetail.Departs ->
                if (detail.delayMinutes > 0) {
                    getString(Res.string.journey_progress_leaves_late, time(detail.atMillis), detail.delayMinutes)
                } else {
                    getString(Res.string.journey_progress_leaves, time(detail.atMillis))
                }
            is JourneyDetail.Platform -> getString(Res.string.journey_alert_platform, detail.platform)
            is JourneyDetail.From -> getString(Res.string.journey_alert_from, detail.stopName)
            is JourneyDetail.NextStop -> getString(Res.string.journey_progress_next_stop, detail.stopName)
            is JourneyDetail.StopsLeft -> getPluralString(Res.plurals.journey_progress_stops_left, detail.count, detail.count)
            is JourneyDetail.Change ->
                detail.platform?.let { getString(Res.string.journey_progress_change_platform, detail.lineName, time(detail.atMillis), it) }
                    ?: getString(Res.string.journey_progress_change, detail.lineName, time(detail.atMillis))
            is JourneyDetail.Arrival -> getString(Res.string.journey_progress_arrival, time(detail.atMillis))
            is JourneyDetail.Arrived -> getString(Res.string.journey_progress_arrived_time, time(detail.atMillis))
            JourneyDetail.LocationLost -> getString(Res.string.journey_progress_location_lost)
            JourneyDetail.Muted -> getString(Res.string.journey_progress_muted)
        }

    companion object {
        const val SEPARATOR = " · "

        /** The pre-Android 16 bar's maximum: the journey in thousandths. */
        const val PROGRESS_MAX = 1_000
        private const val MILLIS_PER_SECOND = 1_000L

        /** Walks and waits on the segmented bar: a mid grey that reads on light and dark shades. */
        private const val MUTED_SEGMENT_COLOR = 0xFF9E9E9E.toInt()

        /**
         * Android 16's segmented progress: one segment per walk, wait and ride, in proportion to its
         * time, rides in the notification's colour and the rest muted, with a point where each ride
         * after the start boards. Lengths are in seconds, so the bar's maximum is the journey's length.
         */
        fun progressStyle(progress: JourneyProgress): NotificationCompat.ProgressStyle {
            val style = NotificationCompat.ProgressStyle()
            val start = progress.startAtMillis ?: return style.setProgressIndeterminate(true)
            var position = 0
            progress.timeline.forEach { segment ->
                val length = (segment.lengthMillis / MILLIS_PER_SECOND).toInt().coerceAtLeast(1)
                val bar = NotificationCompat.ProgressStyle.Segment(length)
                if (segment.kind != JourneyTimelineSegment.Kind.RIDE) bar.setColor(MUTED_SEGMENT_COLOR)
                style.addProgressSegment(bar)
                if (segment.kind == JourneyTimelineSegment.Kind.RIDE && position > 0) {
                    style.addProgressPoint(NotificationCompat.ProgressStyle.Point(position))
                }
                position += length
            }
            val elapsed = ((progress.nowMillis - start) / MILLIS_PER_SECOND).toInt().coerceIn(0, position)
            return style.setProgress(elapsed)
        }
    }
}

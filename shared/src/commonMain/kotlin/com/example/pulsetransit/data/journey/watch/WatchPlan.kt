package com.example.pulsetransit.data.journey.watch

import com.example.pulsetransit.data.journey.JourneyPhase
import com.example.pulsetransit.data.journey.JourneyPosition
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.JourneyTimelineSegment
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.data.parseIsoToMillis
import com.example.pulsetransit.platform.WatchWording

/**
 * A followed journey as the watch app shows it, from now to its arrival, already worded. The watch
 * holds the whole plan and walks through it by its own clock, since the phone may be suspended
 * (iOS) or out of reach for the rest of the journey; the phone sends a new one when times change.
 */
data class WatchPlan(
    val journeyId: String,
    /** "Journey to Home". */
    val title: String,
    /** What to show from when, in time order; the first is what to show now. */
    val frames: List<Frame>,
    /** The journey's walks, waits and rides, for the progress bar. */
    val segments: List<JourneyTimelineSegment>,
    /** The alerts still to come, which the watch shows at their times. */
    val alerts: List<Alert>,
    val arrivalAtMillis: Long?,
) {
    data class Frame(
        val fromMillis: Long,
        val phase: JourneyPhase,
        val headline: String,
        val detail: String,
        /** The moment counted down to; null when there is none. */
        val nextAtMillis: Long?,
    )

    data class Alert(
        val id: String,
        val atMillis: Long,
        val title: String,
        val text: String?,
    )

    /**
     * Whether a watch holding [sent] already shows all of this at [nowMillis]: [sent]'s frames and
     * alerts from now on are this plan's. Then nothing needs sending, so the watch hears from the
     * phone when something changed and not at every tick.
     */
    fun isCoveredBy(
        sent: WatchPlan?,
        nowMillis: Long,
    ): Boolean {
        if (sent == null || sent.copy(frames = frames, alerts = alerts) != this) return false
        val current = sent.frames.indexOfLast { it.fromMillis <= nowMillis }.coerceAtLeast(0)
        // The first frame starts "now" in each plan, whenever that was.
        val sentAhead = sent.frames.drop(current).mapIndexed { index, frame -> if (index == 0) frame.copy(fromMillis = 0) else frame }
        val ahead = frames.mapIndexed { index, frame -> if (index == 0) frame.copy(fromMillis = 0) else frame }
        return sentAhead == ahead && sent.alerts.filter { it.atMillis > nowMillis } == alerts
    }
}

/** Builds a [WatchPlan] from a followed journey. */
object WatchPlans {
    /**
     * Keeps a long journey's plan well under the 16 kB a message to the watch may be; one with more
     * changes than this gets the rest in a later plan.
     */
    const val MAX_FRAMES = 24
    const val MAX_ALERTS = 16

    /**
     * [journey]'s progress now and at every moment from [nowMillis] on at which what it says
     * changes: a step starting or ending, and each stop passed on a ride. At most [MAX_FRAMES].
     */
    fun moments(
        journey: TrackedJourney,
        nowMillis: Long,
        locationLost: Boolean,
    ): List<JourneyProgress> {
        val now = JourneyPosition.progress(journey, nowMillis, locationLost)
        val changes =
            (
                now.steps.flatMap { listOf(it.startMillis, it.endMillis) } +
                    journey.legStops.values
                        .flatten()
                        .mapNotNull { parseIsoToMillis(it.expectedTime) }
            ).filter { it > nowMillis }.distinct().sorted()
        val moments = mutableListOf(now)
        changes.forEach { at ->
            val then = JourneyPosition.progress(journey, at, locationLost)
            if (then.copy(nowMillis = 0) != moments.last().copy(nowMillis = 0)) moments += then
        }
        return moments.take(MAX_FRAMES)
    }

    /** The plan to send at [nowMillis], with [upcoming] the alerts still to show. */
    suspend fun plan(
        journey: TrackedJourney,
        upcoming: List<ScheduledAlert>,
        nowMillis: Long,
        locationLost: Boolean,
        wording: WatchWording,
    ): WatchPlan {
        val moments = moments(journey, nowMillis, locationLost)
        val frames =
            moments.map { progress ->
                val text = wording.progress(progress)
                WatchPlan.Frame(
                    fromMillis = progress.nowMillis,
                    phase = progress.phase,
                    headline = text.title,
                    detail = text.text.orEmpty(),
                    nextAtMillis = progress.nextAtMillis?.takeIf { progress.phase != JourneyPhase.ARRIVED },
                )
            }
        val alerts =
            upcoming.sortedBy { it.atMillis }.take(MAX_ALERTS).map { scheduled ->
                val text = wording.alert(scheduled.alert)
                WatchPlan.Alert(scheduled.alert.id, scheduled.atMillis, text.title, text.text)
            }
        val first = moments.first()
        return WatchPlan(
            journeyId = journey.id,
            title = wording.title(first),
            frames = frames,
            segments = first.timeline,
            alerts = alerts,
            arrivalAtMillis = first.arrivalAtMillis,
        )
    }
}

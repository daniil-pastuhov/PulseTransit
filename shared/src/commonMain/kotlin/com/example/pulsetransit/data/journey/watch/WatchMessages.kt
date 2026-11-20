package com.example.pulsetransit.data.journey.watch

import com.example.pulsetransit.data.journey.JourneyPhase
import com.example.pulsetransit.data.journey.JourneyTimelineSegment

/** What the watch app tells the phone. */
sealed interface WatchEvent {
    /** The watch app has opened and holds nothing yet. */
    data object Started : WatchEvent

    /** The watch app is still open; sent every [WatchMessages.ALIVE_EVERY_SECONDS]. */
    data object Alive : WatchEvent

    data object Closed : WatchEvent

    /** "Stop following" was chosen on the watch for [journeyId]. */
    data class Stop(
        val journeyId: String,
    ) : WatchEvent
}

/**
 * The messages between the phone and the watch app, which `garminApp` reads and writes by the same
 * keys. Only strings, 32-bit whole numbers and lists of them: times are seconds since 1970, absent
 * texts are empty and absent times 0. Change [VERSION] with the format; the watch app asks for an
 * update of whichever side is older.
 */
object WatchMessages {
    const val VERSION = 1

    /** How often the open watch app says it is. */
    const val ALIVE_EVERY_SECONDS = 60

    const val TYPE = "t"
    const val TYPE_PLAN = "plan"
    const val TYPE_ALERT = "alert"
    const val TYPE_END = "end"
    const val TYPE_NONE = "none"
    const val TYPE_STARTED = "start"
    const val TYPE_ALIVE = "alive"
    const val TYPE_CLOSED = "bye"
    const val TYPE_STOP = "stop"

    const val KEY_VERSION = "v"
    const val KEY_JOURNEY = "id"
    const val KEY_TITLE = "title"
    const val KEY_TEXT = "text"
    const val KEY_ARRIVAL = "arr"
    const val KEY_FRAMES = "f"
    const val KEY_SEGMENTS = "s"
    const val KEY_ALERTS = "a"
    const val KEY_ALERT = "alert"
    const val KEY_ARRIVED = "arrived"

    private const val MILLIS_PER_SECOND = 1_000L
    private const val PHASE_ARRIVED = 3

    private fun seconds(millis: Long?): Int = millis?.let { (it / MILLIS_PER_SECOND).toInt() } ?: 0

    /**
     * The whole journey. Frames are `[from, phase, headline, detail, nextAt]`, segments
     * `[kind, start, end]`, alerts `[at, id, title, text]`.
     */
    fun plan(plan: WatchPlan): Map<String, Any> =
        mapOf(
            TYPE to TYPE_PLAN,
            KEY_VERSION to VERSION,
            KEY_JOURNEY to plan.journeyId,
            KEY_TITLE to plan.title,
            KEY_ARRIVAL to seconds(plan.arrivalAtMillis),
            KEY_FRAMES to
                plan.frames.map { listOf(seconds(it.fromMillis), phaseCode(it.phase), it.headline, it.detail, seconds(it.nextAtMillis)) },
            KEY_SEGMENTS to plan.segments.map { listOf(segmentCode(it.kind), seconds(it.startMillis), seconds(it.endMillis)) },
            KEY_ALERTS to plan.alerts.map { listOf(seconds(it.atMillis), it.id, it.title, it.text.orEmpty()) },
        )

    /** An alert to show now: one the plan does not hold (a faster route, a missed ride), or one that is due. */
    fun alert(
        journeyId: String,
        alertId: String,
        title: String,
        text: String?,
    ): Map<String, Any> =
        mapOf(
            TYPE to TYPE_ALERT,
            KEY_VERSION to VERSION,
            KEY_JOURNEY to journeyId,
            KEY_ALERT to alertId,
            KEY_TITLE to title,
            KEY_TEXT to text.orEmpty(),
        )

    /** The journey is no longer followed: it [arrived], or was stopped. */
    fun end(
        journeyId: String,
        arrived: Boolean,
    ): Map<String, Any> = mapOf(TYPE to TYPE_END, KEY_VERSION to VERSION, KEY_JOURNEY to journeyId, KEY_ARRIVED to if (arrived) 1 else 0)

    /** No journey is followed, for a watch app opened by hand. */
    fun none(): Map<String, Any> = mapOf(TYPE to TYPE_NONE, KEY_VERSION to VERSION)

    /** What a message from the watch app says; null for one this version does not know. */
    fun parse(message: Map<String, Any?>): WatchEvent? =
        when (message[TYPE]) {
            TYPE_STARTED -> WatchEvent.Started
            TYPE_ALIVE -> WatchEvent.Alive
            TYPE_CLOSED -> WatchEvent.Closed
            TYPE_STOP -> (message[KEY_JOURNEY] as? String)?.let { WatchEvent.Stop(it) }
            else -> null
        }

    fun phaseCode(phase: JourneyPhase): Int =
        when (phase) {
            JourneyPhase.WAITING -> 0
            JourneyPhase.RIDING -> 1
            JourneyPhase.WALKING -> 2
            JourneyPhase.ARRIVED -> PHASE_ARRIVED
        }

    fun segmentCode(kind: JourneyTimelineSegment.Kind): Int =
        when (kind) {
            JourneyTimelineSegment.Kind.WALK -> 0
            JourneyTimelineSegment.Kind.WAIT -> 1
            JourneyTimelineSegment.Kind.RIDE -> 2
        }
}

package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.TripInfo
import kotlinx.serialization.Serializable

/** How a followed journey knows where the user is. Chosen once, in the permission rationale. */
enum class JourneyTrackingMode {
    /** Live Entur times only; the device's position is never read. */
    TIME_ONLY,

    /**
     * Live times, and the device's position for the whole journey, also with the app closed (a
     * foreground service on Android, the location background mode on iOS), so a missed ride is
     * noticed. On iOS that also keeps the app, and so the alerts, following delays.
     */
    LOCATION,
    ;

    val usesLocation: Boolean get() = this == LOCATION
}

/**
 * A route the user asked to be notified about, as saved so it survives the app being killed and can
 * be reopened from the board. [legStops] holds the last stops fetched per leg index, so alerts keep
 * following the last known live times while offline.
 */
@Serializable
data class TrackedJourney(
    val id: String,
    val trip: TripInfo,
    val mode: JourneyTrackingMode,
    val startedAtMillis: Long,
    val legStops: Map<Int, List<JourneyStop>> = emptyMap(),
    /** [JourneyAlert.id]s already shown, so a restart never repeats one. */
    val firedAlertIds: Set<String> = emptySet(),
    /** Set by "Skip next alerts": the journey stays followed and saved, but shows nothing more. */
    val muted: Boolean = false,
    /** When the journey ended (arrived or stopped); null while it is being followed. */
    val endedAtMillis: Long? = null,
)

/** One notification about a followed journey. [id] is stable across refreshes, for dedupe. */
sealed interface JourneyAlert {
    val id: String

    /**
     * The vehicle of leg [legIndex] leaves in a minute, at [departsAtMillis]. Shown as that clock
     * time, not as "in 1 minute": a notification is not updated once posted, and may be read later.
     */
    data class DepartingSoon(
        val legIndex: Int,
        val lineName: String,
        val destination: String?,
        val stopName: String?,
        val platform: String?,
        val departsAtMillis: Long,
    ) : JourneyAlert {
        override val id get() = "depart-$legIndex"
    }

    /** The stop after the current one is where leg [legIndex] gets off. */
    data class NextStopIsYours(
        val legIndex: Int,
        val stopName: String,
    ) : JourneyAlert {
        override val id get() = "next-stop-$legIndex"
    }

    /**
     * Leg [legIndex] has arrived; change to [lineName], which leaves at [departsAtMillis]. A clock
     * time rather than minutes from now, which would go stale in a notification that stays up.
     */
    data class Transfer(
        val legIndex: Int,
        val lineName: String,
        val platform: String?,
        val departsAtMillis: Long,
    ) : JourneyAlert {
        override val id get() = "transfer-$legIndex"
    }

    /**
     * Time to walk to [name]. When [isFinal] it is the walk to the destination, and the alert offers
     * directions to [latitude], [longitude].
     */
    data class WalkTo(
        val legIndex: Int,
        val name: String,
        val minutes: Int,
        val isFinal: Boolean,
        val latitude: Double?,
        val longitude: Double?,
    ) : JourneyAlert {
        override val id get() = "walk-$legIndex"

        /** Whether the alert offers "Navigate me". */
        val canNavigate: Boolean get() = isFinal && latitude != null && longitude != null
    }

    /** The device is far from where the journey should be: the user probably missed it. */
    data object MissedJourney : JourneyAlert {
        override val id get() = "missed"
    }

    data class Arrived(
        val destinationName: String,
    ) : JourneyAlert {
        override val id get() = "arrived"
    }
}

/** An alert and when it is due. */
data class ScheduledAlert(
    val alert: JourneyAlert,
    val atMillis: Long,
)

/** Where a followed journey is now: the ongoing notification and the Live Activity show this. */
data class JourneyProgress(
    val journeyId: String,
    val destinationName: String,
    val phase: JourneyPhase,
    /** The line ridden or waited for; null while walking. */
    val lineName: String?,
    /** The stop or place the current step heads for. */
    val targetName: String?,
    /**
     * When the current step's next moment comes, to count down to: departure while waiting or
     * walking to a ride, getting off while riding, and the arrival on the last walk.
     */
    val nextAtMillis: Long?,
    val arrivalAtMillis: Long?,
    /** Stops left on the current ride; null when not riding or not known. */
    val stopsLeft: Int?,
    val mode: JourneyTrackingMode,
    /** True when location was wanted but is not available, so the journey follows times only. */
    val locationLost: Boolean,
    val muted: Boolean,
    /** When this was worked out. */
    val nowMillis: Long,
    /** What the vehicle waited for shows on its front ("Vestli"); null when not waiting or not known. */
    val towards: String?,
    /** The journey's steps with their live times, from the first to the arrival. */
    val steps: List<JourneyStep>,
    /** Which of [steps] the journey is on; null once arrived. */
    val currentStepIndex: Int?,
    /** What to say under the headline, most important first; see [JourneyDetail]. */
    val details: List<JourneyDetail>,
    /** The journey from its start to the arrival, waits included, for a progress bar. */
    val timeline: List<JourneyTimelineSegment>,
) {
    /** The steps still to come after the current one. */
    val upcomingSteps: List<JourneyStep>
        get() = currentStepIndex?.let { steps.drop(it + 1) }.orEmpty()

    /**
     * Whole minutes until [nextAtMillis], rounded up so "1 min" lasts until the moment itself; null
     * once arrived, without a next moment, or when it has passed.
     */
    val minutesToNext: Int?
        get() {
            val next = nextAtMillis?.takeIf { phase != JourneyPhase.ARRIVED } ?: return null
            val left = next - nowMillis
            if (left <= 0) return null
            return ((left + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
        }

    /** When the journey starts; null without times. */
    val startAtMillis: Long?
        get() = timeline.firstOrNull()?.startMillis

    /** How far along the journey is, from 0 at its start to 1 on arrival; 0 without times. */
    val elapsedFraction: Double
        get() {
            val start = timeline.firstOrNull()?.startMillis ?: return 0.0
            val end = timeline.last().endMillis
            if (end <= start) return if (nowMillis >= end) 1.0 else 0.0
            return ((nowMillis - start).toDouble() / (end - start)).coerceIn(0.0, 1.0)
        }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

enum class JourneyPhase {
    WAITING,
    RIDING,
    WALKING,
    ARRIVED,
}

/** One step of a journey as its progress shows it: a ride, or a walk that takes time. */
data class JourneyStep(
    val legIndex: Int,
    val isRide: Boolean,
    /** The line; null on a walk. */
    val lineName: String?,
    /** What the vehicle shows on its front. */
    val towards: String?,
    /** The boarding platform's code ("2", "B"). */
    val platform: String?,
    val fromName: String?,
    /** Where the step ends: the stop to get off at, or the destination on the last step. */
    val toName: String?,
    val startMillis: Long,
    val endMillis: Long,
    /** Whole minutes a ride leaves after its timetable, from live times; 0 when on time or unknown. */
    val delayMinutes: Int,
)

/**
 * One fact under the progress' headline. [JourneyProgress.details] are in the order to show them,
 * so the first ones fit a notification's collapsed line; each platform words them.
 */
sealed interface JourneyDetail {
    /** "Leaves 09:08", with ", 2 min late" when [delayMinutes] is above 0. */
    data class Departs(
        val atMillis: Long,
        val delayMinutes: Int,
    ) : JourneyDetail

    /** "Platform 2". */
    data class Platform(
        val platform: String,
    ) : JourneyDetail

    /** "From Jernbanetorget": where to board. */
    data class From(
        val stopName: String,
    ) : JourneyDetail

    /** "Next stop: Nationaltheatret", while riding with more than one stop left. */
    data class NextStop(
        val stopName: String,
    ) : JourneyDetail

    /** "2 stops left". */
    data class StopsLeft(
        val count: Int,
    ) : JourneyDetail

    /** "Then 20 at 09:18, platform B": the next ride after this step. */
    data class Change(
        val lineName: String,
        val platform: String?,
        val atMillis: Long,
    ) : JourneyDetail

    /** "Arrives 09:31": when the journey reaches its destination. */
    data class Arrival(
        val atMillis: Long,
    ) : JourneyDetail

    /** "Arrived 09:31", once it has. */
    data class Arrived(
        val atMillis: Long,
    ) : JourneyDetail

    /** Location was wanted but is off, so the journey follows times only. */
    data object LocationLost : JourneyDetail

    /** "Skip next alerts" was chosen. */
    data object Muted : JourneyDetail
}

/** A stretch of the journey's time, for a progress bar split by what the user does in it. */
data class JourneyTimelineSegment(
    val kind: Kind,
    val startMillis: Long,
    val endMillis: Long,
) {
    enum class Kind {
        WALK,
        WAIT,
        RIDE,
    }

    val lengthMillis: Long get() = endMillis - startMillis
}

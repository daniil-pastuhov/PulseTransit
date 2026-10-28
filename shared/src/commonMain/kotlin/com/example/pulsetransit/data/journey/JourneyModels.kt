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
    /** When the current step's next moment comes: departure while waiting, arrival while riding or walking. */
    val nextAtMillis: Long?,
    val arrivalAtMillis: Long?,
    /** Stops left on the current ride; null when not riding or not known. */
    val stopsLeft: Int?,
    val mode: JourneyTrackingMode,
    /** True when location was wanted but is not available, so the journey follows times only. */
    val locationLost: Boolean,
    val muted: Boolean,
)

enum class JourneyPhase {
    WAITING,
    RIDING,
    WALKING,
    ARRIVED,
}

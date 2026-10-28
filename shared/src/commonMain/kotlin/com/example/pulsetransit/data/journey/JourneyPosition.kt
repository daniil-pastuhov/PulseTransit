package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.distanceMeters
import com.example.pulsetransit.data.isShown
import com.example.pulsetransit.data.isTransit
import com.example.pulsetransit.data.journey.JourneyAlertPlanner.LegTime
import com.example.pulsetransit.data.journey.JourneyAlertPlanner.endMillis
import com.example.pulsetransit.data.journey.JourneyAlertPlanner.legTimes
import com.example.pulsetransit.data.legEndName
import com.example.pulsetransit.data.parseIsoToMillis

/**
 * Where a followed journey is: the step it is on, for the progress notification and Live Activity,
 * and whether the device is where the journey's vehicle should be. Pure, like [JourneyAlertPlanner],
 * from the same live leg times.
 */
object JourneyPosition {
    /** How far the device may be from where the vehicle should be before the user counts as off it. */
    const val OFF_JOURNEY_METERS = 700.0

    /** The vehicle must have left this long ago before being off it means anything. */
    const val OFF_JOURNEY_GRACE_MILLIS = 60_000L

    /**
     * Whether a fix at [latitude], [longitude] is too far from where [journey]'s vehicle should be at
     * [nowMillis]: off the ride, so the user probably missed it. The vehicle's position is the point
     * between the two stops whose times bracket now, in proportion to the time. Null when it cannot
     * be told: not on a ride, or no stop positions.
     */
    fun isOffJourney(
        journey: TrackedJourney,
        nowMillis: Long,
        latitude: Double,
        longitude: Double,
    ): Boolean? {
        val times = legTimes(journey)
        val ride =
            journey.trip.legs.indices.firstOrNull { index ->
                val time = times[index]
                journey.trip.legs[index].isTransit &&
                    time != null &&
                    nowMillis >= time.startMillis + OFF_JOURNEY_GRACE_MILLIS &&
                    nowMillis < time.endMillis
            } ?: return null
        val expected = expectedPosition(journey, ride, times[ride]!!, nowMillis) ?: return null
        return distanceMeters(latitude, longitude, expected.first, expected.second) > OFF_JOURNEY_METERS
    }

    private fun expectedPosition(
        journey: TrackedJourney,
        ride: Int,
        time: LegTime,
        nowMillis: Long,
    ): Pair<Double, Double>? {
        val leg = journey.trip.legs[ride]
        val points =
            journey.legStops[ride]
                .orEmpty()
                .mapNotNull { stop ->
                    val lat = stop.latitude ?: return@mapNotNull null
                    val lon = stop.longitude ?: return@mapNotNull null
                    val at = parseIsoToMillis(stop.expectedTime) ?: return@mapNotNull null
                    TimedPoint(at, lat, lon)
                }.ifEmpty {
                    listOfNotNull(
                        leg.fromLatitude?.let { lat -> leg.fromLongitude?.let { TimedPoint(time.startMillis, lat, it) } },
                        leg.toLatitude?.let { lat -> leg.toLongitude?.let { TimedPoint(time.endMillis, lat, it) } },
                    )
                }
        if (points.size < 2) return null
        val after = points.indexOfFirst { it.atMillis > nowMillis }
        // Past every point it is at the last stop, before the first at the first; otherwise between
        // the two around now.
        val a = points[if (after < 0) points.lastIndex else (after - 1).coerceAtLeast(0)]
        val b = points[if (after < 0) points.lastIndex else after]
        val span = (b.atMillis - a.atMillis).coerceAtLeast(1)
        val f = ((nowMillis - a.atMillis).toDouble() / span).coerceIn(0.0, 1.0)
        return (a.lat + (b.lat - a.lat) * f) to (a.lon + (b.lon - a.lon) * f)
    }

    private data class TimedPoint(
        val atMillis: Long,
        val lat: Double,
        val lon: Double,
    )

    /** Where [journey] is at [nowMillis]. */
    fun progress(
        journey: TrackedJourney,
        nowMillis: Long,
        locationLost: Boolean,
    ): JourneyProgress {
        val trip = journey.trip
        val times = legTimes(journey)
        val arrival = endMillis(journey)
        val current =
            trip.legs.indices.firstOrNull { index ->
                val leg = trip.legs[index]
                val time = times[index]
                (leg.isTransit || leg.isShown) && time != null && time.endMillis > nowMillis
            }

        fun progress(
            phase: JourneyPhase,
            line: String?,
            target: String?,
            nextAt: Long?,
            stopsLeft: Int?,
        ) = JourneyProgress(
            journeyId = journey.id,
            destinationName = trip.destinationName,
            phase = phase,
            lineName = line,
            targetName = target,
            nextAtMillis = nextAt,
            arrivalAtMillis = arrival,
            stopsLeft = stopsLeft,
            mode = journey.mode,
            locationLost = locationLost,
            muted = journey.muted,
        )
        if (current == null) return progress(JourneyPhase.ARRIVED, null, trip.destinationName, arrival, null)
        val leg = trip.legs[current]
        val time = times[current]!!
        return when {
            !leg.isTransit -> progress(JourneyPhase.WALKING, null, legEndName(trip, current), time.endMillis, null)
            nowMillis < time.startMillis -> progress(JourneyPhase.WAITING, leg.lineName, leg.fromName, time.startMillis, null)
            else -> {
                val stopsLeft =
                    journey.legStops[current]
                        ?.count { stop -> (parseIsoToMillis(stop.expectedTime) ?: 0) > nowMillis }
                progress(JourneyPhase.RIDING, leg.lineName, legEndName(trip, current), time.endMillis, stopsLeft)
            }
        }
    }
}

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
        val steps = steps(journey, times)
        val currentStep = steps.indexOfFirst { it.endMillis > nowMillis }.takeIf { it >= 0 }
        val notes = listOfNotNull(JourneyDetail.LocationLost.takeIf { locationLost }, JourneyDetail.Muted.takeIf { journey.muted })

        fun progress(
            phase: JourneyPhase,
            step: JourneyStep?,
            target: String?,
            nextAt: Long?,
            stopsLeft: Int?,
            details: List<JourneyDetail>,
        ) = JourneyProgress(
            journeyId = journey.id,
            destinationName = trip.destinationName,
            phase = phase,
            lineName = step?.lineName,
            targetName = target,
            nextAtMillis = nextAt,
            arrivalAtMillis = arrival,
            stopsLeft = stopsLeft,
            mode = journey.mode,
            locationLost = locationLost,
            muted = journey.muted,
            nowMillis = nowMillis,
            towards = step?.towards?.takeIf { phase == JourneyPhase.WAITING },
            steps = steps,
            currentStepIndex = currentStep,
            details = details + notes,
            timeline = timeline(steps),
        )
        if (currentStep == null) {
            return progress(
                JourneyPhase.ARRIVED,
                null,
                trip.destinationName,
                arrival,
                null,
                listOfNotNull(arrival?.let { JourneyDetail.Arrived(it) }),
            )
        }
        val step = steps[currentStep]
        val arrivalDetail = listOfNotNull(arrival?.let { JourneyDetail.Arrival(it) })
        // The next ride after this step: where to change, or, on a walk to it, what to catch.
        val nextRide = steps.drop(currentStep + 1).firstOrNull { it.isRide }
        val change = listOfNotNull(nextRide?.let { JourneyDetail.Change(it.lineName.orEmpty(), it.platform, it.startMillis) })
        return when {
            !step.isRide -> {
                // On the way to a ride, what counts is when it leaves.
                val nextAt = steps.getOrNull(currentStep + 1)?.takeIf { it.isRide }?.startMillis ?: step.endMillis
                progress(JourneyPhase.WALKING, step, step.toName, nextAt, null, change + arrivalDetail)
            }
            nowMillis < step.startMillis ->
                progress(JourneyPhase.WAITING, step, step.fromName, step.startMillis, null, waitingDetails(step) + arrivalDetail)
            else -> {
                // The ride's stops still ahead, the one to get off at last.
                val ahead =
                    journey.legStops[step.legIndex]
                        ?.filter { stop -> (parseIsoToMillis(stop.expectedTime) ?: 0) > nowMillis }
                val riding =
                    listOfNotNull(
                        ahead?.takeIf { it.size > 1 }?.let { JourneyDetail.NextStop(it.first().name) },
                        ahead?.let { JourneyDetail.StopsLeft(it.size) },
                    )
                progress(JourneyPhase.RIDING, step, step.toName, step.endMillis, ahead?.size, riding + change + arrivalDetail)
            }
        }
    }

    /** When and where the ride waited for leaves. */
    private fun waitingDetails(step: JourneyStep): List<JourneyDetail> =
        listOfNotNull(
            JourneyDetail.Departs(step.startMillis, step.delayMinutes),
            step.platform?.let { JourneyDetail.Platform(it) },
            // The headline names the vehicle's destination then, so this says where to board.
            step.fromName?.takeIf { step.towards != null }?.let { JourneyDetail.From(it) },
        )

    /** The legs the progress shows, with their live times: every ride, and walks that take time. */
    private fun steps(
        journey: TrackedJourney,
        times: List<LegTime?>,
    ): List<JourneyStep> {
        val trip = journey.trip
        return trip.legs.indices.mapNotNull { index ->
            val leg = trip.legs[index]
            val time = times[index]
            if (!leg.isShown || time == null) return@mapNotNull null
            val aimedStart = leg.aimedStartTime?.let(::parseIsoToMillis)
            JourneyStep(
                legIndex = index,
                isRide = leg.isTransit,
                lineName = leg.lineName,
                towards = leg.destination?.takeIf { leg.isTransit },
                platform = leg.platform?.takeIf { leg.isTransit },
                fromName = leg.fromName,
                toName = legEndName(trip, index),
                startMillis = time.startMillis,
                endMillis = time.endMillis,
                delayMinutes =
                    if (leg.isTransit && aimedStart != null) {
                        ((time.startMillis - aimedStart) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)
                    } else {
                        0
                    },
            )
        }
    }

    /** [steps] end to end, with a wait wherever one starts after the one before it ends. */
    private fun timeline(steps: List<JourneyStep>): List<JourneyTimelineSegment> {
        val segments = mutableListOf<JourneyTimelineSegment>()
        steps.forEach { step ->
            val previousEnd = segments.lastOrNull()?.endMillis
            if (previousEnd != null && step.startMillis > previousEnd) {
                segments += JourneyTimelineSegment(JourneyTimelineSegment.Kind.WAIT, previousEnd, step.startMillis)
            }
            // A walk that runs into the next ride's time (a delay moved it) starts where the ride before ended.
            val start = previousEnd?.let { maxOf(it, step.startMillis) } ?: step.startMillis
            val kind = if (step.isRide) JourneyTimelineSegment.Kind.RIDE else JourneyTimelineSegment.Kind.WALK
            segments += JourneyTimelineSegment(kind, start, maxOf(start, step.endMillis))
        }
        return segments
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}

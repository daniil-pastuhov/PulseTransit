package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.isShown
import com.example.pulsetransit.data.isTransit
import com.example.pulsetransit.data.legEndName
import com.example.pulsetransit.data.parseIsoToMillis

/**
 * What a followed journey says and when. Pure: every answer comes from the saved journey, whose
 * stops carry the latest live times, so a delay moves every alert after it.
 *
 * - Each ride: "leaves in 1 minute" a minute before it does, and "next stop is yours" as it leaves
 *   the stop before the one to get off at.
 * - Between rides: where to change and how long there is.
 * - A walk to the first ride, and the walk to the destination, which offers directions.
 * - Arrival.
 */
object JourneyAlertPlanner {
    const val DEPARTING_SOON_MILLIS = 60_000L

    /** Without the ride's stops, "next stop is yours" comes this long before it arrives. */
    const val NEXT_STOP_FALLBACK_MILLIS = 2 * 60_000L

    /** Alerts this late are dropped rather than shown, e.g. after the app was stopped for a while. */
    const val STALE_AFTER_MILLIS = 10 * 60_000L

    private const val MILLIS_PER_MINUTE = 60_000L

    /** Boarding, getting off, and at least one stop between them. */
    private const val MIN_STOPS_WITH_ONE_BETWEEN = 3

    /** When each leg starts and ends, from live stop times where there are some. */
    data class LegTime(
        val startMillis: Long,
        val endMillis: Long,
    )

    /**
     * [journey]'s legs' times. A ride uses its live stops; a walk moves with the ride before it, or,
     * before the first ride, with that ride's delay, so a late bus never has the user leave early.
     * Null for a leg without times.
     */
    fun legTimes(journey: TrackedJourney): List<LegTime?> {
        val legs = journey.trip.legs
        val times = arrayOfNulls<LegTime>(legs.size)

        // How late the first ride after leg [index] runs, from its live times; 0 when unknown.
        fun delayOfNextRide(index: Int): Long {
            val next = (index + 1 until legs.size).firstOrNull { legs[it].isTransit } ?: return 0
            val live = times[next] ?: return 0
            val planned = plannedTime(legs[next]) ?: return 0
            return live.startMillis - planned.startMillis
        }
        legs.forEachIndexed { index, leg ->
            if (leg.isTransit) times[index] = transitTime(leg, journey.legStops[index])
        }
        legs.forEachIndexed { index, leg ->
            if (leg.isTransit) return@forEachIndexed
            val planned = plannedTime(leg) ?: return@forEachIndexed
            val duration = planned.endMillis - planned.startMillis
            val previous = times.getOrNull(index - 1)
            val start =
                when {
                    previous != null -> previous.endMillis
                    else -> planned.startMillis + delayOfNextRide(index)
                }
            times[index] = LegTime(start, start + duration)
        }
        return times.toList()
    }

    private fun plannedTime(leg: TripLeg): LegTime? {
        val start = leg.expectedStartTime?.let(::parseIsoToMillis) ?: return null
        val end = leg.expectedEndTime?.let(::parseIsoToMillis) ?: return null
        return LegTime(start, end)
    }

    private fun transitTime(
        leg: TripLeg,
        stops: List<JourneyStop>?,
    ): LegTime? {
        val planned = plannedTime(leg)
        val start = stops?.firstOrNull()?.let { parseIsoToMillis(it.expectedTime) } ?: planned?.startMillis ?: return null
        val end = stops?.lastOrNull()?.let { parseIsoToMillis(it.expectedTime) } ?: planned?.endMillis ?: return null
        return LegTime(start, end)
    }

    /** Every alert of [journey] with its time, in time order, fired or not. */
    fun schedule(journey: TrackedJourney): List<ScheduledAlert> {
        val legs = journey.trip.legs
        val times = legTimes(journey)
        val rides = legs.indices.filter { legs[it].isTransit }
        val alerts =
            legs.indices.flatMap { index ->
                val time = times[index] ?: return@flatMap emptyList()
                if (legs[index].isTransit) {
                    rideAlerts(journey, index, time, times, rides)
                } else {
                    listOfNotNull(walkAlert(journey, index, time, rides))
                }
            }
        val arrival =
            times.lastOrNull()?.let { last ->
                val name = journey.trip.destinationName.ifBlank { legs.lastOrNull()?.toName.orEmpty() }
                ScheduledAlert(JourneyAlert.Arrived(name), last.endMillis)
            }
        return (alerts + listOfNotNull(arrival)).sortedBy { it.atMillis }
    }

    /** A ride's alerts: leaving in a minute, the next stop being the one, and the change after it. */
    private fun rideAlerts(
        journey: TrackedJourney,
        index: Int,
        time: LegTime,
        times: List<LegTime?>,
        rides: List<Int>,
    ): List<ScheduledAlert> {
        val trip = journey.trip
        val leg = trip.legs[index]
        val stops = journey.legStops[index].orEmpty()
        val departing =
            ScheduledAlert(
                JourneyAlert.DepartingSoon(
                    legIndex = index,
                    lineName = leg.lineName.orEmpty(),
                    destination = leg.destination,
                    stopName = leg.fromName,
                    platform = leg.platform,
                ),
                time.startMillis - DEPARTING_SOON_MILLIS,
            )
        val nextStop =
            (stops.lastOrNull()?.name ?: legEndName(trip, index))?.let { getOffAt ->
                ScheduledAlert(JourneyAlert.NextStopIsYours(index, getOffAt), nextStopAt(stops, time))
            }
        val nextRide = rides.firstOrNull { it > index }
        val transfer =
            nextRide?.let { times[it] }?.let { nextTime ->
                val minutes = ((nextTime.startMillis - time.endMillis) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)
                val next = trip.legs[nextRide]
                ScheduledAlert(JourneyAlert.Transfer(index, next.lineName.orEmpty(), next.platform, minutes), time.endMillis)
            }
        return listOfNotNull(departing, nextStop, transfer)
    }

    /**
     * The walk to the first ride, a minute before it should start, and the walk to the destination,
     * which offers directions. A walk between rides is the transfer alert's to tell.
     */
    private fun walkAlert(
        journey: TrackedJourney,
        index: Int,
        time: LegTime,
        rides: List<Int>,
    ): ScheduledAlert? {
        val leg = journey.trip.legs[index]
        val isFirst = rides.none { it < index }
        val isFinal = index == journey.trip.legs.lastIndex
        if (!leg.isShown || !(isFirst || isFinal)) return null
        val alert =
            JourneyAlert.WalkTo(
                legIndex = index,
                name = legEndName(journey.trip, index).orEmpty(),
                minutes = leg.durationMinutes,
                isFinal = isFinal,
                latitude = leg.toLatitude,
                longitude = leg.toLongitude,
            )
        return ScheduledAlert(alert, if (isFirst) time.startMillis - DEPARTING_SOON_MILLIS else time.startMillis)
    }

    /**
     * "Next stop is yours" comes as the vehicle leaves the stop before the one to get off at; on a
     * one-stop ride that is boarding, and without the stops, shortly before arrival.
     */
    private fun nextStopAt(
        stops: List<JourneyStop>,
        time: LegTime,
    ): Long =
        when {
            stops.size >= MIN_STOPS_WITH_ONE_BETWEEN ->
                parseIsoToMillis(stops[stops.size - 2].expectedTime)
                    ?: (time.endMillis - NEXT_STOP_FALLBACK_MILLIS)
            stops.size == 2 -> time.startMillis
            else -> (time.endMillis - NEXT_STOP_FALLBACK_MILLIS).coerceAtLeast(time.startMillis)
        }

    /** The alerts due at [nowMillis] that have not fired yet, split into those to show and those too late. */
    fun dueAlerts(
        journey: TrackedJourney,
        nowMillis: Long,
    ): DueAlerts {
        val due = schedule(journey).filter { it.atMillis <= nowMillis && it.alert.id !in journey.firedAlertIds }
        val show =
            if (journey.muted) {
                emptyList()
            } else {
                due.filter { nowMillis - it.atMillis <= STALE_AFTER_MILLIS }.map { it.alert }
            }
        return DueAlerts(show = show, consumedIds = due.map { it.alert.id }.toSet())
    }

    data class DueAlerts(
        val show: List<JourneyAlert>,
        /** Every id now due, shown or not, to be marked fired. */
        val consumedIds: Set<String>,
    )

    /** The alerts still to come after [nowMillis], for platforms that schedule them in advance. */
    fun upcomingAlerts(
        journey: TrackedJourney,
        nowMillis: Long,
    ): List<ScheduledAlert> =
        if (journey.muted) {
            emptyList()
        } else {
            schedule(journey).filter { it.atMillis > nowMillis && it.alert.id !in journey.firedAlertIds }
        }

    /** When the journey is over: its last leg's end, or null without times. */
    fun endMillis(journey: TrackedJourney): Long? = legTimes(journey).lastOrNull { it != null }?.endMillis
}

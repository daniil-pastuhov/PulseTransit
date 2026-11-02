package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.PlaceKind
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.isTransit
import com.example.pulsetransit.data.parseIsoToMillis
import com.example.pulsetransit.data.tripKey

/**
 * The check for a faster way on, made once at each change of a followed journey: when the ride
 * before it reaches the transfer stop, the routes from there to the destination are searched again,
 * and one arriving [MIN_SAVING_MILLIS] sooner than the journey's own live arrival is offered as
 * [JourneyAlert.FasterRoute]. Pure, like [JourneyAlertPlanner]: the tracker does the searching.
 */
object FasterRoutes {
    /** How much sooner a route must arrive to be worth changing plans for. */
    const val MIN_SAVING_MILLIS = 3 * 60_000L

    /** How many routes on to ask Entur for. */
    const val CANDIDATES = 3

    /** Where a check searches from and to. */
    data class Search(
        /** The ride that has reached the transfer stop. */
        val rideIndex: Int,
        val latitude: Double,
        val longitude: Double,
        val destination: StopPlace,
    )

    /**
     * The check due at [nowMillis], if any: a ride with another after it has arrived, and the next
     * one has not left yet, so the user is at the transfer stop with a choice still to make. Null
     * when muted, when that transfer was checked already, or without the positions to search by.
     */
    fun dueSearch(
        journey: TrackedJourney,
        nowMillis: Long,
    ): Search? {
        val legs = journey.trip.legs
        val times = JourneyAlertPlanner.legTimes(journey)
        val rides = legs.indices.filter { legs[it].isTransit }
        val ride =
            rides
                .zipWithNext()
                .firstOrNull { (ride, next) ->
                    val arrived = times[ride]?.endMillis
                    val leaves = times[next]?.startMillis
                    arrived != null && leaves != null && ride !in journey.checkedTransfers && nowMillis in arrived until leaves
                }?.first
        val search =
            ride?.let { index ->
                val leg = legs[index]
                val lat = leg.toLatitude
                val lon = leg.toLongitude
                val destination = destination(journey)
                if (lat != null && lon != null && destination != null) Search(index, lat, lon, destination) else null
            }
        return search.takeUnless { journey.muted }
    }

    /**
     * Where the journey ends, as a place to route to: the end of its last leg. By its position
     * rather than its stop id, so a route arriving at another platform of the same stop counts.
     */
    fun destination(journey: TrackedJourney): StopPlace? {
        val last = journey.trip.legs.lastOrNull() ?: return null
        val lat = last.toLatitude ?: return null
        val lon = last.toLongitude ?: return null
        val name = journey.trip.destinationName.ifBlank { last.toName.orEmpty() }
        return StopPlace(id = "", name = name, latitude = lat, longitude = lon, kind = PlaceKind.PLACE)
    }

    /**
     * The route among [candidates] that arrives soonest, if that is at least [MIN_SAVING_MILLIS]
     * before [journey]'s live arrival. The rest of the journey itself is never one: it arrives when
     * the journey does.
     */
    fun fasterOf(
        journey: TrackedJourney,
        candidates: List<TripInfo>,
    ): TripInfo? {
        val current = JourneyAlertPlanner.endMillis(journey) ?: return null
        return candidates
            .mapNotNull { trip -> arrivalMillis(trip)?.let { trip to it } }
            .filter { (_, arrival) -> arrival <= current - MIN_SAVING_MILLIS }
            .minByOrNull { (_, arrival) -> arrival }
            ?.first
    }

    /** The alert offering [route] at the transfer after ride [rideIndex] of [journey]. */
    fun alert(
        journey: TrackedJourney,
        rideIndex: Int,
        route: TripInfo,
    ): JourneyAlert.FasterRoute? {
        val arrival = arrivalMillis(route) ?: return null
        val current = JourneyAlertPlanner.endMillis(journey) ?: return null
        val ride = route.legs.firstOrNull { it.isTransit }
        return JourneyAlert.FasterRoute(
            legIndex = rideIndex,
            lineName = ride?.lineName,
            stopName = ride?.fromName,
            platform = ride?.platform,
            departsAtMillis = ride?.expectedStartTime?.let(::parseIsoToMillis),
            arrivesAtMillis = arrival,
            minutesSooner = ((current - arrival) / MILLIS_PER_MINUTE).toInt(),
        )
    }

    /**
     * The journey that follows [journey]'s offered faster route instead, from [nowMillis]; null when
     * none is offered or its first ride has already left, so it can no longer be caught. Keeps the
     * mode and the destination's name, which a route searched from a position does not carry.
     */
    fun switched(
        journey: TrackedJourney,
        nowMillis: Long,
    ): TrackedJourney? {
        val route = journey.fasterRoute ?: return null
        val firstRide = route.legs.firstOrNull { it.isTransit }
        val leaves = firstRide?.expectedStartTime?.let(::parseIsoToMillis)
        if (leaves != null && leaves <= nowMillis) return null
        val trip = route.copy(destinationName = journey.trip.destinationName)
        return TrackedJourney(
            id = tripKey(trip),
            trip = trip,
            mode = journey.mode,
            startedAtMillis = nowMillis,
        )
    }

    private fun arrivalMillis(trip: TripInfo): Long? =
        (trip.expectedEndTime ?: trip.legs.lastOrNull()?.expectedEndTime)?.let(::parseIsoToMillis)

    private const val MILLIS_PER_MINUTE = 60_000L
}

/**
 * Follows the faster route offered at a transfer instead of the journey it was offered for, from a
 * notification's "Switch" action. The tracker takes the old journey's alerts down and follows the
 * new one. Does nothing when no route is offered, or it can no longer be caught.
 */
fun JourneyStore.switchToFasterRoute(nowMillis: Long) {
    val journey = active.value ?: return
    val switched = FasterRoutes.switched(journey, nowMillis) ?: return
    start(switched)
}

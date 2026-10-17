package com.example.pulsetransit.data

interface EnturRepository {
    suspend fun searchStopPlaces(query: String): List<StopPlace>

    /**
     * Stops, addresses, streets, points of interest and named places, for picking a trip
     * destination. Results near the focus point, when given, rank higher.
     */
    suspend fun searchPlaces(
        query: String,
        focusLat: Double? = null,
        focusLon: Double? = null,
    ): List<StopPlace>

    suspend fun getNearbyStopPlaces(
        lat: Double,
        lon: Double,
        radiusKm: Double,
    ): List<StopPlace>

    /**
     * Departures for a stop. If the request fails, the last good response is served from the
     * cache with its original fetch time attached; departures already due are dropped, since a
     * bus that has left is worse than no answer.
     *
     * [timeRangeSeconds] is how far ahead Entur looks. [limit] still caps the count, so a wider
     * window only changes the answer for stops with fewer than [limit] departures inside it.
     * With [quayIds], the limit applies to the selected quays' departures, not the whole stop's.
     */
    suspend fun getDepartures(
        stopId: String,
        limit: Int = 10,
        quayIds: Set<String>? = null,
        timeRangeSeconds: Int = DEFAULT_DEPARTURE_WINDOW_SECONDS,
    ): Sourced<List<Departure>>

    suspend fun getStopPlaceWithQuays(stopId: String): StopPlace?

    /**
     * Every stop of one trip, in order, for a departure's details. Not cached: the details ask
     * only while open, and an empty answer just leaves the route out.
     */
    suspend fun getJourneyStops(
        serviceJourneyId: String,
        serviceDate: String,
    ): List<JourneyStop>

    /**
     * As [getDepartures], but for the best route to a destination from a set of stop places, by
     * [options]. Routes are cached per option set, so changing them never serves an old route.
     */
    suspend fun getFastestTrip(
        fromIds: List<String>,
        to: StopPlace,
        options: RouteOptions = RouteOptions(),
    ): Sourced<TripInfo?>

    /**
     * As [getDepartures], but for the next [count] route alternatives to a destination from a
     * coordinate, best first by [options] (the first is the one to take). Empty when there are none.
     */
    suspend fun getTripOptions(
        lat: Double,
        lon: Double,
        to: StopPlace,
        count: Int,
        options: RouteOptions = RouteOptions(),
    ): Sourced<List<TripInfo>>

    companion object {
        /** Entur's own default for `estimatedCalls`. */
        const val DEFAULT_DEPARTURE_WINDOW_SECONDS = 24 * 60 * 60

        /**
         * Used on request for stops with nothing inside the default window. Kept short because the
         * board tells the user there is nothing in this span, and timetable data further out is
         * not reliable enough to claim that; three days still reaches past a weekend.
         */
        const val EXTENDED_DEPARTURE_WINDOW_DAYS = 3
        const val EXTENDED_DEPARTURE_WINDOW_SECONDS = EXTENDED_DEPARTURE_WINDOW_DAYS * 24 * 60 * 60
    }
}

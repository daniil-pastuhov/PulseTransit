package com.example.pulsetransit.data

interface EnturRepository {
    suspend fun searchStopPlaces(query: String): List<StopPlace>

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
     */
    suspend fun getDepartures(
        stopId: String,
        limit: Int = 10,
        quayIds: Set<String>? = null,
        timeRangeSeconds: Int = DEFAULT_DEPARTURE_WINDOW_SECONDS,
    ): Sourced<List<Departure>>

    suspend fun getStopPlaceWithQuays(stopId: String): StopPlace?

    /** As [getDepartures], but for the fastest route to a destination from a set of stop places. */
    suspend fun getFastestTrip(
        fromIds: List<String>,
        toId: String,
    ): Sourced<TripInfo?>

    /** As [getDepartures], but for the fastest route to a destination from a coordinate. */
    suspend fun getFastestTrip(
        lat: Double,
        lon: Double,
        toId: String,
    ): Sourced<TripInfo?>

    companion object {
        /** Entur's own default for `estimatedCalls`. */
        const val DEFAULT_DEPARTURE_WINDOW_SECONDS = 24 * 60 * 60

        /** Used on request for stops with nothing inside the default window. */
        const val EXTENDED_DEPARTURE_WINDOW_DAYS = 7
        const val EXTENDED_DEPARTURE_WINDOW_SECONDS = EXTENDED_DEPARTURE_WINDOW_DAYS * 24 * 60 * 60
    }
}

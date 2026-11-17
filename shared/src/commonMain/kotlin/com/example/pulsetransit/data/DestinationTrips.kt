package com.example.pulsetransit.data

/**
 * One destination's routes listed on their own, opened by tapping its card's title: more of them
 * and further ahead than the card has room for, in departure order. The first page is the board's
 * own trip query asked for [FIRST_PAGE] routes, refreshed with the board; "Later" pages ask the
 * same query to depart a minute after the last route listed ([laterPageTime]), up to [MAX_TRIPS].
 *
 * [trips] starts as the card's routes, so the list is never empty while the first page loads.
 * [failed] is set when Entur could not be reached and the cache had nothing either: the routes
 * already listed stay up. [cachedAtMillis] is when the routes shown were fetched, when they came
 * from the cache instead of Entur.
 */
data class DestinationTrips(
    val destination: StopPlace,
    val trips: List<TripInfo> = emptyList(),
    /** A first page (or its refresh) is on the way. */
    val isLoading: Boolean = true,
    /** A later page is on the way. */
    val isLoadingLater: Boolean = false,
    /** A first page has been answered at least once, so an empty [trips] means there are none. */
    val hasLoaded: Boolean = false,
    val failed: Boolean = false,
    val cachedAtMillis: Long? = null,
    /** Whether "Later" can ask for more: the last answer was live and still added routes. */
    val canLoadLater: Boolean = false,
) {
    /** The routes still to leave at [nowMillis]; one leaving this very minute is kept. */
    fun upcoming(nowMillis: Long): List<TripInfo> =
        trips.filter { trip -> parseIsoToMillis(trip.expectedStartTime)?.let { it >= nowMillis - DEPARTED_GRACE_MILLIS } ?: true }

    /** Entur answered and found no route at all. */
    val hasNoRoutes: Boolean get() = hasLoaded && !isLoading && !failed && trips.isEmpty()

    /** When the next later page departs from: a minute after the last route listed. */
    fun laterPageTime(): TripTime.DepartAt? =
        trips.mapNotNull { parseIsoToMillis(it.expectedStartTime) }.maxOrNull()?.let { TripTime.DepartAt(it + LATER_PAGE_STEP_MILLIS) }

    /**
     * After a first page [result]: its routes, then the ones listed after its last from later
     * pages, so a refresh updates the near routes without losing the far ones. A failure keeps
     * what is listed. [laterAllowed] is false for an "arrive by" search, which has no later.
     */
    fun withFirstPage(
        result: Sourced<List<TripInfo>>,
        laterAllowed: Boolean,
    ): DestinationTrips {
        if (result.failed && result.data.isEmpty()) {
            return copy(isLoading = false, hasLoaded = true, failed = true, canLoadLater = false)
        }
        val fresh = result.data
        val lastFresh = fresh.mapNotNull { parseIsoToMillis(it.expectedStartTime) }.maxOrNull()
        val freshKeys = fresh.map(::tripKey).toSet()
        val kept =
            if (lastFresh == null) {
                emptyList()
            } else {
                trips.filter { trip ->
                    tripKey(trip) !in freshKeys && (parseIsoToMillis(trip.expectedStartTime) ?: Long.MIN_VALUE) > lastFresh
                }
            }
        val merged = chronological(fresh + kept).take(MAX_TRIPS)
        return copy(
            trips = merged,
            isLoading = false,
            hasLoaded = true,
            failed = false,
            cachedAtMillis = result.fetchedAtMillis,
            canLoadLater = laterAllowed && !result.isFromCache && merged.isNotEmpty() && merged.size < MAX_TRIPS,
        )
    }

    /**
     * After a later page [result]: its new routes added in order. A page that adds nothing ends
     * "Later"; a failed one leaves it offered, to try again.
     */
    fun withLaterPage(result: Sourced<List<TripInfo>>): DestinationTrips {
        if (result.failed) return copy(isLoadingLater = false)
        val known = trips.map(::tripKey).toSet()
        val added = result.data.filter { tripKey(it) !in known }
        val merged = chronological(trips + added).take(MAX_TRIPS)
        return copy(
            trips = merged,
            isLoadingLater = false,
            canLoadLater = added.isNotEmpty() && !result.isFromCache && merged.size < MAX_TRIPS,
        )
    }

    companion object {
        /** Routes asked for at once: about three times what a phone's card shows. */
        const val FIRST_PAGE = 12

        /** Routes each "Later" asks for. */
        const val LATER_PAGE = 8

        /** The most the list grows to. */
        const val MAX_TRIPS = 48

        private const val LATER_PAGE_STEP_MILLIS = 60_000L
        private const val DEPARTED_GRACE_MILLIS = 60_000L

        /** [trips] in departure order, each journey once. */
        fun chronological(trips: List<TripInfo>): List<TripInfo> =
            trips
                .distinctBy(::tripKey)
                .sortedBy { parseIsoToMillis(it.expectedStartTime) ?: Long.MAX_VALUE }
    }
}

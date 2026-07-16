package com.example.pulsetransit.data

/**
 * A repository payload plus where it came from.
 *
 * [fetchedAtMillis] is null for a live network response and carries the original fetch time when
 * the cache was served instead, so callers never present stale data as if it were current.
 *
 * Note that a cache hit does not always mean something went wrong: for departures and trips it
 * means the network call failed, but for weather it is the normal path while MET's `Expires`
 * time has not passed. Callers decide what a cache hit means for them.
 */
data class Sourced<T>(
    val data: T,
    val fetchedAtMillis: Long? = null,
) {
    val isFromCache: Boolean get() = fetchedAtMillis != null

    companion object {
        fun <T> live(data: T): Sourced<T> = Sourced(data, null)

        fun <T> cached(
            data: T,
            fetchedAtMillis: Long,
        ): Sourced<T> = Sourced(data, fetchedAtMillis)
    }
}

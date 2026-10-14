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
 *
 * [failed] marks the remaining case: the network call failed and nothing was cached, so [data] is
 * empty because we don't know, not because there is nothing. Without it that result would look
 * exactly like a live response that happened to be empty.
 */
data class Sourced<T>(
    val data: T,
    val fetchedAtMillis: Long? = null,
    val failed: Boolean = false,
) {
    val isFromCache: Boolean get() = fetchedAtMillis != null

    companion object {
        fun <T> live(data: T): Sourced<T> = Sourced(data, null)

        fun <T> cached(
            data: T,
            fetchedAtMillis: Long,
        ): Sourced<T> = Sourced(data, fetchedAtMillis)

        /** The fetch failed and there was no cache to fall back on; [empty] stands in for data. */
        fun <T> failed(empty: T): Sourced<T> = Sourced(empty, null, failed = true)
    }
}

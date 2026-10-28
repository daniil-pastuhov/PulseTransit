package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.isTransit
import com.example.pulsetransit.data.legStops
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * Follows the store's active journey until it ends: refreshes its live stop times, reads the
 * device's location when the journey's mode allows, and sends [JourneyAlertPlanner]'s alerts and
 * progress to the [notifier]. Every decision is the planner's; this only keeps it fed.
 *
 * Run [run] on the main thread (see [JourneyStore]). It returns when no journey is active: after
 * arrival, or when the journey is stopped from the app or a notification.
 */
class JourneyTracker(
    private val store: JourneyStore,
    private val enturRepository: EnturRepository,
    private val notifier: JourneyNotifier,
    private val locationProvider: LocationProvider,
    private val clock: Clock = Clock.System,
) {
    private val _locationLost = MutableStateFlow(false)

    /** True when the journey's mode wants location but the permission was taken away. */
    val locationLost: StateFlow<Boolean> = _locationLost.asStateFlow()

    private data class Fix(
        val latitude: Double,
        val longitude: Double,
    )

    private var lastFix: Fix? = null
    private var offSinceMillis: Long? = null
    private var lastRefreshMillis: Long? = null

    private fun now() = clock.now().toEpochMilliseconds()

    suspend fun run() {
        coroutineScope {
            val location = launch { followLocation() }
            try {
                while (true) {
                    val journey = store.active.value ?: break
                    follow(journey.id)
                }
            } finally {
                location.cancel()
            }
        }
    }

    /** Follows journey [id] until it ends or another takes its place. */
    private suspend fun follow(id: String) {
        val journey = store.active.value?.takeIf { it.id == id } ?: return
        lastFix = null
        offSinceMillis = null
        lastRefreshMillis = null
        try {
            while (step(id)) {
                // Woken early by any change to the store: a stop or skip from a notification.
                withTimeoutOrNull(TICK_MILLIS) { store.active.drop(1).first() }
            }
        } finally {
            val replaced = store.active.value.let { it != null && it.id != id }
            notifier.end(journey, clearAlerts = replaced)
        }
    }

    /**
     * One tick of journey [id]: live times when due, alerts, progress and the schedule. False once
     * the journey is over or no longer followed.
     */
    private suspend fun step(id: String): Boolean {
        val journey = store.active.value?.takeIf { it.id == id } ?: return false
        val now = now()
        val lastRefresh = lastRefreshMillis
        if (lastRefresh == null || now - lastRefresh >= refreshIntervalMillis(journey, now)) {
            refreshStops(journey)
            lastRefreshMillis = now
        }
        store.active.value
            ?.takeIf { it.id == id }
            ?.let { checkOffJourney(it, now) }
        fireDue(id, now)
        val latest = store.active.value?.takeIf { it.id == id } ?: return false
        notifier.updateProgress(JourneyPosition.progress(latest, now, _locationLost.value))
        notifier.scheduleUpcoming(latest, JourneyAlertPlanner.upcomingAlerts(latest, now))
        val end = JourneyAlertPlanner.endMillis(latest)
        val over = end == null || now >= end + LINGER_AFTER_ARRIVAL_MILLIS
        if (over) store.finish(now)
        return !over
    }

    private fun fireDue(
        id: String,
        now: Long,
    ) {
        val journey = store.active.value?.takeIf { it.id == id } ?: return
        val due = JourneyAlertPlanner.dueAlerts(journey, now)
        if (due.consumedIds.isEmpty()) return
        store.update(id) { it.copy(firedAlertIds = it.firedAlertIds + due.consumedIds) }
        due.show.forEach { notifier.show(journey, it) }
    }

    /** Fetches the live stops of every ride; a failed fetch keeps the stops from before. */
    private suspend fun refreshStops(journey: TrackedJourney) {
        val fetched =
            journey.trip.legs
                .mapIndexedNotNull { index, leg ->
                    val serviceJourneyId = leg.serviceJourneyId ?: return@mapIndexedNotNull null
                    val serviceDate = leg.serviceDate ?: return@mapIndexedNotNull null
                    if (!leg.isTransit) return@mapIndexedNotNull null
                    val stops =
                        try {
                            enturRepository.getJourneyStops(serviceJourneyId, serviceDate)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (
                            @Suppress("TooGenericExceptionCaught") e: Exception,
                        ) {
                            e.printStackTrace()
                            emptyList()
                        }
                    stops.legStops(leg).takeIf { it.isNotEmpty() }?.let { index to it }
                }.toMap()
        if (fetched.isNotEmpty()) {
            store.update(journey.id) { it.copy(legStops = it.legStops + fetched) }
        }
    }

    /** Often while something is about to happen, rarely while nothing is. */
    private fun refreshIntervalMillis(
        journey: TrackedJourney,
        now: Long,
    ): Long {
        val next = JourneyAlertPlanner.upcomingAlerts(journey.copy(muted = false), now).firstOrNull()
        return if (next != null && next.atMillis - now <= SOON_MILLIS) FAST_REFRESH_MILLIS else SLOW_REFRESH_MILLIS
    }

    /**
     * Fires [JourneyAlert.MissedJourney] once the device has been off the journey for
     * [MISSED_AFTER_MILLIS], so one bad fix never does, and then mutes the journey: its later steps
     * no longer apply.
     */
    private fun checkOffJourney(
        journey: TrackedJourney,
        now: Long,
    ) {
        if (JourneyAlert.MissedJourney.id in journey.firedAlertIds) return
        // Updates come only after the device moves, so an old fix still means "still there" while
        // they run; it is cleared when they stop.
        val off = lastFix?.let { fix -> JourneyPosition.isOffJourney(journey, now, fix.latitude, fix.longitude) }
        if (off != true) {
            offSinceMillis = null
            return
        }
        val since = offSinceMillis ?: now.also { offSinceMillis = it }
        if (now - since < MISSED_AFTER_MILLIS) return
        store.update(journey.id) {
            it.copy(firedAlertIds = it.firedAlertIds + JourneyAlert.MissedJourney.id, muted = true)
        }
        if (!journey.muted) notifier.show(journey, JourneyAlert.MissedJourney)
    }

    private suspend fun followLocation() {
        val wanted = store.active.map { it?.mode?.usesLocation == true }.distinctUntilChanged()
        wanted.collectLatest { on ->
            lastFix = null
            if (!on) return@collectLatest
            locationProvider.locationUpdates(LocationAccuracy.PRECISE, MIN_DISTANCE_METERS).collect { result ->
                when (result) {
                    is LocationResult.Fix -> {
                        _locationLost.value = false
                        lastFix = Fix(result.latitude, result.longitude)
                    }
                    LocationResult.PermissionDenied -> {
                        _locationLost.value = true
                        lastFix = null
                    }
                    LocationResult.Unavailable -> Unit
                }
            }
        }
    }

    companion object {
        const val TICK_MILLIS = 15_000L
        const val FAST_REFRESH_MILLIS = 30_000L
        const val SLOW_REFRESH_MILLIS = 2 * 60_000L

        /** How near the next alert must be for [FAST_REFRESH_MILLIS]. */
        const val SOON_MILLIS = 10 * 60_000L

        /** The arrival notification stays this long before the journey is finished and saved to history. */
        const val LINGER_AFTER_ARRIVAL_MILLIS = 2 * 60_000L
        const val MISSED_AFTER_MILLIS = 90_000L
        const val MIN_DISTANCE_METERS = 25f
    }
}

package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.distanceMeters
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Follows the device for on-spot mode. It asks for a precise fix first and falls back to an
 * approximate one when none comes within [FIRST_FIX_TIMEOUT_MILLIS], whatever the platform reports
 * meanwhile. It reports every state
 * through [onLocation], but calls [onMoved] only once the device has gone [MIN_MOVE_METERS] from
 * where it last did, and at most once per [MOVE_THROTTLE_MILLIS] (with the latest fix by then), so
 * walking doesn't hammer Entur.
 */
internal class DeviceFollower(
    private val locationProvider: LocationProvider,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val onLocation: (OnSpotLocation) -> Unit,
    private val onMoved: (LocationResult.Fix) -> Unit,
) {
    companion object {
        const val MIN_MOVE_METERS = 20.0
        const val MOVE_THROTTLE_MILLIS = 15_000L
        const val FIRST_FIX_TIMEOUT_MILLIS = 15_000L
    }

    /** The newest fix, whether or not it was far enough to report as a move. */
    var latestFix: LocationResult.Fix? = null
        private set

    private var followJob: Job? = null
    private var moveJob: Job? = null
    private var lastMovedFix: LocationResult.Fix? = null
    private var lastMovedAtMillis: Long? = null

    /** Starts following, or starts again if already following. */
    fun start() {
        followJob?.cancel()
        followJob = scope.launch { follow() }
    }

    fun stop() {
        followJob?.cancel()
        followJob = null
        moveJob?.cancel()
        moveJob = null
    }

    /** Stops and forgets every fix, so the next start reports its first fix as a move. */
    fun reset() {
        stop()
        latestFix = null
        lastMovedFix = null
        lastMovedAtMillis = null
    }

    private suspend fun follow() {
        var located = false
        for (accuracy in LocationAccuracy.entries) {
            if (!located) onLocation(OnSpotLocation.Locating(accuracy))
            val denied =
                followAt(accuracy) { fix ->
                    located = true
                    onFix(fix, accuracy)
                }
            if (denied) {
                onLocation(OnSpotLocation.PermissionDenied)
                return
            }
        }
        if (!located) onLocation(OnSpotLocation.Unavailable)
    }

    /**
     * Collects [accuracy] updates until access is denied (true), or until no first fix arrives in
     * time (false). Once a fix has come it keeps following, through any spells without a signal,
     * for as long as it is collected.
     */
    private suspend fun followAt(
        accuracy: LocationAccuracy,
        onFix: (LocationResult.Fix) -> Unit,
    ): Boolean =
        coroutineScope {
            val updates = locationProvider.locationUpdates(accuracy, MIN_MOVE_METERS.toFloat()).produceIn(this)
            try {
                // A platform may report "unavailable" while it is still acquiring (iOS does for every
                // transient "location unknown"), so only the timeout ends the wait for a first fix.
                val first =
                    withTimeoutOrNull(FIRST_FIX_TIMEOUT_MILLIS) {
                        var result: LocationResult?
                        do {
                            result = updates.receiveCatching().getOrNull()
                        } while (result is LocationResult.Unavailable)
                        result
                    }
                when (first) {
                    is LocationResult.Fix -> onFix(first)
                    LocationResult.PermissionDenied -> return@coroutineScope true
                    else -> return@coroutineScope false // timed out, or the platform stopped the updates
                }
                for (result in updates) {
                    when (result) {
                        is LocationResult.Fix -> onFix(result)
                        LocationResult.PermissionDenied -> return@coroutineScope true
                        LocationResult.Unavailable -> Unit // keep the last fix until a new one comes
                    }
                }
                false
            } finally {
                updates.cancel()
            }
        }

    private fun onFix(
        fix: LocationResult.Fix,
        accuracy: LocationAccuracy,
    ) {
        latestFix = fix
        onLocation(OnSpotLocation.Located(fix.latitude, fix.longitude, accuracy))
        val last = lastMovedFix
        val moved = last == null || distanceMeters(last.latitude, last.longitude, fix.latitude, fix.longitude) >= MIN_MOVE_METERS
        if (!moved || moveJob?.isActive == true) return // a pending move reads the latest fix when it runs
        val now = clock.now().toEpochMilliseconds()
        val wait = lastMovedAtMillis?.let { (it + MOVE_THROTTLE_MILLIS - now).coerceAtLeast(0) } ?: 0
        moveJob =
            scope.launch {
                if (wait > 0) delay(wait.milliseconds)
                val latest = latestFix ?: return@launch
                lastMovedFix = latest
                lastMovedAtMillis = clock.now().toEpochMilliseconds()
                onMoved(latest)
            }
    }
}

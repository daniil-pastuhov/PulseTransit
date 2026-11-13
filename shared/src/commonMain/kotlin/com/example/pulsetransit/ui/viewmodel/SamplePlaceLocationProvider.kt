package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.SamplePlace
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * [device], unless [samplePlace] names a place: then that place's position, at once and without
 * asking for access, so someone abroad can try on-spot mode as if standing there. Read on each
 * call, so a choice takes effect when following starts again.
 */
internal class SamplePlaceLocationProvider(
    private val device: LocationProvider,
    private val samplePlace: () -> SamplePlace?,
) : LocationProvider {
    override suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult = samplePlace()?.fix ?: device.currentLocation(accuracy)

    override fun locationUpdates(
        accuracy: LocationAccuracy,
        minDistanceMeters: Float,
    ): Flow<LocationResult> =
        flow {
            val place = samplePlace()
            if (place == null) {
                emitAll(device.locationUpdates(accuracy, minDistanceMeters))
            } else {
                emit(place.fix)
                awaitCancellation() // the place never moves; keep the flow open like a real one
            }
        }

    private val SamplePlace.fix get() = LocationResult.Fix(latitude, longitude)
}

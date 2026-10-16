package com.example.pulsetransit.platform

import kotlinx.coroutines.flow.Flow

/** Device location: one-shot for Settings' "Detect location", and continuous for on-spot mode. */
interface LocationProvider {
    suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult

    /**
     * Follows the device while collected, and stops the platform's location updates when the
     * collection ends (the flow is cold, like `motionEvents()`).
     *
     * Emits a [LocationResult.Fix] for the first location and then each time the device has moved
     * at least [minDistanceMeters]. Emits [LocationResult.Unavailable] when the platform reports it
     * cannot locate the device at this accuracy (fixes may still follow), and
     * [LocationResult.PermissionDenied] when access is not granted, after which nothing more comes.
     */
    fun locationUpdates(
        accuracy: LocationAccuracy,
        minDistanceMeters: Float,
    ): Flow<LocationResult>
}

enum class LocationAccuracy {
    /** Satellite fix: slower, and may fail indoors. */
    PRECISE,

    /** Network or cell based: faster and coarser, the fallback when [PRECISE] fails. */
    APPROXIMATE,
}

sealed interface LocationResult {
    data class Fix(
        val latitude: Double,
        val longitude: Double,
    ) : LocationResult

    /** The platform answered, but had no location at this accuracy. */
    data object Unavailable : LocationResult

    /** The user has not granted location access. */
    data object PermissionDenied : LocationResult
}

package com.example.pulsetransit.platform

/** One-shot device location, used to find nearby stops and to centre the weather forecast. */
interface LocationProvider {
    suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult
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

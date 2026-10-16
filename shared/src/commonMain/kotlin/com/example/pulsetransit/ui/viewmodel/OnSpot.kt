package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.platform.LocationAccuracy

/** Where on-spot mode stands in finding the device. */
sealed interface OnSpotLocation {
    /** Not following the device: board mode, or the screen is not showing. */
    data object Idle : OnSpotLocation

    /** Waiting for a first fix at [accuracy]. */
    data class Locating(
        val accuracy: LocationAccuracy,
    ) : OnSpotLocation

    data class Located(
        val latitude: Double,
        val longitude: Double,
        val accuracy: LocationAccuracy,
    ) : OnSpotLocation

    /** Neither a precise nor an approximate fix came; the screen offers a retry. */
    data object Unavailable : OnSpotLocation

    /** Location access is not granted; the screen offers a retry once it is. */
    data object PermissionDenied : OnSpotLocation
}

/** A stop near the device, with its straight-line distance; null when the geocoder gave no coordinates. */
data class NearbyStop(
    val stop: StopPlace,
    val distanceMeters: Int?,
)

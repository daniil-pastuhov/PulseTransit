package com.example.pulsetransit.data

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEGREES_PER_HALF_TURN = 180.0

/** Great-circle (haversine) distance in metres between two WGS84 coordinates. */
fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val dLat = (lat2 - lat1).toRadians()
    val dLon = (lon2 - lon1).toRadians()
    val sinLat = sin(dLat / 2)
    val sinLon = sin(dLon / 2)
    val a = sinLat * sinLat + cos(lat1.toRadians()) * cos(lat2.toRadians()) * sinLon * sinLon
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private fun Double.toRadians(): Double = this * PI / DEGREES_PER_HALF_TURN

/**
 * How near a destination the trip's origin must be to count as already there, so a route to it
 * would only say "walk a minute or two". 200 m is about two and a half minutes on foot: far enough
 * to absorb a phone fix's error (often 20–50 m in town) and the gap between a stop place's centre
 * and its platforms, yet nearer than anyone would want routing to.
 */
const val ARRIVED_RADIUS_METERS = 200.0

/**
 * Whether [lat], [lon] is within [ARRIVED_RADIUS_METERS] of this place. False when the place has
 * no coordinates, since then there is nothing to measure.
 */
fun StopPlace.isArrivedFrom(
    lat: Double,
    lon: Double,
): Boolean {
    val placeLat = latitude ?: return false
    val placeLon = longitude ?: return false
    return distanceMeters(lat, lon, placeLat, placeLon) <= ARRIVED_RADIUS_METERS
}

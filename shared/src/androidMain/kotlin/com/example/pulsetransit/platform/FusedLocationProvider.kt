package com.example.pulsetransit.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.android.gms.location.LocationResult as FusedLocationResult

/** [LocationProvider] on Play Services' fused location provider. */
class FusedLocationProvider(
    context: Context,
) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    // The permission is checked by catching the SecurityException, which also covers it being
    // revoked between the check and the call.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult =
        try {
            suspendCancellableCoroutine { continuation ->
                val cts = CancellationTokenSource()
                continuation.invokeOnCancellation { cts.cancel() }
                client
                    .getCurrentLocation(accuracy.toPriority(), cts.token)
                    .addOnSuccessListener { location ->
                        continuation.resume(
                            location?.let { LocationResult.Fix(it.latitude, it.longitude) }
                                ?: LocationResult.Unavailable,
                        )
                    }.addOnFailureListener {
                        continuation.resume(LocationResult.Unavailable)
                    }
            }
        } catch (_: SecurityException) {
            LocationResult.PermissionDenied
        }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(
        accuracy: LocationAccuracy,
        minDistanceMeters: Float,
    ): Flow<LocationResult> =
        callbackFlow {
            val callback =
                object : LocationCallback() {
                    override fun onLocationResult(result: FusedLocationResult) {
                        result.lastLocation?.let { trySend(LocationResult.Fix(it.latitude, it.longitude)) }
                    }

                    override fun onLocationAvailability(availability: LocationAvailability) {
                        if (!availability.isLocationAvailable) trySend(LocationResult.Unavailable)
                    }
                }
            val request =
                LocationRequest
                    .Builder(accuracy.toPriority(), UPDATE_INTERVAL_MILLIS)
                    .setMinUpdateDistanceMeters(minDistanceMeters)
                    .build()
            try {
                client
                    .requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener { trySend(LocationResult.Unavailable) }
            } catch (_: SecurityException) {
                trySend(LocationResult.PermissionDenied)
                close()
            }
            awaitClose { client.removeLocationUpdates(callback) }
        }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 10_000L
    }

    private fun LocationAccuracy.toPriority() =
        when (this) {
            LocationAccuracy.PRECISE -> Priority.PRIORITY_HIGH_ACCURACY
            LocationAccuracy.APPROXIMATE -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
}

package com.example.pulsetransit.platform

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

    private fun LocationAccuracy.toPriority() =
        when (this) {
            LocationAccuracy.PRECISE -> Priority.PRIORITY_HIGH_ACCURACY
            LocationAccuracy.APPROXIMATE -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
}

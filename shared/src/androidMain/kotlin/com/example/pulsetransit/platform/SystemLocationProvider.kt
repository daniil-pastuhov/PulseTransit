package com.example.pulsetransit.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [LocationProvider] on the platform's `LocationManager`, for devices without Play Services, where
 * [FusedLocationProvider] cannot work. [PRECISE][LocationAccuracy.PRECISE] uses GPS and
 * [APPROXIMATE][LocationAccuracy.APPROXIMATE] the network provider, or the platform's own fused one
 * where the network provider is missing (on many devices it comes with Play Services).
 */
class SystemLocationProvider(
    context: Context,
) : LocationProvider {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val executor = ContextCompat.getMainExecutor(appContext)

    // The permission is checked up front to pick the provider, and the SecurityException is still
    // caught, which covers it being revoked between the check and the call.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult {
        val provider = providerFor(accuracy) ?: return noProviderResult()
        return try {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor) { location ->
                    continuation.resume(location?.toFix() ?: LocationResult.Unavailable)
                }
            }
        } catch (_: SecurityException) {
            LocationResult.PermissionDenied
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(
        accuracy: LocationAccuracy,
        minDistanceMeters: Float,
    ): Flow<LocationResult> =
        callbackFlow {
            val provider = providerFor(accuracy)
            if (provider == null) {
                // Nothing will ever come, so end the updates rather than wait for a first fix.
                trySend(noProviderResult())
                close()
                awaitClose()
                return@callbackFlow
            }
            val listener =
                object : LocationListenerCompat {
                    override fun onLocationChanged(location: Location) {
                        trySend(location.toFix())
                    }

                    override fun onProviderDisabled(provider: String) {
                        trySend(LocationResult.Unavailable)
                    }
                }
            val request =
                LocationRequestCompat
                    .Builder(UPDATE_INTERVAL_MILLIS)
                    .setMinUpdateDistanceMeters(minDistanceMeters)
                    .setQuality(accuracy.toQuality())
                    .build()
            try {
                LocationManagerCompat.requestLocationUpdates(manager, provider, request, executor, listener)
            } catch (_: SecurityException) {
                trySend(LocationResult.PermissionDenied)
                close()
            }
            awaitClose { LocationManagerCompat.removeUpdates(manager, listener) }
        }

    /**
     * The enabled provider for [accuracy], or null when there is none. With only coarse access GPS
     * is off limits, so [PRECISE][LocationAccuracy.PRECISE] gets a coarse provider instead, as
     * the fused provider does.
     */
    private fun providerFor(accuracy: LocationAccuracy): String? {
        val candidates =
            when {
                accuracy == LocationAccuracy.PRECISE && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ->
                    listOf(LocationManager.GPS_PROVIDER)
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) -> COARSE_PROVIDERS
                else -> emptyList()
            }
        return candidates.firstOrNull { manager.isProviderEnabled(it) }
    }

    private fun noProviderResult() =
        if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            LocationResult.Unavailable
        } else {
            LocationResult.PermissionDenied
        }

    // Fine access implies coarse: Android grants both together.
    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toFix() = LocationResult.Fix(latitude, longitude)

    private fun LocationAccuracy.toQuality() =
        when (this) {
            LocationAccuracy.PRECISE -> LocationRequestCompat.QUALITY_HIGH_ACCURACY
            LocationAccuracy.APPROXIMATE -> LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
        }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 10_000L

        // "fused" is LocationManager.FUSED_PROVIDER, which only has a constant from API 31; on older
        // versions isProviderEnabled simply reports it as off.
        val COARSE_PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, "fused")
    }
}

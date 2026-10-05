package com.example.pulsetransit.platform

import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/*
 * The iOS app implements its platform services in Swift. Swift cannot implement a Kotlin
 * `suspend fun` or produce a `Flow`, so each service is a callback-style port here, and a small
 * adapter turns it into the interface the shared code depends on.
 */

/** Device location in Swift (CoreLocation). Calls [onResult] exactly once per request. */
interface LocationSource {
    fun requestLocation(
        accuracy: LocationAccuracy,
        onResult: (LocationResult) -> Unit,
    )

    /** Abandons the request in flight; a later [requestLocation] starts afresh. */
    fun cancel()
}

/** Ambient light, or the best estimate iOS allows, in lux. Emits through [onLux] until [stop]. */
interface LightSource {
    fun start(onLux: (Double) -> Unit)

    fun stop()
}

/** Front-camera motion in Swift (AVFoundation). Calls [onMotion] each time motion is seen, until [stop]. */
interface MotionSource {
    fun start(onMotion: () -> Unit)

    fun stop()
}

internal class LocationSourceProvider(
    private val source: LocationSource,
) : LocationProvider {
    override suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { source.cancel() }
            source.requestLocation(accuracy) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }
}

internal class LightSourceSensorManager(
    private val source: LightSource,
) : SystemSensorManager {
    override fun getLightFlow(): Flow<Float> =
        callbackFlow {
            source.start { lux -> trySend(lux.toFloat()) }
            awaitClose { source.stop() }
        }
}

internal class MotionSourceDetector(
    private val source: MotionSource,
) : CameraMotionDetector {
    override fun motionEvents(): Flow<Unit> =
        callbackFlow {
            source.start { trySend(Unit) }
            awaitClose { source.stop() }
        }
}

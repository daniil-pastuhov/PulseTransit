package com.example.pulsetransit.platform

import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.MotionFrameDiff
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

/**
 * Front-camera frames in Swift (AVFoundation), as luminance samples: every
 * [MotionFrameDiff.SUBSAMPLE]th pixel of the Y plane on both axes, row by row, at most one frame per
 * [MotionFrameDiff.MIN_FRAME_INTERVAL_MILLIS]. Delivered on one serial queue until [stop]. The
 * motion decision itself is shared code, so it behaves the same as on Android.
 */
interface CameraFrameSource {
    fun start(onFrame: (ByteArray) -> Unit)

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

internal class CameraFrameMotionDetector(
    private val source: CameraFrameSource,
    private val settingsRepository: SettingsRepository,
) : CameraMotionDetector {
    override fun motionEvents(): Flow<Unit> =
        callbackFlow {
            // Read per collection, like Android, so restarting the camera picks up a new setting.
            val threshold = settingsRepository.getCameraSensitivity().motionPixelCountThreshold
            val motion = MotionFrameDiff()
            source.start { frame ->
                if (motion.isMotion(frame, threshold)) trySend(Unit)
            }
            awaitClose { source.stop() }
        }
}

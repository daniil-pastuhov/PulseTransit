package com.example.pulsetransit.platform

import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.MotionFrameDiff
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

    /**
     * Starts continuous updates: [onResult] gets a fix each time the device has moved
     * [distanceFilterMeters], and the other [LocationResult]s as described on
     * [LocationProvider.locationUpdates]. Runs until [stopUpdates]; a new call replaces the last.
     * Each [LocationResult.Fix] should carry the fix's time, horizontal accuracy and speed where
     * CoreLocation knows them (the walking pace needs them; a fix without accuracy is not used).
     */
    fun startUpdates(
        accuracy: LocationAccuracy,
        distanceFilterMeters: Double,
        onResult: (LocationResult) -> Unit,
    )

    fun stopUpdates()
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

/**
 * The watch the user chose and its PulseTransit app, in Swift (Garmin's Connect IQ SDK). After
 * [start], [onAvailability] is called with the state now and at every change, and [onMessage] with
 * each dictionary the watch app sends; both on the main thread.
 */
interface WatchSource {
    fun start(
        onAvailability: (WatchAvailability) -> Unit,
        onMessage: (Map<String, Any?>) -> Unit,
    )

    /** See [WatchLink.openApp]. */
    fun openApp()

    /** Calls [onResult] exactly once, on the main thread: whether the message reached the watch app. */
    fun send(
        message: Map<String, Any>,
        onResult: (Boolean) -> Unit,
    )

    /** See [WatchLink.openStore]; only called with a [GarminWatchApp.STORE_ID]. */
    fun openStore()
}

/** [WatchWording] in Swift, which words the notifications and the Live Activity there. */
interface WatchWordingSource {
    fun title(progress: JourneyProgress): String

    fun progress(progress: JourneyProgress): WatchText

    fun alert(alert: JourneyAlert): WatchText
}

/**
 * The Apple Watch in Swift (WatchConnectivity). [start] gives the callback for what the watch app
 * sends; messages that came before it was called must be kept and handed over then.
 */
interface WatchMirrorSource {
    fun start(onMessage: (Map<String, Any?>) -> Unit)

    /** See [WatchMirror.update]. */
    fun update(message: Map<String, Any>)
}

internal class WatchMirrorSourceMirror(
    private val source: WatchMirrorSource,
) : WatchMirror {
    // A channel, not a shared flow: a "Stop following" that woke the app is delivered once, to
    // whoever starts listening after it came.
    private val messages = Channel<Map<String, Any?>>(Channel.UNLIMITED)
    override val incoming: Flow<Map<String, Any?>> = messages.receiveAsFlow()

    init {
        source.start { messages.trySend(it) }
    }

    override fun update(message: Map<String, Any>) = source.update(message)
}

internal class WatchSourceLink(
    private val source: WatchSource,
) : WatchLink {
    private val _availability = MutableStateFlow(WatchAvailability.NONE)
    override val availability: StateFlow<WatchAvailability> = _availability.asStateFlow()

    private val _incoming = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = INCOMING_BUFFER)
    override val incoming: Flow<Map<String, Any?>> = _incoming.asSharedFlow()

    init {
        source.start(
            onAvailability = { _availability.value = it },
            onMessage = { _incoming.tryEmit(it) },
        )
    }

    override fun openApp() = source.openApp()

    override val storeAvailable: Boolean = GarminWatchApp.STORE_ID.isNotEmpty()

    override fun openStore() {
        if (storeAvailable) source.openStore()
    }

    override suspend fun send(message: Map<String, Any>): Boolean =
        suspendCancellableCoroutine { continuation ->
            source.send(message) { reached ->
                if (continuation.isActive) continuation.resume(reached)
            }
        }

    private companion object {
        const val INCOMING_BUFFER = 16
    }
}

internal class WatchSourceWording(
    private val source: WatchWordingSource,
) : WatchWording {
    override suspend fun title(progress: JourneyProgress): String = source.title(progress)

    override suspend fun progress(progress: JourneyProgress): WatchText = source.progress(progress)

    override suspend fun alert(alert: JourneyAlert): WatchText = source.alert(alert)
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

    override fun locationUpdates(
        accuracy: LocationAccuracy,
        minDistanceMeters: Float,
    ): Flow<LocationResult> =
        callbackFlow {
            source.startUpdates(accuracy, minDistanceMeters.toDouble()) { result ->
                trySend(result)
                if (result is LocationResult.PermissionDenied) close()
            }
            awaitClose { source.stopUpdates() }
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

package com.example.pulsetransit.ui.sensors

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.pulsetransit.data.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors

class CameraMotionDetectorImpl(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) : CameraMotionDetector {
    private val subsample = MotionFrameDiff.SUBSAMPLE
    private val minFrameIntervalNs = MotionFrameDiff.MIN_FRAME_INTERVAL_MILLIS * 1_000_000L

    // A low resolution is plenty for coarse motion detection and cuts CPU/memory sharply.
    private val targetResolution = Size(320, 240)

    // CameraX setup, the analyzer and the teardown all have to live inside the one
    // callbackFlow block, and binding failures surface as plain Exception.
    @Suppress("LongMethod", "TooGenericExceptionCaught")
    override fun motionEvents(): Flow<Unit> =
        callbackFlow {
            // Number of full-resolution pixels that must change to trigger motion. Read once per
            // subscription so a re-collection (e.g. after a settings change) picks up the new value.
            val motionPixelCountThreshold = settingsRepository.getCameraSensitivity().motionPixelCountThreshold

            // Per-subscription state so a re-collection starts fresh. Captured (and mutated)
            // by the analyzer closure below.
            val motion = MotionFrameDiff()
            var lastAnalyzedNs = 0L

            // Owned by this subscription so shutting it down on close can't break a later one.
            val executor = Executors.newSingleThreadExecutor()
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            // CameraX binds to a lifecycle; this one is RESUMED for exactly as long as the flow is
            // collected, so the collector decides when the camera runs. Registry calls must be
            // made on the main thread, which is why they all go through mainExecutor.
            val cameraLifecycle = CollectionLifecycleOwner()
            // Only touched on the main thread. Set when collection ends, so a provider that becomes
            // ready afterwards does not open a camera nobody is listening to.
            var closed = false

            cameraProviderFuture.addListener({
                if (closed) return@addListener
                val cameraProvider = cameraProviderFuture.get()

                val resolutionSelector =
                    ResolutionSelector
                        .Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(targetResolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                        ).build()

                val imageAnalysis =
                    ImageAnalysis
                        .Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setResolutionSelector(resolutionSelector)
                        .build()

                imageAnalysis.setAnalyzer(executor) { image ->
                    val now = image.imageInfo.timestamp
                    if (now - lastAnalyzedNs < minFrameIntervalNs) {
                        image.close()
                        return@setAnalyzer
                    }
                    lastAnalyzedNs = now

                    if (motion.isMotion(sampleLuminance(image), motionPixelCountThreshold)) {
                        trySend(Unit)
                    }
                    image.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraLifecycle.registry.currentState = Lifecycle.State.RESUMED
                    cameraProvider.bindToLifecycle(
                        cameraLifecycle,
                        cameraSelector,
                        imageAnalysis,
                    )
                } catch (e: Exception) {
                    Log.e("CameraMotionDetector", "Use case binding failed", e)
                    close(e)
                }
            }, mainExecutor)

            awaitClose {
                // Destroying the lifecycle unbinds everything bound to it and releases the camera.
                // A lifecycle that never started cannot be destroyed, and has nothing to release.
                mainExecutor.execute {
                    closed = true
                    if (cameraLifecycle.registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                        cameraLifecycle.registry.currentState = Lifecycle.State.DESTROYED
                    }
                }
                executor.shutdown()
            }
        }

    /** A lifecycle owned by one collection of [motionEvents]; see its use there. */
    private class CollectionLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /**
     * Subsamples the Y (luminance) plane directly from its buffer, honouring the plane's
     * row/pixel stride. At about 1200 bytes a frame and 4 frames a second, a fresh array each time
     * costs nothing worth recycling.
     */
    private fun sampleLuminance(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val subW = image.width / subsample
        val subH = image.height / subsample
        val size = subW * subH

        val out = ByteArray(size)

        var idx = 0
        for (sy in 0 until subH) {
            val rowBase = (sy * subsample) * rowStride
            for (sx in 0 until subW) {
                out[idx++] = buffer.get(rowBase + (sx * subsample) * pixelStride)
            }
        }
        return out
    }
}

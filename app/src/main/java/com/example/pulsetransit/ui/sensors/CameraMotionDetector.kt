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
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors
import kotlin.math.abs

class CameraMotionDetector(private val context: Context) {
    private val threshold = 25 // Sensitivity threshold for pixel difference
    private val motionPixelCountThreshold = 5000 // Number of pixels that must change to trigger motion
    private val subsample = 8 // Analyse every Nth pixel to save CPU
    // Motion-to-wake doesn't need full sensor FPS; throttle to ~4 fps to save power.
    private val minFrameIntervalNs = 250_000_000L
    // A low resolution is plenty for coarse motion detection and cuts CPU/memory sharply.
    private val targetResolution = Size(320, 240)

    fun getMotionFlow(lifecycleOwner: LifecycleOwner): Flow<Boolean> = callbackFlow {
        // Per-subscription state so a re-collection starts fresh. Captured (and mutated)
        // by the analyzer closure below.
        var lastFrame: ByteArray? = null
        var scratch: ByteArray? = null
        var lastAnalyzedNs = 0L

        // Owned by this subscription so shutting it down on close can't break a later one.
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(targetResolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
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

                val current = sampleLuminance(image, scratch)
                val previous = lastFrame
                // Recycle the old previous-frame buffer as next iteration's scratch.
                scratch = previous
                lastFrame = current

                if (previous != null && previous.size == current.size && hasMotion(current, previous)) {
                    trySend(true)
                }
                image.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraMotionDetector", "Use case binding failed", e)
                close(e)
            }
        }, ContextCompat.getMainExecutor(context))

        awaitClose {
            // unbindAll must run on the main thread; the provider is ready by the time we close.
            ContextCompat.getMainExecutor(context).execute {
                try {
                    cameraProviderFuture.get().unbindAll()
                } catch (e: Exception) {
                    Log.e("CameraMotionDetector", "Unbind failed", e)
                }
            }
            executor.shutdown()
        }
    }

    /**
     * Subsamples the Y (luminance) plane directly from its buffer, honouring the plane's
     * row/pixel stride. Reuses [reusable] when it is the right size to avoid per-frame allocation.
     */
    private fun sampleLuminance(image: ImageProxy, reusable: ByteArray?): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val subW = image.width / subsample
        val subH = image.height / subsample
        val size = subW * subH

        val out = if (reusable != null && reusable.size == size) reusable else ByteArray(size)

        var idx = 0
        for (sy in 0 until subH) {
            val rowBase = (sy * subsample) * rowStride
            for (sx in 0 until subW) {
                out[idx++] = buffer.get(rowBase + (sx * subsample) * pixelStride)
            }
        }
        return out
    }

    private fun hasMotion(current: ByteArray, previous: ByteArray): Boolean {
        var changedPixels = 0
        for (i in current.indices) {
            val diff = abs((current[i].toInt() and 0xFF) - (previous[i].toInt() and 0xFF))
            if (diff > threshold) {
                changedPixels++
            }
        }
        return changedPixels > motionPixelCountThreshold / (subsample * subsample)
    }
}

package com.example.pulsetransit.ui.sensors

import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.abs

class CameraMotionDetector(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private var lastFrame: ByteArray? = null
    private val threshold = 25 // Sensitivity threshold for pixel difference
    private val motionPixelCountThreshold = 5000 // Number of pixels that must change to trigger motion

    fun getMotionFlow(lifecycleOwner: LifecycleOwner): Flow<Boolean> = callbackFlow {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(executor) { image ->
                val motionDetected = detectMotion(image)
                if (motionDetected) {
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
            executor.shutdown()
        }
    }

    private fun detectMotion(image: ImageProxy): Boolean {
        val plane = image.planes[0] // Y plane (luminance)
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        // Subsample to save CPU
        val width = image.width
        val height = image.height
        val subsample = 8
        val currentFrame = ByteArray((width / subsample) * (height / subsample))
        
        var idx = 0
        for (y in 0 until height step subsample) {
            for (x in 0 until width step subsample) {
                currentFrame[idx++] = data[y * width + x]
            }
        }

        val last = lastFrame
        lastFrame = currentFrame

        if (last == null) return false

        var changedPixels = 0
        for (i in currentFrame.indices) {
            val diff = abs(currentFrame[i].toInt() and 0xFF - (last[i].toInt() and 0xFF))
            if (diff > threshold) {
                changedPixels++
            }
        }

        return changedPixels > motionPixelCountThreshold / (subsample * subsample)
    }
}

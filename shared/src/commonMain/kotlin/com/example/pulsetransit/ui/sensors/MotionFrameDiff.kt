package com.example.pulsetransit.ui.sensors

import kotlin.math.abs

/**
 * Decides whether consecutive camera frames show motion. Each platform samples the camera's
 * luminance (Y) plane every [SUBSAMPLE]th pixel in both directions and passes the samples here, so
 * the comparison and its thresholds are the same everywhere.
 *
 * Not thread-safe: feed it from one camera callback queue.
 */
class MotionFrameDiff {
    companion object {
        /** Sample every Nth pixel on each axis; coarse motion needs no more, and it saves CPU. */
        const val SUBSAMPLE = 8

        /** How far a sample's brightness must change (0–255) to count as changed. */
        const val PIXEL_THRESHOLD = 25

        /** Motion-to-wake doesn't need full frame rate; frames closer than this are skipped. */
        const val MIN_FRAME_INTERVAL_MILLIS = 250L
    }

    private var previous: ByteArray? = null

    /**
     * Compares [frame] with the previous frame and remembers it for the next call.
     * [motionPixelCountThreshold] is in full-resolution pixels, as in [com.example.pulsetransit.data.CameraSensitivity];
     * it is scaled down to the sample grid here. The first frame, or one of a different size (the
     * camera changed resolution), never counts as motion.
     */
    fun isMotion(
        frame: ByteArray,
        motionPixelCountThreshold: Int,
    ): Boolean {
        val last = previous
        previous = frame
        if (last == null || last.size != frame.size) return false

        var changed = 0
        for (i in frame.indices) {
            val diff = abs((frame[i].toInt() and 0xFF) - (last[i].toInt() and 0xFF))
            if (diff > PIXEL_THRESHOLD) changed++
        }
        return changed > motionPixelCountThreshold / (SUBSAMPLE * SUBSAMPLE)
    }
}

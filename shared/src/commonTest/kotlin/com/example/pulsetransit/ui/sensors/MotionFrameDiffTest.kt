package com.example.pulsetransit.ui.sensors

import com.example.pulsetransit.data.CameraSensitivity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MotionFrameDiffTest {
    // A 320x240 frame sampled every 8th pixel: 40 x 30 samples.
    private val size = 40 * 30
    private val high = CameraSensitivity.HIGH.motionPixelCountThreshold
    private val medium = CameraSensitivity.MEDIUM.motionPixelCountThreshold
    private val low = CameraSensitivity.LOW.motionPixelCountThreshold

    private fun frame(
        brightness: Int,
        changedSamples: Int = 0,
        changedTo: Int = brightness,
    ) = ByteArray(size) { i -> (if (i < changedSamples) changedTo else brightness).toByte() }

    private fun primed(
        threshold: Int,
        first: ByteArray = frame(100),
    ) = MotionFrameDiff().apply { isMotion(first, threshold) }

    @Test
    fun `the first frame is never motion`() {
        assertFalse(MotionFrameDiff().isMotion(frame(100), high))
    }

    @Test
    fun `an unchanged scene is not motion`() {
        assertFalse(primed(high).isMotion(frame(110), high))
    }

    @Test
    fun `enough strongly changed samples are motion - scaled to the sample grid`() {
        // MEDIUM is 5000 full-resolution pixels, so 5000 / 64 = 78 samples must be exceeded.
        assertFalse(primed(medium).isMotion(frame(100, changedSamples = 78, changedTo = 200), medium))
        assertTrue(primed(medium).isMotion(frame(100, changedSamples = 79, changedTo = 200), medium))
    }

    @Test
    fun `small brightness changes do not count however many`() {
        // Every sample changes, but only by the threshold, which must be exceeded.
        assertFalse(primed(high).isMotion(frame(100, changedSamples = size, changedTo = 125), high))
    }

    @Test
    fun `brightness is read unsigned so dark to bright counts`() {
        // 250 is a negative byte; signed arithmetic would see a small change here.
        assertTrue(primed(high, frame(10)).isMotion(frame(10, changedSamples = size, changedTo = 250), high))
    }

    @Test
    fun `a frame of a different size restarts the comparison`() {
        assertFalse(primed(high).isMotion(ByteArray(size / 2) { 250.toByte() }, high))
    }

    @Test
    fun `higher sensitivity needs less change`() {
        // 40 samples, about 2560 full-resolution pixels: over HIGH's 2000, under LOW's 10000.
        val changed = frame(100, changedSamples = 40, changedTo = 200)
        assertTrue(primed(high).isMotion(changed, high))
        assertFalse(primed(low).isMotion(changed, low))
    }
}

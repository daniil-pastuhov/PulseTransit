package com.example.pulsetransit.ui.sensors

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow

/**
 * Front-camera motion detection, used to wake the screen.
 *
 * This exists as an interface so [com.example.pulsetransit.ui.viewmodel.StopBoardViewModel] can be
 * tested on the JVM: the real implementation needs a `Context` and CameraX.
 */
interface CameraMotionDetector {
    /** Emits `true` each time motion is seen, while bound to [lifecycleOwner]. */
    fun getMotionFlow(lifecycleOwner: LifecycleOwner): Flow<Boolean>
}

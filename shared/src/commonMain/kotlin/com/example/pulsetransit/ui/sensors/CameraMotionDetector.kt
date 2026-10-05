package com.example.pulsetransit.ui.sensors

import kotlinx.coroutines.flow.Flow

/**
 * Front-camera motion detection, used to wake the screen.
 *
 * This exists as an interface so [com.example.pulsetransit.ui.viewmodel.StopBoardViewModel] can be
 * tested on the JVM: the real implementation needs a `Context` and CameraX.
 */
interface CameraMotionDetector {
    /**
     * Emits each time motion is seen. Cold: the camera opens when collection starts and is
     * released when it is cancelled, so the collector's lifetime is the camera's.
     */
    fun motionEvents(): Flow<Unit>
}

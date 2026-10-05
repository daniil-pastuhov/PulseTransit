package com.example.pulsetransit.ui.sensors

import kotlinx.coroutines.flow.Flow

/**
 * Ambient light readings, used to drive the `AUTO` theme.
 *
 * This exists as an interface so [com.example.pulsetransit.ui.viewmodel.StopBoardViewModel] can be
 * tested on the JVM: the real implementation needs a `Context` to reach the platform SensorManager.
 */
interface SystemSensorManager {
    /** Emits illuminance in lux for as long as it is collected. Never completes on real hardware. */
    fun getLightFlow(): Flow<Float>
}

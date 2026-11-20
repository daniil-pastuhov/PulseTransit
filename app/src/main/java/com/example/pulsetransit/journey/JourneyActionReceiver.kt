package com.example.pulsetransit.journey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.switchToFasterRoute
import com.example.pulsetransit.platform.JourneyLauncher
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A journey notification's actions. "Stop following" ends the followed journey, as the button in
 * the app does; "Switch to it" follows the faster route offered at a transfer instead. The tracker
 * notices either change at once.
 */
class JourneyActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val store: JourneyStore by inject()
    private val launcher: JourneyLauncher by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_STOP -> {
                store.finish(System.currentTimeMillis())
                launcher.stop()
            }
            ACTION_SWITCH -> store.switchToFasterRoute(System.currentTimeMillis())
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.pulsetransit.journey.STOP_FOLLOWING"
        const val ACTION_SWITCH = "com.example.pulsetransit.journey.SWITCH_ROUTE"
    }
}

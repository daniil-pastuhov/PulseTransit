package com.example.pulsetransit.journey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.switchToFasterRoute
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A journey notification's actions. "Skip next alerts" mutes the followed journey, which stays
 * followed and saved; "Switch to it" follows the faster route offered at a transfer instead. The
 * tracker notices either change at once.
 */
class JourneyActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val store: JourneyStore by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_SKIP -> {
                val journey = store.active.value ?: return
                store.update(journey.id) { it.copy(muted = true) }
            }
            ACTION_SWITCH -> store.switchToFasterRoute(System.currentTimeMillis())
        }
    }

    companion object {
        const val ACTION_SKIP = "com.example.pulsetransit.journey.SKIP_ALERTS"
        const val ACTION_SWITCH = "com.example.pulsetransit.journey.SWITCH_ROUTE"
    }
}

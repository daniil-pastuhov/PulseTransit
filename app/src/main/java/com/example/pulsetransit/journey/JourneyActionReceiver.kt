package com.example.pulsetransit.journey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pulsetransit.data.journey.JourneyStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * "Skip next alerts" from a journey notification: mutes the followed journey, which stays followed
 * and saved. The tracker notices the change at once and stops alerting.
 */
class JourneyActionReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val store: JourneyStore by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SKIP) return
        val journey = store.active.value ?: return
        store.update(journey.id) { it.copy(muted = true) }
    }

    companion object {
        const val ACTION_SKIP = "com.example.pulsetransit.journey.SKIP_ALERTS"
    }
}

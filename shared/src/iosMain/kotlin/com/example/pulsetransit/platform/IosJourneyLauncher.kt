package com.example.pulsetransit.platform

import com.example.pulsetransit.data.journey.JourneyTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * [JourneyLauncher] on iOS: the tracker runs in the app's own process. With background location it
 * keeps running while the app is in the background; otherwise iOS suspends the app, and the alerts
 * the notifier scheduled with the system come instead.
 */
internal class IosJourneyLauncher(
    private val tracker: JourneyTracker,
) : JourneyLauncher {
    private val scope = MainScope()
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch { tracker.run() }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}

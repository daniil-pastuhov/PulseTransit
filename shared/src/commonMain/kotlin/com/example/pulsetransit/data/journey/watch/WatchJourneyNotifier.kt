package com.example.pulsetransit.data.journey.watch

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.WatchAvailability
import com.example.pulsetransit.platform.WatchLink
import com.example.pulsetransit.platform.WatchWording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Shows a followed journey on the watch as well as the [phone]:
 *
 * - When a journey starts being followed and a watch with the app is connected, the watch is asked
 *   to open it, once per journey.
 * - While the watch app is open it holds the journey's [WatchPlan], sent again when it changes.
 * - While the watch app is open the alerts come from it alone: the phone shows none and schedules
 *   none, so nothing buzzes twice. The one exception is a faster route, which the phone shows too,
 *   since only its notification can switch to it. The phone's progress notification stays, silent as
 *   it is; Android's service cannot run without it.
 * - "Stop following" on the watch stops the journey as the app's button does.
 *
 * The watch app counts as open from its "started" until its "closed", or until it has not been
 * heard from for [ALIVE_TIMEOUT_MILLIS] (out of reach, or closed without a word). A phone that is
 * suspended then (iOS following times only) cannot take the alerts back until it runs again.
 *
 * Like every [JourneyNotifier] this is called from the main thread, and [scope] must run there.
 */
@Suppress("LongParameterList") // the constructor is the DI seam
class WatchJourneyNotifier(
    private val phone: JourneyNotifier,
    private val link: WatchLink,
    private val wording: WatchWording,
    private val store: JourneyStore,
    /** Stops what follows the journey, after the store finished it: [com.example.pulsetransit.platform.JourneyLauncher.stop]. */
    private val stopFollowing: () -> Unit,
    private val clock: Clock = Clock.System,
    scope: CoroutineScope = MainScope(),
) : JourneyNotifier {
    private class Latest(
        val journey: TrackedJourney,
        val upcoming: List<ScheduledAlert>,
    )

    /** Messages are worded asynchronously, so they are built and sent in order on one queue. */
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var lastHeardMillis: Long? = null
    private var latest: Latest? = null
    private var locationLost = false

    /** What the watch app holds, as far as its answers tell. */
    private var sentPlan: WatchPlan? = null

    /** The journey the watch was last asked to open the app for. */
    private var openedFor: String? = null

    init {
        scope.launch { for (task in queue) task() }
        scope.launch { link.incoming.collect { message -> WatchMessages.parse(message)?.let(::onEvent) } }
        // A watch found a moment after the journey started (the link was still connecting) is asked then.
        scope.launch { link.availability.collect { latest?.let { askToOpen(it.journey) } } }
    }

    private fun now() = clock.now().toEpochMilliseconds()

    private fun watchIsOpen(): Boolean = lastHeardMillis?.let { now() - it <= ALIVE_TIMEOUT_MILLIS } == true

    override fun show(
        journey: TrackedJourney,
        alert: JourneyAlert,
    ) {
        val onWatch = watchIsOpen()
        if (!onWatch || alert is JourneyAlert.FasterRoute) phone.show(journey, alert)
        if (onWatch) {
            queue.trySend {
                val text = wording.alert(alert)
                link.send(WatchMessages.alert(journey.id, alert.id, text.title, text.text))
            }
        }
    }

    override fun updateProgress(progress: JourneyProgress) {
        locationLost = progress.locationLost
        phone.updateProgress(progress)
    }

    override fun scheduleUpcoming(
        journey: TrackedJourney,
        upcoming: List<ScheduledAlert>,
    ) {
        latest = Latest(journey, upcoming)
        askToOpen(journey)
        sync()
    }

    override fun end(
        journey: TrackedJourney,
        clearAlerts: Boolean,
    ) {
        phone.end(journey, clearAlerts)
        if (latest?.journey?.id == journey.id) latest = null
        if (watchIsOpen()) {
            queue.trySend {
                link.send(WatchMessages.end(journey.id, arrived = !clearAlerts))
                if (sentPlan?.journeyId == journey.id) sentPlan = null
            }
        }
    }

    /** Asks once, and only as the journey starts: not when a watch turns up halfway, or the app restarts. */
    private fun askToOpen(journey: TrackedJourney) {
        if (openedFor == journey.id || watchIsOpen()) return
        val justStarted = now() - journey.startedAtMillis <= OPEN_WITHIN_MILLIS
        if (!justStarted || link.availability.value != WatchAvailability.READY) return
        openedFor = journey.id
        link.openApp()
    }

    /** Gives the alerts to whichever shows them now, and the watch its plan when it lacks it. */
    private fun sync() {
        val latest = latest ?: return
        val onWatch = watchIsOpen()
        phone.scheduleUpcoming(latest.journey, if (onWatch) emptyList() else latest.upcoming)
        if (!onWatch) return
        queue.trySend {
            val now = now()
            val plan = WatchPlans.plan(latest.journey, latest.upcoming, now, locationLost, wording)
            if (!plan.isCoveredBy(sentPlan, now) && link.send(WatchMessages.plan(plan))) sentPlan = plan
        }
    }

    private fun onEvent(event: WatchEvent) {
        when (event) {
            WatchEvent.Started -> onOpened()
            // Heard again after a silence: it may have missed anything.
            WatchEvent.Alive -> if (watchIsOpen()) lastHeardMillis = now() else onOpened()
            WatchEvent.Closed -> {
                lastHeardMillis = null
                sync()
            }
            is WatchEvent.Stop -> {
                lastHeardMillis = now()
                if (store.active.value?.id == event.journeyId) {
                    store.finish(now())
                    stopFollowing()
                }
            }
        }
    }

    private fun onOpened() {
        lastHeardMillis = now()
        queue.trySend { sentPlan = null }
        if (latest == null) queue.trySend { link.send(WatchMessages.none()) } else sync()
    }

    companion object {
        /** Two missed "alive" messages and a margin. */
        const val ALIVE_TIMEOUT_MILLIS = (2 * WatchMessages.ALIVE_EVERY_SECONDS + 30) * 1_000L

        /** How long after "Notify me" the watch may still be asked to open the app. */
        const val OPEN_WITHIN_MILLIS = 2 * 60_000L
    }
}

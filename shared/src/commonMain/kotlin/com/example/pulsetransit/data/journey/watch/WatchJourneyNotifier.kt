package com.example.pulsetransit.data.journey.watch

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.WatchAvailability
import com.example.pulsetransit.platform.WatchLink
import com.example.pulsetransit.platform.WatchMirror
import com.example.pulsetransit.platform.WatchWording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
 * - A [mirror] (the Apple Watch) is kept up to date with the plan whatever else happens, and takes
 *   nothing over: its plan carries no alerts, since the phone's own reach that wrist.
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
    /** The watch whose app takes the alerts over while open; [NoWatchLink] where there is none. */
    private val link: WatchLink,
    private val wording: WatchWording,
    private val store: JourneyStore,
    /** Stops what follows the journey, after the store finished it: [com.example.pulsetransit.platform.JourneyLauncher.stop]. */
    private val stopFollowing: () -> Unit,
    private val mirror: WatchMirror? = null,
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

    /** What the [mirror] was last given. */
    private var mirroredPlan: WatchPlan? = null

    /** The journey the watch was last asked to open the app for. */
    private var openedFor: String? = null

    init {
        scope.launch { for (task in queue) task() }
        scope.launch { link.incoming.collect { message -> WatchMessages.parse(message)?.let(::onEvent) } }
        mirror?.let { mirror ->
            // Only its "Stop following" means anything: it never takes the alerts over.
            scope.launch { mirror.incoming.collect { message -> (WatchMessages.parse(message) as? WatchEvent.Stop)?.let(::onStop) } }
        }
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
        mirror?.let { mirror ->
            queue.trySend {
                mirror.update(WatchMessages.end(journey.id, arrived = !clearAlerts))
                if (mirroredPlan?.journeyId == journey.id) mirroredPlan = null
            }
        }
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
        if (!onWatch && mirror == null) return
        queue.trySend {
            val now = now()
            val plan = WatchPlans.plan(latest.journey, latest.upcoming, now, locationLost, wording)
            if (onWatch && !plan.isCoveredBy(sentPlan, now) && link.send(WatchMessages.plan(plan))) sentPlan = plan
            // The mirror shows no alerts of its own, so its plan has none and changes only with the journey.
            val mirrored = plan.copy(alerts = emptyList())
            if (mirror != null && !mirrored.isCoveredBy(mirroredPlan, now)) {
                mirror.update(WatchMessages.plan(mirrored))
                mirroredPlan = mirrored
            }
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
                onStop(event)
            }
        }
    }

    private fun onStop(event: WatchEvent.Stop) {
        if (store.active.value?.id == event.journeyId) {
            store.finish(now())
            stopFollowing()
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

/** No watch that takes alerts over: never available, never heard from. */
object NoWatchLink : WatchLink {
    override val availability = MutableStateFlow(WatchAvailability.NONE)
    override val incoming: Flow<Map<String, Any?>> = emptyFlow()

    override fun openApp() = Unit

    override suspend fun send(message: Map<String, Any>): Boolean = false
}

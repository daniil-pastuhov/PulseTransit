package com.example.pulsetransit.data.journey.watch

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyAlertPlanner
import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyFixtures.journey
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import com.example.pulsetransit.data.journey.JourneyPosition
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.data.journey.JourneyStoreImpl
import com.example.pulsetransit.data.journey.ScheduledAlert
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.WatchAvailability
import com.example.pulsetransit.platform.WatchLink
import com.example.pulsetransit.platform.WatchMirror
import com.example.pulsetransit.platform.WatchText
import com.example.pulsetransit.platform.WatchWording
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WatchJourneyNotifierTest {
    private class PhoneNotifier : JourneyNotifier {
        val shown = mutableListOf<JourneyAlert>()
        val progress = mutableListOf<JourneyProgress>()
        val scheduled = mutableListOf<List<ScheduledAlert>>()
        val ended = mutableListOf<Boolean>()

        override fun show(
            journey: TrackedJourney,
            alert: JourneyAlert,
        ) {
            shown += alert
        }

        override fun updateProgress(progress: JourneyProgress) {
            this.progress += progress
        }

        override fun scheduleUpcoming(
            journey: TrackedJourney,
            upcoming: List<ScheduledAlert>,
        ) {
            scheduled += upcoming
        }

        override fun end(
            journey: TrackedJourney,
            clearAlerts: Boolean,
        ) {
            ended += clearAlerts
        }
    }

    private class FakeLink : WatchLink {
        override val availability = MutableStateFlow(WatchAvailability.READY)
        override val incoming = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 16)
        var opened = 0
        var delivers = true
        val sent = mutableListOf<Map<String, Any>>()

        override fun openApp() {
            opened++
        }

        override suspend fun send(message: Map<String, Any>): Boolean {
            if (delivers) sent += message
            return delivers
        }

        fun types() = sent.map { it[WatchMessages.TYPE] }
    }

    /** Words everything by what it is, so a change in the journey is a change in the words. */
    private object Wording : WatchWording {
        override suspend fun title(progress: JourneyProgress) = "Journey to ${progress.destinationName}"

        override suspend fun progress(progress: JourneyProgress) = WatchText("${progress.phase} ${progress.lineName} ${progress.targetName}", progress.details.joinToString())

        override suspend fun alert(alert: JourneyAlert) = WatchText(alert.id, null)
    }

    private class FakeMirror : WatchMirror {
        override val incoming = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = 16)
        val updates = mutableListOf<Map<String, Any>>()

        override fun update(message: Map<String, Any>) {
            updates += message
        }

        fun types() = updates.map { it[WatchMessages.TYPE] }
    }

    private val mirror = FakeMirror()

    private var now = at(0)
    private val clock =
        object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(now)
        }
    private val store = JourneyStoreImpl(MapSettings())
    private val phone = PhoneNotifier()
    private val link = FakeLink()
    private var stopped = 0

    /** Followed since [now], with live stops. */
    private fun followed() = journey(legStops = liveStops()).copy(startedAtMillis = now)

    private fun watchTest(body: suspend TestScope.(WatchJourneyNotifier) -> Unit) =
        runTest(UnconfinedTestDispatcher()) {
            val notifier = WatchJourneyNotifier(phone, link, Wording, store, { stopped++ }, mirror, clock, backgroundScope)
            body(notifier)
        }

    /** One tracker tick for [journey]. */
    private fun WatchJourneyNotifier.tick(journey: TrackedJourney) {
        updateProgress(JourneyPosition.progress(journey, now, locationLost = false))
        scheduleUpcoming(journey, JourneyAlertPlanner.upcomingAlerts(journey, now))
    }

    private fun say(type: String) = assertTrue(link.incoming.tryEmit(mapOf(WatchMessages.TYPE to type)))

    @Test
    fun `a journey just followed asks the watch to open the app once`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            notifier.tick(journey)
            assertEquals(1, link.opened)
            // Until the watch app answers, the phone alerts as ever and the watch is sent nothing.
            assertTrue(phone.scheduled.last().isNotEmpty())
            assertTrue(link.sent.isEmpty())
        }

    @Test
    fun `no watch app and nothing is asked`() =
        watchTest { notifier ->
            link.availability.value = WatchAvailability.APP_MISSING
            notifier.tick(followed())
            assertEquals(0, link.opened)
        }

    @Test
    fun `a watch found just after the journey started is asked then`() =
        watchTest { notifier ->
            link.availability.value = WatchAvailability.NONE
            notifier.tick(followed())
            assertEquals(0, link.opened)
            link.availability.value = WatchAvailability.READY
            assertEquals(1, link.opened)
        }

    @Test
    fun `a journey followed for a while does not ask again`() =
        watchTest { notifier ->
            val journey = followed()
            now += WatchJourneyNotifier.OPEN_WITHIN_MILLIS + 1
            notifier.tick(journey)
            assertEquals(0, link.opened)
        }

    @Test
    fun `the open watch app gets the plan and the phone schedules no alerts`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            say(WatchMessages.TYPE_STARTED)
            assertEquals(listOf<Any?>(WatchMessages.TYPE_PLAN), link.types())
            assertEquals(journey.id, link.sent.single()[WatchMessages.KEY_JOURNEY])
            assertTrue(phone.scheduled.last().isEmpty())
            // The same journey a tick later is not sent again.
            now += 15_000
            notifier.tick(journey)
            assertEquals(1, link.sent.size)
            assertEquals(2, phone.progress.size)
        }

    @Test
    fun `changed times send a new plan`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            say(WatchMessages.TYPE_STARTED)
            notifier.tick(journey.copy(legStops = liveStops(metroDelay = 2)))
            assertEquals(listOf<Any?>(WatchMessages.TYPE_PLAN, WatchMessages.TYPE_PLAN), link.types())
        }

    @Test
    fun `a plan that did not arrive is sent again`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            link.delivers = false
            say(WatchMessages.TYPE_STARTED)
            link.delivers = true
            notifier.tick(journey)
            assertEquals(listOf<Any?>(WatchMessages.TYPE_PLAN), link.types())
        }

    @Test
    fun `alerts go to the open watch app alone`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            notifier.show(journey, JourneyAlert.Arrived("Home"))
            assertEquals(1, phone.shown.size)
            say(WatchMessages.TYPE_STARTED)
            notifier.show(journey, JourneyAlert.MissedJourney)
            assertEquals(1, phone.shown.size)
            assertEquals(WatchMessages.TYPE_ALERT, link.sent.last()[WatchMessages.TYPE])
            assertEquals("missed", link.sent.last()[WatchMessages.KEY_ALERT])
        }

    @Test
    fun `a faster route shows on the phone too for its switch action`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            say(WatchMessages.TYPE_STARTED)
            val faster = JourneyAlert.FasterRoute(1, "19", "Majorstuen", "C", at(16), at(25), 6)
            notifier.show(journey, faster)
            assertEquals(listOf<JourneyAlert>(faster), phone.shown)
            assertEquals(WatchMessages.TYPE_ALERT, link.sent.last()[WatchMessages.TYPE])
        }

    @Test
    fun `the phone takes the alerts back when the watch app closes`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            say(WatchMessages.TYPE_STARTED)
            say(WatchMessages.TYPE_CLOSED)
            assertTrue(phone.scheduled.last().isNotEmpty())
            notifier.show(journey, JourneyAlert.MissedJourney)
            assertEquals(1, phone.shown.size)
        }

    @Test
    fun `a watch app not heard from counts as closed until it is heard again`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            say(WatchMessages.TYPE_STARTED)
            now += WatchJourneyNotifier.ALIVE_TIMEOUT_MILLIS - 1
            say(WatchMessages.TYPE_ALIVE)
            now += WatchJourneyNotifier.ALIVE_TIMEOUT_MILLIS + 1
            notifier.tick(journey)
            assertTrue(phone.scheduled.last().isNotEmpty())
            val sent = link.sent.size
            // Back in reach: it gets the plan whole, whatever it held.
            say(WatchMessages.TYPE_ALIVE)
            assertEquals(sent + 1, link.sent.size)
            assertTrue(phone.scheduled.last().isEmpty())
        }

    @Test
    fun `stop on the watch stops following`() =
        watchTest { notifier ->
            val journey = followed()
            store.start(journey)
            notifier.tick(journey)
            say(WatchMessages.TYPE_STARTED)
            assertTrue(link.incoming.tryEmit(mapOf(WatchMessages.TYPE to WatchMessages.TYPE_STOP, WatchMessages.KEY_JOURNEY to "another")))
            assertEquals(0, stopped)
            assertTrue(link.incoming.tryEmit(mapOf(WatchMessages.TYPE to WatchMessages.TYPE_STOP, WatchMessages.KEY_JOURNEY to journey.id)))
            assertEquals(1, stopped)
            assertNull(store.active.value)
        }

    @Test
    fun `the end reaches both and says whether the journey arrived`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            say(WatchMessages.TYPE_STARTED)
            notifier.end(journey, clearAlerts = false)
            assertEquals(listOf(false), phone.ended)
            assertEquals(WatchMessages.TYPE_END, link.sent.last()[WatchMessages.TYPE])
            assertEquals(1, link.sent.last()[WatchMessages.KEY_ARRIVED])
        }

    @Test
    fun `the mirror gets the plan without alerts whether or not a watch app is open`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            assertEquals(listOf<Any?>(WatchMessages.TYPE_PLAN), mirror.types())
            assertEquals(emptyList<Any>(), mirror.updates.single()[WatchMessages.KEY_ALERTS])
            // The phone keeps its alerts: the mirror takes nothing over.
            assertTrue(phone.scheduled.last().isNotEmpty())
            notifier.show(journey, JourneyAlert.MissedJourney)
            assertEquals(1, phone.shown.size)
            // The same journey a tick later is not sent again; changed times are.
            now += 15_000
            notifier.tick(journey)
            assertEquals(1, mirror.updates.size)
            notifier.tick(journey.copy(legStops = liveStops(metroDelay = 2)))
            assertEquals(2, mirror.updates.size)
        }

    @Test
    fun `the mirror is told when the journey ends`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            notifier.end(journey, clearAlerts = true)
            assertEquals(WatchMessages.TYPE_END, mirror.updates.last()[WatchMessages.TYPE])
            assertEquals(0, mirror.updates.last()[WatchMessages.KEY_ARRIVED])
        }

    @Test
    fun `stop on the mirror stops following`() =
        watchTest { notifier ->
            val journey = followed()
            store.start(journey)
            notifier.tick(journey)
            assertTrue(mirror.incoming.tryEmit(mapOf(WatchMessages.TYPE to WatchMessages.TYPE_STOP, WatchMessages.KEY_JOURNEY to journey.id)))
            assertEquals(1, stopped)
            assertNull(store.active.value)
        }

    @Test
    fun `a mirror saying it started does not take the alerts`() =
        watchTest { notifier ->
            val journey = followed()
            notifier.tick(journey)
            assertTrue(mirror.incoming.tryEmit(mapOf(WatchMessages.TYPE to WatchMessages.TYPE_STARTED)))
            notifier.tick(journey)
            assertTrue(phone.scheduled.last().isNotEmpty())
            assertTrue(link.sent.isEmpty())
        }

    @Test
    fun `a watch app opened with no journey is told so`() =
        watchTest {
            say(WatchMessages.TYPE_STARTED)
            assertEquals(listOf<Any?>(WatchMessages.TYPE_NONE), link.types())
        }
}

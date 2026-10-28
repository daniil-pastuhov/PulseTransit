package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.journey.JourneyFixtures.BUS_JOURNEY
import com.example.pulsetransit.data.journey.JourneyFixtures.METRO_JOURNEY
import com.example.pulsetransit.data.journey.JourneyFixtures.MINUTE
import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyFixtures.journey
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.LocationAccuracy
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.ui.viewmodel.FakeEnturRepository
import com.example.pulsetransit.ui.viewmodel.FakeLocationProvider
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The tracker against fakes, on virtual time that starts at [startAt]. Its loop runs until the
 * journey ends, so each test ends the journey or cancels the loop ([TrackerHarness.job]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JourneyTrackerTest {
    private class RecordingNotifier : JourneyNotifier {
        val shown = mutableListOf<JourneyAlert>()
        val progress = mutableListOf<JourneyProgress>()
        val scheduled = mutableListOf<List<ScheduledAlert>>()
        val ended = mutableListOf<String>()

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

        /** Whether each ended journey's shown alerts were cleared, by journey id. */
        val clearedAlerts = mutableListOf<Boolean>()

        override fun end(
            journey: TrackedJourney,
            clearAlerts: Boolean,
        ) {
            ended += journey.id
            clearedAlerts += clearAlerts
        }
    }

    private val store = JourneyStoreImpl(MapSettings())
    private val entur = FakeEnturRepository()
    private val location = FakeLocationProvider()
    private val notifier = RecordingNotifier()

    private class TrackerHarness(
        val tracker: JourneyTracker,
        val job: Job,
    )

    init {
        entur.journeyStopsFor = { id ->
            when (id) {
                METRO_JOURNEY -> JourneyFixtures.metroStops()
                BUS_JOURNEY -> JourneyFixtures.busStops()
                else -> emptyList()
            }
        }
    }

    private fun trackerTest(body: suspend TestScope.() -> Unit) = runTest(timeout = 10.seconds) { body() }

    private fun TestScope.track(
        journey: TrackedJourney,
        startAt: Long,
    ): TrackerHarness {
        store.start(journey)
        val clock =
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(startAt + testScheduler.currentTime)
            }
        val tracker = JourneyTracker(store, entur, notifier, location, clock)
        val job = launch { tracker.run() }
        runCurrent()
        return TrackerHarness(tracker, job)
    }

    private fun TestScope.advanceMinutes(minutes: Int) {
        advanceTimeBy(minutes * MINUTE)
        runCurrent()
    }

    private val shownIds get() = notifier.shown.map { it.id }

    @Test
    fun `time-only alerts come at their times and never read the location`() =
        trackerTest {
            val harness = track(journey(JourneyTrackingMode.TIME_ONLY), startAt = at(3))
            assertEquals(listOf("walk-0"), shownIds)

            advanceMinutes(2)
            assertEquals(listOf("walk-0", "depart-1"), shownIds)
            advanceMinutes(5)
            assertEquals(listOf("walk-0", "depart-1", "next-stop-1"), shownIds)
            assertEquals(0, location.updateRequests.size)
            harness.job.cancel()
        }

    @Test
    fun `the journey ends after arrival and is kept in history`() =
        trackerTest {
            val harness = track(journey(), startAt = at(25))
            advanceMinutes(10)

            assertTrue("arrived" in shownIds)
            assertNull(store.active.value)
            assertEquals(journey().id, store.history().single().id)
            assertEquals(listOf(journey().id), notifier.ended)
            assertEquals(listOf(false), notifier.clearedAlerts, "its arrival alert stays in sight")
            assertTrue(harness.job.isCompleted, "the loop returns once nothing is followed")
        }

    @Test
    fun `following another journey takes the first one's alerts down`() =
        trackerTest {
            val first = journey()
            val harness = track(first, startAt = at(3))
            assertEquals(listOf("walk-0"), shownIds)

            store.start(first.copy(id = "another", startedAtMillis = at(3)))
            runCurrent()

            assertEquals(listOf(first.id), notifier.ended)
            assertEquals(listOf(true), notifier.clearedAlerts)
            harness.job.cancel()
        }

    @Test
    fun `location mode reads the location for the whole journey`() =
        trackerTest {
            val harness = track(journey(JourneyTrackingMode.LOCATION), startAt = at(3))
            assertEquals(1, location.activeCollectors)
            assertEquals(LocationAccuracy.PRECISE, location.updateRequests.single())

            advanceMinutes(5)
            assertEquals(1, location.activeCollectors, "still reading it, app open or not")
            harness.job.cancel()
            runCurrent()
            assertEquals(0, location.activeCollectors, "and stops with the journey")
        }

    @Test
    fun `a revoked location falls back to time alerts and says so`() =
        trackerTest {
            val harness = track(journey(JourneyTrackingMode.LOCATION), startAt = at(3))
            location.updates.getValue(LocationAccuracy.PRECISE).emit(LocationResult.PermissionDenied)
            runCurrent()

            assertTrue(harness.tracker.locationLost.value)
            advanceMinutes(2)
            assertTrue("depart-1" in shownIds)
            assertTrue(notifier.progress.last().locationLost)
            harness.job.cancel()
        }

    @Test
    fun `skipping alerts silences the rest but keeps following`() =
        trackerTest {
            val harness = track(journey(), startAt = at(3))
            store.update(journey().id) { it.copy(muted = true) }
            runCurrent()

            advanceMinutes(10)
            assertEquals(listOf("walk-0"), shownIds)
            assertTrue(notifier.progress.last().muted)
            assertEquals(journey().id, store.active.value?.id)
            harness.job.cancel()
        }

    @Test
    fun `stopping ends the loop and takes the notifications down`() =
        trackerTest {
            val harness = track(journey(), startAt = at(3))
            store.finish(at(3))
            runCurrent()

            assertTrue(harness.job.isCompleted)
            assertEquals(listOf(journey().id), notifier.ended)
        }

    @Test
    fun `a restored journey does not repeat alerts already shown`() =
        trackerTest {
            val harness = track(journey(fired = setOf("walk-0", "depart-1")), startAt = at(5))

            assertTrue(shownIds.isEmpty())
            harness.job.cancel()
        }

    @Test
    fun `live times are fetched and a delay moves the alerts`() =
        trackerTest {
            entur.journeyStopsFor = { id ->
                if (id == METRO_JOURNEY) JourneyFixtures.metroStops(delay = 3) else JourneyFixtures.busStops()
            }
            val harness = track(journey(), startAt = at(4))

            assertEquals(liveStops(metroDelay = 3), store.active.value?.legStops)
            advanceMinutes(2)
            assertFalse("depart-1" in shownIds, "the metro is late")
            advanceMinutes(2)
            assertTrue("depart-1" in shownIds)
            harness.job.cancel()
        }

    @Test
    fun `offline the last known live times keep the alerts going`() =
        trackerTest {
            entur.journeyStopsFor = { error("offline") }
            val harness = track(journey(legStops = liveStops(metroDelay = 3)), startAt = at(4))

            assertEquals(liveStops(metroDelay = 3), store.active.value?.legStops, "nothing fetched, nothing lost")
            advanceMinutes(4)
            assertTrue("depart-1" in shownIds)
            harness.job.cancel()
        }

    @Test
    fun `live times are refreshed often while something is about to happen`() =
        trackerTest {
            val harness = track(journey(), startAt = at(3))
            val first = entur.journeyStopsCalls

            advanceMinutes(1)
            assertTrue(entur.journeyStopsCalls > first)
            harness.job.cancel()
        }

    @Test
    fun `staying off the journey says the user missed it only once`() =
        trackerTest {
            val (_, lat, lon) = JourneyFixtures.jernbanetorget
            val harness = track(journey(JourneyTrackingMode.LOCATION, legStops = liveStops()), startAt = at(9))
            location.updates.getValue(LocationAccuracy.PRECISE).emit(LocationResult.Fix(lat, lon))
            runCurrent()

            assertFalse("missed" in shownIds, "one far fix is not enough")
            advanceTimeBy(30_000)
            location.updates.getValue(LocationAccuracy.PRECISE).emit(LocationResult.Fix(lat, lon))
            runCurrent()
            assertFalse("missed" in shownIds, "nor half a minute off the journey")
            advanceTimeBy(30_000)
            location.updates.getValue(LocationAccuracy.PRECISE).emit(LocationResult.Fix(lat + 0.0001, lon))
            advanceMinutes(1)

            assertEquals(1, shownIds.count { it == "missed" })
            assertTrue(store.active.value!!.muted, "the rest of the journey no longer applies")
            advanceMinutes(5)
            assertEquals(1, shownIds.count { it == "missed" })
            harness.job.cancel()
        }

    @Test
    fun `a device that stays put at the platform is told it missed the ride`() =
        trackerTest {
            // One fix before the metro leaves, then none: updates come only when the device moves.
            val (_, lat, lon) = JourneyFixtures.jernbanetorget
            val harness = track(journey(JourneyTrackingMode.LOCATION, legStops = liveStops()), startAt = at(4))
            location.updates.getValue(LocationAccuracy.PRECISE).emit(LocationResult.Fix(lat, lon))
            runCurrent()

            advanceMinutes(8)
            assertEquals(1, shownIds.count { it == "missed" })
            harness.job.cancel()
        }

    @Test
    fun `a device on the journey is never told it missed it`() =
        trackerTest {
            val harness = track(journey(JourneyTrackingMode.LOCATION, legStops = liveStops()), startAt = at(8))
            repeat(6) { step ->
                val (_, lat, lon) = if (step < 3) JourneyFixtures.stortinget else JourneyFixtures.nationaltheatret
                location.updates.getValue(LocationAccuracy.PRECISE).emit(LocationResult.Fix(lat, lon))
                advanceTimeBy(30_000)
                runCurrent()
            }

            assertFalse("missed" in shownIds)
            harness.job.cancel()
        }

    @Test
    fun `time-only journeys are never judged missed`() =
        trackerTest {
            val (_, lat, lon) = JourneyFixtures.jernbanetorget
            val harness = track(journey(JourneyTrackingMode.TIME_ONLY, legStops = liveStops()), startAt = at(8))
            location.updates.getValue(LocationAccuracy.PRECISE).emit(LocationResult.Fix(lat, lon))
            advanceMinutes(3)

            assertFalse("missed" in shownIds)
            harness.job.cancel()
        }

    @Test
    fun `progress and upcoming alerts reach the notifier`() =
        trackerTest {
            val harness = track(journey(legStops = liveStops()), startAt = at(9))

            assertEquals(JourneyPhase.RIDING, notifier.progress.last().phase)
            assertEquals(
                "next-stop-1",
                notifier.scheduled
                    .last()
                    .first()
                    .alert.id,
            )
            harness.job.cancel()
        }
}

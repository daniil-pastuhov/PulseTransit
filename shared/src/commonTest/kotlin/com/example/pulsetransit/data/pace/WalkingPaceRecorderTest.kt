package com.example.pulsetransit.data.pace

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.data.pace.WalkingPaceRecorder.Source
import com.example.pulsetransit.platform.LocationResult
import com.example.pulsetransit.ui.viewmodel.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The recorder between the location sources, the settings and the screens. */
class WalkingPaceRecorderTest {
    private val settings = FakeSettingsRepository().apply { mode = AppMode.ON_SPOT }
    private val start = 1_793_174_400_000L
    private val today = start / 86_400_000L

    /** A two-minute walk north at 1.4 m/s: nine fixes 15 s apart. */
    private fun walk(from: Long = start): List<LocationResult.Fix> =
        (0..8).map { i ->
            LocationResult.Fix(
                latitude = 59.91 + i * 21.0 / 111_195.0,
                longitude = 10.75,
                timeMillis = from + i * 15_000L,
                accuracyMeters = 5.0,
            )
        }

    private fun WalkingPaceRecorder.walk(
        source: Source,
        from: Long = start,
    ) {
        walk(from).forEach { onFix(source, it) }
        endBout(source)
    }

    /** Enough walks, on two days, at 1.55 m/s: Fast. */
    private val fastWalker =
        WalkingPaceRecord(
            samples = (0 until 6).map { WalkingPaceSample(1.55, 180, today - it % 2) },
            boutCount = 6,
        )

    @Test
    fun `a wall board never measures`() {
        settings.mode = AppMode.BOARD
        val recorder = WalkingPaceRecorder(settings)

        recorder.walk(Source.ON_SPOT)
        recorder.walk(Source.JOURNEY_WALK, from = start + 600_000)

        assertEquals(WalkingPaceRecord(), settings.pace)
        assertFalse(recorder.state.value.hasData)
    }

    @Test
    fun `a wall board shows no proposal even with a pace measured`() {
        settings.mode = AppMode.BOARD
        settings.pace = fastWalker
        val state = WalkingPaceRecorder(settings).state.value

        assertNull(state.proposal)
        assertNotNull(state.measured)
    }

    @Test
    fun `a walk followed on the spot is kept as its speed alone`() {
        val recorder = WalkingPaceRecorder(settings)

        recorder.walk(Source.ON_SPOT)

        val sample = settings.pace.samples.single()
        assertEquals(1.4, sample.metersPerSecond)
        assertEquals(120, sample.seconds)
        assertEquals(1, settings.pace.boutCount)
        assertTrue(recorder.state.value.hasData)
        assertNull(recorder.state.value.measured, "one walk is not enough to tell")
    }

    @Test
    fun `on-spot fixes are ignored while a journey follows the device`() {
        val recorder = WalkingPaceRecorder(settings)
        recorder.setJourneyFollowing(true)

        recorder.walk(Source.ON_SPOT)
        assertTrue(settings.pace.samples.isEmpty())

        recorder.walk(Source.JOURNEY_WALK, from = start + 600_000)
        assertEquals(1, settings.pace.samples.size)
    }

    @Test
    fun `one walk seen by both sources is kept once`() {
        val recorder = WalkingPaceRecorder(settings)

        walk().forEach {
            recorder.onFix(Source.ON_SPOT, it)
            recorder.onFix(Source.JOURNEY_WALK, it)
        }
        recorder.endBout(Source.ON_SPOT)
        recorder.endBout(Source.JOURNEY_WALK)

        assertEquals(1, settings.pace.samples.size)
    }

    @Test
    fun `a walk after the clock was set back is still kept`() {
        val recorder = WalkingPaceRecorder(settings)

        recorder.walk(Source.ON_SPOT)
        recorder.walk(Source.ON_SPOT, from = start - 3_600_000)

        assertEquals(2, settings.pace.samples.size)
    }

    @Test
    fun `accepting sets the walking speed and the proposal goes`() {
        settings.pace = fastWalker
        val recorder = WalkingPaceRecorder(settings)
        assertEquals(
            WalkingSpeed.FAST,
            recorder.state.value.proposal
                ?.speed,
        )

        assertTrue(recorder.accept())

        assertEquals(WalkingSpeed.FAST, settings.walking)
        assertNull(recorder.state.value.proposal)
        assertFalse(recorder.accept(), "nothing left to accept")
    }

    @Test
    fun `declining hides the proposal and remembers it`() {
        settings.pace = fastWalker
        val recorder = WalkingPaceRecorder(settings)

        recorder.dismiss()

        assertNull(recorder.state.value.proposal)
        assertEquals(DeclinedWalkingSpeed(WalkingSpeed.FAST.name, 6), settings.pace.declined)
        assertNull(WalkingPaceRecorder(settings).state.value.proposal, "still declined after a restart")
        assertEquals(WalkingSpeed.NORMAL, settings.walking)
    }

    @Test
    fun `choosing another speed by hand declines the pending proposal`() {
        settings.pace = fastWalker
        val recorder = WalkingPaceRecorder(settings)

        settings.walking = WalkingSpeed.SLOW
        recorder.onWalkingSpeedChosen(WalkingSpeed.SLOW)

        assertNull(recorder.state.value.proposal)
        assertEquals(WalkingSpeed.FAST.name, settings.pace.declined?.speed)
    }

    @Test
    fun `choosing the proposed speed by hand clears the proposal`() {
        settings.pace = fastWalker
        val recorder = WalkingPaceRecorder(settings)

        settings.walking = WalkingSpeed.FAST
        recorder.onWalkingSpeedChosen(WalkingSpeed.FAST)

        assertNull(recorder.state.value.proposal)
        assertNull(settings.pace.declined)
    }

    @Test
    fun `forgetting clears every bout and the decline`() {
        settings.pace = fastWalker.copy(declined = DeclinedWalkingSpeed(WalkingSpeed.SLOW.name, 2))
        val recorder = WalkingPaceRecorder(settings)

        recorder.clear()

        assertEquals(WalkingPaceRecord(), settings.pace)
        assertEquals(WalkingPaceRecorder.State(), recorder.state.value)
    }

    @Test
    fun `a proposal comes once enough walks are measured`() {
        val recorder = WalkingPaceRecorder(settings)
        // Six fast walks of two minutes, three on each of two days.
        val walks =
            (0 until 6).map { i -> start - (i % 2) * 86_400_000L + (i / 2) * 600_000L }.sorted()
        walks.forEach { from ->
            (0..8).forEach { i ->
                recorder.onFix(
                    Source.ON_SPOT,
                    LocationResult.Fix(59.91 + i * 24.0 / 111_195.0, 10.75, timeMillis = from + i * 15_000L, accuracyMeters = 5.0),
                )
            }
            recorder.endBout(Source.ON_SPOT)
        }

        val proposal = recorder.state.value.proposal
        assertEquals(WalkingSpeed.FAST, proposal?.speed)
        assertEquals(58, proposal?.kmhTenths)
    }
}

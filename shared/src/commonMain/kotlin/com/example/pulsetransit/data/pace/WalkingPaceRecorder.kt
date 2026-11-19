package com.example.pulsetransit.data.pace

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.WalkingSpeed
import com.example.pulsetransit.platform.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Learns the user's walking pace from fixes the app already receives, and holds what the screens
 * show of it. One per app (a Koin single), fed by the on-spot board's [DeviceFollower][com.example.pulsetransit.ui.viewmodel.DeviceFollower]
 * ([Source.ON_SPOT]) and by the walks of a followed journey ([Source.JOURNEY_WALK]); it never asks
 * for a location itself. While a journey follows the device, its known walk legs are the better
 * source, so on-spot fixes are ignored then.
 *
 * Only on-spot mode measures: in board mode (a wall display) fixes are dropped and no proposal is
 * made. What is kept goes through [SettingsRepository.getWalkingPace], aggregate speeds only.
 *
 * Call it on the main thread, as the board and the journey tracker do.
 */
class WalkingPaceRecorder(
    private val settings: SettingsRepository,
    private val clock: Clock = Clock.System,
) {
    enum class Source {
        /** The on-spot board following the device. */
        ON_SPOT,

        /** A followed journey, fed only while it is on a walk leg. */
        JOURNEY_WALK,
    }

    /** What the screens show; see [WalkingPace] for when there is a [measured] pace or a [proposal]. */
    data class State(
        val measured: MeasuredWalkingPace? = null,
        /** Null in board mode, and whenever [WalkingPace.proposal] has none. */
        val proposal: WalkingSpeedProposal? = null,
        /** Any bout kept at all, so there is something to forget. */
        val hasData: Boolean = false,
    )

    private val detectors = Source.entries.associateWith { WalkingBoutDetector() }
    private var journeyFollowing = false

    /**
     * The last bout recorded, so one walk seen by both sources is kept once: a bout overlapping it
     * is the same walk. Overlap rather than "starts before it ended", so a clock set back does not
     * shut out every later walk.
     */
    private var lastBout: WalkingBout? = null

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<State> = _state.asStateFlow()

    private val measuring: Boolean get() = settings.getAppMode() == AppMode.ON_SPOT

    /** A fix from [source]. Fixes without an accuracy (sample places, IP location) are never used. */
    fun onFix(
        source: Source,
        fix: LocationResult.Fix,
    ) {
        if (!measuring) {
            detectors.values.forEach { it.reset() }
            return
        }
        if (source == Source.ON_SPOT && journeyFollowing) return
        val paceFix =
            PaceFix(
                atMillis = fix.timeMillis ?: clock.now().toEpochMilliseconds(),
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
                speedMetersPerSecond = fix.speedMetersPerSecond,
            )
        detectors.getValue(source).add(paceFix)?.let(::record)
    }

    /** [source] stopped (or left a walk leg): the bout under way ends here. */
    fun endBout(source: Source) {
        detectors.getValue(source).finish()?.let(::record)
    }

    /** Whether a followed journey is reading the location now; on-spot fixes are ignored meanwhile. */
    fun setJourneyFollowing(following: Boolean) {
        if (following == journeyFollowing) return
        if (following) endBout(Source.ON_SPOT)
        journeyFollowing = following
    }

    /** Reads the settings again (mode, walking speed), as after Settings closes. */
    fun refresh() {
        _state.value = compute()
    }

    /** Sets the proposed walking speed; false when there was no proposal. */
    fun accept(): Boolean {
        val proposal = _state.value.proposal ?: return false
        settings.setWalkingSpeed(proposal.speed)
        settings.setWalkingPace(settings.getWalkingPace().copy(declined = null))
        refresh()
        return true
    }

    /** "Not now": the level is not proposed again until [WalkingPace.REPROPOSE_AFTER_BOUTS] more bouts. */
    fun dismiss() {
        val proposal = _state.value.proposal ?: return
        settings.setWalkingPace(WalkingPace.declining(settings.getWalkingPace(), proposal.speed))
        refresh()
    }

    /**
     * The user chose [speed] by hand (already saved). A proposal pending for another level counts as
     * declined, since they picked otherwise; one for [speed] itself is simply met.
     */
    fun onWalkingSpeedChosen(speed: WalkingSpeed) {
        val pending = _state.value.proposal
        val record = settings.getWalkingPace()
        val updated =
            when {
                pending != null && pending.speed != speed -> WalkingPace.declining(record, pending.speed)
                record.declined?.speed == speed.name -> record.copy(declined = null)
                else -> record
            }
        if (updated != record) settings.setWalkingPace(updated)
        refresh()
    }

    /** Forgets every measured bout, any decline, and the bouts under way. */
    fun clear() {
        detectors.values.forEach { it.reset() }
        lastBout = null
        settings.setWalkingPace(WalkingPaceRecord())
        refresh()
    }

    private fun record(bout: WalkingBout) {
        if (!measuring) return
        val last = lastBout
        if (last != null && bout.startMillis < last.endMillis && bout.endMillis > last.startMillis) return // the same walk, seen by the other source
        lastBout = bout
        settings.setWalkingPace(WalkingPace.adding(settings.getWalkingPace(), bout))
        refresh()
    }

    private fun compute(): State {
        val record = settings.getWalkingPace()
        return State(
            measured = WalkingPace.measured(record),
            proposal = if (measuring) WalkingPace.proposal(record, settings.getWalkingSpeed()) else null,
            hasData = record.samples.isNotEmpty(),
        )
    }
}

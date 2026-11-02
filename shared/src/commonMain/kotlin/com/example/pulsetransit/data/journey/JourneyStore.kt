package com.example.pulsetransit.data.journey

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * The journey being followed and the last few finished ones. User data, so it lives in [Settings]
 * beside the other settings rather than in the disposable network cache.
 *
 * Writes are read-modify-write without a lock, so make them from the main thread only: the tracker,
 * the screens and the notification actions all run there.
 */
interface JourneyStore {
    /** The followed journey; null when none is. */
    val active: StateFlow<TrackedJourney?>

    fun history(): List<TrackedJourney>

    /** Starts following [journey], ending any other first. */
    fun start(journey: TrackedJourney)

    /** Replaces the active journey with [transform] of it, if it is still [journeyId]. */
    fun update(
        journeyId: String,
        transform: (TrackedJourney) -> TrackedJourney,
    )

    /** Stops following the active journey and keeps it in the history. */
    fun finish(endedAtMillis: Long)

    /** The mode last picked in the permission rationale; null until the user has picked one. */
    fun preferredMode(): JourneyTrackingMode?

    fun setPreferredMode(mode: JourneyTrackingMode)

    /** The kinds of alert turned off in Settings; empty, so every alert is shown, until one is. */
    fun disabledAlerts(): Set<JourneyAlertKind>

    fun setAlertEnabled(
        kind: JourneyAlertKind,
        enabled: Boolean,
    )

    companion object {
        const val HISTORY_SIZE = 5
    }
}

class JourneyStoreImpl(
    private val prefs: Settings,
) : JourneyStore {
    private object Keys {
        const val ACTIVE = "journey_active_json"
        const val HISTORY = "journey_history_json"
        const val PREFERRED_MODE = "journey_preferred_mode"
        const val DISABLED_ALERTS = "journey_disabled_alerts_json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _active = MutableStateFlow(readActive())
    override val active: StateFlow<TrackedJourney?> = _active.asStateFlow()

    private fun readActive(): TrackedJourney? =
        prefs.getStringOrNull(Keys.ACTIVE)?.let {
            runCatching { json.decodeFromString<TrackedJourney>(it) }.getOrNull()
        }

    private fun writeActive(journey: TrackedJourney?) {
        if (journey == null) prefs.remove(Keys.ACTIVE) else prefs.putString(Keys.ACTIVE, json.encodeToString(journey))
        _active.value = journey
    }

    override fun history(): List<TrackedJourney> =
        prefs.getStringOrNull(Keys.HISTORY)?.let {
            runCatching { json.decodeFromString<List<TrackedJourney>>(it) }.getOrNull()
        } ?: emptyList()

    override fun start(journey: TrackedJourney) {
        _active.value?.let { archive(it, journey.startedAtMillis) }
        writeActive(journey)
    }

    override fun update(
        journeyId: String,
        transform: (TrackedJourney) -> TrackedJourney,
    ) {
        val current = _active.value?.takeIf { it.id == journeyId } ?: return
        writeActive(transform(current))
    }

    override fun finish(endedAtMillis: Long) {
        val current = _active.value ?: return
        archive(current, endedAtMillis)
        writeActive(null)
    }

    private fun archive(
        journey: TrackedJourney,
        endedAtMillis: Long,
    ) {
        val ended = journey.copy(endedAtMillis = endedAtMillis)
        val history = (listOf(ended) + history().filterNot { it.id == journey.id }).take(JourneyStore.HISTORY_SIZE)
        prefs.putString(Keys.HISTORY, json.encodeToString(history))
    }

    override fun preferredMode(): JourneyTrackingMode? =
        prefs.getStringOrNull(Keys.PREFERRED_MODE)?.let { name ->
            JourneyTrackingMode.entries.firstOrNull { it.name == name }
        }

    override fun setPreferredMode(mode: JourneyTrackingMode) {
        prefs.putString(Keys.PREFERRED_MODE, mode.name)
    }

    // By name, so a kind a later version drops is skipped rather than failing the whole set.
    override fun disabledAlerts(): Set<JourneyAlertKind> {
        val names =
            prefs.getStringOrNull(Keys.DISABLED_ALERTS)?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
            } ?: return emptySet()
        return JourneyAlertKind.entries.filter { it.name in names }.toSet()
    }

    override fun setAlertEnabled(
        kind: JourneyAlertKind,
        enabled: Boolean,
    ) {
        val disabled = if (enabled) disabledAlerts() - kind else disabledAlerts() + kind
        prefs.putString(Keys.DISABLED_ALERTS, json.encodeToString(disabled.map { it.name }.sorted()))
    }
}

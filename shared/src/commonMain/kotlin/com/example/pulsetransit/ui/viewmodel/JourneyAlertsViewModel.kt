package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.journey.JourneyOpenRequests
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.JourneyTrackingMode
import com.example.pulsetransit.data.journey.TrackedJourney
import com.example.pulsetransit.data.tripKey
import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.platform.JourneyPermissions
import com.example.pulsetransit.platform.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The "Notify me about this journey" button of a route's details, and the journey being followed.
 *
 * On a tap:
 * - Notifications denied: there is no button.
 * - A permission never asked, or no mode chosen yet: the rationale, where the user picks alerts from
 *   live times only, or also noticing a missed ride with the location. The screen then shows the
 *   system prompts the choice needs ([UiState.permissionRequest]) and reports back.
 * - Location denied: time-only alerts.
 * - Otherwise: the mode chosen last time.
 */
class JourneyAlertsViewModel(
    private val store: JourneyStore,
    private val permissions: JourneyPermissions,
    private val launcher: JourneyLauncher,
    private val openRequests: JourneyOpenRequests,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    /** System prompts to show: [notifications] and/or [location]. The screen answers with [onPermissionsResult]. */
    data class PermissionRequest(
        val mode: JourneyTrackingMode,
        val notifications: Boolean,
        val location: Boolean,
    )

    data class UiState(
        /** Whether the button is shown at all. */
        val available: Boolean = false,
        val activeJourney: TrackedJourney? = null,
        /** The route the rationale is open for; null when it is closed. */
        val rationaleFor: TripInfo? = null,
        val permissionRequest: PermissionRequest? = null,
        /** The app was opened from an alert's "Show journey": open the followed journey's details. */
        val openJourneyRequested: Boolean = false,
    ) {
        fun isFollowing(trip: TripInfo): Boolean = activeJourney?.id == journeyId(trip)
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pendingTrip: TripInfo? = null

    init {
        refreshPermissions()
        viewModelScope.launch {
            store.active.collect { journey -> _uiState.update { it.copy(activeJourney = journey) } }
        }
        viewModelScope.launch {
            openRequests.pending.collect { pending -> _uiState.update { it.copy(openJourneyRequested = pending) } }
        }
    }

    /** The board has opened the details asked for by [UiState.openJourneyRequested]. */
    fun onJourneyOpened() = openRequests.consume()

    /** Re-reads the permissions, which may have changed in system settings; call when a screen shows. */
    fun refreshPermissions() {
        val available = permissions.isSupported && permissions.notificationState() != PermissionState.DENIED
        _uiState.update { it.copy(available = available) }
    }

    fun onNotifyClick(trip: TripInfo) {
        if (!permissions.isSupported) return
        val notifications = permissions.notificationState()
        val location = permissions.locationState()
        val preferred = store.preferredMode()
        when {
            notifications == PermissionState.DENIED -> refreshPermissions()
            notifications == PermissionState.GRANTED && location == PermissionState.DENIED ->
                start(trip, JourneyTrackingMode.TIME_ONLY)
            // A mode chosen before (in the rationale or Settings) is used without asking again,
            // unless it needs the location and that was never asked.
            notifications == PermissionState.NOT_ASKED ||
                preferred == null ||
                (preferred.usesLocation && location == PermissionState.NOT_ASKED) -> {
                pendingTrip = trip
                _uiState.update { it.copy(rationaleFor = trip) }
            }
            else -> start(trip, preferred)
        }
    }

    /** The user picked [mode] in the rationale. */
    fun onRationaleChoice(mode: JourneyTrackingMode) {
        val trip = pendingTrip ?: return
        store.setPreferredMode(mode)
        val request =
            PermissionRequest(
                mode = mode,
                notifications = permissions.notificationState() != PermissionState.GRANTED,
                location = mode.usesLocation && permissions.locationState() != PermissionState.GRANTED,
            )
        _uiState.update { it.copy(rationaleFor = null) }
        if (request.notifications || request.location) {
            _uiState.update { it.copy(permissionRequest = request) }
        } else {
            pendingTrip = null
            start(trip, mode)
        }
    }

    /**
     * The rationale was closed without a choice. Ignored once it is already closed: SwiftUI reports
     * a sheet's dismissal after a choice too, which must not cancel the prompts the choice started.
     */
    fun onRationaleDismiss() {
        if (_uiState.value.rationaleFor == null) return
        pendingTrip = null
        _uiState.update { it.copy(rationaleFor = null) }
    }

    /**
     * The system prompts of [UiState.permissionRequest] were answered. Without notifications nothing
     * starts and the button goes away; without location the journey follows times only.
     */
    fun onPermissionsResult(
        notificationsGranted: Boolean,
        locationGranted: Boolean,
    ) {
        val request = _uiState.value.permissionRequest ?: return
        val trip = pendingTrip
        pendingTrip = null
        permissions.markAsked(notifications = request.notifications, location = request.location)
        _uiState.update { it.copy(permissionRequest = null) }
        refreshPermissions()
        if (trip == null || !notificationsGranted) return
        val mode = if (request.mode.usesLocation && !locationGranted) JourneyTrackingMode.TIME_ONLY else request.mode
        start(trip, mode)
    }

    fun onStopClick() {
        store.finish(clock.now().toEpochMilliseconds())
        launcher.stop()
    }

    private fun start(
        trip: TripInfo,
        mode: JourneyTrackingMode,
    ) {
        store.start(
            TrackedJourney(
                id = journeyId(trip),
                trip = trip,
                mode = mode,
                startedAtMillis = clock.now().toEpochMilliseconds(),
            ),
        )
        launcher.start()
    }

    companion object {
        /** A journey is the trips it rides, so the same route found again is the same journey. */
        fun journeyId(trip: TripInfo): String = tripKey(trip)
    }
}

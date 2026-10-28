package com.example.pulsetransit.ui.platform

import androidx.compose.runtime.Composable
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel

// The board's few needs that Compose Multiplatform has no common API for: permissions, the system
// bars and the store. Android implements them; the desktop app, which exists to develop the UI
// under Compose Hot Reload, mostly does without.

/** Makes the status and navigation bar icons contrast with the theme. */
@Composable
expect fun SystemBarsAppearance(darkTheme: Boolean)

/**
 * Runs camera motion wake while the board is visible, asking for camera access first when the
 * setting is on but access was never granted. Restarts when [key] changes (e.g. the mode).
 */
@Composable
expect fun CameraMotionEffect(
    viewModel: StopBoardViewModel,
    key: Any?,
)

/** What the update notice's button does: installs the store's newer version. */
@Composable
expect fun rememberAppUpdateAction(): () -> Unit

/**
 * Location access for on-spot mode. Asks once when shown without it; the returned action retries
 * the fix, or asks again, sending the user to the system settings once asking no longer shows a
 * prompt. [onGranted] runs when access is granted.
 */
@Composable
expect fun rememberLocationAccess(onGranted: () -> Unit): () -> Unit

/**
 * Asks for location access when the user taps "Detect location" in Settings; [onGranted] runs once
 * it is granted. Unlike [rememberLocationAccess], it never asks on its own.
 */
@Composable
expect fun rememberLocationPermissionRequest(onGranted: () -> Unit): () -> Unit

/**
 * Shows the system prompts journey alerts need, once the user has picked a mode in the rationale:
 * notifications and/or location, as the returned action is told. [onResult] then gets what is
 * granted, whether or not a prompt could be shown.
 */
@Composable
expect fun rememberJourneyPermissionRequest(
    onResult: (notificationsGranted: Boolean, locationGranted: Boolean) -> Unit,
): (notifications: Boolean, location: Boolean) -> Unit

/** Whether the user reads time on a 24-hour clock, for the schedule editor's times. */
@Composable
expect fun is24HourClock(): Boolean

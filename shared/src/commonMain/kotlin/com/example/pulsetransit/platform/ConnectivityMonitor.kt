package com.example.pulsetransit.platform

import kotlinx.coroutines.flow.Flow

/**
 * Whether the device has a working internet connection.
 *
 * Used only to explain and recover from failures (telling "no internet" apart from "Entur is
 * down", and refreshing as soon as the connection returns), never to skip a request: a network
 * the platform hasn't validated can still reach Entur.
 *
 * Implemented per platform: `ConnectivityMonitorImpl` on Android's ConnectivityManager and
 * `PathConnectivityMonitor` on iOS's Network.framework, so the shared ViewModel stays testable.
 */
interface ConnectivityMonitor {
    /** Emits the current state on collection, then every change. */
    fun isOnlineFlow(): Flow<Boolean>
}

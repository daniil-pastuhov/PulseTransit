package com.example.pulsetransit.data

import kotlinx.coroutines.flow.Flow

/**
 * Whether the device has a working internet connection.
 *
 * Used only to explain and recover from failures (telling "no internet" apart from "Entur is
 * down", and refreshing as soon as the connection returns), never to skip a request: a network
 * the platform hasn't validated can still reach Entur.
 *
 * An interface so [com.example.pulsetransit.ui.viewmodel.StopBoardViewModel] can be tested on the
 * JVM: the real implementation needs a `Context` to reach the platform ConnectivityManager.
 */
interface ConnectivityMonitor {
    /** Emits the current state on collection, then every change. */
    fun isOnlineFlow(): Flow<Boolean>
}

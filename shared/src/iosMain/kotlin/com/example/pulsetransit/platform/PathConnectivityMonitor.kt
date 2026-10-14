package com.example.pulsetransit.platform

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * [ConnectivityMonitor] on Network.framework's path monitor. A satisfied path means the device has
 * a usable route; unlike Android's VALIDATED it does not prove the internet answers, which is fine
 * as this only labels failures and never gates a request.
 */
class PathConnectivityMonitor : ConnectivityMonitor {
    override fun isOnlineFlow(): Flow<Boolean> =
        callbackFlow {
            val monitor = nw_path_monitor_create()
            // The monitor reports the current path as soon as it starts, then every change.
            nw_path_monitor_set_update_handler(monitor) { path ->
                trySend(nw_path_get_status(path) == nw_path_status_satisfied)
            }
            nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.homieapps.pulsetransit.connectivity", null))
            nw_path_monitor_start(monitor)
            awaitClose { nw_path_monitor_cancel(monitor) }
        }.distinctUntilChanged()
}

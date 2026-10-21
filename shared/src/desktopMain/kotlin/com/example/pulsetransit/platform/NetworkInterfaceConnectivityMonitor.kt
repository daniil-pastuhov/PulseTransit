package com.example.pulsetransit.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ConnectivityMonitor] on the JVM's view of the network interfaces: online while any interface
 * other than loopback is up with a routable address. The JVM has no change callbacks, so it looks
 * every [pollInterval] and emits only changes. Like Android's, it never sends anything itself; a
 * network that is up but cannot reach Entur still shows as online, which only affects the label.
 */
class NetworkInterfaceConnectivityMonitor(
    private val pollInterval: Duration = 5.seconds,
) : ConnectivityMonitor {
    override fun isOnlineFlow(): Flow<Boolean> =
        flow {
            while (true) {
                emit(hasRoutableInterface())
                delay(pollInterval)
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private fun hasRoutableInterface(): Boolean =
        try {
            NetworkInterface.networkInterfaces().anyMatch { nic ->
                nic.isUp &&
                    !nic.isLoopback &&
                    nic.inetAddresses().anyMatch { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            }
        } catch (_: SocketException) {
            // The interfaces could not be listed; assume online so failures are not mislabelled.
            true
        }
}

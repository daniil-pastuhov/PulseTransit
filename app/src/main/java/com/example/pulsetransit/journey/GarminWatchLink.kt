package com.example.pulsetransit.journey

import android.content.Context
import android.util.Log
import com.example.pulsetransit.platform.WatchAvailability
import com.example.pulsetransit.platform.WatchLink
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The Garmin watch paired in the Garmin Connect app, and the PulseTransit watch app on it
 * (`garminApp/`), through the Connect IQ SDK. Everything goes through Garmin Connect's service, so
 * without that app, or before the SDK has bound to it, there is no watch: [availability] is
 * [WatchAvailability.NONE] and nothing is asked of the user. The SDK calls back on the main thread.
 *
 * Of several watches, the first connected one that has the app is used.
 */
internal class GarminWatchLink(
    context: Context,
) : WatchLink {
    private val appContext = context.applicationContext
    private val connectIq = ConnectIQ.getInstance(appContext, ConnectIQ.IQConnectType.WIRELESS)
    private val app = IQApp(APP_ID)

    private val _availability = MutableStateFlow(WatchAvailability.NONE)
    override val availability: StateFlow<WatchAvailability> = _availability.asStateFlow()

    private val _incoming = MutableSharedFlow<Map<String, Any?>>(extraBufferCapacity = INCOMING_BUFFER)
    override val incoming: Flow<Map<String, Any?>> = _incoming.asSharedFlow()

    /** Each known watch that is connected, and whether it has the app; null while that is being asked. */
    private val connected = mutableMapOf<Long, Boolean?>()
    private val devices = mutableMapOf<Long, IQDevice>()

    init {
        // No auto UI: a rider without a Garmin watch must not be asked to install Garmin Connect.
        connectIq.initialize(
            appContext,
            false,
            object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    watchDevices()
                }

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                    Log.i(TAG, "Connect IQ unavailable: $status")
                }

                override fun onSdkShutDown() {
                    connected.clear()
                    devices.clear()
                    publish()
                }
            },
        )
    }

    /** The watch messages go to: a connected one with the app. */
    private fun target(): IQDevice? = connected.entries.firstOrNull { it.value == true }?.let { devices[it.key] }

    private fun publish() {
        _availability.value =
            when {
                connected.values.any { it == true } -> WatchAvailability.READY
                connected.values.any { it == false } -> WatchAvailability.APP_MISSING
                else -> WatchAvailability.NONE
            }
    }

    private fun watchDevices() {
        guarded {
            connectIq.knownDevices.orEmpty().forEach { device ->
                devices[device.deviceIdentifier] = device
                connectIq.registerForDeviceEvents(device) { changed, status -> onStatus(changed, status) }
                onStatus(device, connectIq.getDeviceStatus(device))
            }
        }
    }

    private fun onStatus(
        device: IQDevice,
        status: IQDevice.IQDeviceStatus?,
    ) {
        val id = device.deviceIdentifier
        if (status != IQDevice.IQDeviceStatus.CONNECTED) {
            connected.remove(id)
            publish()
            return
        }
        if (id in connected) return
        connected[id] = null
        guarded {
            connectIq.getApplicationInfo(
                APP_ID,
                device,
                object : ConnectIQ.IQApplicationInfoListener {
                    override fun onApplicationInfoReceived(info: IQApp?) = onAppKnown(device, installed = true)

                    override fun onApplicationNotInstalled(applicationId: String?) = onAppKnown(device, installed = false)
                },
            )
        }
    }

    private fun onAppKnown(
        device: IQDevice,
        installed: Boolean,
    ) {
        val id = device.deviceIdentifier
        // Disconnected while the answer was on its way.
        if (id !in connected) return
        connected[id] = installed
        if (installed) {
            guarded {
                connectIq.registerForAppEvents(device, app) { _, _, messages, status ->
                    if (status == ConnectIQ.IQMessageStatus.SUCCESS) messages.orEmpty().forEach(::onMessage)
                }
            }
        }
        publish()
    }

    /** The watch app sends dictionaries; anything else is not ours. */
    private fun onMessage(message: Any?) {
        val map = message as? Map<*, *> ?: return
        _incoming.tryEmit(map.entries.associate { it.key.toString() to it.value })
    }

    override fun openApp() {
        val device = target() ?: return
        guarded {
            connectIq.openApplication(device, app) { _, _, status -> Log.i(TAG, "Open on watch: $status") }
        }
    }

    override suspend fun send(message: Map<String, Any>): Boolean {
        val device = target() ?: return false
        return suspendCancellableCoroutine { continuation ->
            val sent =
                guarded {
                    connectIq.sendMessage(device, app, message) { _, _, status ->
                        if (continuation.isActive) continuation.resume(status == ConnectIQ.IQMessageStatus.SUCCESS)
                    }
                }
            if (!sent && continuation.isActive) continuation.resume(false)
        }
    }

    /** Runs an SDK call, which throws when Garmin Connect's service is not bound. False when it did. */
    private inline fun guarded(call: () -> Unit): Boolean =
        try {
            call()
            true
        } catch (e: InvalidStateException) {
            Log.w(TAG, "Connect IQ not ready", e)
            false
        } catch (e: ServiceUnavailableException) {
            Log.w(TAG, "Garmin Connect service unavailable", e)
            false
        }

    companion object {
        /** The watch app's id: `garminApp/manifest.xml`. */
        const val APP_ID = "4748155f3aa5402685a2b17d28ebbfe4"
        private const val TAG = "GarminWatchLink"
        private const val INCOMING_BUFFER = 16
    }
}

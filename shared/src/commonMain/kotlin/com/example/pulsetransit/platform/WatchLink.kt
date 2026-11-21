package com.example.pulsetransit.platform

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Whether a followed journey can be shown on a watch. */
enum class WatchAvailability {
    /** No watch is connected, or the platform has none to talk to. */
    NONE,

    /** A watch is connected, without the PulseTransit watch app. */
    APP_MISSING,

    /** A watch is connected and has the watch app. */
    READY,
}

/**
 * The connected watch and its PulseTransit app (Garmin, through the Connect IQ SDK). Messages are
 * the dictionaries of [com.example.pulsetransit.data.journey.watch.WatchMessages]: strings, whole
 * numbers and lists of them only, which is what both ends can carry. Calls come from the main thread.
 */
interface WatchLink {
    val availability: StateFlow<WatchAvailability>

    /** What the watch app sends, as it arrives. */
    val incoming: Flow<Map<String, Any?>>

    /** Asks the watch to open the app. The watch asks its wearer first, so nothing may come of it. */
    fun openApp()

    /** False when the message did not reach the watch app. */
    suspend fun send(message: Map<String, Any>): Boolean

    /** Whether [openStore] has a page to open. */
    val storeAvailable: Boolean get() = false

    /** Opens the watch app's page in the watch maker's store, where it is installed on the watch. */
    fun openStore() = Unit
}

/** The Garmin watch app (`garminApp/`) in the Connect IQ Store. */
object GarminWatchApp {
    /**
     * The app's id in the store: the last part of its page's address, apps.garmin.com/apps/<id>,
     * which the store gives an app when it is first uploaded (not the manifest's id). Empty until
     * the app is listed: the apps then offer no button to a page that is not there.
     */
    const val STORE_ID = ""
}

/**
 * A watch that shows a followed journey beside the phone without taking anything over from it: the
 * Apple Watch. Its app is suspended when the wrist drops, so it cannot show alerts of its own; the
 * system brings the phone's notifications to the wrist instead. It is simply kept up to date.
 */
interface WatchMirror {
    /** What the watch app sends: "Stop following". Each message is delivered once, also when it came before anyone listened. */
    val incoming: Flow<Map<String, Any?>>

    /**
     * The journey's state now, replacing what was sent before: a plan, or that the journey ended
     * (see [com.example.pulsetransit.data.journey.watch.WatchMessages]). It reaches the watch when
     * the watch next can take it; only the latest matters.
     */
    fun update(message: Map<String, Any>)
}

/** A title and the line under it. */
data class WatchText(
    val title: String,
    val text: String?,
)

/**
 * Words a journey for the watch as the platform words its own notifications, in the phone's
 * language: the watch app shows what it is sent and has no strings of its own.
 */
interface WatchWording {
    /** "Journey to Home". */
    suspend fun title(progress: JourneyProgress): String

    /** "On 5 to Majorstuen", with the step's facts under it. */
    suspend fun progress(progress: JourneyProgress): WatchText

    suspend fun alert(alert: JourneyAlert): WatchText
}

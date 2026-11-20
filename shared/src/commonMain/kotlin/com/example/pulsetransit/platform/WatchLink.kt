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

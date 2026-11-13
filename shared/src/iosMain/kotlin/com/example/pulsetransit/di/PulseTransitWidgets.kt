package com.example.pulsetransit.di

import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.widget.BoardWidgetSource
import com.example.pulsetransit.data.widget.DeparturesWidgetState
import com.example.pulsetransit.data.widget.GlanceWidgetState
import com.example.pulsetransit.data.widget.RouteWidgetState
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import platform.Foundation.NSLock

/**
 * The widget extension's entry point into the shared code: what the home-screen widgets show,
 * loaded by the same [BoardWidgetSource] as Android's, through the cached repositories. The
 * extension is its own process, so it starts Koin itself, with only what that needs; settings and
 * the cache are the App Group's, shared with the app (see `AppGroup.kt`).
 */
object PulseTransitWidgets : KoinComponent {
    private val source: BoardWidgetSource
        get() {
            start()
            return get()
        }

    private val startLock = NSLock()

    /**
     * Starts Koin once per process. The extension calls it at launch; every call below also does,
     * under a lock, since WidgetKit asks for several timelines at once and a second `startKoin` throws.
     */
    fun start() {
        startLock.lock()
        try {
            if (KoinPlatformTools.defaultContext().getOrNull() == null) startKoin { modules(sharedModule, iosPlatformModule) }
        } finally {
            startLock.unlock()
        }
    }

    /** The favourite stops a departures widget can be set to, in the board's order. */
    fun stops(): List<StopPlace> = source.stops()

    /** The saved destinations a route widget can be set to. */
    fun destinations(): List<StopPlace> = source.destinations()

    /** Departures of [stopId] (or the first favourite), [maxRows] rows of up to [timesPerRow] times. */
    suspend fun departures(
        stopId: String?,
        maxRows: Int,
        timesPerRow: Int,
    ): DeparturesWidgetState = source.departures(stopId, maxRows, timesPerRow)

    /** The fastest route to [destinationId] (or the destination the board would show now). */
    suspend fun route(destinationId: String?): RouteWidgetState = source.route(destinationId)

    /** The weather and the first favourite's next departures. */
    suspend fun glance(): GlanceWidgetState = source.glance()
}

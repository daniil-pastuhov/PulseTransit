package com.example.pulsetransit.data.widget

import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DestinationSchedule
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.WeatherInfo
import kotlinx.datetime.LocalDateTime
import kotlin.time.Instant

/*
 * What the home-screen widgets show, worked out without any UI, so the Android (Glance) and iOS
 * (WidgetKit) widgets pick and arrange the same data the same way. Loading lives in
 * [BoardWidgetSource]; this file is the pure part.
 */

/** One line-to-destination row of a departures widget: the line's next few departures there. */
data class WidgetDepartureRow(
    val line: String,
    val destination: String,
    val transportMode: String?,
    /** The next departure's platform, when its quay has a public code. */
    val platform: String?,
    /** Soonest first; never empty. */
    val departures: List<Departure>,
) {
    val next: Departure get() = departures.first()

    /** Epoch millis of [departures]' expected times, for the widget to print as clock times. */
    val departureMillis: List<Long> get() = departures.mapNotNull { Instant.parseOrNull(it.expectedTime)?.toEpochMilliseconds() }
}

/**
 * Turns a stop's departures into widget rows. Unlike the board, which folds only consecutive
 * departures of a line, every departure of a line to one destination joins one row: a widget has
 * room for a handful of rows and should give each line one. Rows keep the order of their first
 * departure.
 *
 * Departures already gone at [nowMillis] are dropped (a widget may be drawn from data minutes old),
 * as are [hiddenLines] (the board's line filter) and, unless [showCancelled], cancelled ones.
 */
@Suppress("LongParameterList") // Each is one board setting or limit; a holder type would only rename them.
fun widgetDepartureRows(
    departures: List<Departure>,
    nowMillis: Long,
    hiddenLines: Set<String>,
    showCancelled: Boolean,
    maxRows: Int,
    timesPerRow: Int,
): List<WidgetDepartureRow> {
    if (maxRows <= 0 || timesPerRow <= 0) return emptyList()
    val rows = LinkedHashMap<Pair<String, String>, MutableList<Departure>>()
    departures
        .asSequence()
        .filter { it.line !in hiddenLines }
        .filter { showCancelled || !it.isCancelled }
        .filter { departure ->
            val millis = Instant.parseOrNull(departure.expectedTime)?.toEpochMilliseconds()
            millis == null || millis >= nowMillis
        }.forEach { departure ->
            val key = departure.line to departure.destination
            if (key in rows || rows.size < maxRows) rows.getOrPut(key) { mutableListOf() } += departure
        }
    return rows.values.map { list ->
        val first = list.first()
        WidgetDepartureRow(
            line = first.line,
            destination = first.destination,
            transportMode = first.transportMode,
            platform = first.platform,
            departures = list.take(timesPerRow),
        )
    }
}

/** The place a widget was set to if it is still saved, else the first saved one; null when none is. */
fun pickWidgetPlace(
    places: List<StopPlace>,
    configuredId: String?,
): StopPlace? = places.firstOrNull { it.id == configuredId } ?: places.firstOrNull()

/**
 * The destination a route widget shows: the one it was set to if still saved, else the first whose
 * schedule is active at [now] (as the board shows only those), else simply the first.
 */
fun pickWidgetDestination(
    destinations: List<StopPlace>,
    schedules: Map<String, DestinationSchedule>,
    configuredId: String?,
    now: LocalDateTime,
): StopPlace? =
    destinations.firstOrNull { it.id == configuredId }
        ?: destinations.firstOrNull { schedules[it.id]?.isActive(now) ?: true }
        ?: destinations.firstOrNull()

/** One ridden leg of a route, as a widget lists it: "🚇 2". */
data class WidgetRouteLeg(
    val mode: String?,
    val line: String,
)

/** A fastest route boiled down to a widget's line: leave at, ride, arrive at. */
data class WidgetRoute(
    val originName: String,
    /** When to set off: the trip's start, which is the walk to the stop when there is one. */
    val leaveAtMillis: Long,
    /** When the first ridden leg leaves its stop; null for a route that is all walking. */
    val boardAtMillis: Long?,
    /** The first ridden leg's platform. */
    val platform: String?,
    val arriveAtMillis: Long?,
    val durationMinutes: Int,
    /** The ridden legs in order; empty for a route that is all walking. */
    val legs: List<WidgetRouteLeg>,
    /** Whether the first ridden leg's time is from realtime data. */
    val isRealtime: Boolean,
)

private const val FOOT = "foot"

/** [trip] as a widget shows it, or null when its start time cannot be read. */
fun widgetRoute(trip: TripInfo): WidgetRoute? {
    val leaveAt = Instant.parseOrNull(trip.expectedStartTime)?.toEpochMilliseconds() ?: return null
    val ridden = trip.legs.filter { it.mode != FOOT && it.lineName != null }
    val firstRide = ridden.firstOrNull()
    val legs =
        if (trip.legs.isEmpty() && trip.lineName.isNotBlank()) {
            // Trips cached before legs were kept carry only the line summary.
            listOf(WidgetRouteLeg(null, trip.lineName))
        } else {
            ridden.map { WidgetRouteLeg(it.mode, it.lineName.orEmpty()) }
        }
    return WidgetRoute(
        originName = trip.originName,
        leaveAtMillis = leaveAt,
        boardAtMillis = firstRide?.expectedStartTime?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() },
        platform = firstRide?.platform,
        arriveAtMillis = trip.expectedEndTime?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() },
        durationMinutes = trip.durationMinutes,
        legs = legs,
        isRealtime = firstRide?.isRealtime ?: false,
    )
}

/** Where a widget's data came from, so it can say when it is not live. */
data class WidgetFreshness(
    /** When the shown data was fetched, if it came from the offline cache; null when live. */
    val staleSinceMillis: Long?,
    /** The fetch failed and nothing was cached, so an empty widget means "unknown", not "nothing". */
    val failed: Boolean,
)

/** What a departures widget shows. */
sealed interface DeparturesWidgetState {
    /** No favourite stop is saved yet. */
    data object NoStops : DeparturesWidgetState

    data class Board(
        val stop: StopPlace,
        val rows: List<WidgetDepartureRow>,
        val freshness: WidgetFreshness,
    ) : DeparturesWidgetState
}

/** What a route widget shows. */
sealed interface RouteWidgetState {
    /** No destination is saved yet. */
    data object NoDestinations : RouteWidgetState

    /** Neither a saved location nor a favourite stop to route from. */
    data class NoOrigin(
        val destination: StopPlace,
    ) : RouteWidgetState

    /** The saved location is already at the destination, so there is nothing to route. */
    data class AtDestination(
        val destination: StopPlace,
    ) : RouteWidgetState

    data class Route(
        val destination: StopPlace,
        /** Null when Entur found no route (or, with [freshness] failed, could not be asked). */
        val route: WidgetRoute?,
        val freshness: WidgetFreshness,
    ) : RouteWidgetState
}

/** What the compact widget shows: the clock is the platform's own, the rest comes from here. */
data class GlanceWidgetState(
    /** The forecast for the current hour; null when weather is off or there is no location. */
    val weather: WeatherInfo?,
    /** The first favourite stop, or null when none is saved. */
    val stop: StopPlace?,
    /** That stop's next departures, a row with one time each, soonest first; as many as a tall widget may show. */
    val upcoming: List<WidgetDepartureRow>,
    val freshness: WidgetFreshness,
) {
    /** The next departure, the one every size shows. */
    val next: WidgetDepartureRow? get() = upcoming.firstOrNull()

    companion object {
        /** The most departures a glance widget lists. */
        const val MAX_UPCOMING = 4
    }
}

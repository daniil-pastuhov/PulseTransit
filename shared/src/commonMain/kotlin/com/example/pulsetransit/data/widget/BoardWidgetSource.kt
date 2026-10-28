package com.example.pulsetransit.data.widget

import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.RouteOptions
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.Sourced
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.data.isArrivedFrom
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Loads what the home-screen widgets show, through the same repositories as the board: the cached
 * Entur calls (so a failed refresh falls back to the last good answer) and the MET cache (so a
 * widget refresh never asks MET again before its `Expires` time). Settings are read fresh on every
 * call, since a widget refresh may follow a change made in the app.
 *
 * Routes are planned the way board mode plans them: from the saved location, or without one from
 * the favourite stops, with the route settings and the board's line filter.
 */
class BoardWidgetSource(
    private val enturRepository: EnturRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock = Clock.System,
    /** Null follows the device, read per call so a time zone change is picked up. */
    private val timeZone: TimeZone? = null,
) {
    /** The stops a departures widget can be set to. */
    fun stops(): List<StopPlace> = settingsRepository.getFavorites()

    /** The destinations a route widget can be set to. */
    fun destinations(): List<StopPlace> = settingsRepository.getDestinationStations()

    /** Departures of the stop [stopId] (or the first favourite), at most [maxRows] rows of [timesPerRow] times. */
    suspend fun departures(
        stopId: String?,
        maxRows: Int,
        timesPerRow: Int,
    ): DeparturesWidgetState {
        val stop = pickWidgetPlace(stops(), stopId) ?: return DeparturesWidgetState.NoStops
        val result = fetchDepartures(stop, maxRows)
        val rows =
            widgetDepartureRows(
                departures = result.data,
                nowMillis = clock.now().toEpochMilliseconds(),
                hiddenLines = settingsRepository.getFilteredLines(),
                showCancelled = settingsRepository.isShowCancelledLines(),
                maxRows = maxRows,
                timesPerRow = timesPerRow,
            )
        return DeparturesWidgetState.Board(stop, rows, result.freshness())
    }

    /** The fastest route to the destination [destinationId] (or the one [pickWidgetDestination] picks). */
    suspend fun route(destinationId: String?): RouteWidgetState {
        val now = clock.now()
        val destination =
            pickWidgetDestination(
                destinations = destinations(),
                schedules = settingsRepository.getDestinationSchedules(),
                configuredId = destinationId,
                now = now.toLocalDateTime(timeZone ?: TimeZone.currentSystemDefault()),
            ) ?: return RouteWidgetState.NoDestinations
        val options =
            RouteOptions(
                preference = settingsRepository.getRoutePreference(),
                excludedModes = settingsRepository.getExcludedRouteModes(),
                bannedLines = settingsRepository.getFilteredLines(),
                walkingSpeed = settingsRepository.getWalkingSpeed(),
            )
        val lat = settingsRepository.getSavedLatitude()
        val lon = settingsRepository.getSavedLongitude()
        val fromIds = stops().map { it.id }
        return when {
            lat != null && lon != null && destination.isArrivedFrom(lat, lon) -> RouteWidgetState.AtDestination(destination)
            lat != null && lon != null ->
                enturRepository
                    .getTripOptions(lat = lat, lon = lon, to = destination, count = 1, options = options)
                    .let { routed(destination, Sourced(it.data.firstOrNull(), it.fetchedAtMillis, it.failed), now) }
            fromIds.isEmpty() -> RouteWidgetState.NoOrigin(destination)
            destination.id in fromIds -> RouteWidgetState.AtDestination(destination)
            else -> routed(destination, enturRepository.getFastestTrip(fromIds = fromIds, to = destination, options = options), now)
        }
    }

    private fun routed(
        destination: StopPlace,
        result: Sourced<TripInfo?>,
        now: Instant,
    ): RouteWidgetState.Route {
        // A cached route may already have left; then there is nothing to take.
        val route = result.data?.let(::widgetRoute)?.takeIf { it.leaveAtMillis >= now.toEpochMilliseconds() }
        return RouteWidgetState.Route(destination, route, result.freshness())
    }

    /** The compact widget's weather and the first favourite's next departure. */
    suspend fun glance(): GlanceWidgetState {
        val weather = currentWeather()
        val stop = stops().firstOrNull()
        if (stop == null) return GlanceWidgetState(weather, null, null, WidgetFreshness(null, false))
        val result = fetchDepartures(stop, maxRows = 1)
        val next =
            widgetDepartureRows(
                departures = result.data,
                nowMillis = clock.now().toEpochMilliseconds(),
                hiddenLines = settingsRepository.getFilteredLines(),
                showCancelled = false,
                maxRows = 1,
                timesPerRow = 1,
            ).firstOrNull()
        return GlanceWidgetState(weather, stop, next, result.freshness())
    }

    private suspend fun fetchDepartures(
        stop: StopPlace,
        maxRows: Int,
    ) = enturRepository.getDepartures(
        stopId = stop.id,
        // Several departures fold into each row and some are filtered out, so ask for more.
        limit = (maxRows * DEPARTURES_PER_ROW).coerceIn(MIN_DEPARTURES, MAX_DEPARTURES),
        quayIds = settingsRepository.getSelectedQuays(stop.id),
    )

    /** The forecast hour now under way, honouring the weather setting. */
    private suspend fun currentWeather() =
        if (!settingsRepository.isShowWeather()) {
            null
        } else {
            val lat = settingsRepository.getSavedLatitude()
            val lon = settingsRepository.getSavedLongitude()
            if (lat == null || lon == null) null else weatherRepository.getWeather(lat, lon).data.firstOrNull()
        }

    private fun <T> Sourced<T>.freshness() = WidgetFreshness(fetchedAtMillis, failed)

    private companion object {
        const val DEPARTURES_PER_ROW = 4
        const val MIN_DEPARTURES = 10
        const val MAX_DEPARTURES = 40
    }
}

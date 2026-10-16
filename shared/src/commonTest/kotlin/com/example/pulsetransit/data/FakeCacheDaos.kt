package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedDeparture
import com.example.pulsetransit.data.cache.CachedForecast
import com.example.pulsetransit.data.cache.CachedForecastHour
import com.example.pulsetransit.data.cache.CachedTrip
import com.example.pulsetransit.data.cache.DepartureCacheDao
import com.example.pulsetransit.data.cache.ForecastCacheDao
import com.example.pulsetransit.data.cache.TripCacheDao

/**
 * In-memory stand-ins for the Room DAOs. The `@Transaction` default methods (`replaceStop`, `replaceRoute`,
 * `replaceForecast`) are inherited from the interfaces, so these fakes exercise the same
 * clear-then-insert sequence the generated implementations run.
 */
class FakeDepartureCacheDao : DepartureCacheDao {
    val rows = mutableListOf<CachedDeparture>()
    private var nextId = 1L

    override suspend fun getUpcoming(
        stopId: String,
        nowMillis: Long,
    ): List<CachedDeparture> =
        rows
            .filter { it.stopId == stopId && it.expectedAtMillis >= nowMillis }
            .sortedBy { it.position }

    override suspend fun clearStop(stopId: String) {
        rows.removeAll { it.stopId == stopId }
    }

    override suspend fun insertAll(departures: List<CachedDeparture>) {
        departures.forEach { rows += it.copy(id = nextId++) }
    }

    override suspend fun pruneDepartedBefore(cutoffMillis: Long) {
        rows.removeAll { it.expectedAtMillis < cutoffMillis }
    }
}

class FakeTripCacheDao : TripCacheDao {
    val rows = mutableListOf<CachedTrip>()

    override suspend fun getUpcoming(
        routeKey: String,
        nowMillis: Long,
    ): List<CachedTrip> =
        rows
            .filter { it.routeKey == routeKey && it.expectedStartAtMillis >= nowMillis }
            .sortedBy { it.position }

    override suspend fun clearRoute(routeKey: String) {
        rows.removeAll { it.routeKey == routeKey }
    }

    override suspend fun insertAll(trips: List<CachedTrip>) {
        rows += trips
    }

    override suspend fun pruneStartedBefore(cutoffMillis: Long) {
        rows.removeAll { it.expectedStartAtMillis < cutoffMillis }
    }
}

class FakeForecastCacheDao : ForecastCacheDao {
    var forecast: CachedForecast? = null
    val hours = mutableListOf<CachedForecastHour>()

    override suspend fun getForecast(locationKey: String): CachedForecast? = forecast?.takeIf { it.locationKey == locationKey }

    override suspend fun getUpcomingHours(
        locationKey: String,
        nowMillis: Long,
    ): List<CachedForecastHour> =
        hours
            .filter { it.locationKey == locationKey && it.timeAtMillis >= nowMillis }
            .sortedBy { it.timeAtMillis }

    override suspend fun upsertForecast(forecast: CachedForecast) {
        this.forecast = forecast
    }

    override suspend fun clearHours(locationKey: String) {
        hours.removeAll { it.locationKey == locationKey }
    }

    override suspend fun insertHours(hours: List<CachedForecastHour>) {
        this.hours += hours
    }
}

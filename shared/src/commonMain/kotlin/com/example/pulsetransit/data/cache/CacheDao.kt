package com.example.pulsetransit.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface DepartureCacheDao {
    /**
     * Upcoming departures only — anything already due is stale by definition and never served.
     * Ordered by [CachedDeparture.position] so the board sees exactly the order Entur sent.
     */
    @Query(
        "SELECT * FROM cached_departures WHERE stopId = :stopId AND expectedAtMillis >= :nowMillis " +
            "ORDER BY position ASC",
    )
    suspend fun getUpcoming(
        stopId: String,
        nowMillis: Long,
    ): List<CachedDeparture>

    @Query("DELETE FROM cached_departures WHERE stopId = :stopId")
    suspend fun clearStop(stopId: String)

    @Insert
    suspend fun insertAll(departures: List<CachedDeparture>)

    /** Replaces everything cached for [stopId], so the cache mirrors the last good response. */
    @Transaction
    suspend fun replaceStop(
        stopId: String,
        departures: List<CachedDeparture>,
    ) {
        clearStop(stopId)
        insertAll(departures)
    }

    /** Drops departures that are long past, keeping the cache from growing without bound. */
    @Query("DELETE FROM cached_departures WHERE expectedAtMillis < :cutoffMillis")
    suspend fun pruneDepartedBefore(cutoffMillis: Long)
}

@Dao
interface TripCacheDao {
    /** The route's alternatives that have not left yet, in the order Entur returned them. */
    @Query(
        "SELECT * FROM cached_trips WHERE routeKey = :routeKey AND expectedStartAtMillis >= :nowMillis " +
            "ORDER BY position ASC",
    )
    suspend fun getUpcoming(
        routeKey: String,
        nowMillis: Long,
    ): List<CachedTrip>

    @Query("DELETE FROM cached_trips WHERE routeKey = :routeKey")
    suspend fun clearRoute(routeKey: String)

    @Insert
    suspend fun insertAll(trips: List<CachedTrip>)

    /** Replaces every alternative cached for [routeKey], so the cache mirrors the last good response. */
    @Transaction
    suspend fun replaceRoute(
        routeKey: String,
        trips: List<CachedTrip>,
    ) {
        clearRoute(routeKey)
        insertAll(trips)
    }

    @Query("DELETE FROM cached_trips WHERE expectedStartAtMillis < :cutoffMillis")
    suspend fun pruneStartedBefore(cutoffMillis: Long)
}

@Dao
interface ForecastCacheDao {
    @Query("SELECT * FROM cached_forecasts WHERE locationKey = :locationKey")
    suspend fun getForecast(locationKey: String): CachedForecast?

    /** Future hours only, so an old forecast quietly empties out rather than showing past hours. */
    @Query(
        "SELECT * FROM cached_forecast_hours WHERE locationKey = :locationKey " +
            "AND timeAtMillis >= :nowMillis ORDER BY timeAtMillis ASC",
    )
    suspend fun getUpcomingHours(
        locationKey: String,
        nowMillis: Long,
    ): List<CachedForecastHour>

    /** Used on its own for a 304, which carries no body but does refresh the expiry. */
    @Upsert
    suspend fun upsertForecast(forecast: CachedForecast)

    @Query("DELETE FROM cached_forecast_hours WHERE locationKey = :locationKey")
    suspend fun clearHours(locationKey: String)

    @Insert
    suspend fun insertHours(hours: List<CachedForecastHour>)

    @Transaction
    suspend fun replaceForecast(
        forecast: CachedForecast,
        hours: List<CachedForecastHour>,
    ) {
        upsertForecast(forecast)
        clearHours(forecast.locationKey)
        insertHours(hours)
    }
}

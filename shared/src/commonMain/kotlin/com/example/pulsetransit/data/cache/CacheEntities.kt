package com.example.pulsetransit.data.cache

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One cached departure. [expectedAtMillis] is [CachedDeparture.expectedTime] pre-parsed to epoch
 * millis so departures that have already left can be dropped in SQL — serving a bus that left
 * ten minutes ago is worse than serving nothing.
 */
@Entity(tableName = "cached_departures", indices = [Index("stopId"), Index("expectedAtMillis")])
data class CachedDeparture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stopId: String,
    /**
     * Index within Entur's response. Departures are replayed in this order rather than sorted by
     * time: Entur can return several identical-looking calls at the same timestamp, and ordering
     * by time would break ties arbitrarily, changing how the board groups consecutive departures.
     */
    val position: Int,
    val line: String,
    val destination: String,
    val expectedTime: String,
    val aimedTime: String,
    val expectedAtMillis: Long,
    val isRealtime: Boolean,
    val isCancelled: Boolean,
    val transportMode: String?,
    val delayMinutes: Int,
    val notices: List<String>,
    val quayId: String?,
    val platform: String? = null,
    val lineName: String? = null,
    val authority: String? = null,
    val operator: String? = null,
    val serviceJourneyId: String? = null,
    val serviceDate: String? = null,
    /** When this row was fetched from Entur, used to tell the user how stale the board is. */
    val fetchedAt: Long,
)

/** One cached fastest-route trip, keyed by the origin/destination pair it was requested for. */
@Entity(tableName = "cached_trips")
data class CachedTrip(
    @PrimaryKey val routeKey: String,
    val destinationName: String,
    val expectedStartTime: String,
    val expectedStartAtMillis: Long,
    /** Arrival at the destination, as Entur wrote it; null if Entur left it out. */
    val expectedEndTime: String? = null,
    val lineName: String,
    val durationMinutes: Int,
    val originName: String,
    val legs: List<CachedTripLeg>,
    val fetchedAt: Long,
)

/** [TripLeg][com.example.pulsetransit.data.TripLeg] as stored inside [CachedTrip.legs]. */
@Serializable
data class CachedTripLeg(
    val mode: String? = null,
    val lineName: String? = null,
    val fromName: String? = null,
    val durationMinutes: Int = 0,
)

/**
 * Per-location forecast metadata backing MET's required caching behaviour: their terms say not to
 * repeat a request before [expiresAtMillis], and to revalidate with [lastModified] when it passes.
 */
@Entity(tableName = "cached_forecasts")
data class CachedForecast(
    @PrimaryKey val locationKey: String,
    /** Verbatim MET `Last-Modified` header, echoed back as `If-Modified-Since`. */
    val lastModified: String?,
    /** MET `Expires` header as epoch millis. No request may be repeated before this. */
    val expiresAtMillis: Long,
    val fetchedAt: Long,
)

/** One hour of a cached forecast. Rows are replaced wholesale when MET returns a new document. */
@Entity(
    tableName = "cached_forecast_hours",
    primaryKeys = ["locationKey", "time"],
    indices = [Index("locationKey"), Index("timeAtMillis")],
    foreignKeys = [
        ForeignKey(
            entity = CachedForecast::class,
            parentColumns = ["locationKey"],
            childColumns = ["locationKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CachedForecastHour(
    val locationKey: String,
    val time: String,
    val timeAtMillis: Long,
    val temperature: Double,
    val symbolCode: String,
    val precipitation: Double,
)

package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedForecast
import com.example.pulsetransit.data.cache.CachedForecastHour
import com.example.pulsetransit.data.cache.ForecastCacheDao
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

/**
 * MET Norway locationforecast.
 *
 * Their terms of service require clients to cache: do not repeat a request before the time in the
 * `Expires` header, revalidate with `If-Modified-Since` using the stored `Last-Modified`, and keep
 * using stored data on a `304 Not Modified`. This class implements that contract on top of Room,
 * which also means a network failure serves the last good forecast instead of an empty board.
 *
 * See https://api.met.no/doc/TermsOfService and https://api.met.no/doc/locationforecast/HowTO.
 */
class WeatherRepositoryImpl(
    private val client: HttpClient,
    private val cache: ForecastCacheDao,
) : WeatherRepository {
    private companion object {
        const val URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        const val USER_AGENT = "PulseTransit/1.0 github.com/daniil-pastuhov"
        const val FORECAST_HOURS = 8

        /** Used when MET sends no usable `Expires`; their data updates roughly hourly. */
        const val DEFAULT_EXPIRY_MS = 30 * 60 * 1000L

        /** Backoff when MET throttles us and sends no `Retry-After`. */
        const val THROTTLE_BACKOFF_MS = 10 * 60 * 1000L

        const val MILLIS_PER_SECOND = 1000L

        /** MET's terms require coordinates truncated to 4 decimals. */
        const val COORDINATE_SCALE = 10_000.0

        /**
         * Ceiling on the `Expires` gate. MET sends roughly half an hour; this only stops an
         * absurd value from parking the forecast indefinitely on a display that runs for months.
         */
        const val MAX_EXPIRY_MS = 6 * 60 * 60 * 1000L

        /**
         * Forecast entries are hourly, so the hour already in progress (10:00 when it is 10:25)
         * is still the current one and must survive the "drop past hours" filter.
         */
        const val CURRENT_HOUR_GRACE_MS = 60 * 60 * 1000L
    }

    @Serializable
    private data class MetResponse(
        val properties: MetProperties,
    )

    @Serializable
    private data class MetProperties(
        val timeseries: List<MetTimeSeries>,
    )

    @Serializable
    private data class MetTimeSeries(
        val time: String,
        val data: MetData,
    )

    @Serializable
    private data class MetData(
        val instant: MetInstant,
        val next_1_hours: MetNextHours? = null,
        val next_6_hours: MetNextHours? = null,
    )

    @Serializable
    private data class MetInstant(
        val details: MetDetails,
    )

    @Serializable
    private data class MetDetails(
        val air_temperature: Double,
        val precipitation_amount: Double? = null,
    )

    @Serializable
    private data class MetNextHours(
        val summary: MetSummary,
        val details: MetNextDetails? = null,
    )

    @Serializable
    private data class MetSummary(
        val symbol_code: String,
    )

    @Serializable
    private data class MetNextDetails(
        val precipitation_amount: Double? = null,
    )

    override suspend fun getWeather(
        lat: Double,
        lon: Double,
    ): Sourced<List<WeatherInfo>> {
        // MET requires at most 4 decimals — more is rejected with 403 on newer products, and the
        // truncation is what lets their CDN cache effectively.
        val roundedLat = round4(lat)
        val roundedLon = round4(lon)
        val locationKey = "$roundedLat,$roundedLon"
        val now = System.currentTimeMillis()

        val stored = cache.getForecast(locationKey)

        // "Do not repeat requests until the time indicated in the Expires response header."
        if (stored != null && now < stored.expiresAtMillis) {
            return servedFromCache(locationKey, stored.fetchedAt, now)
        }

        return try {
            val response =
                client.get(URL) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    parameter("lat", roundedLat)
                    parameter("lon", roundedLon)
                    // Only valid against the exact previous Last-Modified, never an arbitrary time.
                    stored?.lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
                }

            when {
                // Body is empty; keep the stored hours and just carry the new expiry forward.
                response.status == HttpStatusCode.NotModified && stored != null -> {
                    cache.upsertForecast(
                        stored.copy(
                            lastModified = response.headers[HttpHeaders.LastModified] ?: stored.lastModified,
                            expiresAtMillis = response.expiresAt(now),
                        ),
                    )
                    servedFromCache(locationKey, stored.fetchedAt, now)
                }

                // Throttled: back off so we stop hammering, and keep showing what we have.
                response.status == HttpStatusCode.TooManyRequests -> {
                    stored?.let { cache.upsertForecast(it.copy(expiresAtMillis = now + retryAfterMs(response))) }
                    fallbackToCache(locationKey, stored?.fetchedAt, now)
                }

                // 203 means the product is deprecated but the body is still usable, so it is
                // logged rather than discarded.
                response.status.isSuccess() -> {
                    if (response.status == HttpStatusCode.NonAuthoritativeInformation) {
                        System.err.println("MET locationforecast reports this product as deprecated (203)")
                    }
                    val hours = response.body<MetResponse>().toWeather()
                    cache.replaceForecast(
                        CachedForecast(
                            locationKey = locationKey,
                            lastModified = response.headers[HttpHeaders.LastModified],
                            expiresAtMillis = response.expiresAt(now),
                            fetchedAt = now,
                        ),
                        hours.map { it.toEntity(locationKey) },
                    )
                    Sourced.live(hours)
                }

                else -> fallbackToCache(locationKey, stored?.fetchedAt, now)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackToCache(locationKey, stored?.fetchedAt, now)
        }
    }

    private suspend fun servedFromCache(
        locationKey: String,
        fetchedAt: Long,
        nowMillis: Long,
    ): Sourced<List<WeatherInfo>> {
        val hours = cache.getUpcomingHours(locationKey, nowMillis - CURRENT_HOUR_GRACE_MS)
        return Sourced.cached(hours.map { it.toWeatherInfo() }, fetchedAt)
    }

    private suspend fun fallbackToCache(
        locationKey: String,
        fetchedAt: Long?,
        nowMillis: Long,
    ): Sourced<List<WeatherInfo>> =
        // Nothing cached either: there is nothing to show, and nothing to call stale.
        if (fetchedAt == null) {
            Sourced(emptyList())
        } else {
            servedFromCache(locationKey, fetchedAt, nowMillis)
        }

    /** MET sends `Expires` as an RFC-1123 date; fall back to a conservative window without it. */
    private fun HttpResponse.expiresAt(nowMillis: Long): Long {
        val header = headers[HttpHeaders.Expires] ?: return nowMillis + DEFAULT_EXPIRY_MS
        val parsed =
            try {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("GMT") }
                    .parse(header)
                    ?.time
            } catch (_: Exception) {
                null
            }
        // An already-past Expires would mean re-requesting on every tick.
        val expiry = parsed?.takeIf { it > nowMillis } ?: (nowMillis + DEFAULT_EXPIRY_MS)
        return minOf(expiry, nowMillis + MAX_EXPIRY_MS)
    }

    private fun retryAfterMs(response: HttpResponse): Long =
        response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.times(MILLIS_PER_SECOND) ?: THROTTLE_BACKOFF_MS

    private fun MetResponse.toWeather(): List<WeatherInfo> =
        properties.timeseries.take(FORECAST_HOURS).map { ts ->
            WeatherInfo(
                time = ts.time,
                temperature = ts.data.instant.details.air_temperature,
                symbolCode =
                    ts.data.next_1_hours
                        ?.summary
                        ?.symbol_code ?: "unknown",
                precipitation =
                    ts.data.next_1_hours
                        ?.details
                        ?.precipitation_amount ?: 0.0,
            )
        }

    private fun WeatherInfo.toEntity(locationKey: String) =
        CachedForecastHour(
            locationKey = locationKey,
            time = time,
            timeAtMillis = parseIsoToMillis(time) ?: 0L,
            temperature = temperature,
            symbolCode = symbolCode,
            precipitation = precipitation,
        )

    private fun CachedForecastHour.toWeatherInfo() =
        WeatherInfo(
            time = time,
            temperature = temperature,
            symbolCode = symbolCode,
            precipitation = precipitation,
        )

    private fun round4(value: Double): Double = (value * COORDINATE_SCALE).roundToLong() / COORDINATE_SCALE
}

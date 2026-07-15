package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedForecast
import com.example.pulsetransit.data.cache.CachedForecastHour
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Covers the caching contract MET's terms of service require: no repeat request before `Expires`,
 * revalidation via `If-Modified-Since`, keep using stored data on `304`, and coordinates truncated
 * to 4 decimals. Failure fallback is covered here too, since it shares the same cache.
 */
class WeatherRepositoryImplTest {

    private val cache = FakeForecastCacheDao()
    private val hour = 60 * 60 * 1000L

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private fun httpDate(millis: Long): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date(millis))

    /** A MET body with hourly entries starting one hour out, so none are filtered as past. */
    private fun metBody(count: Int = 3, temperature: Double = 21.0): String {
        val entries = (1..count).joinToString(",") { i ->
            """
            {"time":"${isoUtc(System.currentTimeMillis() + i * hour)}",
             "data":{"instant":{"details":{"air_temperature":$temperature}},
             "next_1_hours":{"summary":{"symbol_code":"clearsky_day"},
             "details":{"precipitation_amount":0.2}}}}
            """.trimIndent()
        }
        return """{"properties":{"timeseries":[$entries]}}"""
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun jsonHeaders(vararg extra: Pair<String, String>) = headersOf(
        *buildList {
            add(HttpHeaders.ContentType to listOf("application/json"))
            extra.forEach { (k, v) -> add(k to listOf(v)) }
        }.toTypedArray()
    )

    @Test
    fun `successful fetch is returned live and stored with MET's expiry and validator`() = runTest {
        val now = System.currentTimeMillis()
        val engine = MockEngine {
            respond(
                metBody(),
                HttpStatusCode.OK,
                jsonHeaders(
                    HttpHeaders.Expires to httpDate(now + 31 * 60 * 1000L),
                    HttpHeaders.LastModified to "Wed, 15 Jul 2026 13:18:24 GMT"
                )
            )
        }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        assertFalse("a fresh network response is not from cache", result.isFromCache)
        assertNull(result.fetchedAtMillis)
        assertEquals(3, result.data.size)
        assertEquals(21.0, result.data.first().temperature, 0.001)

        assertEquals("Wed, 15 Jul 2026 13:18:24 GMT", cache.forecast?.lastModified)
        assertTrue("expiry comes from the Expires header", cache.forecast!!.expiresAtMillis > now + 30 * 60 * 1000L)
        assertEquals(3, cache.hours.size)
    }

    @Test
    fun `coordinates are truncated to four decimals`() = runTest {
        val engine = MockEngine { respond(metBody(), HttpStatusCode.OK, jsonHeaders()) }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        repo.getWeather(59.9149942, 10.7322001)

        val url = engine.requestHistory.single().url.toString()
        assertTrue("expected 4-decimal lat in $url", url.contains("lat=59.915"))
        assertTrue("expected 4-decimal lon in $url", url.contains("lon=10.7322"))
        // MET rejects 5+ decimals on newer products, and truncation is what makes caching work.
        assertFalse(url.contains("59.9149942"))
        assertFalse(url.contains("10.7322001"))
    }

    @Test
    fun `no request is made before the stored Expires time`() = runTest {
        val now = System.currentTimeMillis()
        cache.forecast = CachedForecast(
            locationKey = "59.915,10.7322",
            lastModified = "Wed, 15 Jul 2026 13:18:24 GMT",
            expiresAtMillis = now + 20 * 60 * 1000L,
            fetchedAt = now - 5 * 60 * 1000L
        )
        cache.hours += CachedForecastHour(
            locationKey = "59.915,10.7322",
            time = isoUtc(now + hour),
            timeAtMillis = now + hour,
            temperature = 18.0,
            symbolCode = "cloudy",
            precipitation = 0.0
        )
        val engine = MockEngine { respond(metBody(), HttpStatusCode.OK, jsonHeaders()) }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        assertEquals("MET forbids repeating a request before Expires", 0, engine.requestHistory.size)
        assertTrue(result.isFromCache)
        assertEquals(now - 5 * 60 * 1000L, result.fetchedAtMillis)
        assertEquals(18.0, result.data.single().temperature, 0.001)
    }

    @Test
    fun `expired forecast revalidates with the stored Last-Modified and keeps data on 304`() = runTest {
        val now = System.currentTimeMillis()
        val fetchedAt = now - 40 * 60 * 1000L
        cache.forecast = CachedForecast(
            locationKey = "59.915,10.7322",
            lastModified = "Wed, 15 Jul 2026 13:18:24 GMT",
            expiresAtMillis = now - 60_000L, // expired
            fetchedAt = fetchedAt
        )
        cache.hours += CachedForecastHour(
            locationKey = "59.915,10.7322",
            time = isoUtc(now + hour),
            timeAtMillis = now + hour,
            temperature = 18.0,
            symbolCode = "cloudy",
            precipitation = 0.0
        )
        val engine = MockEngine {
            respond(
                "",
                HttpStatusCode.NotModified,
                headersOf(HttpHeaders.Expires to listOf(httpDate(now + 30 * 60 * 1000L)))
            )
        }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        // The validator must be the exact previous Last-Modified, not an arbitrary timestamp.
        assertEquals(
            "Wed, 15 Jul 2026 13:18:24 GMT",
            engine.requestHistory.single().headers[HttpHeaders.IfModifiedSince]
        )
        assertTrue("304 carries no body, so stored data is kept", result.isFromCache)
        assertEquals(fetchedAt, result.fetchedAtMillis)
        assertEquals(18.0, result.data.single().temperature, 0.001)
        assertTrue("the 304's Expires reopens the gate", cache.forecast!!.expiresAtMillis > now)
    }

    @Test
    fun `network failure falls back to the cached forecast`() = runTest {
        val now = System.currentTimeMillis()
        val fetchedAt = now - 2 * hour
        cache.forecast = CachedForecast("59.915,10.7322", null, now - 60_000L, fetchedAt)
        cache.hours += CachedForecastHour(
            "59.915,10.7322", isoUtc(now + hour), now + hour, 18.0, "cloudy", 0.0
        )
        val engine = MockEngine { throw java.io.IOException("offline") }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        assertTrue(result.isFromCache)
        assertEquals(fetchedAt, result.fetchedAtMillis)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `network failure with nothing cached reports empty rather than stale`() = runTest {
        val engine = MockEngine { throw java.io.IOException("offline") }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        assertTrue(result.data.isEmpty())
        assertNull("nothing cached means nothing to call stale", result.fetchedAtMillis)
    }

    @Test
    fun `hours already past are never served from cache`() = runTest {
        val now = System.currentTimeMillis()
        cache.forecast = CachedForecast("59.915,10.7322", null, now + hour, now - 3 * hour)
        cache.hours += listOf(
            // Two hours ago: genuinely past.
            CachedForecastHour("59.915,10.7322", isoUtc(now - 2 * hour), now - 2 * hour, 15.0, "cloudy", 0.0),
            // The hour in progress must survive.
            CachedForecastHour("59.915,10.7322", isoUtc(now - 5 * 60_000L), now - 5 * 60_000L, 16.0, "cloudy", 0.0),
            CachedForecastHour("59.915,10.7322", isoUtc(now + hour), now + hour, 17.0, "cloudy", 0.0)
        )
        val engine = MockEngine { respond(metBody(), HttpStatusCode.OK, jsonHeaders()) }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        assertEquals(listOf(16.0, 17.0), result.data.map { it.temperature })
    }

    @Test
    fun `throttling backs off and keeps serving the cache`() = runTest {
        val now = System.currentTimeMillis()
        cache.forecast = CachedForecast("59.915,10.7322", null, now - 60_000L, now - hour)
        cache.hours += CachedForecastHour(
            "59.915,10.7322", isoUtc(now + hour), now + hour, 18.0, "cloudy", 0.0
        )
        val engine = MockEngine {
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter to listOf("120")))
        }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        assertTrue(result.isFromCache)
        assertTrue(
            "Retry-After should push the expiry out so we stop hammering MET",
            cache.forecast!!.expiresAtMillis >= now + 120_000L
        )
    }

    @Test
    fun `server error falls back to cache instead of blanking the forecast`() = runTest {
        val now = System.currentTimeMillis()
        cache.forecast = CachedForecast("59.915,10.7322", null, now - 60_000L, now - hour)
        cache.hours += CachedForecastHour(
            "59.915,10.7322", isoUtc(now + hour), now + hour, 18.0, "cloudy", 0.0
        )
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val repo = WeatherRepositoryImpl(client(engine), cache)

        val result = repo.getWeather(59.9149942, 10.7322001)

        assertTrue(result.isFromCache)
        assertEquals(1, result.data.size)
    }
}

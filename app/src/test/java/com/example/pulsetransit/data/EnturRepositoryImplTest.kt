package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedDeparture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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

class EnturRepositoryImplTest {

    private val departureCache = FakeDepartureCacheDao()
    private val tripCache = FakeTripCacheDao()
    private val stopId = "NSR:StopPlace:59872"
    private val minute = 60 * 1000L

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Europe/Oslo") }
            .format(Date(millis))

    private fun call(line: String, destination: String, atMillis: Long, quay: String = "NSR:Quay:1") = """
        {"expectedDepartureTime":"${iso(atMillis)}","aimedDepartureTime":"${iso(atMillis)}",
         "cancellation":false,"realtimeState":"updated",
         "destinationDisplay":{"frontText":"$destination"},
         "quay":{"id":"$quay"},
         "serviceJourney":{"line":{"publicCode":"$line","name":"$line","transportMode":"rail"},"notices":[]},
         "notices":[]}
    """.trimIndent()

    private fun departuresBody(vararg calls: String) =
        """{"data":{"stopPlace":{"id":"$stopId","name":"Oslo S","estimatedCalls":[${calls.joinToString(",")}]}}}"""

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun jsonOk(body: String) = MockEngine {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @Test
    fun `successful fetch is live and populates the cache`() = runTest {
        val now = System.currentTimeMillis()
        val engine = jsonOk(
            departuresBody(
                call("R13", "Drammen", now + 5 * minute),
                call("L1", "Lillestrøm", now + 9 * minute)
            )
        )
        val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

        val result = repo.getDepartures(stopId, limit = 15)

        assertFalse(result.isFromCache)
        assertNull(result.fetchedAtMillis)
        assertEquals(listOf("R13", "L1"), result.data.map { it.line })
        assertEquals(2, departureCache.rows.size)
    }

    @Test
    fun `network failure serves the cached board with its original fetch time`() = runTest {
        val now = System.currentTimeMillis()
        val fetchedAt = now - 3 * minute
        departureCache.rows += cached("R13", "Drammen", now + 5 * minute, position = 0, fetchedAt = fetchedAt)
        val engine = MockEngine { throw java.io.IOException("offline") }
        val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

        val result = repo.getDepartures(stopId, limit = 15)

        assertTrue(result.isFromCache)
        assertEquals(fetchedAt, result.fetchedAtMillis)
        assertEquals(listOf("R13"), result.data.map { it.line })
    }

    @Test
    fun `departures that have already left are dropped from the cached board`() = runTest {
        val now = System.currentTimeMillis()
        departureCache.rows += listOf(
            cached("R13", "Drammen", now - 2 * minute, position = 0), // already left
            cached("L1", "Lillestrøm", now + 4 * minute, position = 1)
        )
        val engine = MockEngine { throw java.io.IOException("offline") }
        val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

        val result = repo.getDepartures(stopId, limit = 15)

        assertEquals(
            "a bus that already left is worse than no answer",
            listOf("L1"),
            result.data.map { it.line }
        )
    }

    @Test
    fun `network failure with nothing cached reports empty rather than stale`() = runTest {
        val engine = MockEngine { throw java.io.IOException("offline") }
        val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

        val result = repo.getDepartures(stopId, limit = 15)

        assertTrue(result.data.isEmpty())
        assertNull(result.fetchedAtMillis)
    }

    @Test
    fun `an empty live response is honest, not a cache fallback`() = runTest {
        val now = System.currentTimeMillis()
        departureCache.rows += cached("R13", "Drammen", now + 5 * minute, position = 0)
        val repo = EnturRepositoryImpl(client(jsonOk(departuresBody())), departureCache, tripCache)

        val result = repo.getDepartures(stopId, limit = 15)

        // Entur answering "no departures" is a real answer; old ones must not be resurrected.
        assertTrue(result.data.isEmpty())
        assertNull(result.fetchedAtMillis)
        assertTrue("the cache should mirror the last good response", departureCache.rows.isEmpty())
    }

    @Test
    fun `a response missing the stop falls back instead of wiping the cache`() = runTest {
        val now = System.currentTimeMillis()
        departureCache.rows += cached("R13", "Drammen", now + 5 * minute, position = 0)
        val repo = EnturRepositoryImpl(
            client(jsonOk("""{"data":{"stopPlace":null}}""")),
            departureCache,
            tripCache
        )

        val result = repo.getDepartures(stopId, limit = 15)

        assertTrue("an API-level failure is not an empty stop", result.isFromCache)
        assertEquals(listOf("R13"), result.data.map { it.line })
    }

    @Test
    fun `cached departures replay in Entur's order, not sorted by time`() = runTest {
        val now = System.currentTimeMillis()
        val at = now + 20 * minute
        // Entur really does return identical calls interleaved with a different destination at the
        // same timestamp; re-sorting on the tied timestamp would regroup them and break list keys.
        val engine = jsonOk(
            departuresBody(
                call("L1", "Lillestrøm", at),
                call("L1", "Lillestrøm", at),
                call("L1", "Haugenstua", at),
                call("L1", "Lillestrøm", at)
            )
        )
        val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
        repo.getDepartures(stopId, limit = 15)

        assertEquals(listOf(0, 1, 2, 3), departureCache.rows.map { it.position })

        // Serve the same set back from the cache and confirm the order survived the round trip.
        val offline = EnturRepositoryImpl(
            client(MockEngine { throw java.io.IOException("offline") }),
            departureCache,
            tripCache
        )
        val result = offline.getDepartures(stopId, limit = 15)

        assertEquals(
            listOf("Lillestrøm", "Lillestrøm", "Haugenstua", "Lillestrøm"),
            result.data.map { it.destination }
        )
    }

    @Test
    fun `quay filtering is applied before caching`() = runTest {
        val now = System.currentTimeMillis()
        val engine = jsonOk(
            departuresBody(
                call("R13", "Drammen", now + 5 * minute, quay = "NSR:Quay:1"),
                call("L1", "Lillestrøm", now + 9 * minute, quay = "NSR:Quay:2")
            )
        )
        val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

        val result = repo.getDepartures(stopId, limit = 15, quayIds = setOf("NSR:Quay:2"))

        assertEquals(listOf("L1"), result.data.map { it.line })
        assertEquals(listOf("L1"), departureCache.rows.map { it.line })
    }

    private fun cached(
        line: String,
        destination: String,
        atMillis: Long,
        position: Int,
        fetchedAt: Long = System.currentTimeMillis()
    ) = CachedDeparture(
        stopId = stopId,
        position = position,
        line = line,
        destination = destination,
        expectedTime = iso(atMillis),
        aimedTime = iso(atMillis),
        expectedAtMillis = atMillis,
        isRealtime = true,
        isCancelled = false,
        transportMode = "rail",
        delayMinutes = 0,
        notices = emptyList(),
        quayId = "NSR:Quay:1",
        fetchedAt = fetchedAt
    )
}

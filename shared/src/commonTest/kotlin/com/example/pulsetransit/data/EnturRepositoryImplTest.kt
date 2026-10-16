package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedDeparture
import com.example.pulsetransit.data.cache.CachedTrip
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnturRepositoryImplTest {
    private val departureCache = FakeDepartureCacheDao()
    private val tripCache = FakeTripCacheDao()
    private val stopId = "NSR:StopPlace:59872"
    private val minute = 60 * 1000L

    private fun call(
        line: String,
        destination: String,
        atMillis: Long,
        quay: String = "NSR:Quay:1",
    ) = """
        {"expectedDepartureTime":"${isoOslo(atMillis)}","aimedDepartureTime":"${isoOslo(atMillis)}",
         "cancellation":false,"realtimeState":"updated",
         "destinationDisplay":{"frontText":"$destination"},
         "quay":{"id":"$quay"},
         "serviceJourney":{"line":{"publicCode":"$line","name":"$line","transportMode":"rail"},"notices":[]},
         "notices":[]}
        """.trimIndent()

    private fun departuresBody(vararg calls: String) =
        """{"data":{"stopPlace":{"id":"$stopId","name":"Oslo S","estimatedCalls":[${calls.joinToString(",")}]}}}"""

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun jsonOk(body: String) =
        MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

    @Test
    fun `successful fetch is live and populates the cache`() =
        runTest {
            val now = nowMillis()
            val engine =
                jsonOk(
                    departuresBody(
                        call("R13", "Drammen", now + 5 * minute),
                        call("L1", "Lillestrøm", now + 9 * minute),
                    ),
                )
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 15)

            assertFalse(result.isFromCache)
            assertNull(result.fetchedAtMillis)
            assertEquals(listOf("R13", "L1"), result.data.map { it.line })
            assertEquals(2, departureCache.rows.size)
        }

    @Test
    fun `network failure serves the cached board with its original fetch time`() =
        runTest {
            val now = nowMillis()
            val fetchedAt = now - 3 * minute
            departureCache.rows += cached("R13", "Drammen", now + 5 * minute, position = 0, fetchedAt = fetchedAt)
            val engine = MockEngine { throw IOException("offline") }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 15)

            assertTrue(result.isFromCache)
            assertEquals(fetchedAt, result.fetchedAtMillis)
            assertEquals(listOf("R13"), result.data.map { it.line })
        }

    @Test
    fun `departures that have already left are dropped from the cached board`() =
        runTest {
            val now = nowMillis()
            departureCache.rows +=
                listOf(
                    cached("R13", "Drammen", now - 2 * minute, position = 0), // already left
                    cached("L1", "Lillestrøm", now + 4 * minute, position = 1),
                )
            val engine = MockEngine { throw IOException("offline") }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 15)

            assertEquals(
                listOf("L1"),
                result.data.map { it.line },
                message = "a bus that already left is worse than no answer",
            )
        }

    @Test
    fun `network failure with nothing cached reports empty rather than stale`() =
        runTest {
            val engine = MockEngine { throw IOException("offline") }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 15)

            assertTrue(result.data.isEmpty())
            assertNull(result.fetchedAtMillis)
            assertTrue(result.failed, message = "empty because the fetch failed, not because nothing departs")
        }

    @Test
    fun `an empty live response is honest - not a cache fallback`() =
        runTest {
            val now = nowMillis()
            departureCache.rows += cached("R13", "Drammen", now + 5 * minute, position = 0)
            val repo = EnturRepositoryImpl(client(jsonOk(departuresBody())), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 15)

            // Entur answering "no departures" is a real answer; old ones must not be resurrected.
            assertTrue(result.data.isEmpty())
            assertNull(result.fetchedAtMillis)
            assertFalse(result.failed)
            assertTrue(departureCache.rows.isEmpty(), message = "the cache should mirror the last good response")
        }

    @Test
    fun `a response missing the stop falls back instead of wiping the cache`() =
        runTest {
            val now = nowMillis()
            departureCache.rows += cached("R13", "Drammen", now + 5 * minute, position = 0)
            val repo =
                EnturRepositoryImpl(
                    client(jsonOk("""{"data":{"stopPlace":null}}""")),
                    departureCache,
                    tripCache,
                )

            val result = repo.getDepartures(stopId, limit = 15)

            assertTrue(result.isFromCache, message = "an API-level failure is not an empty stop")
            assertEquals(listOf("R13"), result.data.map { it.line })
        }

    @Test
    fun `cached departures replay in Entur's order - not sorted by time`() =
        runTest {
            val now = nowMillis()
            val at = now + 20 * minute
            // Entur really does return identical calls interleaved with a different destination at the
            // same timestamp; re-sorting on the tied timestamp would regroup them and break list keys.
            val engine =
                jsonOk(
                    departuresBody(
                        call("L1", "Lillestrøm", at),
                        call("L1", "Lillestrøm", at),
                        call("L1", "Haugenstua", at),
                        call("L1", "Lillestrøm", at),
                    ),
                )
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            repo.getDepartures(stopId, limit = 15)

            assertEquals(listOf(0, 1, 2, 3), departureCache.rows.map { it.position })

            // Serve the same set back from the cache and confirm the order survived the round trip.
            val offline =
                EnturRepositoryImpl(
                    client(MockEngine { throw IOException("offline") }),
                    departureCache,
                    tripCache,
                )
            val result = offline.getDepartures(stopId, limit = 15)

            assertEquals(
                listOf("Lillestrøm", "Lillestrøm", "Haugenstua", "Lillestrøm"),
                result.data.map { it.destination },
            )
        }

    /** A by-quay response: the stop itself, then each quay's own calls (null for an unknown id). */
    private fun quaysBody(vararg quays: Pair<String, List<String>>?) =
        """{"data":{"stopPlace":{"id":"$stopId","name":"Oslo S"},"quays":[${
            quays.joinToString(",") { q ->
                q?.let { (id, calls) -> """{"id":"$id","estimatedCalls":[${calls.joinToString(",")}]}""" } ?: "null"
            }
        }]}}"""

    private fun variables(body: String) = Json.parseToJsonElement(body).jsonObject["variables"]!!.jsonObject

    @Test
    fun `a trip from coordinates is cached under a 4-decimal dot-separated key`() =
        runTest {
            val now = nowMillis()
            val destination = StopPlace(id = "NSR:StopPlace:2", name = "Lillestrøm")
            // The key a previous lookup from (59.9149, 10.7322) stored. It must not depend on the
            // device locale, which on a Norwegian phone would otherwise write "59,9149".
            tripCache.rows +=
                CachedTrip(
                    routeKey = "59.9149,10.7322>NSR:StopPlace:2",
                    destinationName = "Lillestrøm",
                    expectedStartTime = isoOslo(now + 10 * minute),
                    expectedStartAtMillis = now + 10 * minute,
                    lineName = "L1",
                    durationMinutes = 12,
                    originName = "Oslo S",
                    legs = emptyList(),
                    fetchedAt = now - 5 * minute,
                )
            val repo = EnturRepositoryImpl(client(MockEngine { throw IOException("offline") }), departureCache, tripCache)

            // A few metres away: the same key after rounding, so the same cached trip.
            val result = repo.getTripOptions(lat = 59.914_91, lon = 10.732_24, to = destination, count = 1)

            assertEquals(listOf("L1"), result.data.map { it.lineName })
            assertTrue(result.isFromCache)
        }

    @Test
    fun `selected quays are queried directly and their departures merged by time`() =
        runTest {
            val now = nowMillis()
            val bodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    bodies += (request.body as TextContent).text
                    respond(
                        quaysBody(
                            "NSR:Quay:1" to
                                listOf(
                                    call("L2", "Ski", now + 10 * minute, quay = "NSR:Quay:1"),
                                    call("L2", "Ski", now + 40 * minute, quay = "NSR:Quay:1"),
                                ),
                            null,
                            "NSR:Quay:2" to listOf(call("L2", "Skøyen", now + 20 * minute, quay = "NSR:Quay:2")),
                        ),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 2, quayIds = setOf("NSR:Quay:1", "NSR:Quay:2"))

            // Filtering the stop's next calls instead lets a busy bus terminal crowd out the trains.
            val vars = variables(bodies.single())
            assertTrue(vars["byQuay"]!!.jsonPrimitive.content.toBoolean())
            assertEquals(
                setOf("NSR:Quay:1", "NSR:Quay:2"),
                vars["quayIds"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
            )
            assertEquals(listOf("Ski", "Skøyen"), result.data.map { it.destination })
            assertEquals(listOf("Ski", "Skøyen"), departureCache.rows.map { it.destination })
        }

    @Test
    fun `without a quay selection the whole stop is queried`() =
        runTest {
            val bodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    bodies += (request.body as TextContent).text
                    respond(departuresBody(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            repo.getDepartures(stopId, limit = 15)

            assertFalse(variables(bodies.single())["byQuay"]!!.jsonPrimitive.content.toBoolean())
        }

    @Test
    fun `an empty quay selection shows nothing`() =
        runTest {
            val now = nowMillis()
            val engine = jsonOk(departuresBody(call("R13", "Drammen", now + 5 * minute)))
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 15, quayIds = emptySet())

            assertTrue(result.data.isEmpty())
        }

    @Test
    fun `time range is sent as a query variable`() =
        runTest {
            val bodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    bodies += (request.body as TextContent).text
                    respond(departuresBody(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            repo.getDepartures(stopId, limit = 15)
            repo.getDepartures(stopId, limit = 15, timeRangeSeconds = EnturRepository.EXTENDED_DEPARTURE_WINDOW_SECONDS)

            val ranges =
                bodies.map {
                    Json
                        .parseToJsonElement(it)
                        .jsonObject["variables"]!!
                        .jsonObject["timeRange"]!!
                        .jsonPrimitive.int
                }
            assertEquals(
                listOf(EnturRepository.DEFAULT_DEPARTURE_WINDOW_SECONDS, EnturRepository.EXTENDED_DEPARTURE_WINDOW_SECONDS),
                ranges,
            )
        }

    private fun cached(
        line: String,
        destination: String,
        atMillis: Long,
        position: Int,
        fetchedAt: Long = nowMillis(),
    ) = CachedDeparture(
        stopId = stopId,
        position = position,
        line = line,
        destination = destination,
        expectedTime = isoOslo(atMillis),
        aimedTime = isoOslo(atMillis),
        expectedAtMillis = atMillis,
        isRealtime = true,
        isCancelled = false,
        transportMode = "rail",
        delayMinutes = 0,
        notices = emptyList(),
        quayId = "NSR:Quay:1",
        fetchedAt = fetchedAt,
    )

    @Test
    fun `a cache fallback is stale - not failed`() =
        runTest {
            val now = nowMillis()
            departureCache.rows += cached("R13", "Drammen", now + 5 * minute, position = 0)
            val engine = MockEngine { throw IOException("offline") }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val result = repo.getDepartures(stopId, limit = 15)

            assertTrue(result.isFromCache)
            assertFalse(result.failed)
        }

    @Test
    fun `a trip that fails with nothing cached is reported as failed`() =
        runTest {
            val engine = MockEngine { throw IOException("offline") }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:1", name = "Oslo S")

            val byLocation = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 3)
            val byOrigins = repo.getFastestTrip(fromIds = listOf(stopId), to = to)

            assertTrue(byLocation.data.isEmpty())
            assertTrue(byLocation.failed)
            assertNull(byOrigins.data)
            assertTrue(byOrigins.failed)
        }

    @Test
    fun `a trip carries its arrival time live and from the cache`() =
        runTest {
            val now = nowMillis()
            val start = isoOslo(now + 10 * minute)
            val end = isoOslo(now + 52 * minute)
            val body =
                """{"data":{"trip":{"tripPatterns":[{"expectedStartTime":"$start","expectedEndTime":"$end",
                   "duration":2520,"legs":[{"mode":"metro","duration":2520,
                   "line":{"publicCode":"2","name":"Ellingsrudåsen"},"fromPlace":{"name":"Oslo S"}}]}]}}}"""
            var online = true
            val engine =
                MockEngine {
                    if (!online) throw IOException("offline")
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            val live = repo.getFastestTrip(fromIds = listOf(stopId), to = to)
            online = false
            val cached = repo.getFastestTrip(fromIds = listOf(stopId), to = to)

            assertEquals(end, live.data?.expectedEndTime)
            assertFalse(live.isFromCache)
            assertEquals(end, cached.data?.expectedEndTime)
            assertTrue(cached.isFromCache)
        }

    @Test
    fun `no trip found is an answer - not a failure`() =
        runTest {
            val repo =
                EnturRepositoryImpl(
                    client(jsonOk("""{"data":{"trip":{"tripPatterns":[]}}}""")),
                    departureCache,
                    tripCache,
                )
            val to = StopPlace(id = "NSR:StopPlace:1", name = "Oslo S")

            val byLocation = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 3)
            val byOrigins = repo.getFastestTrip(fromIds = listOf(stopId), to = to)

            assertTrue(byLocation.data.isEmpty())
            assertFalse(byLocation.failed)
            assertNull(byOrigins.data)
            assertFalse(byOrigins.failed)
        }

    @Test
    fun `route alternatives keep Entur's order live and from the cache`() =
        runTest {
            val now = nowMillis()

            fun pattern(
                line: String,
                startMinutes: Int,
            ) = """{"expectedStartTime":"${isoOslo(now + startMinutes * minute)}","duration":1200,
                   "legs":[{"mode":"bus","duration":1200,"line":{"publicCode":"$line","name":"$line"},
                   "fromPlace":{"name":"Oslo S"}}]}"""
            val body =
                """{"data":{"trip":{"tripPatterns":[${pattern("31", 5)},${pattern("2", 3)},${pattern("20", 9)}]}}}"""
            val bodies = mutableListOf<String>()
            var online = true
            val engine =
                MockEngine { request ->
                    if (!online) throw IOException("offline")
                    bodies += (request.body as TextContent).text
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            val live = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 3)
            online = false
            val cached = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 3)

            assertEquals("3", variables(bodies.first())["count"].toString())
            assertEquals(listOf("31", "2", "20"), live.data.map { it.lineName })
            assertFalse(live.isFromCache)
            assertEquals(listOf("31", "2", "20"), cached.data.map { it.lineName })
            assertTrue(cached.isFromCache)

            // Entur may return more patterns than asked for; only the first `count` are kept.
            online = true
            assertEquals(listOf("31", "2"), repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 2).data.map { it.lineName })
        }

    @Test
    fun `quays carry every line and their child stop and empty bays are dropped`() =
        runTest {
            val body =
                """
                {"data":{"stopPlace":{"id":"$stopId","name":"Oslo S","quays":[
                  {"id":"NSR:Quay:562","publicCode":"16","name":"Oslo S",
                   "stopPlace":{"id":"NSR:StopPlace:337","name":"Oslo S"},
                   "lines":[{"publicCode":"R23"},{"publicCode":"L2x"},{"publicCode":"L2"},{"publicCode":"R23"}],
                   "estimatedCalls":[{"destinationDisplay":{"frontText":"Ski"}},
                                     {"destinationDisplay":{"frontText":"Halden"}},
                                     {"destinationDisplay":{"frontText":"Ski"}}]},
                  {"id":"NSR:Quay:9","description":"Taxi",
                   "stopPlace":{"id":"NSR:StopPlace:2","name":"Oslo S Trelastgata"},
                   "lines":[],"estimatedCalls":[]}
                ]}}}
                """.trimIndent()
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)

            val quays = repo.getStopPlaceWithQuays(stopId)!!.quays

            assertEquals(listOf("NSR:Quay:562"), quays.map { it.id })
            assertEquals(listOf("L2", "L2x", "R23"), quays.single().lines)
            assertEquals(listOf("Ski", "Halden"), quays.single().destinations)
            assertEquals("NSR:StopPlace:337", quays.single().stopPlaceId)
        }
}

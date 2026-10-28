package com.example.pulsetransit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a departure's details need from Entur: its trip and line, and the trip's stops. */
class EnturDepartureDetailsTest {
    private val departureCache = FakeDepartureCacheDao()
    private val tripCache = FakeTripCacheDao()
    private val stopId = "NSR:StopPlace:5719"
    private val minute = 60 * 1000L

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun repo(engine: MockEngine) = EnturRepositoryImpl(client(engine), departureCache, tripCache)

    private fun ok(body: String) =
        MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

    @Test
    fun `departures carry their trip and line details live and from the cache`() =
        runTest {
            val at = isoOslo(nowMillis() + 10 * minute)
            val body =
                """{"data":{"stopPlace":{"id":"$stopId","name":"Kantarellen legesenter","estimatedCalls":[
                   {"expectedDepartureTime":"$at","aimedDepartureTime":"$at","cancellation":false,
                    "realtimeState":"updated","date":"2026-10-16",
                    "destinationDisplay":{"frontText":"Holmlia stasjon"},
                    "quay":{"id":"NSR:Quay:10487","publicCode":"B"},
                    "serviceJourney":{"id":"RUT:ServiceJourney:1",
                      "line":{"publicCode":"73","name":"Holmlia stasjon - Brenna","transportMode":"bus",
                              "authority":{"name":"Ruter"},"operator":{"name":"Connect Bus AS"}},
                      "notices":[]},
                    "notices":[]}]}}}"""
            var online = true
            val engine =
                MockEngine {
                    if (!online) throw IOException("offline")
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = repo(engine)

            val live = repo.getDepartures(stopId, limit = 15)
            online = false
            val cached = repo.getDepartures(stopId, limit = 15)

            assertFalse(live.isFromCache)
            assertTrue(cached.isFromCache)
            for (departure in listOf(live.data.single(), cached.data.single())) {
                assertEquals("B", departure.platform)
                assertEquals("Holmlia stasjon - Brenna", departure.lineName)
                assertEquals("Ruter", departure.authority)
                assertEquals("Connect Bus AS", departure.operator)
                assertEquals("RUT:ServiceJourney:1", departure.serviceJourneyId)
                assertEquals("2026-10-16", departure.serviceDate)
            }
        }

    @Test
    fun `a blank platform code is no platform`() =
        runTest {
            val at = isoOslo(nowMillis() + 10 * minute)
            val body =
                """{"data":{"stopPlace":{"id":"$stopId","name":"Kantarellen legesenter","estimatedCalls":[
                   {"expectedDepartureTime":"$at","aimedDepartureTime":"$at","cancellation":false,
                    "destinationDisplay":{"frontText":"Brenna"},"quay":{"id":"NSR:Quay:10486","publicCode":""},
                    "serviceJourney":{"line":{"publicCode":"73"}},"notices":[]}]}}}"""

            val departure = repo(ok(body)).getDepartures(stopId, limit = 15).data.single()

            assertEquals(null, departure.platform)
        }

    @Test
    fun `a trip's stops are asked for by id and date with the client name`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        """{"data":{"serviceJourney":{"estimatedCalls":[]}}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            repo(engine).getJourneyStops("RUT:ServiceJourney:1", "2026-10-16")

            val request = requests.single()
            assertTrue(request.headers["ET-Client-Name"]?.isNotBlank() == true)
            val variables =
                Json
                    .parseToJsonElement((request.body as TextContent).text)
                    .jsonObject["variables"]!!
                    .jsonObject
            assertEquals("RUT:ServiceJourney:1", variables["id"]!!.jsonPrimitive.content)
            assertEquals("2026-10-16", variables["date"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a trip's stops use arrival times and stop names and skip calls missing either`() =
        runTest {
            val body =
                """{"data":{"serviceJourney":{"estimatedCalls":[
                   {"expectedDepartureTime":"2026-10-16T20:29:00+02:00",
                    "quay":{"id":"Q1","name":"Mortensrud quay","stopPlace":{"id":"S1","name":"Mortensrud T"}}},
                   {"expectedArrivalTime":"2026-10-16T20:32:00+02:00","expectedDepartureTime":"2026-10-16T20:33:00+02:00",
                    "quay":{"id":"Q2","name":"Kantarellen legesenter"}},
                   {"quay":{"id":"Q3","stopPlace":{"id":"S3","name":"No time"}}},
                   {"expectedArrivalTime":"2026-10-16T20:42:00+02:00","cancellation":true,"quay":{"id":"Q4"}},
                   {"expectedArrivalTime":"2026-10-16T20:45:00+02:00","cancellation":true,
                    "quay":{"id":"Q5","stopPlace":{"id":"S5","name":"Holmlia stasjon"}}}
                ]}}}"""

            val stops = repo(ok(body)).getJourneyStops("RUT:ServiceJourney:1", "2026-10-16")

            assertEquals(
                listOf(
                    JourneyStop("Q1", "S1", "Mortensrud T", "2026-10-16T20:29:00+02:00"),
                    JourneyStop("Q2", null, "Kantarellen legesenter", "2026-10-16T20:32:00+02:00"),
                    JourneyStop("Q5", "S5", "Holmlia stasjon", "2026-10-16T20:45:00+02:00", isCancelled = true),
                ),
                stops,
            )
        }

    @Test
    fun `a trip's stops carry their quay's position for journey alerts`() =
        runTest {
            val body =
                """{"data":{"serviceJourney":{"estimatedCalls":[
                   {"expectedDepartureTime":"2026-10-16T20:29:00+02:00",
                    "quay":{"id":"Q1","latitude":59.8475,"longitude":10.8269,"stopPlace":{"id":"S1","name":"Mortensrud T"}}},
                   {"expectedArrivalTime":"2026-10-16T20:32:00+02:00","quay":{"id":"Q2","name":"No position"}}
                ]}}}"""

            val stops = repo(ok(body)).getJourneyStops("RUT:ServiceJourney:1", "2026-10-16")

            assertEquals(59.8475 to 10.8269, stops[0].latitude to stops[0].longitude)
            assertEquals(null to null, stops[1].latitude to stops[1].longitude)
        }

    @Test
    fun `route legs carry where they start and end and keep it in the cache`() =
        runTest {
            val body =
                """{"data":{"trip":{"tripPatterns":[{"expectedStartTime":"2026-10-16T20:29:00+02:00",
                   "expectedEndTime":"2026-10-16T20:50:00+02:00","duration":1260,"legs":[
                   {"mode":"foot","duration":300,"distance":400,
                    "fromPlace":{"name":"Origin","latitude":59.91,"longitude":10.75},
                    "toPlace":{"name":"Destination","latitude":59.92,"longitude":10.67}}]}]}}}"""

            val trip =
                repo(ok(body))
                    .getTripOptions(59.91, 10.75, StopPlace(id = "NSR:StopPlace:1", name = "Home"), count = 1)
                    .data
                    .single()
            val leg = trip.legs.single()

            assertEquals(listOf(59.91, 10.75, 59.92, 10.67), listOf(leg.fromLatitude, leg.fromLongitude, leg.toLatitude, leg.toLongitude))
            assertEquals(leg, leg.toCacheEntity().toTripLeg())
        }

    @Test
    fun `a trip whose stops cannot be fetched has none`() =
        runTest {
            val stops = repo(MockEngine { throw IOException("offline") }).getJourneyStops("RUT:ServiceJourney:1", "2026-10-16")

            assertEquals(emptyList(), stops)
        }
}

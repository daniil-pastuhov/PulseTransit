package com.example.pulsetransit.data

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
import kotlin.test.assertTrue

/** How [RouteOptions] reach Entur's trip queries, and how the answers are ranked and cached. */
class EnturRouteOptionsTest {
    private val departureCache = FakeDepartureCacheDao()
    private val tripCache = FakeTripCacheDao()
    private val stopId = "NSR:StopPlace:59872"
    private val minute = 60 * 1000L

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun jsonOk(body: String) =
        MockEngine {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

    private fun variables(body: String) = Json.parseToJsonElement(body).jsonObject["variables"]!!.jsonObject

    @Test
    fun `default route options send only the route count`() =
        runTest {
            val bodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    bodies += (request.body as TextContent).text
                    respond(
                        """{"data":{"trip":{"tripPatterns":[]}}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            repo.getFastestTrip(fromIds = listOf(stopId), to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen"))

            assertEquals(setOf("from", "to", "count"), variables(bodies.single()).keys)
            assertEquals(1, variables(bodies.single())["count"]!!.jsonPrimitive.int)
        }

    private val fewerTransfersAvoiding31 =
        RouteOptions(
            preference = RoutePreference.FEWER_TRANSFERS,
            excludedModes = setOf(RouteTransportMode.EXPRESS_BOAT),
            bannedLines = setOf("31"),
        )

    /**
     * Answers line lookups with two lines numbered 31 and trips with three routes, the earliest
     * with a change, recording each request body in [bodies].
     */
    private fun routeOptionsEngine(
        bodies: MutableList<String>,
        online: () -> Boolean = { true },
    ): MockEngine {
        val now = nowMillis()

        fun pattern(
            arrivalMinutes: Int,
            vararg lines: String,
        ): String {
            val legs = lines.joinToString(",") { """{"mode":"bus","duration":600,"line":{"publicCode":"$it"}}""" }
            return """{"expectedStartTime":"${isoOslo(now + 5 * minute)}",
               "expectedEndTime":"${isoOslo(now + arrivalMinutes * minute)}","duration":1200,"legs":[$legs]}"""
        }
        val tripBody = """{"data":{"trip":{"tripPatterns":[${pattern(30, "66", "L1")},${pattern(40, "5")},${pattern(50, "20")}]}}}"""
        val linesBody = """{"data":{"lines":[{"id":"RUT:Line:31","publicCode":"31"},{"id":"SKY:Line:31","publicCode":"31"}]}}"""
        return MockEngine { request ->
            if (!online()) throw IOException("offline")
            val body = (request.body as TextContent).text
            bodies += body
            val answer = if ("lines(" in body) linesBody else tripBody
            respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    @Test
    fun `route options reach Entur as one exclusion filter with filtered lines by id`() =
        runTest {
            val bodies = mutableListOf<String>()
            val repo = EnturRepositoryImpl(client(routeOptionsEngine(bodies)), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 2, options = fewerTransfersAvoiding31)
            repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 2, options = fewerTransfersAvoiding31)

            assertEquals(1, bodies.count { "lines(" in it }, "line ids are looked up once")
            val trip = variables(bodies.last())
            assertEquals(RouteOptions.RANKING_CANDIDATES, trip["count"]!!.jsonPrimitive.int, "a re-ranked search asks for more")
            assertEquals(RouteOptions.FEWER_TRANSFERS_PENALTY, trip["transferPenalty"]!!.jsonPrimitive.int)
            val excluded =
                trip["filters"]!!
                    .jsonArray
                    .single()
                    .jsonObject["not"]!!
                    .jsonArray
            val excludedMode =
                excluded[0]
                    .jsonObject["transportModes"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("water", excludedMode["transportMode"]!!.jsonPrimitive.content)
            assertEquals(
                listOf("highSpeedPassengerService"),
                excludedMode["transportSubModes"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
            assertEquals(listOf("RUT:Line:31", "SKY:Line:31"), excluded[1].jsonObject["lines"]!!.jsonArray.map { it.jsonPrimitive.content })
        }

    @Test
    fun `re-ranked routes are cached under their own options`() =
        runTest {
            var online = true
            val repo = EnturRepositoryImpl(client(routeOptionsEngine(mutableListOf()) { online }), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            val live = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 2, options = fewerTransfersAvoiding31)
            online = false
            val cached = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 2, options = fewerTransfersAvoiding31)
            val withDefaults = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 2)

            assertEquals(listOf("5", "20"), live.data.map { it.lineName }, "direct routes first, then by arrival")
            assertEquals(listOf("5", "20"), cached.data.map { it.lineName })
            assertTrue(cached.isFromCache)
            assertTrue(withDefaults.failed, "routes cached under other options are not served for the defaults")
        }

    @Test
    fun `the best route from the origins follows the preference`() =
        runTest {
            val now = nowMillis()
            val body =
                """{"data":{"trip":{"tripPatterns":[
                   {"expectedStartTime":"${isoOslo(now + 2 * minute)}","expectedEndTime":"${isoOslo(now + 20 * minute)}","duration":1080,
                    "legs":[{"mode":"foot","duration":300,"distance":900},{"mode":"bus","duration":780,"line":{"publicCode":"31"}}]},
                   {"expectedStartTime":"${isoOslo(now + 4 * minute)}","expectedEndTime":"${isoOslo(now + 25 * minute)}","duration":1260,
                    "legs":[{"mode":"foot","duration":60,"distance":80},{"mode":"bus","duration":1200,"line":{"publicCode":"20"}}]}]}}}"""
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            val fastest = repo.getFastestTrip(fromIds = listOf(stopId), to = to)
            val lessWalking = repo.getFastestTrip(fromIds = listOf(stopId), to = to, options = RouteOptions(RoutePreference.LESS_WALKING))

            assertEquals("31", fastest.data?.lineName)
            assertEquals("20", lessWalking.data?.lineName)
        }

    @Test
    fun `less walking sends its walk reluctance and no transfer penalty`() =
        runTest {
            val bodies = mutableListOf<String>()
            val repo = EnturRepositoryImpl(client(routeOptionsEngine(bodies)), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            repo.getFastestTrip(fromIds = listOf(stopId), to = to, options = RouteOptions(RoutePreference.LESS_WALKING))

            val trip = variables(bodies.single())
            assertEquals(RouteOptions.LESS_WALKING_RELUCTANCE, trip["walkReluctance"]!!.jsonPrimitive.content.toDouble())
            assertFalse("transferPenalty" in trip)
            assertFalse("filters" in trip, "nothing excluded, so no filter")
            assertFalse("walkSpeed" in trip, "a normal pace is left to Entur")
        }

    @Test
    fun `a walking speed other than normal is sent in metres per second`() =
        runTest {
            val bodies = mutableListOf<String>()
            val repo = EnturRepositoryImpl(client(routeOptionsEngine(bodies)), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            repo.getFastestTrip(fromIds = listOf(stopId), to = to, options = RouteOptions(walkingSpeed = WalkingSpeed.SLOW))
            repo.getFastestTrip(fromIds = listOf(stopId), to = to, options = RouteOptions(walkingSpeed = WalkingSpeed.FAST))

            assertEquals(listOf(1.0, 1.7), bodies.map { variables(it)["walkSpeed"]!!.jsonPrimitive.content.toDouble() })
        }

    @Test
    fun `across origins a preset picks by its own measure - not the earliest start`() =
        runTest {
            val now = nowMillis()

            // From the first origin: leaves first but walks 900 m. From the second: leaves later, walks 80 m.
            fun body(
                startMinutes: Int,
                walkMeters: Int,
                line: String,
            ) = """{"data":{"trip":{"tripPatterns":[{"expectedStartTime":"${isoOslo(now + startMinutes * minute)}",
                   "expectedEndTime":"${isoOslo(now + 30 * minute)}","duration":1200,
                   "legs":[{"mode":"foot","duration":300,"distance":$walkMeters},
                           {"mode":"bus","duration":900,"line":{"publicCode":"$line"}}]}]}}}"""
            val engine =
                MockEngine { request ->
                    val from = variables((request.body as TextContent).text)["from"]!!.jsonPrimitive.content
                    val answer = if (from == "NSR:StopPlace:A") body(2, 900, "31") else body(6, 80, "20")
                    respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")
            val origins = listOf("NSR:StopPlace:A", "NSR:StopPlace:B")

            assertEquals("31", repo.getFastestTrip(fromIds = origins, to = to).data?.lineName, "fastest keeps the earliest start")
            assertEquals(
                "20",
                repo.getFastestTrip(fromIds = origins, to = to, options = RouteOptions(RoutePreference.LESS_WALKING)).data?.lineName,
            )
        }
}

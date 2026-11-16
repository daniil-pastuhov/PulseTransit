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
    fun `default route options send only the route count with room for the walk`() =
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
            // The board's routes, whoever asks (see the cache tests below), and one for the walk.
            assertEquals(BoardRouteAlternatives.ROUTES_PER_DESTINATION + 1, variables(bodies.single())["count"]!!.jsonPrimitive.int)
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

    @Test
    fun `a chosen time reaches Entur and keeps its routes apart in the cache`() =
        runTest {
            val bodies = mutableListOf<String>()
            val repo = EnturRepositoryImpl(client(routeOptionsEngine(bodies)), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")
            val at = 1_800_000_000_000L

            repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 1, options = RouteOptions())
            repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 1, options = RouteOptions(time = TripTime.DepartAt(at)))
            repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 1, options = RouteOptions(time = TripTime.ArriveBy(at)))

            val (now, departAt, arriveBy) = bodies.map(::variables)
            assertFalse("dateTime" in now || "arriveBy" in now, "now is Entur's default")
            assertEquals(enturDateTime(at), departAt["dateTime"]!!.jsonPrimitive.content)
            assertFalse("arriveBy" in departAt)
            assertEquals(enturDateTime(at), arriveBy["dateTime"]!!.jsonPrimitive.content)
            assertTrue(arriveBy["arriveBy"]!!.jsonPrimitive.content.toBoolean())
            val keys =
                listOf(RouteOptions(), RouteOptions(time = TripTime.DepartAt(at)), RouteOptions(time = TripTime.ArriveBy(at)))
                    .map { it.cacheKeySuffix }
            assertEquals(3, keys.toSet().size)
            assertEquals("", keys.first(), "routes for now keep their old key")
        }

    @Test
    fun `to arrive by a time routes that have already left are dropped and the latest to leave wins`() =
        runTest {
            val now = nowMillis()

            fun pattern(
                startMinutes: Int,
                line: String,
            ) = """{"expectedStartTime":"${isoOslo(now + startMinutes * minute)}",
                   "expectedEndTime":"${isoOslo(now + 40 * minute)}","duration":1200,
                   "legs":[{"mode":"bus","duration":1200,"line":{"publicCode":"$line"}}]}"""
            val engine =
                MockEngine { request ->
                    val from = variables((request.body as TextContent).text)["from"]?.jsonPrimitive?.content
                    val patterns =
                        when (from) {
                            "NSR:StopPlace:A" -> listOf(pattern(10, "1"), pattern(-5, "gone"))
                            "NSR:StopPlace:B" -> listOf(pattern(15, "2"))
                            else -> listOf(pattern(-2, "gone"), pattern(8, "3"))
                        }
                    respond(
                        """{"data":{"trip":{"tripPatterns":[${patterns.joinToString(",")}]}}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")
            val options = RouteOptions(time = TripTime.ArriveBy(now + 45 * minute))

            val options2 = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 2, options = options)
            assertEquals(listOf("3"), options2.data.map { it.lineName })
            val fastest = repo.getFastestTrip(fromIds = listOf("NSR:StopPlace:A", "NSR:StopPlace:B"), to = to, options = options)
            assertEquals("2", fastest.data?.lineName)
        }

    /** A route leaving and arriving these minutes from [now], on foot when [line] is null. */
    private fun route(
        now: Long,
        startMinutes: Int,
        endMinutes: Int,
        line: String? = null,
        journey: String? = null,
    ): String {
        val foot = """{"mode":"foot","duration":300}"""
        val ride = journey?.let { ""","serviceJourney":{"id":"$it"}""" }.orEmpty()
        val legs = if (line == null) foot else """$foot,{"mode":"bus","duration":300,"line":{"publicCode":"$line"}$ride}"""
        return """{"expectedStartTime":"${isoOslo(now + startMinutes * minute)}",
           "expectedEndTime":"${isoOslo(now + endMinutes * minute)}","duration":600,"legs":[$legs]}"""
    }

    private fun routes(vararg routes: String) = """{"data":{"trip":{"tripPatterns":[${routes.joinToString(",")}]}}}"""

    private val majorstuen = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

    @Test
    fun `a walk that a ride beats is not offered`() =
        runTest {
            val now = nowMillis()
            val body = routes(route(now, 0, 27), route(now, 7, 14, "5"), route(now, 10, 30, "23"))
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)

            val trips = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 1)

            assertEquals(listOf("5"), trips.data.map { it.lineName })
            assertEquals("5", repo.getFastestTrip(fromIds = listOf(stopId), to = majorstuen).data?.lineName)
        }

    @Test
    fun `a walk stays when it is the only route`() =
        runTest {
            val body = routes(route(nowMillis(), 0, 27))
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)

            val trips = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 3)

            assertEquals(listOf("?"), trips.data.map { it.lineName })
        }

    @Test
    fun `a walk stays when it arrives no later than any ride`() =
        runTest {
            val now = nowMillis()
            val body = routes(route(now, 0, 6), route(now, 2, 6, "5"), route(now, 8, 15, "23"))
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)

            val trips = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 3)

            assertEquals(listOf("?", "5", "23"), trips.data.map { it.lineName })
        }

    @Test
    fun `a walk with an unreadable arrival stays`() =
        runTest {
            val now = nowMillis()
            val walk = """{"expectedStartTime":"${isoOslo(now)}","expectedEndTime":"later","duration":600,
               "legs":[{"mode":"foot","duration":600}]}"""
            val repo = EnturRepositoryImpl(client(jsonOk(routes(walk, route(now, 2, 6, "5")))), departureCache, tripCache)

            val trips = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 2)

            assertEquals(listOf("?", "5"), trips.data.map { it.lineName })
        }

    @Test
    fun `to arrive by a time a walk gives way only to a ride leaving later`() =
        runTest {
            val now = nowMillis()
            val options = RouteOptions(time = TripTime.ArriveBy(now + 40 * minute))

            suspend fun lines(vararg routes: String) =
                EnturRepositoryImpl(client(jsonOk(routes(*routes))), departureCache, tripCache)
                    .getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 3, options = options)
                    .data
                    .map { it.lineName }

            assertEquals(listOf("5"), lines(route(now, 10, 40), route(now, 15, 38, "5")))
            assertEquals(listOf("?", "5"), lines(route(now, 10, 40), route(now, 5, 38, "5")))
        }

    @Test
    fun `across origins the walk from one gives way to a ride from another`() =
        runTest {
            val now = nowMillis()
            val engine =
                MockEngine { request ->
                    val from = variables((request.body as TextContent).text)["from"]?.jsonPrimitive?.content
                    val body = if (from == "NSR:StopPlace:A") routes(route(now, 0, 27)) else routes(route(now, 7, 14, "5"))
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val trip = repo.getFastestTrip(fromIds = listOf("NSR:StopPlace:A", "NSR:StopPlace:B"), to = majorstuen)

            assertEquals("5", trip.data?.lineName)
        }

    @Test
    fun `a ride with an unreadable arrival does not beat a walk`() =
        runTest {
            val now = nowMillis()
            val ride = """{"expectedStartTime":"${isoOslo(now + 2 * minute)}","expectedEndTime":"later","duration":600,
               "legs":[{"mode":"bus","duration":600,"line":{"publicCode":"5"}}]}"""
            val repo = EnturRepositoryImpl(client(jsonOk(routes(route(now, 0, 27), ride))), departureCache, tripCache)

            val trips = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 2)

            assertEquals(listOf("?", "5"), trips.data.map { it.lineName })
        }

    @Test
    fun `a route without legs is not taken for a walk`() =
        runTest {
            val now = nowMillis()
            val legless = """{"expectedStartTime":"${isoOslo(now)}","expectedEndTime":"${isoOslo(now + 27 * minute)}",
               "duration":600,"legs":[]}"""
            val repo = EnturRepositoryImpl(client(jsonOk(routes(legless, route(now, 7, 14, "5")))), departureCache, tripCache)

            val trips = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 2)

            assertEquals(listOf("?", "5"), trips.data.map { it.lineName })
        }

    @Test
    fun `a re-ranked search for the most routes still leaves room for the walk`() =
        runTest {
            val bodies = mutableListOf<String>()
            val repo = EnturRepositoryImpl(client(routeOptionsEngine(bodies)), departureCache, tripCache)
            val options = RouteOptions(preference = RoutePreference.FEWER_TRANSFERS)

            repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = RouteOptions.RANKING_CANDIDATES, options = options)

            assertEquals(RouteOptions.RANKING_CANDIDATES + 1, variables(bodies.single())["count"]!!.jsonPrimitive.int)
        }

    @Test
    fun `from one origin a walk arriving with a ride stays the best route`() =
        runTest {
            val now = nowMillis()
            val body = routes(route(now, 0, 6), route(now, 2, 6, "5"))
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)

            assertEquals("?", repo.getFastestTrip(fromIds = listOf(stopId), to = majorstuen).data?.lineName)
        }

    @Test
    fun `from one origin to arrive by a time a walk leaving last stays the best route`() =
        runTest {
            val now = nowMillis()
            val body = routes(route(now, 10, 40), route(now, 5, 38, "5"))
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)
            val options = RouteOptions(time = TripTime.ArriveBy(now + 40 * minute))

            val trip = repo.getFastestTrip(fromIds = listOf(stopId), to = majorstuen, options = options)

            assertEquals("?", trip.data?.lineName)
        }

    /** Answers origin A with [fromA] and any other with [fromB]. */
    private fun twoOrigins(
        fromA: String,
        fromB: String,
    ) = MockEngine { request ->
        val from = variables((request.body as TextContent).text)["from"]?.jsonPrimitive?.content
        respond(routes(if (from == "NSR:StopPlace:A") fromA else fromB), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @Test
    fun `across origins a walk arriving before the ride from another stays`() =
        runTest {
            val now = nowMillis()
            val engine = twoOrigins(fromA = route(now, 0, 14), fromB = route(now, 7, 20, "5"))
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val trip = repo.getFastestTrip(fromIds = listOf("NSR:StopPlace:A", "NSR:StopPlace:B"), to = majorstuen)

            assertEquals("?", trip.data?.lineName)
        }

    @Test
    fun `across origins to arrive by a time a walk leaving last stays`() =
        runTest {
            val now = nowMillis()
            val engine = twoOrigins(fromA = route(now, 10, 40), fromB = route(now, 5, 38, "5"))
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            val options = RouteOptions(time = TripTime.ArriveBy(now + 40 * minute))

            val trip = repo.getFastestTrip(fromIds = listOf("NSR:StopPlace:A", "NSR:StopPlace:B"), to = majorstuen, options = options)

            assertEquals("?", trip.data?.lineName)
        }

    @Test
    fun `an origin whose only route to arrive by a time has left gives no route`() =
        runTest {
            val now = nowMillis()
            val body = """{"data":{"trip":{"tripPatterns":[{"expectedStartTime":"${isoOslo(now - 2 * minute)}",
               "expectedEndTime":"${isoOslo(now + 20 * minute)}","duration":1320,
               "legs":[{"mode":"bus","duration":1320,"line":{"publicCode":"gone"}}]}]}}}"""
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            val trip = repo.getFastestTrip(fromIds = listOf(stopId), to = to, options = RouteOptions(time = TripTime.ArriveBy(now + 25 * minute)))

            assertEquals(null, trip.data)
        }

    @Test
    fun `to arrive by a time a route with an unreadable start is kept`() =
        runTest {
            val body = """{"data":{"trip":{"tripPatterns":[{"expectedStartTime":"soon","expectedEndTime":"later","duration":600,
               "legs":[{"mode":"bus","duration":600,"line":{"publicCode":"7"}}]}]}}}"""
            val repo = EnturRepositoryImpl(client(jsonOk(body)), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")

            val trips = repo.getTripOptions(lat = 59.91, lon = 10.75, to = to, count = 1, options = RouteOptions(time = TripTime.ArriveBy(nowMillis() + 30 * minute)))

            assertEquals(listOf("7"), trips.data.map { it.lineName })
        }

    @Test
    fun `across origins a preset breaks ties by leaving latest to arrive by a time`() =
        runTest {
            val now = nowMillis()

            fun body(
                startMinutes: Int,
                line: String,
            ) = """{"data":{"trip":{"tripPatterns":[{"expectedStartTime":"${isoOslo(now + startMinutes * minute)}",
                   "expectedEndTime":"${isoOslo(now + (startMinutes + 20) * minute)}","duration":1200,
                   "legs":[{"mode":"bus","duration":1200,"line":{"publicCode":"$line"}}]}]}}}"""
            val engine =
                MockEngine { request ->
                    val from = variables((request.body as TextContent).text)["from"]!!.jsonPrimitive.content
                    val answer = if (from == "NSR:StopPlace:A") body(5, "early") else body(12, "late")
                    respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)
            val to = StopPlace(id = "NSR:StopPlace:3", name = "Majorstuen")
            val options = RouteOptions(preference = RoutePreference.FEWER_TRANSFERS, time = TripTime.ArriveBy(now + 40 * minute))

            val trip = repo.getFastestTrip(fromIds = listOf("NSR:StopPlace:A", "NSR:StopPlace:B"), to = to, options = options)

            assertEquals("late", trip.data?.lineName)
        }

    /** From the first origin the 31 at +4 and +12; from the second the 5 at +2, +8 and +14. */
    private fun twoOriginsEngine(
        bodies: MutableList<String>,
        online: () -> Boolean = { true },
    ): MockEngine {
        val now = nowMillis()
        return MockEngine { request ->
            if (!online()) throw IOException("offline")
            val body = (request.body as TextContent).text
            bodies += body
            val from = variables(body)["from"]!!.jsonPrimitive.content
            val answer =
                if (from == "NSR:StopPlace:A") {
                    routes(route(now, 4, 20, "31"), route(now, 12, 28, "31"))
                } else {
                    routes(route(now, 2, 18, "5"), route(now, 8, 24, "5"), route(now, 14, 30, "5"))
                }
            respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    @Test
    fun `from the favourites several routes are merged across origins in one request each`() =
        runTest {
            val bodies = mutableListOf<String>()
            val repo = EnturRepositoryImpl(client(twoOriginsEngine(bodies)), departureCache, tripCache)

            val trips = repo.getTripOptionsFromStops(fromIds = listOf("NSR:StopPlace:A", "NSR:StopPlace:B"), to = majorstuen, count = 4)

            assertEquals(listOf("5", "31", "5", "31"), trips.data.map { it.lineName }, "earliest first, the same line's later ones too")
            assertEquals(2, bodies.size, "one request per origin, as for one route")
            assertEquals(listOf(5, 5), bodies.map { variables(it)["count"]!!.jsonPrimitive.int }, "each asks for the routes and the walk")
            assertFalse(trips.isFromCache)
        }

    @Test
    fun `from the favourites one route is still the earliest across origins`() =
        runTest {
            val bodies = mutableListOf<String>()
            val repo = EnturRepositoryImpl(client(twoOriginsEngine(bodies)), departureCache, tripCache)
            val origins = listOf("NSR:StopPlace:A", "NSR:StopPlace:B")

            assertEquals("5", repo.getFastestTrip(fromIds = origins, to = majorstuen).data?.lineName)
            assertEquals(2, bodies.size, "one request per origin")
        }

    @Test
    fun `routes from the favourites are cached together and served offline`() =
        runTest {
            var online = true
            val repo = EnturRepositoryImpl(client(twoOriginsEngine(mutableListOf()) { online }), departureCache, tripCache)
            val origins = listOf("NSR:StopPlace:A", "NSR:StopPlace:B")

            val live = repo.getTripOptionsFromStops(fromIds = origins, to = majorstuen, count = 4)
            online = false
            val cached = repo.getTripOptionsFromStops(fromIds = origins.reversed(), to = majorstuen, count = 4)
            val first = repo.getTripOptionsFromStops(fromIds = origins, to = majorstuen, count = 1)

            assertEquals(live.data.map { it.expectedStartTime }, cached.data.map { it.expectedStartTime })
            assertEquals(listOf("5", "31", "5", "31"), cached.data.map { it.lineName })
            assertTrue(cached.isFromCache)
            assertFalse(cached.failed)
            assertEquals(listOf("5"), first.data.map { it.lineName }, "fewer asked, fewer served")
            assertEquals("5", repo.getFastestTrip(fromIds = origins, to = majorstuen).data?.lineName)
        }

    @Test
    fun `routes from the favourites with nothing cached fail as unknown`() =
        runTest {
            val repo = EnturRepositoryImpl(client(twoOriginsEngine(mutableListOf()) { false }), departureCache, tripCache)

            val trips = repo.getTripOptionsFromStops(fromIds = listOf("NSR:StopPlace:A"), to = majorstuen, count = 4)

            assertTrue(trips.failed)
            assertEquals(emptyList(), trips.data)
        }

    @Test
    fun `a widget asking for one route leaves the board's cached alternatives from the favourites`() =
        runTest {
            val bodies = mutableListOf<String>()
            var online = true
            val repo = EnturRepositoryImpl(client(twoOriginsEngine(bodies) { online }), departureCache, tripCache)
            val origins = listOf("NSR:StopPlace:A", "NSR:StopPlace:B")

            val board = repo.getTripOptionsFromStops(fromIds = origins, to = majorstuen, count = 4)
            val widget = repo.getFastestTrip(fromIds = origins, to = majorstuen)
            online = false
            val offline = repo.getTripOptionsFromStops(fromIds = origins, to = majorstuen, count = 4)

            assertEquals("5", widget.data?.lineName)
            assertEquals(4, bodies.size, "still one request per origin each time")
            assertEquals(board.data.map { it.expectedStartTime }, offline.data.map { it.expectedStartTime })
            assertEquals(4, offline.data.size)
            assertTrue(offline.isFromCache)
        }

    @Test
    fun `a widget asking for one route leaves the board's cached alternatives from the location`() =
        runTest {
            val now = nowMillis()
            val bodies = mutableListOf<String>()
            var online = true
            val engine =
                MockEngine { request ->
                    if (!online) throw IOException("offline")
                    bodies += (request.body as TextContent).text
                    val answer = routes(route(now, 2, 18, "5"), route(now, 4, 20, "31"), route(now, 8, 24, "5"), route(now, 12, 28, "31"))
                    respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val board = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 4)
            val widget = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 1)
            online = false
            val offline = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 4)

            assertEquals(listOf("5"), widget.data.map { it.lineName }, "the widget still gets the one it asked for")
            assertEquals(2, bodies.size)
            assertEquals(listOf(5, 5), bodies.map { variables(it)["count"]!!.jsonPrimitive.int })
            assertEquals(board.data.map { it.expectedStartTime }, offline.data.map { it.expectedStartTime })
            assertEquals(listOf("5", "31", "5", "31"), offline.data.map { it.lineName })
            assertTrue(offline.isFromCache)
        }

    @Test
    fun `more routes than the board's are still fetched and served in full`() =
        runTest {
            val now = nowMillis()
            val bodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    bodies += (request.body as TextContent).text
                    val answer = routes(*Array(7) { route(now, 2 + 3 * it, 18 + 3 * it, "${it + 1}") })
                    respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val onSpot = repo.getTripOptions(lat = 59.91, lon = 10.75, to = majorstuen, count = 6)

            assertEquals(6, onSpot.data.size, "on the spot may ask for more than the board")
            assertEquals(7, variables(bodies.single())["count"]!!.jsonPrimitive.int)
        }

    @Test
    fun `the same departure reached from two favourites is listed once`() =
        runTest {
            val now = nowMillis()
            val engine =
                MockEngine { request ->
                    val from = variables((request.body as TextContent).text)["from"]!!.jsonPrimitive.content
                    val answer =
                        if (from == "NSR:StopPlace:A") {
                            // A short walk to the 31 that leaves at +9.
                            routes(route(now, 4, 20, "31", journey = "RUT:ServiceJourney:31-0900"))
                        } else {
                            // A longer walk to that same 31, then the next 31.
                            routes(
                                route(now, 2, 20, "31", journey = "RUT:ServiceJourney:31-0900"),
                                route(now, 10, 26, "31", journey = "RUT:ServiceJourney:31-0915"),
                            )
                        }
                    respond(answer, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = EnturRepositoryImpl(client(engine), departureCache, tripCache)

            val trips = repo.getTripOptionsFromStops(fromIds = listOf("NSR:StopPlace:A", "NSR:StopPlace:B"), to = majorstuen, count = 4)

            assertEquals(
                listOf(isoOslo(now + 2 * minute), isoOslo(now + 10 * minute)),
                trips.data.map { it.expectedStartTime },
                "the 31 at 09:00 once, as its first-ranked route, and the next 31 kept",
            )
        }
}

package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.DepartureCacheDao
import com.example.pulsetransit.data.cache.TripCacheDao
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Clock

class EnturRepositoryImpl(
    private val client: HttpClient,
    private val departureCache: DepartureCacheDao,
    private val tripCache: TripCacheDao,
    private val clock: Clock = Clock.System,
) : EnturRepository {
    private val clientNameHeader = "ET-Client-Name"
    private val clientNameValue = "personal-pulsetransit"
    private val lineIdLookup = LineIdLookup(client, clientNameHeader, clientNameValue)

    private companion object {
        /** How long departed rows are kept before pruning, purely to bound cache growth. */
        const val PRUNE_AFTER_MS = 6 * 60 * 60 * 1000L

        const val COORDINATE_DECIMALS = 4
        const val COORDINATE_SCALE = 10_000.0

        /** What both trip queries ask of a route: enough per leg for the board and its details. */
        val TRIP_PATTERN_FRAGMENT =
            """
            fragment TripPatternFields on TripPattern {
              expectedStartTime
              expectedEndTime
              duration
              legs {
                mode
                duration
                distance
                expectedStartTime
                expectedEndTime
                aimedStartTime
                realtime
                serviceDate
                line {
                  publicCode
                  name
                  authority {
                    name
                  }
                }
                fromPlace {
                  name
                  quay {
                    id
                    publicCode
                  }
                }
                toPlace {
                  name
                  quay {
                    id
                  }
                }
                fromEstimatedCall {
                  destinationDisplay {
                    frontText
                  }
                  notices {
                    text
                  }
                }
                serviceJourney {
                  id
                  notices {
                    text
                  }
                }
              }
            }
            """.trimIndent()
    }

    // Geocoder V3 response data structures
    @Serializable
    private data class GeoJsonResponse(
        val features: List<GeoJsonFeature> = emptyList(),
    )

    @Serializable
    private data class GeoJsonFeature(
        val geometry: GeoJsonGeometry? = null,
        val properties: GeoJsonProperties? = null,
    )

    @Serializable
    private data class GeoJsonGeometry(
        val type: String,
        val coordinates: List<Double> = emptyList(), // [lon, lat]
    )

    @Serializable
    private data class GeoJsonProperties(
        val id: String,
        val layer: String? = null,
        val names: GeoJsonNames? = null,
        val address: GeoJsonAddress? = null,
    )

    @Serializable
    private data class GeoJsonNames(
        val default: String,
        val display: String? = null,
    )

    @Serializable
    private data class GeoJsonAddress(
        val locality: String? = null,
    )

    // GraphQL response data structures
    @Serializable
    private data class GraphQLRequest(
        val query: String,
        val variables: JsonObject,
    )

    @Serializable
    private data class GraphQLResponse<T>(
        val data: T? = null,
        val errors: List<GraphQLError>? = null,
    )

    @Serializable
    private data class GraphQLError(
        val message: String,
    )

    @Serializable
    private data class StopPlaceData(
        val stopPlace: StopPlaceGraphQL? = null,
        // Entur answers an unknown quay id with a null entry rather than an error.
        val quays: List<QuayGraphQL?>? = null,
    )

    @Serializable
    private data class TripData(
        val trip: TripGraphQL? = null,
    )

    @Serializable
    private data class TripGraphQL(
        val tripPatterns: List<TripPatternGraphQL> = emptyList(),
    )

    @Serializable
    private data class TripPatternGraphQL(
        val expectedStartTime: String,
        val expectedEndTime: String? = null,
        val duration: Long,
        val legs: List<LegGraphQL> = emptyList(),
    )

    @Serializable
    private data class LegGraphQL(
        val mode: String? = null,
        val duration: Long = 0,
        val distance: Double = 0.0,
        val expectedStartTime: String? = null,
        val expectedEndTime: String? = null,
        val aimedStartTime: String? = null,
        val realtime: Boolean = false,
        val serviceDate: String? = null,
        val line: LineGraphQL? = null,
        val fromPlace: TripPlaceGraphQL? = null,
        val toPlace: TripPlaceGraphQL? = null,
        val fromEstimatedCall: LegCallGraphQL? = null,
        val serviceJourney: ServiceJourneyGraphQL? = null,
    )

    @Serializable
    private data class TripPlaceGraphQL(
        val name: String? = null,
        val quay: QuayIdGraphQL? = null,
    )

    @Serializable
    private data class LegCallGraphQL(
        val destinationDisplay: DestinationDisplayGraphQL? = null,
        val notices: List<NoticeGraphQL> = emptyList(),
    )

    @Serializable
    private data class StopPlaceGraphQL(
        val id: String,
        val name: String,
        val estimatedCalls: List<EstimatedCallGraphQL> = emptyList(),
        val quays: List<QuayGraphQL> = emptyList(),
    )

    @Serializable
    private data class QuayGraphQL(
        val id: String,
        val publicCode: String? = null,
        val name: String? = null,
        val description: String? = null,
        val stopPlace: StopPlaceRefGraphQL? = null,
        val lines: List<LineRefGraphQL> = emptyList(),
        val estimatedCalls: List<EstimatedCallGraphQL> = emptyList(),
    ) {
        fun toQuay() =
            Quay(
                id = id,
                publicCode = publicCode,
                name = name,
                description = description,
                lines =
                    lines
                        .mapNotNull { it.publicCode?.takeIf(String::isNotBlank) }
                        .distinct()
                        .sortedWith(::naturalCompare),
                destinations =
                    estimatedCalls
                        .mapNotNull { it.destinationDisplay?.frontText?.takeIf(String::isNotBlank) }
                        .distinct(),
                stopPlaceId = stopPlace?.id,
                stopPlaceName = stopPlace?.name,
            )
    }

    @Serializable
    private data class StopPlaceRefGraphQL(
        val id: String,
        val name: String? = null,
    )

    @Serializable
    private data class LineRefGraphQL(
        val publicCode: String? = null,
    )

    @Serializable
    private data class EstimatedCallGraphQL(
        val expectedDepartureTime: String? = null,
        val aimedDepartureTime: String? = null,
        val cancellation: Boolean = false,
        val realtimeState: String? = null,
        val destinationDisplay: DestinationDisplayGraphQL? = null,
        val serviceJourney: ServiceJourneyGraphQL? = null,
        val notices: List<NoticeGraphQL> = emptyList(),
        val quay: QuayIdGraphQL? = null,
        val date: String? = null,
    )

    @Serializable
    private data class QuayIdGraphQL(
        val id: String,
        val publicCode: String? = null,
    )

    @Serializable
    private data class NamedGraphQL(
        val name: String? = null,
    )

    @Serializable
    private data class NoticeGraphQL(
        val text: String? = null,
    )

    @Serializable
    private data class DestinationDisplayGraphQL(
        val frontText: String? = null,
    )

    @Serializable
    private data class ServiceJourneyGraphQL(
        val id: String? = null,
        val line: LineGraphQL? = null,
        val notices: List<NoticeGraphQL> = emptyList(),
    )

    @Serializable
    private data class LineGraphQL(
        val publicCode: String? = null,
        val name: String? = null,
        val transportMode: String? = null,
        val authority: NamedGraphQL? = null,
        val operator: NamedGraphQL? = null,
    )

    override suspend fun searchStopPlaces(query: String): List<StopPlace> {
        if (query.isBlank()) return emptyList()
        return try {
            val response: GeoJsonResponse =
                client
                    .get("https://api.entur.io/geocoder/v3/autocomplete") {
                        header(clientNameHeader, clientNameValue)
                        parameter("q", query)
                        parameter("layers", "stopPlace")
                        parameter("limit", 15)
                    }.body()

            response.features.mapNotNull { it.toStopPlace() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun searchPlaces(
        query: String,
        focusLat: Double?,
        focusLon: Double?,
    ): List<StopPlace> {
        if (query.isBlank()) return emptyList()
        return try {
            val response: GeoJsonResponse =
                client
                    .get("https://api.entur.io/geocoder/v3/autocomplete") {
                        header(clientNameHeader, clientNameValue)
                        parameter("q", query)
                        // groupOfStopPlaces is left out: the trip planner can't route to one.
                        parameter("layers", "stopPlace,address,poi,place")
                        parameter("limit", 15)
                        if (focusLat != null && focusLon != null) {
                            parameter("lat", focusLat)
                            parameter("lon", focusLon)
                        }
                    }.body()

            response.features.mapNotNull { it.toStopPlace() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun GeoJsonFeature.toStopPlace(): StopPlace? {
        val props = properties ?: return null
        val kind =
            when (props.layer) {
                "address", "street" -> PlaceKind.ADDRESS
                "poi" -> PlaceKind.POI
                "place" -> PlaceKind.PLACE
                // Absent on older responses, which only ever asked for stop places.
                else -> PlaceKind.STOP
            }
        return StopPlace(
            id = props.id,
            name = props.names?.default ?: "Unknown",
            locality = props.address?.locality,
            latitude = geometry?.coordinates?.getOrNull(1),
            longitude = geometry?.coordinates?.getOrNull(0),
            kind = kind,
        )
    }

    /**
     * The trip planner only knows NSR ids; an address or POI id finds no trips at all, so those
     * are routed to their coordinates instead.
     */
    private fun StopPlace.toTripLocation(): JsonObject {
        val lat = latitude
        val lon = longitude
        return buildJsonObject {
            if (kind != PlaceKind.STOP && lat != null && lon != null) {
                put("name", name)
                putJsonObject("coordinates") {
                    put("latitude", lat)
                    put("longitude", lon)
                }
            } else {
                put("place", id)
            }
        }
    }

    override suspend fun getNearbyStopPlaces(
        lat: Double,
        lon: Double,
        radiusKm: Double,
    ): List<StopPlace> =
        try {
            val response: GeoJsonResponse =
                client
                    .get("https://api.entur.io/geocoder/v3/reverse") {
                        header(clientNameHeader, clientNameValue)
                        parameter("lat", lat)
                        parameter("lon", lon)
                        parameter("radius", radiusKm)
                        parameter("layers", "stopPlace")
                        parameter("limit", 20)
                    }.body()

            response.features.mapNotNull { it.toStopPlace() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

    // Length here is the inlined GraphQL query string, not branching logic.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override suspend fun getDepartures(
        stopId: String,
        limit: Int,
        quayIds: Set<String>?,
        timeRangeSeconds: Int,
    ): Sourced<List<Departure>> {
        val queryStr =
            """
            query GetDepartures(${'$'}id: String!, ${'$'}limit: Int!, ${'$'}timeRange: Int!, ${'$'}quayIds: [String]!, ${'$'}byQuay: Boolean!) {
              stopPlace(id: ${'$'}id) {
                id
                name
                estimatedCalls(numberOfDepartures: ${'$'}limit, timeRange: ${'$'}timeRange, includeCancelledTrips: true) @skip(if: ${'$'}byQuay) {
                  ...Call
                }
              }
              quays(ids: ${'$'}quayIds) @include(if: ${'$'}byQuay) {
                id
                estimatedCalls(numberOfDepartures: ${'$'}limit, timeRange: ${'$'}timeRange, includeCancelledTrips: true) {
                  ...Call
                }
              }
            }

            fragment Call on EstimatedCall {
              expectedDepartureTime
              aimedDepartureTime
              cancellation
              realtimeState
              date
              destinationDisplay {
                frontText
              }
              quay {
                id
                publicCode
              }
              serviceJourney {
                id
                line {
                  publicCode
                  name
                  transportMode
                  authority {
                    name
                  }
                  operator {
                    name
                  }
                }
                notices {
                  text
                }
              }
              notices {
                text
              }
            }
            """.trimIndent()

        // With a quay selection, ask each quay for its own departures. Filtering the stop's calls
        // here instead lets a busy quay use up the whole limit: at a station shared with a bus
        // terminal, the trains drop out of the next [limit] calls and the board shows none.
        val byQuay = !quayIds.isNullOrEmpty()
        val variables =
            buildJsonObject {
                put("id", stopId)
                put("limit", limit)
                put("timeRange", timeRangeSeconds)
                putJsonArray("quayIds") { quayIds.orEmpty().forEach { add(it) } }
                put("byQuay", byQuay)
            }

        val now = clock.now().toEpochMilliseconds()

        return try {
            val response: GraphQLResponse<StopPlaceData> =
                client
                    .post("https://api.entur.io/journey-planner/v3/graphql") {
                        header(clientNameHeader, clientNameValue)
                        contentType(ContentType.Application.Json)
                        setBody(GraphQLRequest(queryStr, variables))
                    }.body()

            val data = response.data
            // A response without the stop is an API-level failure, not an empty stop, so it
            // falls back rather than wiping the cache.
            if (data?.stopPlace == null) return departuresFromCache(stopId, now)
            val estimatedCalls =
                if (byQuay) {
                    // Stable sort, so calls tied on time keep Entur's order within each quay.
                    data.quays
                        .orEmpty()
                        .filterNotNull()
                        .flatMap { it.estimatedCalls }
                        .sortedBy { call -> (call.expectedDepartureTime ?: call.aimedDepartureTime)?.let(::parseIsoToMillis) }
                        .take(limit)
                } else {
                    data.stopPlace.estimatedCalls
                }

            val departures =
                estimatedCalls
                    .filter { call -> quayIds == null || quayIds.contains(call.quay?.id) }
                    .map { call ->
                        val delay =
                            try {
                                val expected = call.expectedDepartureTime?.let { parseIsoToMillis(it) }
                                val aimed = call.aimedDepartureTime?.let { parseIsoToMillis(it) }
                                if (expected != null && aimed != null) {
                                    ((expected - aimed) / 60000).toInt()
                                } else {
                                    0
                                }
                            } catch (_: Exception) {
                                0
                            }

                        val notices =
                            (
                                call.notices.mapNotNull { it.text } +
                                    call.serviceJourney
                                        ?.notices
                                        ?.mapNotNull { it.text }
                                        .orEmpty()
                            ).distinct()

                        Departure(
                            line = call.serviceJourney?.line?.publicCode ?: call.serviceJourney?.line?.name ?: "?",
                            destination = call.destinationDisplay?.frontText ?: "Unknown",
                            expectedTime = call.expectedDepartureTime ?: call.aimedDepartureTime ?: "",
                            aimedTime = call.aimedDepartureTime ?: "",
                            isRealtime = call.realtimeState != null && call.realtimeState != "scheduled",
                            isCancelled = call.cancellation,
                            transportMode = call.serviceJourney?.line?.transportMode,
                            delayMinutes = delay,
                            notices = notices,
                            quayId = call.quay?.id,
                            platform = call.quay?.publicCode?.takeIf(String::isNotBlank),
                            lineName =
                                call.serviceJourney
                                    ?.line
                                    ?.name
                                    ?.takeIf(String::isNotBlank),
                            authority =
                                call.serviceJourney
                                    ?.line
                                    ?.authority
                                    ?.name
                                    ?.takeIf(String::isNotBlank),
                            operator =
                                call.serviceJourney
                                    ?.line
                                    ?.operator
                                    ?.name
                                    ?.takeIf(String::isNotBlank),
                            serviceJourneyId = call.serviceJourney?.id,
                            serviceDate = call.date,
                        )
                    }

            departureCache.replaceStop(
                stopId,
                departures.mapIndexed { index, departure -> departure.toCacheEntity(stopId, index, now) },
            )
            departureCache.pruneDepartedBefore(now - PRUNE_AFTER_MS)
            Sourced.live(departures)
        } catch (e: Exception) {
            e.printStackTrace()
            departuresFromCache(stopId, now)
        }
    }

    /**
     * Last good departures for [stopId], minus anything already due. Note the cache mirrors the
     * quay selection in force when it was written, so a selection changed while offline only
     * takes effect on the next successful fetch.
     */
    private suspend fun departuresFromCache(
        stopId: String,
        nowMillis: Long,
    ): Sourced<List<Departure>> {
        val cached = departureCache.getUpcoming(stopId, nowMillis)
        val fetchedAt = cached.firstOrNull()?.fetchedAt ?: return Sourced.failed(emptyList())
        return Sourced.cached(cached.map { it.toDeparture() }, fetchedAt)
    }

    override suspend fun getJourneyStops(
        serviceJourneyId: String,
        serviceDate: String,
    ): List<JourneyStop> = client.fetchJourneyStops(serviceJourneyId, serviceDate, clientNameHeader, clientNameValue)

    override suspend fun getStopPlaceWithQuays(stopId: String): StopPlace? {
        val queryStr =
            """
            query GetStopPlace(${'$'}id: String!) {
              stopPlace(id: ${'$'}id) {
                id
                name
                quays {
                  id
                  publicCode
                  name
                  description
                  stopPlace {
                    id
                    name
                  }
                  lines {
                    publicCode
                  }
                  estimatedCalls(numberOfDepartures: 10, timeRange: 43200) {
                    destinationDisplay {
                      frontText
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val variables =
            buildJsonObject {
                put("id", stopId)
            }

        return try {
            val response: GraphQLResponse<StopPlaceData> =
                client
                    .post("https://api.entur.io/journey-planner/v3/graphql") {
                        header(clientNameHeader, clientNameValue)
                        contentType(ContentType.Application.Json)
                        setBody(GraphQLRequest(queryStr, variables))
                    }.body()

            val stop = response.data?.stopPlace ?: return null
            StopPlace(
                id = stop.id,
                name = stop.name,
                quays =
                    stop.quays
                        // A parent stop lists taxi ranks and unused bus bays too. Nothing ever
                        // departs from them, so they would only be noise in the quay picker.
                        .filter { it.lines.isNotEmpty() || it.estimatedCalls.isNotEmpty() }
                        .map { it.toQuay() },
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Length here is the inlined GraphQL query string, not branching logic.
    @Suppress("LongMethod")
    override suspend fun getTripOptions(
        lat: Double,
        lon: Double,
        to: StopPlace,
        count: Int,
        options: RouteOptions,
    ): Sourced<List<TripInfo>> {
        val now = clock.now().toEpochMilliseconds()
        val routeKey = routeKey(lat, lon, to.id) + options.cacheKeySuffix
        val wanted = count.coerceAtLeast(1)

        val queryStr =
            """
            query GetTrip(${'$'}lat: Float!, ${'$'}lon: Float!, ${'$'}to: Location!, $TRIP_OPTION_VARIABLES) {
              trip(
                from: { coordinates: { latitude: ${'$'}lat, longitude: ${'$'}lon } }
                to: ${'$'}to
                $TRIP_OPTION_ARGUMENTS
              ) {
                tripPatterns {
                  ...TripPatternFields
                }
              }
            }
            """.trimIndent() + "\n" + TRIP_PATTERN_FRAGMENT

        return try {
            val variables =
                buildJsonObject {
                    put("lat", lat)
                    put("lon", lon)
                    put("to", to.toTripLocation())
                    putRouteOptions(options, wanted, lineIdLookup.lineIds(options.bannedLines))
                }
            val response: GraphQLResponse<TripData> =
                client
                    .post("https://api.entur.io/journey-planner/v3/graphql") {
                        header(clientNameHeader, clientNameValue)
                        contentType(ContentType.Application.Json)
                        setBody(GraphQLRequest(queryStr, variables))
                    }.body()

            val trips =
                response.data
                    ?.trip
                    ?.tripPatterns
                    .orEmpty()
                    .map(::parseTripPattern)
                    .rankedBy(options.preference)
                    .take(wanted)
            if (trips.isEmpty()) {
                return Sourced(emptyList())
            }

            tripCache.replaceRoute(routeKey, trips.mapIndexed { index, trip -> trip.toCacheEntity(routeKey, index, now) })
            tripCache.pruneStartedBefore(now - PRUNE_AFTER_MS)
            Sourced.live(trips)
        } catch (e: Exception) {
            e.printStackTrace()
            val cached = tripCache.getUpcoming(routeKey, now)
            if (cached.isEmpty()) {
                Sourced.failed(emptyList())
            } else {
                Sourced.cached(cached.take(wanted).map { it.toTripInfo() }, cached.minOf { it.fetchedAt })
            }
        }
    }

    // Length here is the inlined GraphQL query string, not branching logic.
    @Suppress("LongMethod")
    override suspend fun getFastestTrip(
        fromIds: List<String>,
        to: StopPlace,
        options: RouteOptions,
    ): Sourced<TripInfo?> {
        if (fromIds.isEmpty()) return Sourced(null)

        val now = clock.now().toEpochMilliseconds()
        val routeKey = routeKey(fromIds, to.id) + options.cacheKeySuffix
        // The best route from each origin that has one.
        val bestByOrigin = mutableListOf<TripInfo>()
        // Tracked so that "every origin failed" falls back to the cache, while "the API answered
        // but found no trips" is reported honestly as no trips.
        var anyRequestFailed = false

        for (fromId in fromIds) {
            val queryStr =
                """
                query GetTrip(${'$'}from: String!, ${'$'}to: Location!, $TRIP_OPTION_VARIABLES) {
                  trip(
                    from: { place: ${'$'}from }
                    to: ${'$'}to
                    $TRIP_OPTION_ARGUMENTS
                  ) {
                    tripPatterns {
                      ...TripPatternFields
                    }
                  }
                }
                """.trimIndent() + "\n" + TRIP_PATTERN_FRAGMENT

            try {
                val variables =
                    buildJsonObject {
                        put("from", fromId)
                        put("to", to.toTripLocation())
                        putRouteOptions(options, wanted = 1, lineIdLookup.lineIds(options.bannedLines))
                    }
                val response: GraphQLResponse<TripData> =
                    client
                        .post("https://api.entur.io/journey-planner/v3/graphql") {
                            header(clientNameHeader, clientNameValue)
                            contentType(ContentType.Application.Json)
                            setBody(GraphQLRequest(queryStr, variables))
                        }.body()

                val best =
                    response.data
                        ?.trip
                        ?.tripPatterns
                        .orEmpty()
                        .map(::parseTripPattern)
                        .rankedBy(options.preference)
                        .firstOrNull() ?: continue
                bestByOrigin += best
            } catch (e: Exception) {
                e.printStackTrace()
                anyRequestFailed = true
            }
        }

        val fastestTrip =
            if (options.preference == RoutePreference.FASTEST) {
                bestByOrigin.minByOrNull { it.expectedStartTime }
            } else {
                bestByOrigin.rankedBy(options.preference).firstOrNull()
            }
        if (fastestTrip == null) {
            // Only reach for the cache when the network is the reason we have nothing.
            return if (anyRequestFailed) tripFromCache(routeKey, now) else Sourced(null)
        }

        tripCache.replaceRoute(routeKey, listOf(fastestTrip.toCacheEntity(routeKey, 0, now)))
        tripCache.pruneStartedBefore(now - PRUNE_AFTER_MS)
        return Sourced.live(fastestTrip)
    }

    private fun parseTripPattern(pattern: TripPatternGraphQL): TripInfo {
        // The first leg may be a foot leg (line == null); use the first transit leg for line/origin
        val transitLeg = pattern.legs.firstOrNull { it.line != null }
        return TripInfo(
            destinationName = "", // Will be filled by ViewModel or if we query for destination separately
            expectedStartTime = pattern.expectedStartTime,
            expectedEndTime = pattern.expectedEndTime,
            lineName = transitLeg?.line?.publicCode ?: transitLeg?.line?.name ?: "?",
            durationMinutes = (pattern.duration / 60).toInt(),
            originName =
                transitLeg?.fromPlace?.name ?: pattern.legs
                    .firstOrNull()
                    ?.fromPlace
                    ?.name ?: "Unknown",
            legs = pattern.legs.map(::parseLeg),
        )
    }

    private fun parseLeg(leg: LegGraphQL) =
        TripLeg(
            mode = leg.mode,
            lineName = leg.line?.publicCode ?: leg.line?.name,
            fromName = leg.fromPlace?.name,
            durationMinutes = (leg.duration / 60).toInt(),
            toName = leg.toPlace?.name,
            expectedStartTime = leg.expectedStartTime,
            expectedEndTime = leg.expectedEndTime,
            aimedStartTime = leg.aimedStartTime,
            distanceMeters = leg.distance.roundToInt(),
            isRealtime = leg.realtime,
            platform =
                leg.fromPlace
                    ?.quay
                    ?.publicCode
                    ?.takeIf(String::isNotBlank),
            destination =
                leg.fromEstimatedCall
                    ?.destinationDisplay
                    ?.frontText
                    ?.takeIf(String::isNotBlank),
            lineFullName = leg.line?.name?.takeIf(String::isNotBlank),
            authority =
                leg.line
                    ?.authority
                    ?.name
                    ?.takeIf(String::isNotBlank),
            notices =
                (
                    leg.fromEstimatedCall
                        ?.notices
                        .orEmpty()
                        .mapNotNull { it.text } +
                        leg.serviceJourney
                            ?.notices
                            .orEmpty()
                            .mapNotNull { it.text }
                ).distinct(),
            serviceJourneyId = leg.serviceJourney?.id,
            serviceDate = leg.serviceDate,
            fromQuayId = leg.fromPlace?.quay?.id,
            toQuayId = leg.toPlace?.quay?.id,
        )

    /** Stable key for an origin set and destination, independent of the order favourites are in. */
    private fun routeKey(
        fromIds: List<String>,
        toId: String,
    ): String = fromIds.sorted().joinToString(",") + ">" + toId

    private fun routeKey(
        lat: Double,
        lon: Double,
        toId: String,
    ): String = "${fixed4(lat)},${fixed4(lon)}>$toId"

    /**
     * [value] with exactly four decimals and a dot ("59.9149"), in every locale. Four decimals is
     * about 11 m, close enough that nearby requests share a cache entry.
     */
    private fun fixed4(value: Double): String {
        val scaled = (abs(value) * COORDINATE_SCALE).roundToLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        val fraction = (scaled % COORDINATE_SCALE.toLong()).toString().padStart(COORDINATE_DECIMALS, '0')
        return "$sign${scaled / COORDINATE_SCALE.toLong()}.$fraction"
    }

    private suspend fun tripFromCache(
        routeKey: String,
        nowMillis: Long,
    ): Sourced<TripInfo?> {
        val cached = tripCache.getUpcoming(routeKey, nowMillis).firstOrNull() ?: return Sourced.failed(null)
        return Sourced.cached(cached.toTripInfo(), cached.fetchedAt)
    }
}

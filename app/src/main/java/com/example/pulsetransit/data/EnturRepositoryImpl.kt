package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedDeparture
import com.example.pulsetransit.data.cache.CachedTrip
import com.example.pulsetransit.data.cache.CachedTripLeg
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class EnturRepositoryImpl(
    private val client: HttpClient,
    private val departureCache: DepartureCacheDao,
    private val tripCache: TripCacheDao,
) : EnturRepository {
    private val clientNameHeader = "ET-Client-Name"
    private val clientNameValue = "personal-pulsetransit"

    private companion object {
        /** How long departed rows are kept before pruning, purely to bound cache growth. */
        const val PRUNE_AFTER_MS = 6 * 60 * 60 * 1000L
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
        val duration: Long,
        val legs: List<LegGraphQL> = emptyList(),
    )

    @Serializable
    private data class LegGraphQL(
        val mode: String? = null,
        val duration: Long = 0,
        val line: LineGraphQL? = null,
        val fromPlace: TripPlaceGraphQL? = null,
    )

    @Serializable
    private data class TripPlaceGraphQL(
        val name: String? = null,
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
        val estimatedCalls: List<EstimatedCallGraphQL> = emptyList(),
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
    )

    @Serializable
    private data class QuayIdGraphQL(
        val id: String,
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
        val line: LineGraphQL? = null,
        val notices: List<NoticeGraphQL> = emptyList(),
    )

    @Serializable
    private data class LineGraphQL(
        val publicCode: String? = null,
        val name: String? = null,
        val transportMode: String? = null,
    )

    override suspend fun searchStopPlaces(query: String): List<StopPlace> {
        if (query.isBlank()) return emptyList()
        // ... (keeping implementation but I need to make it non-suspend or match interface)
        // Wait, the interface is suspend.
        return try {
            val response: GeoJsonResponse =
                client
                    .get("https://api.entur.io/geocoder/v3/autocomplete") {
                        header(clientNameHeader, clientNameValue)
                        parameter("q", query)
                        parameter("layers", "stopPlace")
                        parameter("limit", 15)
                    }.body()

            response.features.mapNotNull { feature ->
                val props = feature.properties ?: return@mapNotNull null
                val coords = feature.geometry?.coordinates
                val lon = coords?.getOrNull(0)
                val lat = coords?.getOrNull(1)
                StopPlace(
                    id = props.id,
                    name = props.names?.default ?: "Unknown",
                    locality = props.address?.locality,
                    latitude = lat,
                    longitude = lon,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getNearbyStopPlaces(
        lat: Double,
        lon: Double,
        radiusKm: Double,
    ): List<StopPlace> {
        return try {
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

            response.features.mapNotNull { feature ->
                val props = feature.properties ?: return@mapNotNull null
                val coords = feature.geometry?.coordinates
                val fLon = coords?.getOrNull(0)
                val fLat = coords?.getOrNull(1)
                StopPlace(
                    id = props.id,
                    name = props.names?.default ?: "Unknown",
                    locality = props.address?.locality,
                    latitude = fLat,
                    longitude = fLon,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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
            query GetDepartures(${'$'}id: String!, ${'$'}limit: Int!, ${'$'}timeRange: Int!) {
              stopPlace(id: ${'$'}id) {
                id
                name
                estimatedCalls(numberOfDepartures: ${'$'}limit, timeRange: ${'$'}timeRange, includeCancelledTrips: true) {
                  expectedDepartureTime
                  aimedDepartureTime
                  cancellation
                  realtimeState
                  destinationDisplay {
                    frontText
                  }
                  quay {
                    id
                  }
                  serviceJourney {
                    line {
                      publicCode
                      name
                      transportMode
                    }
                    notices {
                      text
                    }
                  }
                  notices {
                    text
                  }
                }
              }
            }
            """.trimIndent()

        val variables =
            buildJsonObject {
                put("id", stopId)
                put("limit", limit)
                put("timeRange", timeRangeSeconds)
            }

        val now = System.currentTimeMillis()

        return try {
            val response: GraphQLResponse<StopPlaceData> =
                client
                    .post("https://api.entur.io/journey-planner/v3/graphql") {
                        header(clientNameHeader, clientNameValue)
                        contentType(ContentType.Application.Json)
                        setBody(GraphQLRequest(queryStr, variables))
                    }.body()

            val estimatedCalls =
                response.data?.stopPlace?.estimatedCalls
                    // A response without the stop is an API-level failure, not an empty stop, so it
                    // falls back rather than wiping the cache.
                    ?: return departuresFromCache(stopId, now)

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
        val fetchedAt = cached.firstOrNull()?.fetchedAt ?: return Sourced(emptyList())
        return Sourced.cached(cached.map { it.toDeparture() }, fetchedAt)
    }

    private fun Departure.toCacheEntity(
        stopId: String,
        position: Int,
        fetchedAt: Long,
    ) = CachedDeparture(
        stopId = stopId,
        position = position,
        line = line,
        destination = destination,
        expectedTime = expectedTime,
        aimedTime = aimedTime,
        expectedAtMillis = parseIsoToMillis(expectedTime) ?: 0L,
        isRealtime = isRealtime,
        isCancelled = isCancelled,
        transportMode = transportMode,
        delayMinutes = delayMinutes,
        notices = notices,
        quayId = quayId,
        fetchedAt = fetchedAt,
    )

    private fun CachedDeparture.toDeparture() =
        Departure(
            line = line,
            destination = destination,
            expectedTime = expectedTime,
            aimedTime = aimedTime,
            isRealtime = isRealtime,
            isCancelled = isCancelled,
            transportMode = transportMode,
            delayMinutes = delayMinutes,
            notices = notices,
            quayId = quayId,
        )

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
                  estimatedCalls(numberOfDepartures: 1) {
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
                    stop.quays.map {
                        Quay(
                            it.id,
                            it.publicCode,
                            it.name,
                            it.description,
                            it.estimatedCalls
                                .firstOrNull()
                                ?.destinationDisplay
                                ?.frontText,
                        )
                    },
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Length here is the inlined GraphQL query string, not branching logic.
    @Suppress("LongMethod")
    override suspend fun getFastestTrip(
        lat: Double,
        lon: Double,
        toId: String,
    ): Sourced<TripInfo?> {
        val now = System.currentTimeMillis()
        val routeKey = routeKey(lat, lon, toId)

        val queryStr =
            """
            query GetTrip(${'$'}lat: Float!, ${'$'}lon: Float!, ${'$'}to: String!) {
              trip(
                from: { coordinates: { latitude: ${'$'}lat, longitude: ${'$'}lon } }
                to: { place: ${'$'}to }
                numTripPatterns: 1
              ) {
                tripPatterns {
                  expectedStartTime
                  duration
                  legs {
                    mode
                    duration
                    line {
                      publicCode
                      name
                    }
                    fromPlace {
                      name
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val variables =
            buildJsonObject {
                put("lat", lat)
                put("lon", lon)
                put("to", toId)
            }

        return try {
            val response: GraphQLResponse<TripData> =
                client
                    .post("https://api.entur.io/journey-planner/v3/graphql") {
                        header(clientNameHeader, clientNameValue)
                        contentType(ContentType.Application.Json)
                        setBody(GraphQLRequest(queryStr, variables))
                    }.body()

            val pattern =
                response.data
                    ?.trip
                    ?.tripPatterns
                    ?.firstOrNull()
            if (pattern == null) {
                return Sourced(null)
            }

            val tripInfo = parseTripPattern(pattern)

            tripCache.upsert(tripInfo.toCacheEntity(routeKey, now))
            tripCache.pruneStartedBefore(now - PRUNE_AFTER_MS)
            Sourced.live(tripInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            tripFromCache(routeKey, now)
        }
    }

    // Length here is the inlined GraphQL query string, not branching logic.
    @Suppress("LongMethod")
    override suspend fun getFastestTrip(
        fromIds: List<String>,
        toId: String,
    ): Sourced<TripInfo?> {
        if (fromIds.isEmpty()) return Sourced(null)

        val now = System.currentTimeMillis()
        val routeKey = routeKey(fromIds, toId)
        var fastestTrip: TripInfo? = null
        // Tracked so that "every origin failed" falls back to the cache, while "the API answered
        // but found no trips" is reported honestly as no trips.
        var anyRequestFailed = false

        for (fromId in fromIds) {
            val queryStr =
                """
                query GetTrip(${'$'}from: String!, ${'$'}to: String!) {
                  trip(
                    from: { place: ${'$'}from }
                    to: { place: ${'$'}to }
                    numTripPatterns: 1
                  ) {
                    tripPatterns {
                      expectedStartTime
                      duration
                      legs {
                        mode
                        duration
                        line {
                          publicCode
                          name
                        }
                        fromPlace {
                          name
                        }
                      }
                    }
                  }
                }
                """.trimIndent()

            val variables =
                buildJsonObject {
                    put("from", fromId)
                    put("to", toId)
                }

            try {
                val response: GraphQLResponse<TripData> =
                    client
                        .post("https://api.entur.io/journey-planner/v3/graphql") {
                            header(clientNameHeader, clientNameValue)
                            contentType(ContentType.Application.Json)
                            setBody(GraphQLRequest(queryStr, variables))
                        }.body()

                val pattern =
                    response.data
                        ?.trip
                        ?.tripPatterns
                        ?.firstOrNull() ?: continue
                val tripInfo = parseTripPattern(pattern)

                if (fastestTrip == null || tripInfo.expectedStartTime < fastestTrip.expectedStartTime) {
                    fastestTrip = tripInfo
                }
            } catch (e: Exception) {
                e.printStackTrace()
                anyRequestFailed = true
            }
        }

        if (fastestTrip == null) {
            // Only reach for the cache when the network is the reason we have nothing.
            return if (anyRequestFailed) tripFromCache(routeKey, now) else Sourced(null)
        }

        tripCache.upsert(fastestTrip.toCacheEntity(routeKey, now))
        tripCache.pruneStartedBefore(now - PRUNE_AFTER_MS)
        return Sourced.live(fastestTrip)
    }

    private fun parseTripPattern(pattern: TripPatternGraphQL): TripInfo {
        // The first leg may be a foot leg (line == null); use the first transit leg for line/origin
        val transitLeg = pattern.legs.firstOrNull { it.line != null }
        return TripInfo(
            destinationName = "", // Will be filled by ViewModel or if we query for destination separately
            expectedStartTime = pattern.expectedStartTime,
            lineName = transitLeg?.line?.publicCode ?: transitLeg?.line?.name ?: "?",
            durationMinutes = (pattern.duration / 60).toInt(),
            originName =
                transitLeg?.fromPlace?.name ?: pattern.legs
                    .firstOrNull()
                    ?.fromPlace
                    ?.name ?: "Unknown",
            legs =
                pattern.legs.map { leg ->
                    TripLeg(
                        mode = leg.mode,
                        lineName = leg.line?.publicCode ?: leg.line?.name,
                        fromName = leg.fromPlace?.name,
                        durationMinutes = (leg.duration / 60).toInt(),
                    )
                },
        )
    }

    /** Stable key for an origin set and destination, independent of the order favourites are in. */
    private fun routeKey(
        fromIds: List<String>,
        toId: String,
    ): String = fromIds.sorted().joinToString(",") + ">" + toId

    private fun routeKey(
        lat: Double,
        lon: Double,
        toId: String,
    ): String = "%.4f,%.4f>%s".format(lat, lon, toId)

    private suspend fun tripFromCache(
        routeKey: String,
        nowMillis: Long,
    ): Sourced<TripInfo?> {
        val cached = tripCache.getUpcoming(routeKey, nowMillis) ?: return Sourced(null)
        return Sourced.cached(cached.toTripInfo(), cached.fetchedAt)
    }

    private fun TripInfo.toCacheEntity(
        routeKey: String,
        fetchedAt: Long,
    ) = CachedTrip(
        routeKey = routeKey,
        destinationName = destinationName,
        expectedStartTime = expectedStartTime,
        expectedStartAtMillis = parseIsoToMillis(expectedStartTime) ?: 0L,
        lineName = lineName,
        durationMinutes = durationMinutes,
        originName = originName,
        legs = legs.map { CachedTripLeg(it.mode, it.lineName, it.fromName, it.durationMinutes) },
        fetchedAt = fetchedAt,
    )

    private fun CachedTrip.toTripInfo() =
        TripInfo(
            destinationName = destinationName,
            expectedStartTime = expectedStartTime,
            lineName = lineName,
            durationMinutes = durationMinutes,
            originName = originName,
            legs = legs.map { TripLeg(it.mode, it.lineName, it.fromName, it.durationMinutes) },
        )
}

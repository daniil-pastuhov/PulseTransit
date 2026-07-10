package com.example.enturstopboard.data

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

class EnturRepositoryImpl(private val client: HttpClient) : EnturRepository {
    private val clientNameHeader = "ET-Client-Name"
    private val clientNameValue = "personal-enturstopboard"

    // Geocoder V3 response data structures
    @Serializable
    private data class GeoJsonResponse(
        val features: List<GeoJsonFeature> = emptyList()
    )

    @Serializable
    private data class GeoJsonFeature(
        val geometry: GeoJsonGeometry? = null,
        val properties: GeoJsonProperties? = null
    )

    @Serializable
    private data class GeoJsonGeometry(
        val type: String,
        val coordinates: List<Double> = emptyList() // [lon, lat]
    )

    @Serializable
    private data class GeoJsonProperties(
        val id: String,
        val names: GeoJsonNames? = null,
        val address: GeoJsonAddress? = null
    )

    @Serializable
    private data class GeoJsonNames(
        val default: String,
        val display: String? = null
    )

    @Serializable
    private data class GeoJsonAddress(
        val locality: String? = null
    )

    // GraphQL response data structures
    @Serializable
    private data class GraphQLRequest(
        val query: String,
        val variables: JsonObject
    )

    @Serializable
    private data class GraphQLResponse<T>(
        val data: T? = null,
        val errors: List<GraphQLError>? = null
    )

    @Serializable
    private data class GraphQLError(
        val message: String
    )

    @Serializable
    private data class StopPlaceData(
        val stopPlace: StopPlaceGraphQL? = null
    )

    @Serializable
    private data class StopPlaceGraphQL(
        val id: String,
        val name: String,
        val estimatedCalls: List<EstimatedCallGraphQL> = emptyList(),
        val quays: List<QuayGraphQL> = emptyList()
    )

    @Serializable
    private data class QuayGraphQL(
        val id: String,
        val publicCode: String? = null,
        val name: String? = null,
        val description: String? = null,
        val estimatedCalls: List<EstimatedCallGraphQL> = emptyList()
    )

    @Serializable
    private data class EstimatedCallGraphQL(
        val expectedDepartureTime: String? = null,
        val aimedDepartureTime: String? = null,
        val destinationDisplay: DestinationDisplayGraphQL? = null,
        val serviceJourney: ServiceJourneyGraphQL? = null,
        val notices: List<NoticeGraphQL> = emptyList(),
        val quay: QuayIdGraphQL? = null
    )

    @Serializable
    private data class QuayIdGraphQL(
        val id: String
    )

    @Serializable
    private data class NoticeGraphQL(
        val text: String? = null
    )

    @Serializable
    private data class DestinationDisplayGraphQL(
        val frontText: String? = null
    )

    @Serializable
    private data class ServiceJourneyGraphQL(
        val line: LineGraphQL? = null,
        val notices: List<NoticeGraphQL> = emptyList()
    )

    @Serializable
    private data class LineGraphQL(
        val publicCode: String? = null,
        val name: String? = null,
        val transportMode: String? = null
    )

    override suspend fun searchStopPlaces(query: String): List<StopPlace> {
        if (query.isBlank()) return emptyList()
        // ... (keeping implementation but I need to make it non-suspend or match interface)
        // Wait, the interface is suspend.
        return try {
            val response: GeoJsonResponse = client.get("https://api.entur.io/geocoder/v3/autocomplete") {
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
                    longitude = lon
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getNearbyStopPlaces(lat: Double, lon: Double, radiusKm: Double): List<StopPlace> {
        return try {
            val response: GeoJsonResponse = client.get("https://api.entur.io/geocoder/v3/reverse") {
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
                    longitude = fLon
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getDepartures(stopId: String, limit: Int, quayIds: Set<String>?): List<Departure> {
        val queryStr = """
            query GetDepartures(${'$'}id: String!, ${'$'}limit: Int!) {
              stopPlace(id: ${'$'}id) {
                id
                name
                estimatedCalls(numberOfDepartures: ${'$'}limit) {
                  expectedDepartureTime
                  aimedDepartureTime
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

        val variables = buildJsonObject {
            put("id", stopId)
            put("limit", limit)
        }

        return try {
            val response: GraphQLResponse<StopPlaceData> = client.post("https://api.entur.io/journey-planner/v3/graphql") {
                header(clientNameHeader, clientNameValue)
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(queryStr, variables))
            }.body()

            val estimatedCalls = response.data?.stopPlace?.estimatedCalls ?: return emptyList()

            estimatedCalls
                .filter { call -> quayIds == null || quayIds.contains(call.quay?.id) }
                .map { call ->
                    val delay = try {
                        val expected = call.expectedDepartureTime?.let { parseIso(it) }
                        val aimed = call.aimedDepartureTime?.let { parseIso(it) }
                        if (expected != null && aimed != null) {
                            ((expected - aimed) / 60000).toInt()
                        } else 0
                    } catch (e: Exception) { 0 }
                    
                    val notices = (call.notices.mapNotNull { it.text } + 
                                  call.serviceJourney?.notices?.mapNotNull { it.text }.orEmpty()).distinct()

                    Departure(
                        line = call.serviceJourney?.line?.publicCode ?: call.serviceJourney?.line?.name ?: "?",
                        destination = call.destinationDisplay?.frontText ?: "Unknown",
                        expectedTime = call.expectedDepartureTime ?: "",
                        aimedTime = call.aimedDepartureTime ?: "",
                        isRealtime = call.expectedDepartureTime != null && call.expectedDepartureTime != call.aimedDepartureTime,
                        transportMode = call.serviceJourney?.line?.transportMode,
                        delayMinutes = delay,
                        notices = notices,
                        quayId = call.quay?.id
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseIso(iso: String): Long? {
        return try {
            // Simple approach: handle common ISO formats
            val formatted = iso.replace("Z", "+0000").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US).parse(formatted)?.time
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getStopPlaceWithQuays(stopId: String): StopPlace? {
        val queryStr = """
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

        val variables = buildJsonObject {
            put("id", stopId)
        }

        return try {
            val response: GraphQLResponse<StopPlaceData> = client.post("https://api.entur.io/journey-planner/v3/graphql") {
                header(clientNameHeader, clientNameValue)
                contentType(ContentType.Application.Json)
                setBody(GraphQLRequest(queryStr, variables))
            }.body()

            val stop = response.data?.stopPlace ?: return null
            StopPlace(
                id = stop.id,
                name = stop.name,
                quays = stop.quays.map { 
                    Quay(
                        it.id, 
                        it.publicCode, 
                        it.name, 
                        it.description,
                        it.estimatedCalls.firstOrNull()?.destinationDisplay?.frontText
                    ) 
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

package com.example.pulsetransit.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Every stop of one trip, in order; see [EnturRepository.getJourneyStops]. Empty on any failure. */
internal suspend fun HttpClient.fetchJourneyStops(
    serviceJourneyId: String,
    serviceDate: String,
    clientNameHeader: String,
    clientNameValue: String,
): List<JourneyStop> {
    val queryStr =
        """
        query GetJourney(${'$'}id: String!, ${'$'}date: Date!) {
          serviceJourney(id: ${'$'}id) {
            estimatedCalls(date: ${'$'}date) {
              expectedArrivalTime
              expectedDepartureTime
              cancellation
              quay {
                id
                name
                latitude
                longitude
                stopPlace {
                  id
                  name
                }
              }
            }
          }
        }
        """.trimIndent()
    val variables =
        buildJsonObject {
            put("id", serviceJourneyId)
            put("date", serviceDate)
        }
    return try {
        val response: JourneyResponse =
            post("https://api.entur.io/journey-planner/v3/graphql") {
                header(clientNameHeader, clientNameValue)
                contentType(ContentType.Application.Json)
                setBody(JourneyRequest(queryStr, variables))
            }.body()
        response.data
            ?.serviceJourney
            ?.estimatedCalls
            .orEmpty()
            .mapNotNull { call ->
                val time = call.expectedArrivalTime ?: call.expectedDepartureTime ?: return@mapNotNull null
                JourneyStop(
                    quayId = call.quay?.id,
                    stopPlaceId = call.quay?.stopPlace?.id,
                    name = call.quay?.stopPlace?.name ?: call.quay?.name ?: return@mapNotNull null,
                    expectedTime = time,
                    isCancelled = call.cancellation,
                    latitude = call.quay?.latitude,
                    longitude = call.quay?.longitude,
                )
            }
    } catch (e: CancellationException) {
        // Rethrown, unlike every other failure: the details cancel a load when another departure
        // is picked, and an empty answer from the old one would overwrite the new one's spinner.
        throw e
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@Serializable
private data class JourneyRequest(
    val query: String,
    val variables: JsonObject,
)

@Serializable
private data class JourneyResponse(
    val data: JourneyData? = null,
)

@Serializable
private data class JourneyData(
    val serviceJourney: JourneyGraphQL? = null,
)

@Serializable
private data class JourneyGraphQL(
    val estimatedCalls: List<JourneyCallGraphQL> = emptyList(),
)

@Serializable
private data class JourneyCallGraphQL(
    val expectedArrivalTime: String? = null,
    val expectedDepartureTime: String? = null,
    val cancellation: Boolean = false,
    val quay: JourneyQuayGraphQL? = null,
)

@Serializable
private data class JourneyQuayGraphQL(
    val id: String,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val stopPlace: JourneyStopPlaceGraphQL? = null,
)

@Serializable
private data class JourneyStopPlaceGraphQL(
    val id: String,
    val name: String? = null,
)

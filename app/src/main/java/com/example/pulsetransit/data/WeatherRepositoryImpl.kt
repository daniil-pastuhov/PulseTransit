package com.example.pulsetransit.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

class WeatherRepositoryImpl(private val client: HttpClient) : WeatherRepository {
    
    @Serializable
    private data class MetResponse(
        val properties: MetProperties
    )

    @Serializable
    private data class MetProperties(
        val timeseries: List<MetTimeSeries>
    )

    @Serializable
    private data class MetTimeSeries(
        val time: String,
        val data: MetData
    )

    @Serializable
    private data class MetData(
        val instant: MetInstant,
        val next_1_hours: MetNextHours? = null,
        val next_6_hours: MetNextHours? = null
    )

    @Serializable
    private data class MetInstant(
        val details: MetDetails
    )

    @Serializable
    private data class MetDetails(
        val air_temperature: Double,
        val precipitation_amount: Double? = null
    )

    @Serializable
    private data class MetNextHours(
        val summary: MetSummary,
        val details: MetNextDetails? = null
    )

    @Serializable
    private data class MetSummary(
        val symbol_code: String
    )

    @Serializable
    private data class MetNextDetails(
        val precipitation_amount: Double? = null
    )

    override suspend fun getWeather(lat: Double, lon: Double): List<WeatherInfo> {
        return try {
            val response: MetResponse = client.get("https://api.met.no/weatherapi/locationforecast/2.0/compact") {
                header("User-Agent", "PulseTransit/1.0 github.com/user/PulseTransit")
                parameter("lat", lat)
                parameter("lon", lon)
            }.body()

            // Filter for next 24 hours, taking samples (e.g., every 3 hours or as available)
            response.properties.timeseries.take(24).map { ts ->
                WeatherInfo(
                    time = ts.time,
                    temperature = ts.data.instant.details.air_temperature,
                    symbolCode = ts.data.next_1_hours?.summary?.symbol_code ?: "unknown",
                    precipitation = ts.data.next_1_hours?.details?.precipitation_amount ?: 0.0
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

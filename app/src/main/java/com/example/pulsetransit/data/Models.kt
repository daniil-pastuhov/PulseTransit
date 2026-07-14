package com.example.pulsetransit.data

import kotlinx.serialization.Serializable

@Serializable
data class StopPlace(
    val id: String,
    val name: String,
    val locality: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val quays: List<Quay> = emptyList()
)

@Serializable
data class Quay(
    val id: String,
    val publicCode: String? = null,
    val name: String? = null,
    val description: String? = null,
    val towards: String? = null
)

@Serializable
data class Departure(
    val line: String,
    val destination: String,
    val expectedTime: String, // ISO timestamp
    val aimedTime: String,    // ISO timestamp
    val isRealtime: Boolean,
    val isCancelled: Boolean = false,
    val transportMode: String? = null,
    val delayMinutes: Int = 0,
    val notices: List<String> = emptyList(),
    val quayId: String? = null
)

@Serializable
data class WeatherInfo(
    val time: String,
    val temperature: Double,
    val symbolCode: String,
    val precipitation: Double = 0.0
)

@Serializable
data class TripLeg(
    val mode: String? = null,       // "foot", "bus", "metro", ...
    val lineName: String? = null,   // null for foot legs
    val fromName: String? = null,
    val durationMinutes: Int = 0
)

@Serializable
data class TripInfo(
    val destinationName: String,
    val expectedStartTime: String,
    val lineName: String,
    val durationMinutes: Int,
    val originName: String,
    val legs: List<TripLeg> = emptyList()
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, AUTO
}

enum class BurnInProtectionLevel(val maxOffsetDp: Float) {
    OFF(0f),
    LOW(3f),
    MEDIUM(6f),
    HIGH(12f)
}

package com.example.enturstopboard.data

interface EnturRepository {
    suspend fun searchStopPlaces(query: String): List<StopPlace>
    suspend fun getNearbyStopPlaces(lat: Double, lon: Double, radiusKm: Double): List<StopPlace>
    suspend fun getDepartures(stopId: String, limit: Int = 10, quayIds: Set<String>? = null): List<Departure>
    suspend fun getStopPlaceWithQuays(stopId: String): StopPlace?
    suspend fun getFastestTrip(fromIds: List<String>, toId: String): TripInfo?
}

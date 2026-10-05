package com.example.pulsetransit.data

interface WeatherRepository {
    /**
     * The next few hours of forecast. Served from the local cache while MET's `Expires` window is
     * still open or when the request fails, in which case the result carries the original fetch
     * time. Past hours are never returned.
     */
    suspend fun getWeather(
        lat: Double,
        lon: Double,
    ): Sourced<List<WeatherInfo>>
}

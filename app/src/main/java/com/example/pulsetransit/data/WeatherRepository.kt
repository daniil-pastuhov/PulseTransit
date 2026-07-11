package com.example.pulsetransit.data

interface WeatherRepository {
    suspend fun getWeather(lat: Double, lon: Double): List<WeatherInfo>
}

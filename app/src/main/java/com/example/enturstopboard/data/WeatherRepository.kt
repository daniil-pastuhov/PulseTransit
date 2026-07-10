package com.example.enturstopboard.data

interface WeatherRepository {
    suspend fun getWeather(lat: Double, lon: Double): List<WeatherInfo>
}

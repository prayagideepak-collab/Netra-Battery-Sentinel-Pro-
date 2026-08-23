package com.example.service

data class WeatherReport(
    val cityName: String = "Unknown",
    val country: String = "",
    val temp: Float = 25.0f,
    val weatherCode: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

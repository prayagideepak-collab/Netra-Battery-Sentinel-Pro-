package com.example.engines.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

object WeatherIconMapper {
    fun getWeatherIcon(condition: String, weatherCode: Int = 0, isNight: Boolean = false): ImageVector {
        val lowerCondition = condition.lowercase()
        return when {
            lowerCondition.contains("thunder") || weatherCode in 200..232 -> Icons.Outlined.Thunderstorm
            lowerCondition.contains("drizzle") || weatherCode in 300..321 -> Icons.Outlined.Grain
            lowerCondition.contains("heavy") || lowerCondition.contains("shower") || weatherCode in 500..531 -> Icons.Outlined.Grain
            lowerCondition.contains("rain") -> Icons.Outlined.WaterDrop
            lowerCondition.contains("snow") || lowerCondition.contains("sleet") || weatherCode in 600..622 -> Icons.Outlined.AcUnit
            lowerCondition.contains("fog") || lowerCondition.contains("mist") || lowerCondition.contains("haze") || weatherCode in 701..781 -> Icons.Outlined.BlurOn
            lowerCondition.contains("wind") -> Icons.Outlined.Air
            lowerCondition.contains("partly") -> if (isNight) Icons.Outlined.Nightlight else Icons.Outlined.WbCloudy
            lowerCondition.contains("cloud") || weatherCode in 801..804 -> Icons.Outlined.Cloud
            lowerCondition.contains("overcast") -> Icons.Outlined.Cloud
            isNight -> Icons.Outlined.DarkMode
            else -> Icons.Outlined.WbSunny
        }
    }
}

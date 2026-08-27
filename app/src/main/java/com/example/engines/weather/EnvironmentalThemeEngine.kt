package com.example.engines.weather

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class EnvironmentalThemeState {
    CLEAR_DAY,
    PARTLY_CLOUDY_DAY,
    CLOUDY_DAY,
    RAIN,
    HEAVY_RAIN,
    THUNDERSTORM,
    DRIZZLE,
    FOG,
    HAZE,
    WINDY,
    CLEAR_NIGHT,
    CLOUDY_NIGHT,
    UNKNOWN_ENVIRONMENT
}

object EnvironmentalThemeEngine {
    fun evaluateEnvironmentalState(condition: String, weatherCode: Int, dayNightState: String): EnvironmentalThemeState {
        val lower = condition.lowercase()
        val isNight = dayNightState.lowercase() == "night" || lower.contains("night")
        return when {
            lower.contains("thunder") || weatherCode in 200..232 -> EnvironmentalThemeState.THUNDERSTORM
            lower.contains("heavy") || lower.contains("shower") || weatherCode in 501..531 -> EnvironmentalThemeState.HEAVY_RAIN
            lower.contains("drizzle") || weatherCode in 300..321 -> EnvironmentalThemeState.DRIZZLE
            lower.contains("rain") || weatherCode == 500 -> EnvironmentalThemeState.RAIN
            lower.contains("snow") || lower.contains("sleet") || weatherCode in 600..622 -> EnvironmentalThemeState.RAIN
            lower.contains("fog") || weatherCode in 701..740 -> EnvironmentalThemeState.FOG
            lower.contains("haze") || lower.contains("smoke") || lower.contains("dust") -> EnvironmentalThemeState.HAZE
            lower.contains("wind") -> EnvironmentalThemeState.WINDY
            lower.contains("partly") -> if (isNight) EnvironmentalThemeState.CLOUDY_NIGHT else EnvironmentalThemeState.PARTLY_CLOUDY_DAY
            lower.contains("cloud") || weatherCode in 801..804 -> if (isNight) EnvironmentalThemeState.CLOUDY_NIGHT else EnvironmentalThemeState.CLOUDY_DAY
            isNight -> EnvironmentalThemeState.CLEAR_NIGHT
            else -> EnvironmentalThemeState.CLEAR_DAY
        }
    }

    fun getThemeColorScheme(state: EnvironmentalThemeState): ColorScheme {
        return when (state) {
            EnvironmentalThemeState.CLEAR_DAY -> lightColorScheme(
                primary = Color(0xFF0284C7), secondary = Color(0xFF0D9488), background = Color(0xFFF0F9FF), surface = Color(0xFFE0F2FE), onPrimary = Color.White
            )
            EnvironmentalThemeState.PARTLY_CLOUDY_DAY -> lightColorScheme(
                primary = Color(0xFF0284C7), secondary = Color(0xFF475569), background = Color(0xFFF8FAFC), surface = Color(0xFFF1F5F9), onPrimary = Color.White
            )
            EnvironmentalThemeState.CLOUDY_DAY -> lightColorScheme(
                primary = Color(0xFF475569), secondary = Color(0xFF64748B), background = Color(0xFFF1F5F9), surface = Color(0xFFE2E8F0), onPrimary = Color.White
            )
            EnvironmentalThemeState.RAIN -> darkColorScheme(
                primary = Color(0xFF38BDF8), secondary = Color(0xFF0284C7), background = Color(0xFF0B132B), surface = Color(0xFF1C2541), onPrimary = Color(0xFF001E30)
            )
            EnvironmentalThemeState.HEAVY_RAIN -> darkColorScheme(
                primary = Color(0xFF60A5FA), secondary = Color(0xFF2563EB), background = Color(0xFF050B14), surface = Color(0xFF1E293B), onPrimary = Color.White
            )
            EnvironmentalThemeState.THUNDERSTORM -> darkColorScheme(
                primary = Color(0xFFFACC15), secondary = Color(0xFF9333EA), background = Color(0xFF09090B), surface = Color(0xFF18181B), onPrimary = Color.Black
            )
            EnvironmentalThemeState.DRIZZLE -> lightColorScheme(
                primary = Color(0xFF0284C7), secondary = Color(0xFF38BDF8), background = Color(0xFFF8FAFC), surface = Color(0xFFE2E8F0), onPrimary = Color.White
            )
            EnvironmentalThemeState.FOG, EnvironmentalThemeState.HAZE -> lightColorScheme(
                primary = Color(0xFF64748B), secondary = Color(0xFF94A3B8), background = Color(0xFFF8FAFC), surface = Color(0xFFE2E8F0), onPrimary = Color.White
            )
            EnvironmentalThemeState.WINDY -> lightColorScheme(
                primary = Color(0xFF0D9488), secondary = Color(0xFF0284C7), background = Color(0xFFF0FDFA), surface = Color(0xFFCCFBF1), onPrimary = Color.White
            )
            EnvironmentalThemeState.CLEAR_NIGHT -> darkColorScheme(
                primary = Color(0xFF38BDF8), secondary = Color(0xFF818CF8), background = Color(0xFF030712), surface = Color(0xFF111827), onPrimary = Color.Black
            )
            EnvironmentalThemeState.CLOUDY_NIGHT -> darkColorScheme(
                primary = Color(0xFF94A3B8), secondary = Color(0xFF64748B), background = Color(0xFF0F172A), surface = Color(0xFF1E293B), onPrimary = Color.White
            )
            EnvironmentalThemeState.UNKNOWN_ENVIRONMENT -> lightColorScheme(
                primary = Color(0xFF0D6D44), secondary = Color(0xFF2E7D32), background = Color(0xFFF7FAF7), surface = Color(0xFFFFFFFF), onPrimary = Color.White
            )
        }
    }
}

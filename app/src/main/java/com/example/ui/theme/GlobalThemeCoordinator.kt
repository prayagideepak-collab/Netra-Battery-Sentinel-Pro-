package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import com.example.engines.festival.FestivalContextEngine
import com.example.engines.weather.EnvironmentalContextEngine
import com.example.engines.weather.EnvironmentalThemeEngine

object GlobalThemeCoordinator {
    fun resolveAuthoritativeTheme(themeMode: String, darkTheme: Boolean, batteryLevel: Int = 100): ColorScheme {
        // Priority 1: Active verified festival
        val currentFestival = FestivalContextEngine.currentFestival.value
        if (currentFestival != null) {
            return getAutoFestivalColorScheme()
        }

        // Priority 2: Weather + Day/Night + City Environment
        val envDataset = EnvironmentalContextEngine.datasetFlow.value
        val envState = EnvironmentalThemeEngine.evaluateEnvironmentalState(
            condition = envDataset.weatherCondition,
            weatherCode = 0,
            dayNightState = envDataset.dayNightState
        )
        return EnvironmentalThemeEngine.getThemeColorScheme(envState)
    }
}

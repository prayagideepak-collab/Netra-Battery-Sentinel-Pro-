package com.example.engines.widget

enum class WidgetLayoutType {
    COMPACT_1X1,
    STANDARD_2X2,
    DETAILED_4X2,
    LOCKSCREEN,
    QUICK_SETTINGS_TILE
}

data class WidgetDisplayState(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 25.0f,
    val healthStatus: String = "GOOD",
    val estimatedRemainingTimeText: String = "18h 30m remaining",
    val currentThermalRisk: String = "NORMAL",
    val activeEngineCount: Int = 10,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

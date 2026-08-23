package com.example.engines.ibce

enum class OptimizationLevel {
    NORMAL,
    LEVEL_1,
    LEVEL_2,
    LEVEL_3
}

enum class ThermalMode {
    NORMAL,
    BALANCED,
    CRITICAL
}

data class IbceStatus(
    val isAutoControlEnabled: Boolean = true,
    val currentOptimizationLevel: OptimizationLevel = OptimizationLevel.NORMAL,
    val currentThermalMode: ThermalMode = ThermalMode.NORMAL,
    val lastTriggerReason: String = "Initialized",
    val batteryLevel: Int = 100,
    val temperatureCelsius: Float = 30.0f
)

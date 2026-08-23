package com.example.data

/**
 * HourlyChargingPattern
 * Represents aggregated charging and battery level behavior grouped by hour of the day (0-23).
 */
data class HourlyChargingPattern(
    val hourOfDay: Int,
    val avgLevel: Float,
    val minLevel: Int,
    val maxLevel: Int,
    val sampleCount: Int,
    val chargingSampleCount: Int
)

/**
 * DailyChargingPattern
 * Represents charging pattern statistics grouped by day of week (1=Sunday, 7=Saturday).
 */
data class DailyChargingPattern(
    val dayOfWeek: Int,
    val avgLevel: Float,
    val chargingSampleCount: Int,
    val totalSampleCount: Int
)

/**
 * ChargingTimeWindowAnalysis
 * Identifies high-frequency charging windows throughout the 24-hour cycle.
 */
data class ChargingTimeWindowAnalysis(
    val hourOfDay: Int,
    val chargingFrequencyPercent: Float,
    val avgTemperature: Float,
    val avgVoltageMv: Float
)

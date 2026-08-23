package com.example.engines.analytics

data class BatteryAnalyticsSummary(
    val batteryScore: Int = 94,             // 0 - 100
    val deviceEfficiencyScore: Int = 91,    // 0 - 100
    val chargingQualityScore: Int = 96,     // 0 - 100
    val totalScreenOnTimeMinutes: Int = 340,
    val totalStandbyTimeMinutes: Int = 1100,
    val totalChargingSessions: Int = 2,
    val avgChargingTempCelsius: Float = 31.4f,
    val totalEnergyConsumedMah: Float = 2850f,
    val batteryWearPercent: Float = 2.1f,
    val lastGeneratedMs: Long = System.currentTimeMillis()
)

data class AnalyticsReport(
    val title: String,
    val period: String, // "Daily", "Weekly", "Monthly"
    val score: Int,
    val summaryText: String,
    val generatedDate: String
)

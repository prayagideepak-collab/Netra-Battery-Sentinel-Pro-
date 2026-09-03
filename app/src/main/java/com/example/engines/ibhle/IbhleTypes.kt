package com.example.engines.ibhle

enum class HealthSubSection {
    OVERVIEW,
    ANALYSIS,
    CHARGING_HISTORY,
    AGING,
    CYCLE_ANALYSIS,
    MAINTENANCE,
    REPORTS
}

data class BatteryHealthMetrics(
    val currentHealthScore: Int = 98,
    val estimatedCapacityMah: Int = 0,
    val designCapacityMah: Int = 0,
    val totalChargeCycles: Int = 0,
    val avgTemperatureCelsius: Float = 0f,
    val chargingEfficiencyPercent: Float = 96.8f,
    val estimatedBatteryAgeMonths: Float = 0f,
    val overallLifecycleScore: Int = 98,
    val habitScore: Int = 95,
    val confidenceLevel: String = "Authoritative Telemetry",
    val dataQualityFlag: String = "HIGH_QUALITY",
    val capabilityStatus: String = "Hardware HAL Telemetry Access",
    val lastAnalysisTimeMs: Long = System.currentTimeMillis()
)

data class HealthAnalysisItem(
    val id: String,
    val title: String,
    val metricValue: String,
    val status: String, // "OPTIMAL", "STABLE", "ATTENTION"
    val detail: String
)

data class MaintenanceRecommendation(
    val id: String,
    val title: String,
    val recommendation: String,
    val urgency: String, // "LOW", "MEDIUM", "HIGH"
    val isAdvisoryOnly: Boolean = true
)

data class BatteryHealthReport(
    val reportId: String,
    val title: String,
    val period: String, // "Daily Summary", "Weekly Summary", "Monthly Summary", "Lifecycle Report", "Charging Behaviour Report"
    val healthScore: Int,
    val keyTakeaway: String,
    val generatedDate: String
)

data class BatteryHealthAuditLog(
    val id: String,
    val eventType: String, // "Health Score Updated", "Cycle Milestone Reached", "Aging Analysis Completed", "Health Report Generated", "Maintenance Recommendation Generated"
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

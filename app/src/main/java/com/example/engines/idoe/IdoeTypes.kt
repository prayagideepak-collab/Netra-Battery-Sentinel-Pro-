package com.example.engines.idoe

enum class OptimizationMode {
    NORMAL,
    BATTERY_SAVER,
    CRITICAL_BATTERY,
    CRITICAL_TEMPERATURE
}

enum class OptimizationCategory {
    STATUS,
    BATTERY_SAVER,
    THERMAL,
    CHARGING,
    BACKGROUND,
    APP_RECOMMENDATIONS
}

data class OptimizationMetricsState(
    val currentMode: OptimizationMode = OptimizationMode.NORMAL,
    val batteryImpactSavingPercent: Float = 14.5f,
    val thermalStatus: String = "OPTIMAL (30.8°C)",
    val activeActionsCount: Int = 5,
    val estimatedBatteryTimeSavedMinutes: Int = 52,
    val backgroundActivityStatus: String = "Batched & Deferred",
    val isSafetyOverrideActive: Boolean = false,
    val lastOptimizationTimeMs: Long = System.currentTimeMillis()
)

data class OptimizationActionItem(
    val id: String,
    val category: OptimizationCategory,
    val title: String,
    val description: String,
    val impactText: String,
    val isActive: Boolean = true,
    val isAdvisoryOnly: Boolean = false
)

data class OptimizationAuditLog(
    val id: String,
    val actionType: String, // "Optimization Started", "Optimization Ended", "Optimization Recommendation", "Optimization Deferred", "Permission Granted", "Permission Revoked"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class OptimizationRecommendation(
    val id: String,
    val title: String,
    val rationale: String,
    val actionSuggestion: String,
    val requiredPermission: String? = null,
    val estimatedGain: String
)

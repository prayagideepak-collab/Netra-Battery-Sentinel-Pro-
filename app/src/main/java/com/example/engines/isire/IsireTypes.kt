package com.example.engines.isire

enum class IntegritySubSection {
    MONITOR,
    COMPATIBILITY,
    CONFIG_VALIDATOR,
    MODULE_SYNC,
    RELIABILITY_ANALYSIS,
    REPORTS
}

enum class FailureSeverity {
    INFO,
    WARNING,
    MAJOR,
    CRITICAL
}

data class IntegrityCheckResult(
    val checkId: String,
    val componentName: String,
    val isPassed: Boolean,
    val severity: FailureSeverity,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CompatibilityProfile(
    val androidVersion: String = "Android 14 (API 34)",
    val manufacturer: String = "Google",
    val batteryCycleApiAvailable: Boolean = true,
    val currentNowApiAvailable: Boolean = true,
    val thermalHeadroomApiAvailable: Boolean = true,
    val sensorIntelligenceAvailable: Boolean = true,
    val unsupportedFeaturesHiddenCount: Int = 0
)

data class NavigationHierarchyStatus(
    val isHierarchyValid: Boolean = true,
    val totalMainSections: Int = 4,
    val totalSubSections: Int = 24,
    val orphanScreensCount: Int = 0,
    val duplicateRoutesCount: Int = 0,
    val lastHierarchyRebuildTime: String = "2026-08-05 22:40"
)

data class ReliabilityMetrics(
    val overallReliabilityScore: Int = 99,
    val runtimeStabilityScore: Int = 100,
    val crashHistoryCount: Int = 0,
    val recoveryFrequency: Int = 0,
    val memoryStabilityPercent: Float = 99.8f,
    val syncQualityPercent: Float = 99.9f,
    val dbIntegrityScore: Int = 100,
    val configHealthScore: Int = 100
)

data class AutoRepairEvent(
    val repairId: String,
    val targetMetadata: String,
    val actionTaken: String,
    val isSuccessful: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

data class SystemIntegrityReport(
    val reportId: String,
    val title: String,
    val category: String, // "Integrity", "Compatibility", "Configuration", "Reliability", "Synchronization"
    val score: Int,
    val summary: String,
    val generatedDate: String
)

data class IntegrityAuditLog(
    val id: String,
    val eventType: String, // "Integrity Check Started", "Auto Repair Performed", "Sync Rebuilt", "Reliability Score Updated"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

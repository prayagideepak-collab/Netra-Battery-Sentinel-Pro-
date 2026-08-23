package com.example.engines.release

enum class ReleaseChannel {
    STABLE,
    BETA,
    INTERNAL
}

enum class ModuleCategory {
    CORE_MONITORING,
    SAFETY_HEALTH,
    ANALYTICS_REPORTS,
    DATA_EXPORT,
    SYSTEM_INTEGRITY
}

data class RegisteredModuleInfo(
    val moduleName: String,
    val version: String = "3.0.0-PROD",
    val dependencies: List<String> = emptyList(),
    val category: ModuleCategory = ModuleCategory.CORE_MONITORING,
    val permissionRequirements: List<String> = emptyList(),
    val runtimeState: String = "ACTIVE",
    val lastValidation: String = "2026-08-05 22:50",
    val integrityStatus: String = "PASSED"
)

data class StartupValidationReport(
    val isValidated: Boolean = true,
    val databaseStatus: String = "PASSED",
    val runtimeStatus: String = "PASSED",
    val servicesStatus: String = "PASSED",
    val workersStatus: String = "PASSED",
    val settingsStatus: String = "PASSED",
    val permissionsStatus: String = "PASSED",
    val moduleRegistryStatus: String = "PASSED",
    val navigationRegistryStatus: String = "PASSED",
    val exportEngineStatus: String = "PASSED",
    val reportsEngineStatus: String = "PASSED",
    val validationTime: String = "2026-08-05 22:50"
)

data class VersionMigrationInfo(
    val previousVersion: String = "2.9.8",
    val currentVersion: String = "3.0.0-PROD",
    val isMigrationSuccessful: Boolean = true,
    val historyPreserved: Boolean = true,
    val logsPreserved: Boolean = true,
    val settingsPreserved: Boolean = true,
    val details: String = "Application Updated: Previous Version 2.9.8 -> Current Version 3.0.0-PROD. All historical data, settings, and logs preserved."
)

data class BackupSyncStatus(
    val localBackupAvailable: Boolean = true,
    val lastLocalBackupTime: String = "2026-08-05 22:00",
    val cloudBackupEnabled: Boolean = false, // Opt-in Google Drive
    val lastCloudSyncTime: String = "Not Configured",
    val encryptedPayloadSizeKb: Long = 128
)

data class ProductionHealthMetrics(
    val overallHealthScore: Int = 100,
    val runtimeStabilityScore: Int = 100,
    val crashRatePercent: Float = 0.0f,
    val memoryUsageMb: Float = 28.4f,
    val threadHealthScore: Int = 100,
    val batteryImpactPercent: Float = 0.15f,
    val dbIntegrityScore: Int = 100,
    val exportIntegrityScore: Int = 100,
    val recoveryStabilityScore: Int = 100,
    val serviceStabilityScore: Int = 100
)

data class LongTermStabilityTrend(
    val crashFrequency: Int = 0,
    val anrFrequency: Int = 0,
    val recoveryFrequency: Int = 0,
    val serviceUptimePercent: Float = 99.99f,
    val memoryGrowthRate: String = "+0.0% / 24h",
    val dbGrowthRate: String = "12.4 KB / day"
)

data class PerformanceBaseline(
    val startupTimeMs: Long = 240,
    val memoryUsageMb: Float = 28.4f,
    val cpuUsagePercent: Float = 0.8f,
    val batteryConsumptionPercentPerHour: Float = 0.12f,
    val dbQueryTimeMs: Long = 4,
    val exportGenerationTimeMs: Long = 120,
    val reportGenerationTimeMs: Long = 85
)

data class ReleaseSuiteTestResult(
    val testName: String,
    val category: String,
    val isPassed: Boolean = true,
    val executionTimeMs: Long = 12,
    val details: String = "Test executed cleanly with zero anomalies."
)

data class FeatureFlag(
    val key: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean
)

data class ReleaseFrameworkState(
    val channel: ReleaseChannel = ReleaseChannel.STABLE,
    val versionName: String = "3.0.0-PROD",
    val versionCode: Int = 300,
    val targetAndroidSdk: Int = 34,
    val minAndroidSdk: Int = 26,
    val isWearOsCompatible: Boolean = true,
    val isFoldableTabletOptimized: Boolean = true,
    val isProductionFrozen: Boolean = true,
    val activeFeatureFlagsCount: Int = 5,
    val lastCheckMs: Long = System.currentTimeMillis()
)

data class ReleaseAuditEntry(
    val entryId: String,
    val actionType: String, // "Production Build Created", "Validation Passed", "Release Approved"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

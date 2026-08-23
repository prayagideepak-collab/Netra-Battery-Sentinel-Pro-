package com.example.engines.developer

enum class DeveloperSubSection {
    RUNTIME_DIAGNOSTICS,
    SERVICE_INSPECTOR,
    ENGINE_MONITOR,
    DATABASE_INSPECTOR,
    PERFORMANCE_MONITOR,
    DIAGNOSTIC_REPORTS,
    MAINTENANCE_TOOLS,
    DEBUG_INFORMATION
}

data class ModuleHealthStatus(
    val name: String,
    val isHealthy: Boolean,
    val statusText: String,
    val priority: Int
)

data class DeveloperDiagnosticsState(
    val isDeveloperModeActive: Boolean = true,
    val totalEnginesRegistered: Int = 18,
    val activeThreadsCount: Int = 12,
    val activeWorkersCount: Int = 1,
    val memoryUsageMb: Float = 24.5f,
    val systemHealthScore: Int = 98,
    val benchmarkScore: Int = 1450,
    val lastDiagnosticExportMs: Long = System.currentTimeMillis()
)

data class ServiceInspectorItem(
    val serviceName: String,
    val state: String, // "RUNNING", "SLEEPING", "WAITING", "COOLDOWN"
    val lastHeartbeatMs: Long,
    val restartCount: Int
)

data class DatabaseInspectorState(
    val dbVersion: Int = 15,
    val tableCount: Int = 14,
    val totalRecordCount: Int = 14280,
    val dbSizeMb: Float = 4.2f,
    val lastOptimizationTime: String = "2026-08-05 22:00",
    val integrityStatus: String = "HEALTHY_VERIFIED"
)

data class PerformanceMetricsState(
    val cpuUsagePercent: Float = 0.15f,
    val memoryUsageMb: Float = 24.5f,
    val batteryImpactPercent: Float = 0.02f,
    val workerCount: Int = 1,
    val threadCount: Int = 12,
    val queueSize: Int = 0,
    val anrRiskLevel: String = "VERY_LOW",
    val avgFrameTimeMs: Float = 11.2f
)

data class SelfTestStepResult(
    val testName: String,
    val category: String, // "Runtime", "Database", "Services", "Recovery", "Export"
    val isPassed: Boolean = true,
    val durationMs: Long,
    val details: String
)

data class EngineeringTimelineItem(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // "Runtime", "Engine", "Database", "Recovery"
    val eventName: String,
    val details: String
)

data class MaintenanceAuditLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionName: String,
    val result: String,
    val details: String
)

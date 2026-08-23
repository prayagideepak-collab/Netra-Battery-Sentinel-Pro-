package com.example.engines.ipropme

enum class PerformanceMode {
    PERFORMANCE,
    BALANCED,
    IDLE,
    CHARGING,
    BATTERY_SAVER,
    CRITICAL_BATTERY,
    CRITICAL_TEMPERATURE
}

enum class SensorSamplingRate(val intervalMs: Long) {
    REAL_TIME(1_000L),
    MEDIUM(5_000L),
    SLOW(15_000L),
    MINIMAL(60_000L)
}

data class ResourceBudget(
    val engineName: String,
    val maxCpuPercent: Int = 15,
    val maxMemoryMb: Float = 50f,
    val maxThreads: Int = 10
)

data class IpropmeMetrics(
    val currentMode: PerformanceMode = PerformanceMode.BALANCED,
    val sensorSamplingRate: SensorSamplingRate = SensorSamplingRate.MEDIUM,
    val heapUsageMb: Float = 0f,
    val maxHeapMb: Float = 0f,
    val activeThreadCount: Int = 0,
    val activeWorkerCount: Int = 0,
    val databaseSizeMb: Float = 0f,
    val performanceHealthScore: Int = 100, // 0 - 100%
    val lastOptimizationMs: Long = System.currentTimeMillis(),
    val totalOptimizationsExecuted: Int = 0,
    val isPowerSaveActive: Boolean = false,
    val isScreenOn: Boolean = true,
    val batteryLevel: Int = 100,
    val temperatureCelsius: Float = 25f
)

data class PerformanceAuditEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val details: String,
    val modeAtTime: PerformanceMode
)

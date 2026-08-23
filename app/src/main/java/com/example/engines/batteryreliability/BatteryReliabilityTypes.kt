package com.example.engines.batteryreliability

enum class RecoveryLevel {
    HEALTH_VERIFICATION,
    LISTENER_REREGISTER,
    COROUTINE_RESTART,
    WORKER_RESTART,
    FOREGROUND_SERVICE_RESTART,
    MANUAL_RECOVERY_REQUIRED
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class EngineHealthStatus(
    val engineName: String,
    val isThreadAlive: Boolean = true,
    val isReceiverRegistered: Boolean = true,
    val isCallbackActive: Boolean = true,
    val lastRecoveryLevel: RecoveryLevel = RecoveryLevel.HEALTH_VERIFICATION,
    val recoveryCooldownActive: Boolean = false,
    val faultCount: Int = 0,
    val statusSummary: String = "Healthy"
)

data class BatteryAiInsight(
    val insightId: String,
    val title: String,
    val description: String,
    val confidence: ConfidenceLevel,
    val category: String, // "Drain", "Charging", "Temperature", "Aging"
    val timestamp: Long = System.currentTimeMillis()
)

data class BatteryPredictionModel(
    val remainingBatteryTimeMinutes: Int = 340,
    val timeToFullChargeMinutes: Int = 45,
    val chargingCompletionEstimate: String = "100% in 45m",
    val dailyDrainTrendPercent: Float = 2.4f,
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH
)

data class PerformanceMonitorMetrics(
    val cpuUsagePercent: Float = 0.6f,
    val ramUsageMb: Float = 27.8f,
    val backgroundRuntimeHours: Float = 14.2f,
    val wakeupsPerHour: Int = 3,
    val notificationLatencyMs: Long = 18
)

data class BatteryReliabilityState(
    val overallHealthScore: Int = 98,
    val activeEngineCount: Int = 5,
    val totalRecoveriesPerformed: Int = 0,
    val exceptionShieldTriggers: Int = 0,
    val lastVerificationTime: String = "Just now"
)

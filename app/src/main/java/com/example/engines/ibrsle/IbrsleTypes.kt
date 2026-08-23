package com.example.engines.ibrsle

import android.content.Context

enum class RuntimeServiceState {
    CREATED,
    REGISTERED,
    INITIALIZING,
    RUNNING,
    IDLE,
    SLEEPING,
    PAUSED,
    RECOVERING,
    STOPPED,
    FAILED
}

enum class ServicePriority(val level: Int) {
    CRITICAL(100),
    HIGH(75),
    MEDIUM(50),
    LOW(25),
    OPTIONAL(10)
}

enum class RestartPolicy {
    ALWAYS_RESTART,
    RESTART_ON_FAILURE,
    MANUAL_ONLY,
    ON_POWER_CONNECTED
}

enum class BatteryImpact {
    NEGLIGIBLE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class RegisteredServiceSpec(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val priority: ServicePriority = ServicePriority.MEDIUM,
    val dependencies: List<String> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val restartPolicy: RestartPolicy = RestartPolicy.RESTART_ON_FAILURE,
    val batteryImpact: BatteryImpact = BatteryImpact.LOW,
    val isCore: Boolean = false,
    val startAction: (Context) -> Boolean = { true },
    val stopAction: (Context) -> Unit = {},
    val healthCheck: (Context) -> Boolean = { true }
)

data class RuntimeServiceStatus(
    val spec: RegisteredServiceSpec,
    val currentState: RuntimeServiceState = RuntimeServiceState.REGISTERED,
    val lastHeartbeatMs: Long = System.currentTimeMillis(),
    val recoveryCount: Int = 0,
    val lastRestartMs: Long = 0L,
    val lastError: String? = null,
    val threadAlive: Boolean = true,
    val workerAlive: Boolean = true,
    val schedulerActive: Boolean = true,
    val callbackActive: Boolean = true
)

data class IbrsleMetrics(
    val totalRegistered: Int = 0,
    val runningCount: Int = 0,
    val sleepingCount: Int = 0,
    val pausedCount: Int = 0,
    val stoppedCount: Int = 0,
    val failedCount: Int = 0,
    val totalRecoveryCount: Int = 0,
    val healthScore: Int = 100, // 0 - 100%
    val lastSyncOrCheckMs: Long = System.currentTimeMillis(),
    val isScreenOn: Boolean = true,
    val isPowerSaverActive: Boolean = false,
    val isChargerConnected: Boolean = false,
    val heapUsageMb: Float = 0f,
    val maxHeapMb: Float = 0f
)

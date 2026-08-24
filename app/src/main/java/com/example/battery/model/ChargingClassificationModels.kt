package com.example.battery.model

/**
 * Netra Battery Sentinel Pro — Authoritative Charging Classification Data Models
 *
 * Implements deterministic classification states with zero arbitrary guessing.
 */

enum class ChargingState(val displayName: String) {
    NOT_CHARGING("Discharging"),
    INITIALIZING("Charging — Calculating..."),
    SLOW("Slow Charging"),
    NORMAL("Normal Charging"),
    FAST("Fast Charging"),
    MAINTENANCE("Maintenance / Near-Full"),
    INSUFFICIENT_DATA("Charging — Insufficient Data");

    val isCharging: Boolean
        get() = this != NOT_CHARGING
}

enum class ChargingConfidence {
    INITIALIZING,
    LOW_SAMPLES,
    ESTIMATING,
    STABLE
}

data class ChargingTelemetryInput(
    val isCharging: Boolean,
    val powerSource: String = "None", // "AC", "USB", "Wireless", "Dock", "None"
    val currentNowMa: Int? = null,
    val voltageMv: Int? = null,
    val powerWatt: Float? = null,
    val batteryPercentage: Int? = null,
    val measuredVelocityPctPerHr: Float? = null,
    val sessionDurationSeconds: Long = 0L,
    val temperatureCelsius: Float? = null,
    val temperatureTrend: String = "STABLE", // "RISING", "FALLING", "STABLE"
    val isScreenOn: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class ChargingClassificationResult(
    val state: ChargingState,
    val confidence: ChargingConfidence,
    val displayName: String = state.displayName,
    val powerSource: String,
    val inputPowerW: Float?,
    val currentMa: Int?,
    val voltageV: Float?,
    val netBatteryGainPctPerHr: Float?,
    val isThermalLimited: Boolean = false,
    val isLoadLimited: Boolean = false,
    val isNearFullTapering: Boolean = false,
    val explanation: String,
    val deviceLearnedBaselinePctPerHr: Float?,
    val timestampMs: Long
)

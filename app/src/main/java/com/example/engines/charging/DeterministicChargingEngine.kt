package com.example.engines.charging

import android.content.Context
import com.example.battery.engine.ChargingClassificationEngine
import com.example.battery.model.ChargingState
import com.example.battery.model.ChargingTelemetryInput

/**
 * Netra Deterministic & Evidence-Based Charging Engine
 *
 * Fully delegates to the authoritative [ChargingClassificationEngine].
 * Maintains zero arbitrary 15%/hr fallback constants and strictly adheres to the Zero-Fabrication rule.
 */

enum class EvidenceChargingState(val displayName: String) {
    INITIALIZING("Charging — Calculating rate..."),
    SLOW("Slow Charging"),
    SLOW_THERMAL_LIMITED("Slow Charging — Thermal Limited"),
    SLOW_LOAD_LIMITED("Slow Charging — High Device Load"),
    NORMAL("Normal Charging"),
    FAST("Fast Charging"),
    MAINTENANCE("Maintenance / Net-Zero"),
    INSUFFICIENT_TELEMETRY("Charging — Insufficient telemetry"),
    NOT_CHARGING("Discharging")
}

data class EvidenceAssessment(
    val state: EvidenceChargingState,
    val measuredRatePctPerHr: Float,
    val inputPowerW: Float?,
    val currentMa: Int?,
    val voltageV: Float?,
    val powerSource: String,
    val explanation: String,
    val isThermalThrottlingDetected: Boolean = false,
    val isHeavyLoadDetected: Boolean = false,
    val deviceBaselineRatePctPerHr: Float
)

object DeterministicChargingEngine {

    fun init(context: Context) {
        ChargingClassificationEngine.init(context)
    }

    /**
     * Records a completed valid charging session to update the device-specific baseline.
     */
    fun recordSessionCompletion(context: Context, measuredAvgRatePctHr: Float, powerSource: String = "AC") {
        ChargingClassificationEngine.recordSessionCompletion(context, powerSource, measuredAvgRatePctHr)
    }

    /**
     * Evaluates charging behaviour without guesswork by delegating to authoritative ChargingClassificationEngine.
     */
    fun evaluate(
        isCharging: Boolean,
        sessionDurationSeconds: Long,
        measuredRatePctPerHr: Float,
        powerWatt: Float,
        currentMa: Int,
        voltageMv: Int,
        temperatureCelsius: Float,
        temperatureTrend: String,
        isScreenOn: Boolean,
        powerSource: String
    ): EvidenceAssessment {
        val result = ChargingClassificationEngine.classify(
            ChargingTelemetryInput(
                isCharging = isCharging,
                powerSource = powerSource,
                currentNowMa = currentMa,
                voltageMv = voltageMv,
                powerWatt = powerWatt,
                batteryPercentage = null,
                measuredVelocityPctPerHr = measuredRatePctPerHr,
                sessionDurationSeconds = sessionDurationSeconds,
                temperatureCelsius = temperatureCelsius,
                temperatureTrend = temperatureTrend,
                isScreenOn = isScreenOn,
                timestampMs = System.currentTimeMillis()
            )
        )

        val mappedState = when (result.state) {
            ChargingState.NOT_CHARGING -> EvidenceChargingState.NOT_CHARGING
            ChargingState.INITIALIZING -> EvidenceChargingState.INITIALIZING
            ChargingState.SLOW -> {
                if (result.isThermalLimited) EvidenceChargingState.SLOW_THERMAL_LIMITED
                else if (result.isLoadLimited) EvidenceChargingState.SLOW_LOAD_LIMITED
                else EvidenceChargingState.SLOW
            }
            ChargingState.NORMAL -> EvidenceChargingState.NORMAL
            ChargingState.FAST -> EvidenceChargingState.FAST
            ChargingState.MAINTENANCE -> EvidenceChargingState.MAINTENANCE
            ChargingState.INSUFFICIENT_DATA -> EvidenceChargingState.INSUFFICIENT_TELEMETRY
        }

        return EvidenceAssessment(
            state = mappedState,
            measuredRatePctPerHr = result.netBatteryGainPctPerHr ?: 0f,
            inputPowerW = result.inputPowerW,
            currentMa = result.currentMa,
            voltageV = result.voltageV,
            powerSource = result.powerSource,
            explanation = result.explanation,
            isThermalThrottlingDetected = result.isThermalLimited,
            isHeavyLoadDetected = result.isLoadLimited,
            deviceBaselineRatePctPerHr = result.deviceLearnedBaselinePctPerHr ?: 0f
        )
    }
}

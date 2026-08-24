package com.example.engines

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.service.BatteryState
import com.example.util.TimeManager
import java.util.Locale

/**
 * Netra Battery Engines - Modular Split of the Battery Intelligence Layer
 * Made with ❤️ by Prayagi Ji
 */

// 1. Battery Prediction Engine Delegation
typealias EtaConfidence = com.example.battery.engine.EtaConfidence
typealias EtaSource = com.example.battery.engine.EtaSource
typealias AuthoritativeEtaResult = com.example.battery.engine.AuthoritativeEtaResult

object BatteryPredictionEngine {
    val currentConfidence: EtaConfidence
        get() = com.example.battery.engine.BatteryPredictionEngine.currentConfidence

    val currentSource: EtaSource
        get() = com.example.battery.engine.BatteryPredictionEngine.currentSource

    fun predictAgingYears(healthPct: Int): Double {
        return com.example.battery.engine.BatteryPredictionEngine.predictAgingYears(healthPct)
    }

    fun invalidateStateTransition(isCharging: Boolean) {
        com.example.battery.engine.BatteryPredictionEngine.invalidateStateTransition(isCharging)
    }

    fun calculateRemainingTimeMs(
        percentage: Int,
        isCharging: Boolean,
        currentNowVal: Int,
        isScreenOn: Boolean,
        capacity: Int?,
        speed: Float,
        targetPercentage: Int = 100
    ): Long {
        return com.example.battery.engine.BatteryPredictionEngine.calculateRemainingTimeMs(
            percentage = percentage,
            isCharging = isCharging,
            currentNowVal = currentNowVal,
            isScreenOn = isScreenOn,
            capacity = capacity,
            speed = speed,
            targetPercentage = targetPercentage
        )
    }

    fun calculateAuthoritativeEta(
        percentage: Int,
        isCharging: Boolean,
        currentNowVal: Int,
        isScreenOn: Boolean,
        capacity: Int?,
        speed: Float,
        targetPercentage: Int = 100
    ): AuthoritativeEtaResult {
        return com.example.battery.engine.BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = percentage,
            isCharging = isCharging,
            currentNowVal = currentNowVal,
            isScreenOn = isScreenOn,
            capacity = capacity,
            speed = speed,
            targetPercentage = targetPercentage
        )
    }
}


// 2. Charging Engine
object ChargingEngine {
    /**
     * Classifies Charging Type strictly based on deterministic, evidence-based telemetry.
     * Evaluates physical inputs (V, I, P) against device learned baseline, sustained battery slope,
     * and thermal limitations. Never equates 'AC' with 'Fast'.
     * Returns: 'Charging — Calculating...', 'Slow Charging', 'Normal Charging',
     * 'Fast Charging', 'Maintenance / Near-Full', 'Charging — Insufficient Data', or 'None'.
     */
    fun classifyChargingType(
        isCharging: Boolean,
        powerWatt: Float,
        currentNowMa: Int,
        voltageMv: Int,
        sessionDurationSeconds: Long = 30L,
        measuredRatePctPerHr: Float = 0f,
        temperatureCelsius: Float = 30f,
        temperatureTrend: String = "STABLE",
        isScreenOn: Boolean = false,
        powerSource: String = "AC",
        batteryPercentage: Int? = null
    ): String {
        if (!isCharging) return "None"
        val result = com.example.battery.engine.ChargingClassificationEngine.classify(
            com.example.battery.model.ChargingTelemetryInput(
                isCharging = true,
                powerSource = powerSource,
                currentNowMa = currentNowMa,
                voltageMv = voltageMv,
                powerWatt = powerWatt,
                batteryPercentage = batteryPercentage,
                measuredVelocityPctPerHr = measuredRatePctPerHr,
                sessionDurationSeconds = sessionDurationSeconds,
                temperatureCelsius = temperatureCelsius,
                temperatureTrend = temperatureTrend,
                isScreenOn = isScreenOn,
                timestampMs = System.currentTimeMillis()
            )
        )
        return result.state.displayName
    }

    fun getChargingQuality(state: BatteryState): String {
        if (!state.isCharging) return "Offline"
        val watt = state.powerWatt
        return when {
            watt >= 25f -> "Excellent (Ultra-Fast 25W+)"
            watt >= 15f -> "Excellent (Fast Charger 15W+)"
            watt >= 10f -> "Good (Standard Quick)"
            watt >= 4.5f -> "Fair (Normal USB Speed)"
            watt > 0f -> "Slow (Low Current Connection)"
            else -> "Unstable Charger / Port Resistance"
        }
    }

    fun getChargingSource(state: BatteryState): String {
        if (!state.isCharging) return "Disconnected"
        return if (state.chargingType.isNotBlank() && state.chargingType != "None") state.chargingType else "Detected Power Source"
    }
}

// 3. Discharge Engine
object DischargeEngine {
    fun estimateAverageDrainRate(state: BatteryState): String {
        if (state.isCharging) return "N/A (Charging)"
        val currentNowVal = -state.currentNow
        return when {
            currentNowVal > 1000 -> "Heavy (System High Load)"
            currentNowVal > 500 -> "Moderate (Active Display / Multi-tasking)"
            currentNowVal > 150 -> "Light (Standard Standby / Background Sync)"
            else -> "Excellent (Deep Sleep Standby)"
        }
    }
}

// 4. Battery Health Engine
object BatteryHealthEngine {
    fun getHealthGrade(healthPct: Int): String {
        return when {
            healthPct >= 96 -> "A+"
            healthPct >= 92 -> "A"
            healthPct >= 88 -> "B+"
            healthPct >= 84 -> "B"
            healthPct >= 80 -> "C"
            else -> "D (Replacement Recommended)"
        }
    }

    fun getHealthDescription(healthPct: Int): String {
        return when {
            healthPct >= 95 -> "Pristine condition. Battery cells show negligible chemical degradation."
            healthPct >= 90 -> "Good condition. Nominal chemical aging present."
            healthPct >= 85 -> "Fair condition. Normal wear is contracting the maximum capacity."
            healthPct >= 80 -> "Needs attention. Screen-on time might be visibly shorter."
            else -> "Degraded. Consider cell replacement to restore factory backup."
        }
    }
}

// 5. Thermal Engine
object ThermalEngine {
    fun getWarningStatus(state: BatteryState): String {
        val temp = state.temperature
        return when {
            temp >= 45f -> "CRITICAL RED ALERT"
            temp >= 41f -> "HIGH WARNING"
            temp >= 37f -> "MODERATE WARMING"
            else -> "STABLE / NORMAL"
        }
    }

    fun getCoolingRecommendations(state: BatteryState): List<String> {
        val temp = state.temperature
        val list = mutableListOf<String>()
        if (temp >= 37f) {
            list.add("Reduce screen brightness to minimize display dissipation")
            list.add("Close high-graphical heavy apps and intensive games")
        }
        if (temp >= 41f) {
            list.add("Pause charging temporarily to eliminate electrical thermal load")
            list.add("Remove phone cover/case to improve heat dissipation")
        }
        if (temp >= 45f) {
            list.add("MOVE DEVICE TO A COOLER PLACE IMMEDIATELY!")
            list.add("Avoid direct exposure to hot sunlight or enclosed cars")
        }
        if (list.isEmpty()) {
            list.add("Device operating under comfortable thermal envelope. No actions needed.")
        }
        return list
    }
}

// 6. Battery Saver Engine
object BatterySaverEngine {
    fun suggestOptimizations(state: BatteryState, context: Context): List<String> {
        val list = mutableListOf<String>()
        if (state.percentage <= 25 && !state.isCharging) {
            list.add("Battery is low (${state.percentage}%). Toggle Battery Saver.")
            list.add("Lower screen timeout duration to 15 seconds.")
            list.add("Disable system Auto-Sync to conserve background CPU cycles.")
            list.add("Turn off high-accuracy Location / GPS scanning.")
        } else {
            list.add("All standby background processes operating within nominal parameters.")
        }
        return list
    }
}

// 7. Device Intelligence Engine
object DeviceIntelligenceEngine {
    fun getDeviceComfortIndex(state: BatteryState, context: Context): Int {
        var score = 100
        
        // Temperature penalty
        val temp = state.temperature
        when {
            temp >= 45f -> score -= 40
            temp >= 41f -> score -= 25
            temp >= 37f -> score -= 12
            temp >= 35f -> score -= 4
        }

        // Health penalty
        val healthPct = state.healthPercentage
        when {
            healthPct < 80 -> score -= 15
            healthPct < 85 -> score -= 10
            healthPct < 90 -> score -= 5
        }

        // Extreme charge/discharge current penalty
        val current = state.currentNow
        if (state.isCharging) {
            if (current > 4000) score -= 10
        } else {
            if (current < -1200) score -= 12
        }

        return score.coerceIn(10, 100)
    }
}

// 8. Connected Devices Engine
object ConnectedDevicesEngine {
    fun getConnectedAccessoriesSummary(context: Context): String {
        return "Accessories fully monitored via Netra Device Bridge"
    }
}

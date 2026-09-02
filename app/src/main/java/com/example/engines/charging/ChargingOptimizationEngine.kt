package com.example.engines.charging

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.ChargingSession
import com.example.engines.thermal.ThermalProtectionEngine
import com.example.engines.thermal.ThermalSessionState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

enum class ChargingOptimizationState {
    NOT_CHARGING,
    CHARGING,
    CHARGING_OPTIMIZED,
    THERMAL_LIMITED_CHARGING,
    FULL_CHARGE,
    RESTORING_CHARGING_STATE
}

enum class ChargingTempClass {
    NORMAL,
    WARM,
    THERMALLY_LIMITED,
    OVERHEATING
}

enum class ChargingSpeedClassification {
    FAST,
    NORMAL,
    SLOW,
    UNKNOWN
}

data class ChargingOptimizationSnapshot(
    val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val startBatteryLevel: Int = 0,
    val chargingType: String = "UNKNOWN",
    val backgroundWorkloadOptimized: Boolean = false
)

object ChargingOptimizationEngine {
    private const val TAG = "ChargingOptimizationEngine"

    @Volatile
    private var currentState: ChargingOptimizationState = ChargingOptimizationState.NOT_CHARGING

    @Volatile
    private var activeSnapshot: ChargingOptimizationSnapshot? = null

    @Volatile
    private var activeDbSessionId: Long? = null

    private val tempTelemetryHistory = mutableListOf<Pair<Long, Float>>()
    private val batteryTelemetryHistory = mutableListOf<Pair<Long, Int>>()

    var onChargingEventCallback: ((eventType: String, title: String, details: String) -> Unit)? = null

    @Synchronized
    fun resetForTesting(context: Context) {
        currentState = ChargingOptimizationState.NOT_CHARGING
        activeSnapshot = null
        activeDbSessionId = null
        tempTelemetryHistory.clear()
        batteryTelemetryHistory.clear()
        AutomaticChargingProtectionEngine.resetForTesting(context)
        ThermalProtectionEngine.resetForTesting(context)
    }

    @Synchronized
    fun getState(): ChargingOptimizationState = currentState

    @Synchronized
    fun getActiveSnapshot(): ChargingOptimizationSnapshot? = activeSnapshot

    @Synchronized
    fun processUpdate(
        context: Context,
        isCharging: Boolean,
        batteryLevel: Int,
        temperature: Float,
        chargingType: String = "AC"
    ): ChargingOptimizationState {
        val now = System.currentTimeMillis()

        if (temperature != -999f) {
            tempTelemetryHistory.add(now to temperature)
            if (tempTelemetryHistory.size > 50) tempTelemetryHistory.removeAt(0)
        }
        batteryTelemetryHistory.add(now to batteryLevel)
        if (batteryTelemetryHistory.size > 50) batteryTelemetryHistory.removeAt(0)

        // Process Automatic Charging Protection Mode
        AutomaticChargingProtectionEngine.processTelemetry(context, isCharging, batteryLevel, temperature, chargingType)

        val thermalState = ThermalProtectionEngine.processTemperature(temperature, context)
        val isThermalProtected = ThermalProtectionEngine.isProtectionActive() || thermalState == ThermalSessionState.THERMAL_PROTECTION || thermalState == ThermalSessionState.THERMAL_ESCALATED

        if (!isCharging) {
            if (currentState != ChargingOptimizationState.NOT_CHARGING) {
                Log.i(TAG, "Charger disconnected. Ending charging optimization. Thermal protection active status: $isThermalProtected")
                endChargingSession(context, batteryLevel, temperature, false)
                currentState = ChargingOptimizationState.NOT_CHARGING
                activeSnapshot = null
                onChargingEventCallback?.invoke(
                    "CHARGING_DISCONNECTED",
                    "Charging Disconnected",
                    "Battery: $batteryLevel%, Temp: $temperature°C"
                )
            }
            return currentState
        }

        if (currentState == ChargingOptimizationState.NOT_CHARGING || currentState == ChargingOptimizationState.RESTORING_CHARGING_STATE) {
            currentState = ChargingOptimizationState.CHARGING
            val snapshot = ChargingOptimizationSnapshot(
                sessionId = UUID.randomUUID().toString(),
                startTime = now,
                startBatteryLevel = batteryLevel,
                chargingType = chargingType,
                backgroundWorkloadOptimized = true
            )
            activeSnapshot = snapshot

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val dao = BatteryDatabase.getDatabase(context).batteryDao()
                    val session = ChargingSession(
                        startTime = now,
                        startPercentage = batteryLevel,
                        chargingType = chargingType,
                        maxTemperature = temperature,
                        avgPower = 0f
                    )
                    activeDbSessionId = dao.insertSession(session)
                    Log.i(TAG, "New charging session recorded in DB with ID: $activeDbSessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist charging session start: ${e.message}")
                }
            }
            Log.i(TAG, "Charging connected at $batteryLevel%, Temp: $temperature°C. Optimization started.")
            onChargingEventCallback?.invoke(
                "CHARGING_CONNECTED",
                "Charging Connected",
                "Battery: $batteryLevel%, Type: $chargingType, Temp: $temperature°C"
            )
        }

        // Priority: THERMAL PROTECTION > CHARGING OPTIMIZATION
        if (isThermalProtected || temperature >= 43.0f) {
            currentState = ChargingOptimizationState.THERMAL_LIMITED_CHARGING
            Log.w(TAG, "Thermal protection active ($temperature°C >= 43°C). Thermal Limited Charging active. Overrides general charging optimization.")
            onChargingEventCallback?.invoke(
                "CHARGING_OPTIMIZATION_LIMITED",
                "Thermal Limited Charging",
                "Temperature $temperature°C >= 43°C. Charging throttled for safety."
            )
        } else if (batteryLevel >= 100) {
            currentState = ChargingOptimizationState.FULL_CHARGE
            Log.i(TAG, "Battery full (100%). Full charge tracking recorded.")
            onChargingEventCallback?.invoke(
                "FULL_CHARGE_REACHED",
                "Full Charge Reached",
                "Battery reached 100% at temperature $temperature°C."
            )
        } else {
            currentState = ChargingOptimizationState.CHARGING_OPTIMIZED
        }

        return currentState
    }

    private fun endChargingSession(context: Context, finalBattery: Int, finalTemp: Float, fullCharge: Boolean) {
        val dbId = activeDbSessionId
        val now = System.currentTimeMillis()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (dbId != null && dbId > 0) {
                    val dao = BatteryDatabase.getDatabase(context).batteryDao()
                    val session = dao.getChargingSession(dbId)
                    if (session != null) {
                        val updated = session.copy(
                            endTime = now,
                            endPercentage = finalBattery,
                            maxTemperature = if (finalTemp > session.maxTemperature) finalTemp else session.maxTemperature
                        )
                        dao.updateSession(updated)
                        Log.i(TAG, "Charging session $dbId finalized in DB.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to finalize charging session in DB: ${e.message}")
            }
        }
        activeDbSessionId = null
    }

    fun classifyTemperature(temp: Float): ChargingTempClass {
        return when {
            temp >= 45.0f -> ChargingTempClass.OVERHEATING
            temp >= 43.0f -> ChargingTempClass.THERMALLY_LIMITED
            temp >= 38.0f -> ChargingTempClass.WARM
            else -> ChargingTempClass.NORMAL
        }
    }

    fun classifyChargingSpeed(deltaPerMin: Float): ChargingSpeedClassification {
        return when {
            deltaPerMin >= 1.5f -> ChargingSpeedClassification.FAST
            deltaPerMin >= 0.5f -> ChargingSpeedClassification.NORMAL
            deltaPerMin > 0f -> ChargingSpeedClassification.SLOW
            else -> ChargingSpeedClassification.UNKNOWN
        }
    }
}

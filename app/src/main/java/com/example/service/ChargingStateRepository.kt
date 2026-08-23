package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChargingState {
    CHARGING,
    FULL_WHILE_POWER_CONNECTED,
    NOT_CHARGING,
    DISCHARGING,
    UNKNOWN
}

enum class ChargingClassification {
    FAST,
    NORMAL,
    SLOW,
    UNKNOWN
}

data class ChargingSessionRecord(
    val sessionId: String,
    val startTimestamp: Long,
    val startBattery: Int,
    val startVoltage: Int,
    val startCurrent: Int,
    val startPowerWatts: Float,
    val startTemperature: Float,
    var minBattery: Int = startBattery,
    var maxBattery: Int = startBattery,
    var minVoltage: Int = startVoltage,
    var maxVoltage: Int = startVoltage,
    var minCurrent: Int = startCurrent,
    var maxCurrent: Int = startCurrent,
    var minPowerWatts: Float = startPowerWatts,
    var maxPowerWatts: Float = startPowerWatts,
    var minTemperature: Float = startTemperature,
    var maxTemperature: Float = startTemperature,
    var isEnded: Boolean = false,
    var endTimestamp: Long = 0L
)

data class VerifiedChargingTelemetry(
    val chargingState: ChargingState,
    val classification: ChargingClassification,
    val batteryPct: Int,
    val voltageMv: Int,
    val currentMa: Int,
    val powerWatts: Float,
    val temperatureC: Float,
    val health: String,
    val plugType: String,
    val isSessionActive: Boolean,
    val sessionId: String?
)

class ChargingStateRepository(private val context: Context, private val eventLogger: Any?) {
    private val TAG = "ChargingStateRepo"

    private val _telemetryState = MutableStateFlow<VerifiedChargingTelemetry>(
        VerifiedChargingTelemetry(
            chargingState = ChargingState.UNKNOWN,
            classification = ChargingClassification.UNKNOWN,
            batteryPct = -1,
            voltageMv = -1,
            currentMa = -9999,
            powerWatts = -999f,
            temperatureC = -999f,
            health = "UNAVAILABLE",
            plugType = "UNKNOWN",
            isSessionActive = false,
            sessionId = null
        )
    )
    val telemetryState: StateFlow<VerifiedChargingTelemetry> = _telemetryState.asStateFlow()

    private var activeSession: ChargingSessionRecord? = null

    init {
        verifyCurrentBatteryState()
    }

    fun verifyCurrentBatteryState() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            if (intent == null) {
                Log.w(TAG, "Battery intent null during verifyCurrentBatteryState")
                return
            }

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val temperatureC = if (tempRaw > 0) tempRaw / 10.0f else -999f
            val healthRaw = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            val health = when (healthRaw) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                else -> "UNAVAILABLE"
            }

            val plugType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "UNPLUGGED"
            }

            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val currentUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
            val currentMa = if (currentUa != Int.MIN_VALUE) currentUa / 1000 else -9999

            val powerWatts = if (voltageMv > 0 && currentMa != -9999) {
                (voltageMv / 1000.0f) * (currentMa / 1000.0f)
            } else {
                -999f
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || (plugged > 0 && status == BatteryManager.BATTERY_STATUS_FULL)
            val isFull = status == BatteryManager.BATTERY_STATUS_FULL

            val chargingState = when {
                isFull && plugged > 0 -> ChargingState.FULL_WHILE_POWER_CONNECTED
                isCharging -> ChargingState.CHARGING
                plugged == 0 -> ChargingState.DISCHARGING
                else -> ChargingState.NOT_CHARGING
            }

            val classification = when {
                chargingState == ChargingState.CHARGING || chargingState == ChargingState.FULL_WHILE_POWER_CONNECTED -> {
                    when {
                        powerWatts > 15.0f || (currentMa > 3000) -> ChargingClassification.FAST
                        powerWatts > 5.0f || (currentMa > 1000) -> ChargingClassification.NORMAL
                        powerWatts > 0f || currentMa > 0 -> ChargingClassification.SLOW
                        else -> ChargingClassification.UNKNOWN
                    }
                }
                else -> ChargingClassification.UNKNOWN
            }

            val now = System.currentTimeMillis()
            if ((chargingState == ChargingState.CHARGING || chargingState == ChargingState.FULL_WHILE_POWER_CONNECTED)) {
                if (activeSession == null) {
                    val sessionId = "CHG_${now}"
                    activeSession = ChargingSessionRecord(
                        sessionId = sessionId,
                        startTimestamp = now,
                        startBattery = batteryPct,
                        startVoltage = voltageMv,
                        startCurrent = currentMa,
                        startPowerWatts = powerWatts,
                        startTemperature = temperatureC
                    )
                    logEvent("CHARGING_STARTED", "Charging Started", "Session $sessionId started with plug $plugType")
                } else {
                    activeSession?.let { s ->
                        s.minBattery = minOf(s.minBattery, batteryPct)
                        s.maxBattery = maxOf(s.maxBattery, batteryPct)
                        if (voltageMv > 0) {
                            s.minVoltage = if (s.minVoltage > 0) minOf(s.minVoltage, voltageMv) else voltageMv
                            s.maxVoltage = maxOf(s.maxVoltage, voltageMv)
                        }
                        if (currentMa != -9999) {
                            s.minCurrent = if (s.minCurrent != -9999) minOf(s.minCurrent, currentMa) else currentMa
                            s.maxCurrent = maxOf(s.maxCurrent, currentMa)
                        }
                        if (powerWatts != -999f) {
                            s.minPowerWatts = if (s.minPowerWatts != -999f) minOf(s.minPowerWatts, powerWatts) else powerWatts
                            s.maxPowerWatts = maxOf(s.maxPowerWatts, powerWatts)
                        }
                        if (temperatureC != -999f) {
                            s.minTemperature = if (s.minTemperature != -999f) minOf(s.minTemperature, temperatureC) else temperatureC
                            s.maxTemperature = maxOf(s.maxTemperature, temperatureC)
                        }
                    }
                }
            } else {
                activeSession?.let { s ->
                    if (!s.isEnded) {
                        s.isEnded = true
                        s.endTimestamp = now
                        logEvent("CHARGING_STOPPED", "Charging Stopped", "Session ${s.sessionId} finalized.")
                        activeSession = null
                    }
                }
            }

            _telemetryState.value = VerifiedChargingTelemetry(
                chargingState = chargingState,
                classification = classification,
                batteryPct = batteryPct,
                voltageMv = voltageMv,
                currentMa = currentMa,
                powerWatts = powerWatts,
                temperatureC = temperatureC,
                health = health,
                plugType = plugType,
                isSessionActive = activeSession != null,
                sessionId = activeSession?.sessionId
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error verifying current battery state", e)
            logEvent("TELEMETRY_ERROR", "Charging Telemetry Error", e.message ?: "Unknown error")
        }
    }

    fun onPowerConnected() {
        logEvent("CHARGING_STATE_DETECTED", "Power Connected Broadcast", "Verifying authoritative state...")
        verifyCurrentBatteryState()
    }

    fun onPowerDisconnected() {
        logEvent("CHARGING_STATE_DETECTED", "Power Disconnected Broadcast", "Finalizing charging session...")
        verifyCurrentBatteryState()
    }

    private fun logEvent(eventType: String, title: String, details: String) {
        try {
            if (eventLogger != null) {
                val method = eventLogger.javaClass.getMethod("logBatteryEventSync", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)
                method.invoke(eventLogger, eventType, title, details, "POWER", "ChargingStateRepository")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event via reflection", e)
        }
    }
}

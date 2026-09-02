package com.example.service

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.ChargingSession
import com.example.data.BatteryEvent
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object NetraNativeAutomationService {
    private const val TAG = "NetraNativeAutomation"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Threshold sequences as mandated
    private val BATTERY_5_THRESHOLDS = listOf(5, 15, 25, 35, 45, 55, 65, 75, 85, 95)
    private val BATTERY_10_THRESHOLDS = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

    // Duplicate event tracking sets
    private val triggeredBattery5Ascent = ConcurrentHashMap.newKeySet<Int>()
    private val triggeredBattery10Ascent = ConcurrentHashMap.newKeySet<Int>()
    private val triggeredDischarge5Descent = ConcurrentHashMap.newKeySet<Int>()
    private val triggeredDischarge10Descent = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var lastPowerConnected: Boolean? = null
    @Volatile
    private var isChargingActive = false

    // State variables
    @Volatile private var batteryStart: Int = -1
    @Volatile private var hoursStart: Long = 0L
    @Volatile private var maxTempCharge: Float = -999f
    @Volatile private var fullChargeTimestamp: Long = 0L

    @Volatile
    private var initJob: Job? = null

    fun initialize(context: Context) {
        Log.i(TAG, "Initializing NetraNativeAutomationService...")
        initJob = scope.launch {
            try {
                val db = BatteryDatabase.getDatabase(context)
                val activeSession = db.batteryDao().getActiveSession()
                if (activeSession != null) {
                    isChargingActive = true
                    batteryStart = activeSession.startPercentage
                    hoursStart = activeSession.startTime
                    lastPowerConnected = true
                    Log.i(TAG, "Restored active charging session from DB: startPercentage=$batteryStart, startTime=$hoursStart")
                } else {
                    lastPowerConnected = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring charging session state", e)
                lastPowerConnected = false
            }
        }
    }

    fun onBatteryUpdate(context: Context, intent: Intent, forcedPluggedState: Boolean? = null) {
        scope.launch {
            try {
                initJob?.join() // Wait for async initialization to restore previous state cleanly

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                if (percentage < 0) return@launch

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val isFull = status == BatteryManager.BATTERY_STATUS_FULL || percentage == 100
                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val currentTemp = if (rawTemp > 0) rawTemp / 10f else 25f

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isScreenOn = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                        powerManager?.isInteractive == true
                    } else {
                        @Suppress("DEPRECATION")
                        powerManager?.isScreenOn == true
                    }
                } catch (e: Exception) {
                    true
                }

                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val isPlugged = forcedPluggedState ?: (plugged != 0)

                // Notify NetraMultiMechanismCoordinator (Capability A, C, D)
                com.example.engines.coordinator.NetraMultiMechanismCoordinator.onBatteryTelemetryUpdate(context, percentage, isCharging, currentTemp)

                // 1. Power Connected / Disconnected detection (Clean downstream tracking, zero side-channel DB writes)
                if (lastPowerConnected != isPlugged) {
                    lastPowerConnected = isPlugged
                    if (isPlugged) {
                        Log.i(TAG, "Power connection observed downstream in NetraNativeAutomationService. Clearing ascent lists.")
                        isChargingActive = true
                        batteryStart = percentage
                        hoursStart = System.currentTimeMillis()
                        maxTempCharge = currentTemp
                        fullChargeTimestamp = 0L
                        triggeredBattery5Ascent.clear()
                        triggeredBattery10Ascent.clear()
                    } else {
                        Log.i(TAG, "Power disconnection observed downstream in NetraNativeAutomationService. Clearing descent lists.")
                        isChargingActive = false
                        triggeredDischarge5Descent.clear()
                        triggeredDischarge10Descent.clear()
                    }
                }

                // 2. Threshold evaluation (Battery 5 and Battery 10 with decreasesTo = false)
                if (isCharging) {
                    evaluateAscentThresholds(context, percentage)
                } else {
                    // 3. Discharge threshold evaluation (Discharge Battery 5 & 10 with decreasesTo = true, power=false, screen=false)
                    if (!isPlugged && !isScreenOn) {
                        evaluateDescentThresholds(context, percentage)
                    }
                }

                // 4. Charge Tracking State Machine Updates
                if (isCharging) {
                    updateChargingState(context, percentage, currentTemp, isFull)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onBatteryUpdate", e)
            }
        }
    }

    fun onDeviceUnlocked(context: Context) {
        scope.launch {
            Log.d(TAG, "Device unlocked event received in automation service. Evaluating tracking state...")
            com.example.engines.coordinator.NetraMultiMechanismCoordinator.onScreenOnOrUnlocked(context)
            if (isChargingActive) {
                val db = BatteryDatabase.getDatabase(context)
                val active = db.batteryDao().getActiveSession()
                if (active != null) {
                    Log.d(TAG, "Active charging session confirmed on unlock. Session start time: ${active.startTime}")
                }
            }
        }
    }

    private fun evaluateAscentThresholds(context: Context, percentage: Int) {
        for (th in BATTERY_5_THRESHOLDS) {
            if (percentage >= th && !triggeredBattery5Ascent.contains(th)) {
                triggeredBattery5Ascent.add(th)
                triggerAutomationAction(context, "BATTERY_5_ASCENT", th, true)
            }
        }
        for (th in BATTERY_10_THRESHOLDS) {
            if (percentage >= th && !triggeredBattery10Ascent.contains(th)) {
                triggeredBattery10Ascent.add(th)
                triggerAutomationAction(context, "BATTERY_10_ASCENT", th, true)
            }
        }
    }

    private fun evaluateDescentThresholds(context: Context, percentage: Int) {
        for (th in BATTERY_5_THRESHOLDS) {
            if (percentage <= th && !triggeredDischarge5Descent.contains(th)) {
                triggeredDischarge5Descent.add(th)
                triggerAutomationAction(context, "DISCHARGE_BATTERY_5", th, false)
            }
        }
        for (th in BATTERY_10_THRESHOLDS) {
            if (percentage <= th && !triggeredDischarge10Descent.contains(th)) {
                triggeredDischarge10Descent.add(th)
                triggerAutomationAction(context, "DISCHARGE_BATTERY_10", th, false)
            }
        }
    }

    private fun triggerAutomationAction(context: Context, type: String, threshold: Int, isAscent: Boolean) {
        Log.i(TAG, "AUTOMATION ACTION TRIGGERED: type=$type, threshold=$threshold, isAscent=$isAscent (Zero voice announcements enforced)")
        scope.launch {
            try {
                val db = BatteryDatabase.getDatabase(context)
                db.batteryDao().insertBatteryEvent(BatteryEvent(
                    timestamp = System.currentTimeMillis(),
                    eventType = type,
                    title = "Automation Trigger: $threshold%",
                    details = "Executed natively for $type at threshold $threshold. Zero voice announcement.",
                    category = "AUTOMATION",
                    source = "NetraNativeAutomationService"
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Error logging automation event", e)
            }
        }
    }

    private suspend fun updateChargingState(context: Context, percentage: Int, temp: Float, isFull: Boolean) {
        if (temp > maxTempCharge) maxTempCharge = temp

        if (isFull && fullChargeTimestamp == 0L) {
            fullChargeTimestamp = System.currentTimeMillis()
            Log.i(TAG, "FULL CHARGE REACHED at 100%. Recording timestamp and finalizing statistics.")
            try {
                val db = BatteryDatabase.getDatabase(context)
                db.batteryDao().insertBatteryEvent(BatteryEvent(
                    timestamp = System.currentTimeMillis(),
                    eventType = "BATTERY_FULL",
                    title = "Battery Full (100%)",
                    details = "Max charging reached. Duration: ${(fullChargeTimestamp - hoursStart)/1000}s. Plugged duration timer active.",
                    category = "AUTOMATION",
                    source = "NetraNativeAutomationService"
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Error recording battery full event", e)
            }
        }
    }
}

package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import android.util.Log
import com.example.data.BatteryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BatteryManager
 *
 * Core battery state monitor and recorder that captures authoritative battery telemetry
 * (plugged status, temperature, health, voltage, current, level, and charging state)
 * and reliably persists it to the local Room database (BatteryHistoryEntity) with
 * adaptive deduplication for ultra-low power consumption and 24/7 continuous operation.
 */
class BatteryManager(
    private val context: Context,
    private val repository: BatteryRepository
) {
    companion object {
        private const val TAG = "NetraBatteryManager"
        private const val MIN_RECORDING_INTERVAL_MS = 30_000L // 30 seconds minimum between identical samples
        private const val HEARTBEAT_RECORDING_INTERVAL_MS = 5 * 60_000L // 5 minutes periodic heartbeat
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory telemetry cache for deduplication
    private var lastRecordedTime = 0L
    private var lastRecordedLevel = -1
    private var lastRecordedCharging = false
    private var lastRecordedChargingType = "NONE"
    private var lastRecordedStatus = "UNKNOWN"
    private var lastRecordedHealth = "UNKNOWN"
    private var lastRecordedTemp = -999.0f

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            when (action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    processBatteryIntent(intent, isPeriodic = false)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.d(TAG, "Power connected broadcast received")
                    captureAndSaveSnapshotNow()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.d(TAG, "Power disconnected broadcast received")
                    captureAndSaveSnapshotNow()
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            context.registerReceiver(batteryReceiver, filter)
            // Immediately capture initial state
            captureAndSaveSnapshotNow()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BatteryManager receiver", e)
        }
    }

    /**
     * Extracts full battery telemetry from sticky or broadcast intent and persists snapshot.
     */
    fun processBatteryIntent(intent: Intent, isPeriodic: Boolean = false) {
        val level = intent.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return

        val batteryPct = ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
        val statusInt = intent.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1)
        val pluggedInt = intent.getIntExtra(AndroidBatteryManager.EXTRA_PLUGGED, -1)
        val healthInt = intent.getIntExtra(AndroidBatteryManager.EXTRA_HEALTH, -1)
        val rawTemp = intent.getIntExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, 0)
        val voltageMv = intent.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, 0)

        val tempCelsius = if (rawTemp > 0) rawTemp / 10.0f else 0.0f

        val chargingType = when (pluggedInt) {
            AndroidBatteryManager.BATTERY_PLUGGED_AC -> "AC"
            AndroidBatteryManager.BATTERY_PLUGGED_USB -> "USB"
            AndroidBatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            4 -> "DOCK" // BATTERY_PLUGGED_DOCK
            0 -> "NONE"
            else -> "UNKNOWN"
        }

        val statusStr = when (statusInt) {
            AndroidBatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            AndroidBatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            AndroidBatteryManager.BATTERY_STATUS_FULL -> "FULL"
            AndroidBatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }

        val healthStr = when (healthInt) {
            AndroidBatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            AndroidBatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            AndroidBatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            AndroidBatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
            AndroidBatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            AndroidBatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            else -> "UNKNOWN"
        }

        val isCharging = statusInt == AndroidBatteryManager.BATTERY_STATUS_CHARGING ||
                (pluggedInt > 0 && statusInt == AndroidBatteryManager.BATTERY_STATUS_FULL)

        // Read hardware current if available
        var currentNowMa = 0
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? AndroidBatteryManager
            val rawCurrent = bm?.getIntProperty(AndroidBatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            currentNowMa = rawCurrent / 1000
            if (Math.abs(currentNowMa) > 15000) {
                currentNowMa /= 1000
            }
            if (isCharging && currentNowMa < 0) currentNowMa = -currentNowMa
            if (!isCharging && currentNowMa > 0) currentNowMa = -currentNowMa
        } catch (e: Exception) {
            Log.w(TAG, "Hardware current property unavailable: ${e.message}")
        }

        // Update repository live battery level state
        repository.updateBatteryLevel(batteryPct)

        // Determine if this snapshot should be written to Room database
        val now = System.currentTimeMillis()
        val timeSinceLastRecord = now - lastRecordedTime
        val stateChanged = isCharging != lastRecordedCharging ||
                chargingType != lastRecordedChargingType ||
                batteryPct != lastRecordedLevel ||
                statusStr != lastRecordedStatus ||
                healthStr != lastRecordedHealth ||
                Math.abs(tempCelsius - lastRecordedTemp) >= 1.0f

        val shouldPersist = (stateChanged && timeSinceLastRecord >= MIN_RECORDING_INTERVAL_MS) ||
                (timeSinceLastRecord >= HEARTBEAT_RECORDING_INTERVAL_MS) ||
                (lastRecordedTime == 0L) ||
                isPeriodic

        if (shouldPersist) {
            lastRecordedTime = now
            lastRecordedLevel = batteryPct
            lastRecordedCharging = isCharging
            lastRecordedChargingType = chargingType
            lastRecordedStatus = statusStr
            lastRecordedHealth = healthStr
            lastRecordedTemp = tempCelsius

            scope.launch {
                try {
                    repository.recordBatterySnapshot(
                        level = batteryPct,
                        isCharging = isCharging,
                        chargingType = chargingType,
                        temperature = tempCelsius,
                        voltageMv = voltageMv,
                        currentNowMa = currentNowMa,
                        health = healthStr,
                        status = statusStr
                    )
                    Log.d(
                        TAG,
                        "Saved battery state snapshot: $batteryPct% | $statusStr | $chargingType | ${tempCelsius}°C | Health: $healthStr"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving battery snapshot to database", e)
                }
            }
        }
    }

    /**
     * Manually triggers immediate state capture and persistence from sticky intent.
     */
    fun captureAndSaveSnapshotNow() {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(null, filter)
            if (stickyIntent != null) {
                processBatteryIntent(stickyIntent, isPeriodic = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing battery snapshot now", e)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(batteryReceiver)
            scope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering batteryReceiver", e)
        }
    }
}


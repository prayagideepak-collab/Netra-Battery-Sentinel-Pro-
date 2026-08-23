package com.example.engines.ibrsle

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log

object IbrsleSleepManager {
    private const val TAG = "IBRSLE_SleepManager"

    fun evaluateDeviceState(context: Context): Triple<Boolean, Boolean, Boolean> {
        var isScreenOn = true
        var isPowerSaver = false
        var isChargerConnected = false

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                isScreenOn = powerManager.isInteractive
                isPowerSaver = powerManager.isPowerSaveMode
            }

            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isChargerConnected = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating device state", e)
        }

        return Triple(isScreenOn, isPowerSaver, isChargerConnected)
    }

    fun shouldSleep(spec: RegisteredServiceSpec, isScreenOn: Boolean, isPowerSaver: Boolean, isChargerConnected: Boolean): Boolean {
        // Core or Critical safety services NEVER sleep
        if (spec.isCore || spec.priority == ServicePriority.CRITICAL) {
            return false
        }

        // If charger connected, wake up all enabled services
        if (isChargerConnected) {
            return false
        }

        // If screen is OFF or Power Saver is ACTIVE, non-critical services should sleep to conserve battery
        if (!isScreenOn || isPowerSaver) {
            if (spec.priority.level <= ServicePriority.MEDIUM.level) {
                return true
            }
        }

        return false
    }
}

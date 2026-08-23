package com.example.engines.ipropme

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log

object IpropmeResourceOptimizer {
    private const val TAG = "IPROPME_Optimizer"

    data class DeviceStateInfo(
        val isScreenOn: Boolean,
        val isPowerSaver: Boolean,
        val isCharging: Boolean,
        val batteryLevel: Int,
        val temperatureCelsius: Float
    )

    fun readDeviceState(context: Context): DeviceStateInfo {
        var isScreenOn = true
        var isPowerSaver = false
        var isCharging = false
        var batteryLevel = 100
        var temperatureCelsius = 25f

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                isScreenOn = pm.isInteractive
                isPowerSaver = pm.isPowerSaveMode
            }

            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryLevel = ((level.toFloat() / scale.toFloat()) * 100).toInt()
                }

                val tempRaw = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (tempRaw > 0) {
                    temperatureCelsius = tempRaw / 10f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading device state", e)
        }

        return DeviceStateInfo(
            isScreenOn = isScreenOn,
            isPowerSaver = isPowerSaver,
            isCharging = isCharging,
            batteryLevel = batteryLevel,
            temperatureCelsius = temperatureCelsius
        )
    }

    fun determinePerformanceMode(state: DeviceStateInfo): PerformanceMode {
        return when {
            state.temperatureCelsius >= 45.0f -> PerformanceMode.CRITICAL_TEMPERATURE
            state.batteryLevel <= 15 -> PerformanceMode.CRITICAL_BATTERY
            state.isCharging -> PerformanceMode.CHARGING
            state.isPowerSaver -> PerformanceMode.BATTERY_SAVER
            !state.isScreenOn -> PerformanceMode.IDLE
            else -> PerformanceMode.BALANCED
        }
    }

    fun determineSensorSamplingRate(mode: PerformanceMode): SensorSamplingRate {
        return when (mode) {
            PerformanceMode.PERFORMANCE -> SensorSamplingRate.REAL_TIME
            PerformanceMode.BALANCED -> SensorSamplingRate.MEDIUM
            PerformanceMode.CHARGING -> SensorSamplingRate.REAL_TIME
            PerformanceMode.IDLE -> SensorSamplingRate.SLOW
            PerformanceMode.BATTERY_SAVER -> SensorSamplingRate.SLOW
            PerformanceMode.CRITICAL_BATTERY -> SensorSamplingRate.MINIMAL
            PerformanceMode.CRITICAL_TEMPERATURE -> SensorSamplingRate.MINIMAL
        }
    }

    fun getMemoryAndThreadStats(): Pair<Float, Float> {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val maxMb = runtime.maxMemory() / (1024f * 1024f)
        return Pair(usedMb, maxMb)
    }

    fun calculateHealthScore(usedHeapMb: Float, maxHeapMb: Float, mode: PerformanceMode): Int {
        var score = 100

        if (maxHeapMb > 0) {
            val ratio = usedHeapMb / maxHeapMb
            if (ratio > 0.85f) {
                score -= 30
            } else if (ratio > 0.70f) {
                score -= 15
            }
        }

        when (mode) {
            PerformanceMode.CRITICAL_TEMPERATURE -> score -= 25
            PerformanceMode.CRITICAL_BATTERY -> score -= 20
            PerformanceMode.BATTERY_SAVER -> score -= 10
            else -> {}
        }

        return score.coerceIn(0, 100)
    }

    fun performSystemGarbageCollection() {
        try {
            System.gc()
            Log.d(TAG, "Garbage collection suggested to system runtime")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing System.gc()", e)
        }
    }
}

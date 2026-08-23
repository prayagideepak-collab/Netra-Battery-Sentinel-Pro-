package com.example.engines

import android.content.Context
import android.hardware.Sensor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Netra Adaptive Power Intelligence Engine (NAPIE)
 * Dynamically adjusts sensor polling and background activity to optimize battery.
 */
object NAPIEEngine {
    private const val TAG = "NAPIEEngine"

    enum class OperationMode {
        ACTIVE, PASSIVE, IDLE, ADAPTIVE_QUIET_MODE
    }

    private val _currentMode = MutableStateFlow(OperationMode.ACTIVE)
    val currentMode: StateFlow<OperationMode> = _currentMode

    private var lastTransitionTime = 0L
    private const val DWELL_TIME_MS = 60000L // 1 minute dwell

    fun updateMode(context: Context, isScreenOn: Boolean, isCharging: Boolean, isMoving: Boolean) {
        val now = System.currentTimeMillis()
        val newMode = when {
            isCharging && !isScreenOn && !isMoving -> OperationMode.ADAPTIVE_QUIET_MODE
            isScreenOn -> OperationMode.ACTIVE
            isMoving -> OperationMode.PASSIVE
            else -> OperationMode.IDLE
        }
        
        if (_currentMode.value != newMode) {
            // Hysteresis/Dwell: Only allow transition if we've been in the current state long enough
            if (now - lastTransitionTime > DWELL_TIME_MS) {
                Log.i(TAG, "NAPIE Mode transition: ${_currentMode.value} -> $newMode")
                _currentMode.value = newMode
                lastTransitionTime = now
            }
        } else {
            lastTransitionTime = now
        }
    }

    fun getSensorDelay(sensorType: Int): Int {
        // Critical safety sensors should never be delayed
        if (sensorType == Sensor.TYPE_ACCELEROMETER || sensorType == Sensor.TYPE_LIGHT) {
             return android.hardware.SensorManager.SENSOR_DELAY_NORMAL
        }
        
        return when (_currentMode.value) {
            OperationMode.ACTIVE -> android.hardware.SensorManager.SENSOR_DELAY_NORMAL
            OperationMode.PASSIVE -> android.hardware.SensorManager.SENSOR_DELAY_UI
            OperationMode.IDLE -> android.hardware.SensorManager.SENSOR_DELAY_GAME
            OperationMode.ADAPTIVE_QUIET_MODE -> android.hardware.SensorManager.SENSOR_DELAY_NORMAL // Corrected: Slower
        }
    }
}

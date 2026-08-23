package com.example.service

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class TemperatureReading(val timestamp: Long, val temperature: Float)

class ColdDetectionEngine {
    private val history = mutableListOf<TemperatureReading>()
    private val maxHistorySize = 10 // Store last 10 readings
    private var isRecoveryMode = false
    private val _coldAlerts = MutableSharedFlow<String>()
    val coldAlerts: SharedFlow<String> = _coldAlerts.asSharedFlow()

    suspend fun processReading(temperature: Float, isHighTempRecovery: Boolean) {
        if (temperature <= -999f) return
        val now = System.currentTimeMillis()
        
        // 1. Recovery Lock
        if (isHighTempRecovery) {
            isRecoveryMode = true
            history.clear()
            return
        }
        
        // If temperature has stabilized after high temp, recovery mode ends
        if (isRecoveryMode && temperature < 40f) { // Arbitrary stable threshold
            isRecoveryMode = false
        }
        
        if (isRecoveryMode) return

        // 2. Add reading
        history.add(TemperatureReading(now, temperature))
        if (history.size > maxHistorySize) history.removeAt(0)
        
        if (history.size < 3) return // Need minimum data points

        // 3. Analyze Trend
        val first = history.first()
        val last = history.last()
        val durationMinutes = (last.timestamp - first.timestamp) / 60000f
        if (durationMinutes == 0f) return
        
        val coolingRate = (first.temperature - last.temperature) / durationMinutes // °C/min

        // 4. Detection Logic
        if (coolingRate > 1.0f) { // > 1°C per minute
            if (temperature < 15f) {
                _coldAlerts.emit("❄️ Rapid temperature drop detected. Check device environment.")
            }
            if (temperature < 5f) {
                _coldAlerts.emit("⚠️ Critical cooling detected. Battery integrity at risk.")
            }
        }
    }
}

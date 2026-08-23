package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.BatteryRepository
import com.example.analytics.CPUAnalytics
import java.util.LinkedList
import java.util.Locale

class NetraExternalHeatInferenceEngine(
    private val context: Context,
    private val repository: BatteryRepository?
) {
    companion object {
        private const val TAG = "NetraExternalHeat"
        private const val WINDOW_DURATION_MS = 3 * 60 * 1000 // 3 minutes window for rate calculation
    }

    private val temperatureHistory = LinkedList<Pair<Long, Float>>()
    
    // Warning state tracking
    private var isWarningActive = false
    private var startTime: Long = 0L
    private var peakTemp: Float = 0f
    private var startTemp: Float = 0f
    private var lastLogTime: Long = 0L

    @Synchronized
    fun updateAndInfer(
        currentTemp: Float,
        isCharging: Boolean,
        isScreenOn: Boolean,
        ambientLightLux: Float,
        outdoorTemp: Float,
        currentSettings: com.example.data.SettingsEntity
    ): InferenceResult {
        val now = System.currentTimeMillis()
        
        // Add current reading to history
        temperatureHistory.add(now to currentTemp)
        
        // Clean up readings older than WINDOW_DURATION_MS
        val cutoff = now - WINDOW_DURATION_MS
        while (temperatureHistory.isNotEmpty() && temperatureHistory.peek().first < cutoff) {
            temperatureHistory.poll()
        }

        // Calculate Temperature Rise Rate (°C/min)
        var riseRatePerMin = 0f
        if (temperatureHistory.size >= 2) {
            val oldest = temperatureHistory.peekFirst()
            val newest = temperatureHistory.peekLast()
            val timeDiffMin = (newest.first - oldest.first) / 60000f
            if (timeDiffMin > 0.1f) { // Require at least 6 seconds of data
                riseRatePerMin = (newest.second - oldest.second) / timeDiffMin
            }
        }

        // Get CPU core load average
        val cpuLoad = try {
            CPUAnalytics.getCpuCoreLoad().average().toFloat()
        } catch (e: Exception) {
            0.15f
        }
        val isCpuLow = cpuLoad < 0.35f

        // Let's determine confidence indicators
        var confidence = 0
        val reasons = mutableListOf<String>()

        // 1. Is temperature rising?
        val isTempRising = riseRatePerMin > 0.05f
        if (isTempRising) {
            confidence += 25
            reasons.add("Temperature is rising continuously (${String.format(Locale.US, "%.2f", riseRatePerMin)}°C/min)")
        }

        // 2. Screen is OFF (indicating idle or external exposure)
        if (!isScreenOn) {
            confidence += 15
            reasons.add("Screen is OFF (no active user usage heat)")
        }

        // 3. CPU Core Load is low
        if (isCpuLow) {
            confidence += 15
            reasons.add("CPU load is low (${String.format(Locale.US, "%.1f", cpuLoad * 100)}%)")
        }

        // 4. Charging status is off or not responsible
        if (!isCharging) {
            confidence += 25
            reasons.add("Device is not charging (excludes charger heat)")
        } else {
            if (riseRatePerMin > 0.3f) {
                confidence += 10
                reasons.add("Rise rate is abnormally fast for charging")
            }
        }

        // 5. Weather vs device temperature
        if (outdoorTemp > 0f && currentTemp > outdoorTemp) {
            val diff = currentTemp - outdoorTemp
            if (diff > 5f) {
                confidence += 10
                reasons.add("Device is warmer than ambient outdoor temp (+${String.format(Locale.US, "%.1f", diff)}°C)")
            }
        }

        // 6. Strong ambient light
        val highLightThreshold = if (currentSettings.lightIntensityThreshold > 0) currentSettings.lightIntensityThreshold.toFloat() else 5000f
        if (ambientLightLux >= highLightThreshold) {
            confidence += 20
            reasons.add("Strong ambient light / direct sunlight detected (${ambientLightLux.toInt()} Lux)")
        }

        confidence = confidence.coerceIn(0, 100)

        // Inference trigger:
        // Must be actually rising, and confidence must be high (>= 60)
        val isExternalHeatDetected = isTempRising && confidence >= 60

        if (isExternalHeatDetected) {
            if (!isWarningActive) {
                isWarningActive = true
                startTime = now
                startTemp = currentTemp
                peakTemp = currentTemp
                lastLogTime = now
            } else {
                if (currentTemp > peakTemp) {
                    peakTemp = currentTemp
                }
            }
        } else {
            // Stops only when temperature stabilizes or decreases consistently
            if (isWarningActive && (riseRatePerMin <= 0f || currentTemp < peakTemp - 0.2f)) {
                isWarningActive = false
            }
        }

        val durationSec = if (isWarningActive) (now - startTime) / 1000L else 0L

        return InferenceResult(
            isInferred = isWarningActive,
            confidence = if (isWarningActive) confidence else 0,
            riseRate = riseRatePerMin,
            reasons = reasons,
            startTime = if (isWarningActive) startTime else 0L,
            endTime = if (isWarningActive) 0L else now,
            peakTemp = peakTemp,
            durationSec = durationSec
        )
    }

    fun reset() {
        temperatureHistory.clear()
        isWarningActive = false
        startTime = 0L
        peakTemp = 0f
        startTemp = 0f
    }
}

data class InferenceResult(
    val isInferred: Boolean,
    val confidence: Int,
    val riseRate: Float,
    val reasons: List<String>,
    val startTime: Long,
    val endTime: Long,
    val peakTemp: Float,
    val durationSec: Long
)

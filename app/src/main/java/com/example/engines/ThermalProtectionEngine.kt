package com.example.engines

import android.content.Context
import android.os.BatteryManager
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.entity.ThermalEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.TimeUnit

sealed class ThermalState {
    object Normal : ThermalState()
    object Protection : ThermalState()
    object Escalated : ThermalState()
    object Restoring : ThermalState()
    object Restored : ThermalState()
}

class ThermalProtectionEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _thermalState = MutableStateFlow<ThermalState>(ThermalState.Normal)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    private val _currentTemperature = MutableStateFlow(0f)
    val currentTemperature: StateFlow<Float> = _currentTemperature.asStateFlow()

    private val _brightnessReduced = MutableStateFlow(false)
    val brightnessReduced: StateFlow<Boolean> = _brightnessReduced.asStateFlow()

    // Temperature thresholds
    private val NORMAL_THRESHOLD = 40f  // ≤40°C
    private val PROTECTION_THRESHOLD = 43f  // 43°C
    private val ESCALATION_THRESHOLD = 45f  // 45°C
    private val RESTORATION_COOLDOWN = TimeUnit.MINUTES.toMillis(5)

    private var lastRestorationAttempt = 0L
    private var restorationStartTime = 0L

    /**
     * Update thermal state based on current battery temperature.
     * Implements the 43°C / 45°C / ≤40°C state machine.
     */
    fun updateThermalState(temperatureCelsius: Float) {
        _currentTemperature.value = temperatureCelsius

        val currentState = _thermalState.value
        val newState = when {
            temperatureCelsius <= NORMAL_THRESHOLD && currentState != ThermalState.Normal -> {
                if (currentState == ThermalState.Restoring || currentState == ThermalState.Escalated) {
                    ThermalState.Restoring
                } else {
                    ThermalState.Normal
                }
            }
            temperatureCelsius in NORMAL_THRESHOLD..PROTECTION_THRESHOLD && currentState != ThermalState.Protection -> {
                ThermalState.Protection
            }
            temperatureCelsius >= ESCALATION_THRESHOLD -> {
                ThermalState.Escalated
            }
            else -> currentState
        }

        if (newState != currentState) {
            _thermalState.value = newState
            handleStateTransition(newState, temperatureCelsius)
        }

        // Handle restoration timeout
        if (currentState == ThermalState.Restoring) {
            checkRestorationCompletion()
        }
    }

    private fun handleStateTransition(newState: ThermalState, temperature: Float) {
        scope.launch {
            when (newState) {
                ThermalState.Protection -> {
                    _brightnessReduced.value = true
                    logThermalEvent("PROTECTION_ACTIVATED", temperature)
                }
                ThermalState.Escalated -> {
                    _brightnessReduced.value = true
                    logThermalEvent("ESCALATION_ACTIVATED", temperature)
                }
                ThermalState.Restoring -> {
                    restorationStartTime = System.currentTimeMillis()
                    logThermalEvent("RESTORATION_STARTED", temperature)
                }
                ThermalState.Restored -> {
                    _brightnessReduced.value = false
                    logThermalEvent("RESTORED", temperature)
                }
                ThermalState.Normal -> {
                    if (_brightnessReduced.value) {
                        _brightnessReduced.value = false
                    }
                    logThermalEvent("NORMAL_STATE", temperature)
                }
            }
        }
    }

    private fun checkRestorationCompletion() {
        val elapsedTime = System.currentTimeMillis() - restorationStartTime
        if (elapsedTime >= RESTORATION_COOLDOWN && System.currentTimeMillis() - lastRestorationAttempt > RESTORATION_COOLDOWN) {
            lastRestorationAttempt = System.currentTimeMillis()
            _thermalState.value = ThermalState.Restored
            handleStateTransition(ThermalState.Restored, _currentTemperature.value)
        }
    }

    /**
     * Get the brightness reduction percentage.
     * Returns 5% reduction during thermal protection.
     */
    fun getBrightnessReduction(): Int {
        return if (_brightnessReduced.value) 5 else 0
    }

    /**
     * Check if brightness should be automatically reduced.
     * This is automatic during thermal protection—no manual override.
     */
    fun shouldReduceBrightness(): Boolean = _brightnessReduced.value

    /**
     * Get thermal status for UI display.
     */
    fun getThermalStatusText(): String = when (_thermalState.value) {
        ThermalState.Normal -> "Normal"
        ThermalState.Protection -> "Thermal Protection Active"
        ThermalState.Escalated -> "Thermal Escalation"
        ThermalState.Restoring -> "Restoring..."
        ThermalState.Restored -> "Restored"
    }

    /**
     * Get semantic color for thermal status.
     */
    fun getThermalStatusColor(): Long = when (_thermalState.value) {
        ThermalState.Normal -> 0xFF4CAF50  // Green
        ThermalState.Protection -> 0xFFFFC107  // Amber
        ThermalState.Escalated -> 0xFFFF5722  // Red-Orange
        ThermalState.Restoring -> 0xFF2196F3  // Blue
        ThermalState.Restored -> 0xFF4CAF50  // Green
    }

    private fun logThermalEvent(eventType: String, temperature: Float) {
        scope.launch {
            try {
                val event = ThermalEvent(
                    eventType = eventType,
                    temperature = temperature,
                    timestamp = Instant.now().toEpochMilli(),
                    state = _thermalState.value::class.simpleName ?: "Unknown"
                )
                database.thermalEventDao().insertEvent(event)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Force reset thermal state for testing or manual reset.
     */
    fun resetThermalState() {
        _thermalState.value = ThermalState.Normal
        _brightnessReduced.value = false
        lastRestorationAttempt = 0L
    }
}

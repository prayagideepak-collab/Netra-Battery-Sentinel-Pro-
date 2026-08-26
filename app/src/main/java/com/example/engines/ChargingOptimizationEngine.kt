package com.example.engines

import android.content.Context
import android.os.BatteryManager
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.entity.ChargingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed class ChargingState {
    object NotCharging : ChargingState()
    object Charging : ChargingState()
    object Optimized : ChargingState()
    object ThermallyLimited : ChargingState()
    object Full : ChargingState()
}

data class ChargingMetrics(
    val currentChargePercent: Int = 0,
    val chargingPowerWatts: Float = 0f,
    val estimatedTimeMinutes: Int = 0,
    val thermallyLimited: Boolean = false,
    val chargingState: ChargingState = ChargingState.NotCharging
)

class ChargingOptimizationEngine(
    private val context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _chargingState = MutableStateFlow<ChargingState>(ChargingState.NotCharging)
    val chargingState: StateFlow<ChargingState> = _chargingState.asStateFlow()

    private val _chargingMetrics = MutableStateFlow(ChargingMetrics())
    val chargingMetrics: StateFlow<ChargingMetrics> = _chargingMetrics.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private var currentSession: ChargingSession? = null
    private val FULL_BATTERY_THRESHOLD = 100
    private val OPTIMIZATION_THRESHOLD_WATTS = 10f
    private val THERMAL_LIMIT_THRESHOLD = 45f  // 45°C

    /**
     * Update charging state based on battery metrics.
     */
    fun updateChargingState(
        chargePercent: Int,
        isCharging: Boolean,
        chargingPowerMilliamps: Int,
        temperature: Float,
        voltage: Int,
        health: Int
    ) {
        val powerWatts = calculatePowerWatts(chargingPowerMilliamps, voltage)
        val thermallyLimited = temperature >= THERMAL_LIMIT_THRESHOLD

        val newState = classifyChargingState(
            chargePercent = chargePercent,
            isCharging = isCharging,
            powerWatts = powerWatts,
            temperature = temperature,
            thermallyLimited = thermallyLimited
        )

        val currentState = _chargingState.value
        if (newState != currentState) {
            _chargingState.value = newState
            handleChargingStateTransition(
                newState,
                chargePercent,
                powerWatts,
                temperature
            )
        }

        val estimatedTimeMinutes = estimateChargingTime(chargePercent, powerWatts, isCharging)
        val metrics = ChargingMetrics(
            currentChargePercent = chargePercent,
            chargingPowerWatts = powerWatts,
            estimatedTimeMinutes = estimatedTimeMinutes,
            thermallyLimited = thermallyLimited,
            chargingState = newState
        )
        _chargingMetrics.value = metrics
    }

    private fun classifyChargingState(
        chargePercent: Int,
        isCharging: Boolean,
        powerWatts: Float,
        temperature: Float,
        thermallyLimited: Boolean
    ): ChargingState {
        return when {
            chargePercent >= FULL_BATTERY_THRESHOLD -> ChargingState.Full
            !isCharging -> ChargingState.NotCharging
            thermallyLimited -> ChargingState.ThermallyLimited
            powerWatts < OPTIMIZATION_THRESHOLD_WATTS && isCharging && chargePercent >= 80 -> {
                ChargingState.Optimized
            }
            isCharging -> ChargingState.Charging
            else -> ChargingState.NotCharging
        }
    }

    private fun handleChargingStateTransition(
        newState: ChargingState,
        chargePercent: Int,
        powerWatts: Float,
        temperature: Float
    ) {
        scope.launch {
            when (newState) {
                ChargingState.Charging -> {
                    if (_sessionActive.value.not()) {
                        startChargingSession(chargePercent)
                    }
                }
                ChargingState.Optimized -> {
                    // Session already active, just update
                    updateChargingSession(chargePercent, powerWatts)
                }
                ChargingState.ThermallyLimited -> {
                    updateChargingSession(chargePercent, powerWatts, thermallyLimited = true)
                }
                ChargingState.Full -> {
                    completeChargingSession(chargePercent)
                }
                ChargingState.NotCharging -> {
                    if (_sessionActive.value) {
                        completeChargingSession(chargePercent)
                    }
                }
            }
        }
    }

    private fun startChargingSession(initialCharge: Int) {
        try {
            val session = ChargingSession(
                startTime = Instant.now().toEpochMilli(),
                startChargePercent = initialCharge,
                endTime = null,
                endChargePercent = null,
                thermallyLimited = false,
                completed = false
            )
            currentSession = session
            _sessionActive.value = true
            scope.launch {
                val sessionId = database.chargingSessionDao().insertSession(session)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateChargingSession(
        currentCharge: Int,
        powerWatts: Float,
        thermallyLimited: Boolean = false
    ) {
        currentSession?.let {
            scope.launch {
                database.chargingSessionDao().updateSession(
                    it.copy(
                        endChargePercent = currentCharge,
                        thermallyLimited = thermallyLimited
                    )
                )
            }
        }
    }

    private fun completeChargingSession(finalCharge: Int) {
        currentSession?.let { session ->
            scope.launch {
                database.chargingSessionDao().updateSession(
                    session.copy(
                        endTime = Instant.now().toEpochMilli(),
                        endChargePercent = finalCharge,
                        completed = true
                    )
                )
                _sessionActive.value = false
                currentSession = null
            }
        }
    }

    private fun calculatePowerWatts(milliamps: Int, voltage: Int): Float {
        return (milliamps.toFloat() * voltage.toFloat()) / 1_000_000f
    }

    private fun estimateChargingTime(chargePercent: Int, powerWatts: Float, isCharging: Boolean): Int {
        if (!isCharging || powerWatts <= 0f) return 0
        val remainingPercent = FULL_BATTERY_THRESHOLD - chargePercent
        val typicalCapacityWh = 15f  // Typical phone battery
        val remainingWh = (remainingPercent / 100f) * typicalCapacityWh
        val hoursRemaining = remainingWh / powerWatts.coerceAtLeast(0.1f)
        return (hoursRemaining * 60).toInt()
    }

    /**
     * Get charging status text for UI.
     */
    fun getChargingStatusText(): String = when (_chargingState.value) {
        ChargingState.NotCharging -> "Not Charging"
        ChargingState.Charging -> "Charging"
        ChargingState.Optimized -> "Optimized Charging"
        ChargingState.ThermallyLimited -> "Charging (Thermally Limited)"
        ChargingState.Full -> "Fully Charged"
    }

    /**
     * Get semantic color for charging status.
     */
    fun getChargingStatusColor(): Long = when (_chargingState.value) {
        ChargingState.NotCharging -> 0xFF9E9E9E  // Gray
        ChargingState.Charging -> 0xFF2196F3  // Blue
        ChargingState.Optimized -> 0xFF4CAF50  // Green
        ChargingState.ThermallyLimited -> 0xFFFFC107  // Amber
        ChargingState.Full -> 0xFF4CAF50  // Green
    }

    /**
     * Get last completed charging session.
     */
    fun getLastChargingSession(): ChargingSession? {
        return currentSession
    }

    /**
     * Clear all sessions (for testing/reset).
     */
    fun resetSessions() {
        _sessionActive.value = false
        currentSession = null
    }
}

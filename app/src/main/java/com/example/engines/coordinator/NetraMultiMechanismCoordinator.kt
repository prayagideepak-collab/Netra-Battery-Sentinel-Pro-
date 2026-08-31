package com.example.engines.coordinator

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryEvent
import com.example.engines.IdleBatterySaverEngine
import com.example.engines.ProximityPocketLockEngine
import com.example.service.AdaptiveLocationBatterySaver
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class NetraSystemOperationalState {
    ACTIVE,
    SCREEN_OFF,
    IDLE_PENDING,
    IDLE_CONFIRMED,
    BATTERY_SAVER_ALREADY_ON,
    BATTERY_SAVER_ENABLED_BY_NETRA,
    CHARGING,
    NOT_CHARGING,
    PROXIMITY_NEAR,
    PROXIMITY_FAR,
    LOCATION_REFRESH_PENDING,
    LOCATION_REFRESH_RUNNING,
    LOCATION_REFRESH_SUCCESS,
    LOCATION_REFRESH_UNAVAILABLE,
    THERMAL_NORMAL,
    THERMAL_WARNING,
    THERMAL_PROTECTION
}

data class MultiMechanismState(
    val operationalState: NetraSystemOperationalState = NetraSystemOperationalState.ACTIVE,
    val batteryPercentage: Int = 50,
    val temperatureCelsius: Float = 30.0f,
    val isCharging: Boolean = false,
    val lowBatteryAlertTriggered: Boolean = false,
    val fullChargeAlertTriggered: Boolean = false,
    val thermalProtectionEngaged: Boolean = false,
    val lastEventSummary: String = "All Mechanisms Operational"
)

/**
 * NetraMultiMechanismCoordinator (Phase 7 - Central State Machine & Coordination)
 * Unifies Capability A (Idle Battery Saver), Capability B (Proximity Pocket Lock),
 * Capability C (Periodic Location Refresh), and Capability D (Battery/Power/Thermal Intelligence).
 * Enforces race-condition protection, duplicate event filtering, policy arbitration,
 * and strict audit persistence.
 */
object NetraMultiMechanismCoordinator : Engine {
    private const val TAG = "NetraMultiCoordinator"
    override val name = "NetraMultiMechanismCoordinator"
    override val priority = 20

    private val isInitialized = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _stateFlow = MutableStateFlow(MultiMechanismState())
    val stateFlow: StateFlow<MultiMechanismState> = _stateFlow.asStateFlow()

    private var locationSaver: AdaptiveLocationBatterySaver? = null
    private val processedLowBatteryThresholds = ConcurrentHashMap.newKeySet<Int>()
    private var lastThermalEventTimestamp = 0L

    override fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return
        Log.i(TAG, "Initializing NetraMultiMechanismCoordinator...")

        val appContext = context.applicationContext

        // Initialize sub-engines
        IdleBatterySaverEngine.initialize(appContext)
        ProximityPocketLockEngine.initialize(appContext)

        // Initialize Periodic Location Refresh (Capability C)
        locationSaver = AdaptiveLocationBatterySaver(appContext, scope) { location ->
            Log.i(TAG, "Periodic location refresh acquired successfully: lat=${location.latitude}, lon=${location.longitude}")
            _stateFlow.value = _stateFlow.value.copy(
                operationalState = NetraSystemOperationalState.LOCATION_REFRESH_SUCCESS,
                lastEventSummary = "Periodic location refreshed (Lat: ${String.format("%.4f", location.latitude)}, Lon: ${String.format("%.4f", location.longitude)})"
            )
            scope.launch {
                persistEvent(appContext, "LOCATION_REFRESH_SUCCESS", "Periodic Location Refreshed", "Lat: ${location.latitude}, Lon: ${location.longitude}, Acc: ${location.accuracy}m", "LOCATION")
            }
        }
        locationSaver?.start()

        Log.i(TAG, "NetraMultiMechanismCoordinator initialized successfully.")
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down NetraMultiMechanismCoordinator...")
        locationSaver?.stop()
        IdleBatterySaverEngine.shutdown()
        ProximityPocketLockEngine.shutdown()
        isInitialized.set(false)
    }

    override fun getStatus(): String {
        return "MultiMechanism: State=${_stateFlow.value.operationalState}, Thermal=${_stateFlow.value.temperatureCelsius}°C"
    }

    /**
     * Handles screen off events across all integrated capabilities
     */
    fun onScreenOff(context: Context) {
        Log.i(TAG, "Central Event: SCREEN_OFF")
        _stateFlow.value = _stateFlow.value.copy(operationalState = NetraSystemOperationalState.SCREEN_OFF)
        IdleBatterySaverEngine.onScreenOff(context)
    }

    /**
     * Handles screen on / device unlock events across integrated capabilities
     */
    fun onScreenOnOrUnlocked(context: Context) {
        Log.i(TAG, "Central Event: SCREEN_ON / UNLOCKED")
        _stateFlow.value = _stateFlow.value.copy(operationalState = NetraSystemOperationalState.ACTIVE)
        IdleBatterySaverEngine.onScreenOnOrUnlocked(context)
    }

    /**
     * Handles battery telemetry updates, triggering Capability D (Battery/Power/Thermal Intelligence)
     */
    fun onBatteryTelemetryUpdate(context: Context, percentage: Int, isCharging: Boolean, temperatureCelsius: Float) {
        val currentState = _stateFlow.value
        if (currentState.batteryPercentage == percentage && currentState.isCharging == isCharging && Math.abs(currentState.temperatureCelsius - temperatureCelsius) < 0.2f) {
            return // suppress duplicate identical telemetry
        }

        _stateFlow.value = currentState.copy(
            batteryPercentage = percentage,
            isCharging = isCharging,
            temperatureCelsius = temperatureCelsius
        )

        scope.launch {
            // 1. Low Battery Intelligence (Default threshold ~25% or configurable)
            if (!isCharging && percentage <= 25 && !processedLowBatteryThresholds.contains(percentage)) {
                processedLowBatteryThresholds.add(percentage)
                Log.i(TAG, "Low Battery Intelligence Triggered at $percentage%")
                _stateFlow.value = _stateFlow.value.copy(lowBatteryAlertTriggered = true, lastEventSummary = "Low Battery Warning ($percentage%)")
                persistEvent(context, "LOW_BATTERY", "Low Battery Warning ($percentage%)", "Battery reached threshold $percentage% without external power.", "POWER")
            }

            // Reset low battery flags if charging or battery recovers above 30%
            if (isCharging || percentage > 30) {
                if (percentage > 30 && processedLowBatteryThresholds.isNotEmpty()) {
                    processedLowBatteryThresholds.clear()
                }
            }

            // 2. Full Charge Intelligence (100%)
            if (isCharging && percentage >= 100 && !currentState.fullChargeAlertTriggered) {
                Log.i(TAG, "Full Charge Intelligence Triggered at 100%")
                _stateFlow.value = _stateFlow.value.copy(fullChargeAlertTriggered = true, lastEventSummary = "Battery Full (100%)")
                persistEvent(context, "BATTERY_FULL", "Battery Full (100%)", "Battery reached full charge capacity.", "POWER")
            } else if (!isCharging && percentage < 98) {
                _stateFlow.value = _stateFlow.value.copy(fullChargeAlertTriggered = false)
            }

            // 3. Thermal Protection Intelligence (Safety threshold e.g. ≥ 40°C / 43°C / 45°C)
            val thermalThreshold = 40.0f
            val now = System.currentTimeMillis()
            if (temperatureCelsius >= thermalThreshold && (now - lastThermalEventTimestamp > 300000L)) { // debounce 5 mins
                lastThermalEventTimestamp = now
                Log.w(TAG, "THERMAL PROTECTION TRIGGERED: Temperature reached $temperatureCelsius°C (Threshold: $thermalThreshold°C)")
                _stateFlow.value = _stateFlow.value.copy(
                    operationalState = NetraSystemOperationalState.THERMAL_PROTECTION,
                    thermalProtectionEngaged = true,
                    lastEventSummary = "Thermal Warning ($temperatureCelsius°C)"
                )

                persistEvent(context, "THERMAL_WARNING", "Thermal Safety Threshold Crossed ($temperatureCelsius°C)", "Battery temperature reached $temperatureCelsius°C. Entering thermal protection mode.", "HARDWARE")
            } else if (temperatureCelsius < 37.0f && currentState.thermalProtectionEngaged) {
                _stateFlow.value = _stateFlow.value.copy(
                    operationalState = NetraSystemOperationalState.ACTIVE,
                    thermalProtectionEngaged = false,
                    lastEventSummary = "Thermal Normal ($temperatureCelsius°C)"
                )
            }
        }
    }

    private suspend fun persistEvent(context: Context, eventType: String, title: String, details: String, category: String) {
        try {
            val db = BatteryDatabase.getDatabase(context)
            db.batteryDao().insertBatteryEvent(
                BatteryEvent(
                    timestamp = System.currentTimeMillis(),
                    eventType = eventType,
                    title = title,
                    details = details,
                    category = category,
                    source = "NetraMultiMechanismCoordinator"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist multi-mechanism event", e)
        }
    }
}

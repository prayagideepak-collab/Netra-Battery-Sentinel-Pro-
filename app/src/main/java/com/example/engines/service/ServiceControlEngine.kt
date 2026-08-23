package com.example.engines.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.modules.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class ServiceState {
    INITIALIZING,
    RUNNING,
    IDLE,
    SLEEPING,
    PAUSED,
    DISABLED,
    RESTORING,
    ERROR
}

enum class ServiceHealth {
    HEALTHY,
    WARNING,
    ERROR
}

data class ServiceInfo(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val isCore: Boolean,
    val currentState: ServiceState,
    val health: ServiceHealth,
    val dependentEvents: List<NotificationEvent> = emptyList(),
    val safetyWarning: String? = null
)

object ServiceControlEngine {
    private const val TAG = "ServiceControlEngine"
    private const val PREF_NAME = "netra_scc_preferences"

    private var prefs: SharedPreferences? = null

    // Memory map to preserve user notification preferences before service is disabled
    private val savedPreferenceMemoryMap = ConcurrentHashMap<NotificationEvent, Pair<Boolean, Boolean>>()

    private val initialServices = listOf(
        // --- Core Battery Services (Locked) ---
        ServiceInfo(
            id = "core_battery_monitoring",
            name = "Battery Monitoring",
            category = "Core Battery",
            description = "Main real-time battery engine monitoring voltage, level, and power draws.",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_battery_percentage",
            name = "Battery Percentage",
            category = "Core Battery",
            description = "Calculates accurate State of Charge (SoC) percentages.",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_charging_detection",
            name = "Charging Detection",
            category = "Core Battery",
            description = "Detects AC, USB, and Wireless charging connection events.",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_charger_connected",
            name = "Charger Connected",
            category = "Core Battery",
            description = "Monitors plug-in event telemetry.",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_charger_disconnected",
            name = "Charger Disconnected",
            category = "Core Battery",
            description = "Monitors unplug event telemetry.",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_charging_status",
            name = "Charging Status",
            category = "Core Battery",
            description = "Evaluates charging speed, rate, and battery status changes.",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_battery_health",
            name = "Battery Health",
            category = "Core Battery",
            description = "Tracks chemical health degradation and capacity estimation.",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_battery_status",
            name = "Battery Status",
            category = "Core Battery",
            description = "Monitors current battery state (Charging, Discharging, Full).",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),
        ServiceInfo(
            id = "core_battery_info",
            name = "Battery Information",
            category = "Core Battery",
            description = "Hardware battery info (Technology, Chemistry, Temperature).",
            isCore = true,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY
        ),

        // --- Optional Monitoring Services ---
        ServiceInfo(
            id = "thermal_monitoring",
            name = "Thermal Monitoring",
            category = "Sensors",
            description = "Monitors battery and ambient temperature to prevent thermal runaway.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.TEMPERATURE_WARNING,
                NotificationEvent.TEMPERATURE_CRITICAL,
                NotificationEvent.TEMPERATURE_EMERGENCY,
                NotificationEvent.TEMPERATURE_NORMAL
            ),
            safetyWarning = "Disabling thermal monitoring stops temperature threshold alerts."
        ),
        ServiceInfo(
            id = "magnetic_field_monitoring",
            name = "Magnetic Field Monitoring",
            category = "Sensors",
            description = "Detects high electromagnetic fields that can disrupt internal sensors.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.MAGNETIC_WARNING,
                NotificationEvent.STRONG_MAGNETIC_FIELD,
                NotificationEvent.CONTINUOUS_MAGNETIC_EXPOSURE,
                NotificationEvent.MAGNETIC_NORMAL
            )
        ),
        ServiceInfo(
            id = "bluetooth_device_monitoring",
            name = "Bluetooth Device Monitoring",
            category = "Connectivity",
            description = "Tracks battery levels of paired Bluetooth devices and accessories.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.DEVICE_CONNECTED,
                NotificationEvent.DEVICE_DISCONNECTED,
                NotificationEvent.DEVICE_BATTERY_LOW,
                NotificationEvent.DEVICE_BATTERY_CRITICAL,
                NotificationEvent.BLUETOOTH_CONNECTED,
                NotificationEvent.BLUETOOTH_DISCONNECTED,
                NotificationEvent.BLUETOOTH_LOW_BATTERY
            )
        ),
        ServiceInfo(
            id = "device_info_monitoring",
            name = "Device Information Monitoring",
            category = "System",
            description = "Tracks OS updates, system health metrics, and hardware stats.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.SYSTEM_UPDATE_INSTALLED,
                NotificationEvent.HEALTH_MONITOR_MESSAGES
            )
        ),
        ServiceInfo(
            id = "ai_optimization_engine",
            name = "AI Optimization Engine",
            category = "AI & Analytics",
            description = "Predictive engine for adaptive charging routines and overcharge prevention.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.OVERCHARGE_STARTED,
                NotificationEvent.OVERCHARGE_REMINDER
            )
        ),
        ServiceInfo(
            id = "battery_analytics",
            name = "Battery Analytics",
            category = "AI & Analytics",
            description = "Collects discharge rates, health metrics, and battery statistics.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.BATTERY_HEALTH_UPDATES,
                NotificationEvent.BATTERY_STATUS_CHANGES
            )
        ),
        ServiceInfo(
            id = "smart_charging_suggestions",
            name = "Smart Charging Suggestions",
            category = "AI & Analytics",
            description = "Provides smart notifications for fast/slow charger detection.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.SLOW_CHARGING_DETECTED,
                NotificationEvent.FAST_CHARGING_DETECTED,
                NotificationEvent.CHARGING_TYPE_CHANGED
            )
        ),
        ServiceInfo(
            id = "background_statistics",
            name = "Background Statistics",
            category = "System",
            description = "Performs periodic logging, data exports, and background sync.",
            isCore = false,
            currentState = ServiceState.RUNNING,
            health = ServiceHealth.HEALTHY,
            dependentEvents = listOf(
                NotificationEvent.SYSTEM_BACKUP_COMPLETE,
                NotificationEvent.SYSTEM_EXPORT_COMPLETE,
                NotificationEvent.SYSTEM_RESTORE_COMPLETE
            )
        )
    )

    private val _services = MutableStateFlow<List<ServiceInfo>>(initialServices)
    val servicesState: StateFlow<List<ServiceInfo>> = _services.asStateFlow()

    fun initialize(context: Context) {
        try {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            loadSavedStates()
            Log.i(TAG, "ServiceControlEngine successfully initialized with ${_services.value.size} services.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ServiceControlEngine", e)
        }
    }

    private fun loadSavedStates() {
        val shared = prefs ?: return
        val currentList = _services.value.map { service ->
            if (service.isCore) {
                service.copy(currentState = ServiceState.RUNNING, health = ServiceHealth.HEALTHY)
            } else {
                val isEnabled = shared.getBoolean("service_${service.id}", true)
                val state = if (isEnabled) ServiceState.RUNNING else ServiceState.DISABLED
                service.copy(currentState = state, health = if (isEnabled) ServiceHealth.HEALTHY else ServiceHealth.WARNING)
            }
        }
        _services.value = currentList
    }

    fun isServiceEnabled(serviceId: String): Boolean {
        val service = _services.value.find { it.id == serviceId } ?: return false
        return service.isCore || service.currentState == ServiceState.RUNNING || service.currentState == ServiceState.IDLE
    }

    /**
     * Toggles optional service status with Notification Preference Memory and Instant Rollback.
     */
    fun toggleService(context: Context, serviceId: String, enable: Boolean): Boolean {
        val service = _services.value.find { it.id == serviceId } ?: return false

        if (service.isCore) {
            Log.w(TAG, "Attempted to toggle Core Locked Service: $serviceId")
            return false
        }

        val previousState = service.currentState

        return try {
            if (!enable) {
                // --- DISABLING SERVICE ---
                // 1. Save current notification preferences to memory map
                service.dependentEvents.forEach { event ->
                    val pref = PreferenceManager.getPreference(event)
                    if (pref != null && !pref.isLocked) {
                        savedPreferenceMemoryMap[event] = Pair(pref.notificationEnabled, pref.announcementEnabled)
                        // Disable notification & announcement for dependent events
                        PreferenceManager.updatePreference(event, notifEnabled = false, annEnabled = false)
                    }
                }

                // 2. Update service state
                updateServiceState(serviceId, ServiceState.DISABLED, ServiceHealth.WARNING)

                // 3. Persist disabled state
                prefs?.edit()?.putBoolean("service_$serviceId", false)?.apply()
                Log.i(TAG, "Service $serviceId disabled successfully.")
                true
            } else {
                // --- ENABLING SERVICE ---
                // 1. Transition state to RESTORING
                updateServiceState(serviceId, ServiceState.RESTORING, ServiceHealth.HEALTHY)

                // 2. Restore notification preferences from memory map (or default true if missing)
                service.dependentEvents.forEach { event ->
                    val saved = savedPreferenceMemoryMap[event]
                    val notif = saved?.first ?: true
                    val ann = saved?.second ?: true
                    PreferenceManager.updatePreference(event, notifEnabled = notif, annEnabled = ann)
                }

                // 3. Complete transition to RUNNING
                updateServiceState(serviceId, ServiceState.RUNNING, ServiceHealth.HEALTHY)

                // 4. Persist enabled state
                prefs?.edit()?.putBoolean("service_$serviceId", true)?.apply()
                Log.i(TAG, "Service $serviceId restored and running successfully.")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle service $serviceId to enable=$enable. Executing Instant Rollback.", e)
            // Instant Rollback to previous state
            updateServiceState(serviceId, previousState, ServiceHealth.ERROR)
            false
        }
    }

    private fun updateServiceState(serviceId: String, state: ServiceState, health: ServiceHealth) {
        val updated = _services.value.map {
            if (it.id == serviceId) {
                it.copy(currentState = state, health = health)
            } else {
                it
            }
        }
        _services.value = updated
    }

    /**
     * Restores default services (all optional services enabled).
     */
    fun restoreDefaultServices(context: Context) {
        try {
            _services.value.filter { !it.isCore }.forEach { service ->
                toggleService(context, service.id, true)
            }
            Log.i(TAG, "All optional services restored to default RUNNING state.")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring default services", e)
        }
    }
}

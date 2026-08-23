package com.example.engines.notification.inale

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.NotificationEventData
import com.example.engines.notification.modules.*
import com.example.engines.service.ServiceControlEngine
import com.example.util.LoggingManager
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

data class InaleDecisionResult(
    val data: NotificationEventData,
    val shouldNotify: Boolean,
    val shouldAnnounce: Boolean,
    val assignedPriority: EventPriority,
    val auditReason: String,
    val severityLabel: String,
    val severityColorHex: String
)

object InaleEngine {
    private const val TAG = "INALE_Engine"

    // --- Mode Control Flags ---
    var isQuietHoursEnabled: Boolean = true
    var quietHoursStartHour: Int = 22 // 10 PM
    var quietHoursEndHour: Int = 7   // 7 AM
    var isSmartChargingFilterEnabled: Boolean = true
    var isRateLimitingEnabled: Boolean = true
    var isDynamicEscalationEnabled: Boolean = true
    var isDrivingMode: Boolean = false

    // --- Internal Tracking Caches ---
    private val debounceCache = ConcurrentHashMap<String, Long>()
    private val infoRateLimitWindow = mutableListOf<Long>()
    private val lastEventMetrics = ConcurrentHashMap<NotificationEvent, Float>()

    /**
     * Map NotificationEvent to its parent Service ID in ServiceControlEngine.
     */
    private fun getServiceIdForEvent(event: NotificationEvent): String? {
        return when (event) {
            NotificationEvent.TEMPERATURE_NORMAL,
            NotificationEvent.TEMPERATURE_WARNING,
            NotificationEvent.TEMPERATURE_CRITICAL,
            NotificationEvent.TEMPERATURE_EMERGENCY,
            NotificationEvent.BATTERY_TEMP_OVER_43,
            NotificationEvent.EXTERNAL_HEAT_SOURCE,
            NotificationEvent.FIRE_RISK -> "thermal_monitoring"

            NotificationEvent.MAGNETIC_NORMAL,
            NotificationEvent.MAGNETIC_WARNING,
            NotificationEvent.STRONG_MAGNETIC_FIELD,
            NotificationEvent.CONTINUOUS_MAGNETIC_EXPOSURE,
            NotificationEvent.MAGNETIC_CRITICAL,
            NotificationEvent.MAGNETIC_EMERGENCY -> "magnetic_field_monitoring"

            NotificationEvent.DEVICE_CONNECTED,
            NotificationEvent.DEVICE_DISCONNECTED,
            NotificationEvent.DEVICE_BATTERY_LOW,
            NotificationEvent.DEVICE_BATTERY_CRITICAL,
            NotificationEvent.BLUETOOTH_CONNECTED,
            NotificationEvent.BLUETOOTH_DISCONNECTED,
            NotificationEvent.BLUETOOTH_LOW_BATTERY -> "bluetooth_device_monitoring"

            NotificationEvent.WEATHER_GOVERNMENT,
            NotificationEvent.HEATWAVE,
            NotificationEvent.THUNDERSTORM,
            NotificationEvent.HEAVY_RAIN,
            NotificationEvent.HIGH_WIND,
            NotificationEvent.DENSE_FOG,
            NotificationEvent.WEATHER_EXTREME -> "weather_monitoring"

            NotificationEvent.SYSTEM_UPDATE_INSTALLED,
            NotificationEvent.HEALTH_MONITOR_MESSAGES -> "device_info_monitoring"

            NotificationEvent.OVERCHARGE_STARTED,
            NotificationEvent.OVERCHARGE_REMINDER -> "ai_optimization_engine"

            NotificationEvent.BATTERY_HEALTH_UPDATES,
            NotificationEvent.BATTERY_STATUS_CHANGES -> "battery_analytics"

            NotificationEvent.SLOW_CHARGING_DETECTED,
            NotificationEvent.FAST_CHARGING_DETECTED,
            NotificationEvent.CHARGING_TYPE_CHANGED -> "smart_charging_suggestions"

            NotificationEvent.SYSTEM_BACKUP_COMPLETE,
            NotificationEvent.SYSTEM_EXPORT_COMPLETE,
            NotificationEvent.SYSTEM_RESTORE_COMPLETE -> "background_statistics"

            else -> null // Core Battery services or Safety Locked
        }
    }

    /**
     * Complete 10-step Event Decision Pipeline
     */
    fun processAndEvaluate(context: Context, data: NotificationEventData): InaleDecisionResult {
        val appContext = context.applicationContext
        val event = data.event
        val now = System.currentTimeMillis()
        var basePriority = PriorityManager.getEventPriority(event, data.overridePriority)

        // Suppress slow charging alerts if USB data transfer mode is active
        if (event == NotificationEvent.SLOW_CHARGING_DETECTED && com.example.engines.charging.ChargingStateManager.isDataTransferActive.value) {
            Log.d(TAG, "Event SLOW_CHARGING_DETECTED suppressed: USB Data Transfer mode is active.")
            val result = InaleDecisionResult(
                data = data,
                shouldNotify = false,
                shouldAnnounce = false,
                assignedPriority = basePriority,
                auditReason = "SUPPRESSED_USB_DATA_TRANSFER",
                severityLabel = getSeverityLabel(basePriority),
                severityColorHex = getSeverityColorHex(basePriority)
            )
            dispatchAndAudit(appContext, result)
            return result
        }

        // --- STEP 1: Safety Override Check ---
        if (SafetyOverrideManager.isSafetyOverride(event)) {
            Log.i(TAG, "Safety Override triggered for event: $event")
            val result = InaleDecisionResult(
                data = data,
                shouldNotify = true,
                shouldAnnounce = true,
                assignedPriority = EventPriority.EMERGENCY,
                auditReason = "SAFETY_OVERRIDE_DELIVERED",
                severityLabel = "EMERGENCY",
                severityColorHex = "#F44336"
            )
            dispatchAndAudit(appContext, result)
            return result
        }

        // --- STEP 2: Service Enabled Check (Phase 3 Integration) ---
        val serviceId = getServiceIdForEvent(event)
        if (serviceId != null && !ServiceControlEngine.isServiceEnabled(serviceId)) {
            Log.d(TAG, "Event $event suppressed: Parent Service [$serviceId] is DISABLED.")
            val result = InaleDecisionResult(
                data = data,
                shouldNotify = false,
                shouldAnnounce = false,
                assignedPriority = basePriority,
                auditReason = "SUPPRESSED_SERVICE_DISABLED",
                severityLabel = getSeverityLabel(basePriority),
                severityColorHex = getSeverityColorHex(basePriority)
            )
            dispatchAndAudit(appContext, result)
            return result
        }

        // --- STEP 3: Capability & Permission Check ---
        if (!CapabilityDetector.isCapabilitySupported(appContext, event)) {
            val result = InaleDecisionResult(
                data = data,
                shouldNotify = false,
                shouldAnnounce = false,
                assignedPriority = basePriority,
                auditReason = "SUPPRESSED_UNSUPPORTED_DEVICE",
                severityLabel = getSeverityLabel(basePriority),
                severityColorHex = getSeverityColorHex(basePriority)
            )
            dispatchAndAudit(appContext, result)
            return result
        }

        val hasPermission = PermissionManager.isPermissionGrantedForEvent(appContext, event)

        // --- STEP 4: User Preference Check (Phase 1 & 2 Integration) ---
        val pref = PreferenceManager.getPreference(event)
        var userNotifPref = (pref?.notificationEnabled ?: true) && hasPermission
        var userAnnPref = pref?.announcementEnabled ?: true

        if (!userNotifPref && !userAnnPref) {
            val result = InaleDecisionResult(
                data = data,
                shouldNotify = false,
                shouldAnnounce = false,
                assignedPriority = basePriority,
                auditReason = "SUPPRESSED_USER_PREFERENCE",
                severityLabel = getSeverityLabel(basePriority),
                severityColorHex = getSeverityColorHex(basePriority)
            )
            dispatchAndAudit(appContext, result)
            return result
        }

        // --- STEP 6: Quiet Hours Filter ---
        if (isQuietHoursEnabled) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val inQuietHours = if (quietHoursStartHour > quietHoursEndHour) {
                currentHour >= quietHoursStartHour || currentHour < quietHoursEndHour
            } else {
                currentHour in quietHoursStartHour until quietHoursEndHour
            }

            if (inQuietHours && basePriority != EventPriority.EMERGENCY && basePriority != EventPriority.CRITICAL) {
                userAnnPref = false
                if (basePriority == EventPriority.INFORMATION || basePriority == EventPriority.BACKGROUND) {
                    userNotifPref = false
                }
                Log.d(TAG, "Quiet Hours active ($currentHour:00): Muting non-critical alert $event")
            }
        }

        // --- STEP 7: Smart Charging Logic Filter ---
        if (isSmartChargingFilterEnabled && isDeviceCharging(appContext)) {
            if (event == NotificationEvent.MAGNETIC_WARNING || event == NotificationEvent.STRONG_MAGNETIC_FIELD) {
                // Suppress normal charging-induced magnetic alerts if temp is normal
                val currentTemp = getBatteryTemperature(appContext)
                if (currentTemp < 40f) {
                    Log.i(TAG, "Smart Charging Filter: Muting magnetic alert $event during safe charging ($currentTemp°C)")
                    val result = InaleDecisionResult(
                        data = data,
                        shouldNotify = false,
                        shouldAnnounce = false,
                        assignedPriority = basePriority,
                        auditReason = "SUPPRESSED_SMART_CHARGING_FILTER",
                        severityLabel = getSeverityLabel(basePriority),
                        severityColorHex = getSeverityColorHex(basePriority)
                    )
                    dispatchAndAudit(appContext, result)
                    return result
                }
            }
        }

        // --- STEP 8: Duplicate Check & Debounce Window (3 seconds) ---
        val signature = "${event.name}:${data.title}:${data.details}"
        val lastSeen = debounceCache[signature] ?: 0L
        if (now - lastSeen < 3000L) {
            Log.d(TAG, "Debounce window caught duplicate event: $signature")
            val result = InaleDecisionResult(
                data = data,
                shouldNotify = false,
                shouldAnnounce = false,
                assignedPriority = basePriority,
                auditReason = "SUPPRESSED_DUPLICATE",
                severityLabel = getSeverityLabel(basePriority),
                severityColorHex = getSeverityColorHex(basePriority)
            )
            dispatchAndAudit(appContext, result)
            return result
        }
        debounceCache[signature] = now

        // --- STEP 9: Dynamic Escalation & Cooldown Manager ---
        var isEscalated = false
        val extractedMetric = extractNumericMetric(data.details)
        val previousMetric = lastEventMetrics[event]

        if (isDynamicEscalationEnabled && extractedMetric != null && previousMetric != null) {
            // Check if metric escalated significantly (e.g. Temp rose >= 2°C or battery level dropped >= 5%)
            if (event.name.contains("TEMP") && extractedMetric - previousMetric >= 2.0f) {
                basePriority = EventPriority.CRITICAL
                isEscalated = true
                Log.w(TAG, "Dynamic Escalation triggered for $event: $previousMetric°C -> $extractedMetric°C")
            } else if (event.name.contains("BATTERY") && previousMetric - extractedMetric >= 5.0f) {
                basePriority = EventPriority.CRITICAL
                isEscalated = true
            }
        }
        if (extractedMetric != null) {
            lastEventMetrics[event] = extractedMetric
        }

        val inNotifCD = CooldownManager.isNotificationInCooldown(event, basePriority)
        val inAnnCD = CooldownManager.isAnnouncementInCooldown(event, basePriority)

        if (!isEscalated) {
            if (inNotifCD) userNotifPref = false
            if (inAnnCD) userAnnPref = false
        }

        // --- STEP 10: Notification Rate Limiter (Information events) ---
        if (isRateLimitingEnabled && basePriority == EventPriority.INFORMATION) {
            synchronized(infoRateLimitWindow) {
                infoRateLimitWindow.removeAll { now - it > 30_000L }
                if (infoRateLimitWindow.size >= 3) {
                    Log.w(TAG, "Information Rate Limiter active (3 alerts in 30s). Muting status notification $event")
                    userNotifPref = false
                } else if (userNotifPref) {
                    infoRateLimitWindow.add(now)
                }
            }
        }

        val finalAuditReason = when {
            isEscalated -> "DYNAMICALLY_ESCALATED"
            userNotifPref || userAnnPref -> "DELIVERED"
            inNotifCD && inAnnCD -> "SUPPRESSED_COOLDOWN"
            else -> "SUPPRESSED_POLICY"
        }

        val result = InaleDecisionResult(
            data = data,
            shouldNotify = userNotifPref,
            shouldAnnounce = userAnnPref,
            assignedPriority = basePriority,
            auditReason = finalAuditReason,
            severityLabel = getSeverityLabel(basePriority),
            severityColorHex = getSeverityColorHex(basePriority)
        )

        dispatchAndAudit(appContext, result)
        return result
    }

    private fun dispatchAndAudit(context: Context, result: InaleDecisionResult) {
        // Dispatch system notification & speech announcement
        NotificationDispatcher.dispatch(
            context = context,
            data = result.data,
            shouldNotify = result.shouldNotify,
            shouldAnnounce = result.shouldAnnounce,
            priority = result.assignedPriority
        )

        // Mandatory Diagnostic Audit Log
        try {
            LoggingManager.logEvent(
                context = context,
                category = "INALE_AUDIT",
                title = "INALE Decision: ${result.data.event.name}",
                details = "Decision=${result.auditReason}, Notify=${result.shouldNotify}, Announce=${result.shouldAnnounce}, Priority=${result.assignedPriority.name}",
                source = "INALE_Engine",
                eventType = "AUDIT",
                status = result.auditReason
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing INALE audit log", e)
        }
    }

    private fun isDeviceCharging(context: Context): Boolean {
        return try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    private fun getBatteryTemperature(context: Context): Float {
        return try {
            val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temp / 10f
        } catch (e: Exception) {
            25f
        }
    }

    private fun extractNumericMetric(text: String): Float? {
        return try {
            val regex = "(\\d+(\\.\\d+)?)".toRegex()
            regex.find(text)?.value?.toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun getSeverityLabel(priority: EventPriority): String {
        return when (priority) {
            EventPriority.EMERGENCY -> "EMERGENCY 🚨"
            EventPriority.CRITICAL -> "CRITICAL 🔴"
            EventPriority.WARNING -> "WARNING 🟡"
            EventPriority.INFORMATION -> "INFORMATION 🟢"
            EventPriority.BACKGROUND -> "BACKGROUND ⚪"
        }
    }

    private fun getSeverityColorHex(priority: EventPriority): String {
        return when (priority) {
            EventPriority.EMERGENCY -> "#F44336"
            EventPriority.CRITICAL -> "#E91E63"
            EventPriority.WARNING -> "#FF9800"
            EventPriority.INFORMATION -> "#4CAF50"
            EventPriority.BACKGROUND -> "#9E9E9E"
        }
    }

    fun getModuleState(moduleName: String, context: Context): InaleModuleState {
        val appContext = context.applicationContext
        return when (moduleName) {
            "Driving Mode" -> {
                InaleModuleState(
                    configured = isDrivingMode,
                    runtimeState = if (isDrivingMode) "RUNNING" else "IDLE",
                    reason = if (isDrivingMode) "Actively filtering non-critical auditory alerts during transit" else "Driving Mode is not active"
                )
            }
            "Quiet Hours" -> {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val inQuietHours = if (quietHoursStartHour > quietHoursEndHour) {
                    currentHour >= quietHoursStartHour || currentHour < quietHoursEndHour
                } else {
                    currentHour in quietHoursStartHour until quietHoursEndHour
                }
                InaleModuleState(
                    configured = isQuietHoursEnabled,
                    runtimeState = if (!isQuietHoursEnabled) "IDLE" else if (inQuietHours) "RUNNING" else "IDLE",
                    reason = if (!isQuietHoursEnabled) "Quiet Hours filter is disabled" else if (inQuietHours) "Quiet hours active. Muting non-critical alerts." else "Outside scheduled quiet hours ($quietHoursStartHour:00 - 0$quietHoursEndHour:00)"
                )
            }
            "Smart Charging" -> {
                val hasMagnetometer = appContext.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_SENSOR_COMPASS)
                if (!hasMagnetometer) {
                    return InaleModuleState(
                        configured = isSmartChargingFilterEnabled,
                        runtimeState = "UNSUPPORTED",
                        reason = "Required magnetic field/compass capability unavailable on this device"
                    )
                }
                val charging = isDeviceCharging(appContext)
                InaleModuleState(
                    configured = isSmartChargingFilterEnabled,
                    runtimeState = if (!isSmartChargingFilterEnabled) "IDLE" else if (charging) "RUNNING" else "BLOCKED",
                    reason = if (!isSmartChargingFilterEnabled) "Smart Charging Filter is disabled" else if (charging) "Actively filtering magnetic spikes during charge cycle" else "Device is not currently connected to a power source"
                )
            }
            "Dynamic Escalation" -> {
                InaleModuleState(
                    configured = isDynamicEscalationEnabled,
                    runtimeState = if (isDynamicEscalationEnabled) "RUNNING" else "IDLE",
                    reason = if (isDynamicEscalationEnabled) "Monitoring warning metrics for real-time deterioration" else "Escalation engine is disabled"
                )
            }
            "Rate Limiter" -> {
                val now = System.currentTimeMillis()
                var isLimiting = false
                synchronized(infoRateLimitWindow) {
                    infoRateLimitWindow.removeAll { now - it > 30_000L }
                    if (infoRateLimitWindow.size >= 3) {
                        isLimiting = true
                    }
                }
                InaleModuleState(
                    configured = isRateLimitingEnabled,
                    runtimeState = if (!isRateLimitingEnabled) "IDLE" else if (isLimiting) "BLOCKED" else "RUNNING",
                    reason = if (!isRateLimitingEnabled) "Rate limiter is disabled" else if (isLimiting) "Rate limit threshold reached (3 alerts in 30s). Suppressing status floods." else "Monitoring alert cadence to prevent status flooding"
                )
            }
            else -> InaleModuleState(false, "IDLE", "Unknown module")
        }
    }
}

data class InaleModuleState(
    val configured: Boolean,
    val runtimeState: String, // RUNNING / IDLE / BLOCKED / UNSUPPORTED / ERROR
    val reason: String
)

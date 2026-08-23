package com.example.engines.iepde

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.modules.CapabilityDetector
import com.example.engines.notification.modules.PermissionManager
import com.example.engines.notification.modules.SafetyOverrideManager
import com.example.engines.service.ServiceControlEngine

object IepdeValidator {
    private const val TAG = "IEPDE_Validator"

    /**
     * Map EventCategory/NotificationEvent to parent Service ID in ServiceControlEngine.
     */
    private fun getServiceIdForEvent(event: NotificationEvent?): String? {
        if (event == null) return null
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

            else -> null
        }
    }

    /**
     * Complete Intelligent Validation Pipeline
     */
    fun validate(context: Context, event: IepdeEvent): ValidationResult {
        val now = System.currentTimeMillis()
        val notifEvent = event.originalNotificationEvent

        // --- STEP 1: Safety Override Check ---
        if (notifEvent != null && SafetyOverrideManager.isSafetyOverride(notifEvent)) {
            return ValidationResult(
                isValid = true,
                reason = "SAFETY_OVERRIDE_GRANTED",
                action = "IMMEDIATE_DELIVERY"
            )
        }

        // --- STEP 2: Schema & Mandatory Fields Check ---
        if (event.eventId.isBlank() || event.eventType.isBlank() || event.source.isBlank() || event.title.isBlank()) {
            Log.w(TAG, "Schema Validation failed for event ID: ${event.eventId}")
            return ValidationResult(isValid = false, reason = "FAILED_SCHEMA_INVALID_FIELDS", action = "DISCARD")
        }

        // --- STEP 3: Event Integrity Checksum Verification ---
        val expectedChecksum = (event.eventId + event.eventType + event.source + event.timestamp).hashCode()
        if (event.checksum != expectedChecksum) {
            Log.e(TAG, "Integrity Check failed for event ${event.eventId}: Checksum mismatch.")
            return ValidationResult(isValid = false, reason = "FAILED_INTEGRITY_CORRUPTED", action = "DISCARD")
        }

        // --- STEP 4: Timestamp & Freshness Check ---
        if (event.timestamp > now + 60_000L) {
            Log.w(TAG, "Future timestamp detected (${event.timestamp} > $now) for event ${event.eventId}")
            return ValidationResult(isValid = false, reason = "FAILED_TIMESTAMP_FUTURE", action = "DISCARD")
        }
        if (now > event.expiryTime) {
            Log.w(TAG, "Expired event detected (Now $now > Expiry ${event.expiryTime}) for ${event.eventId}")
            return ValidationResult(isValid = false, reason = "FAILED_TIMESTAMP_EXPIRED", action = "DISCARD")
        }

        // --- STEP 5: Service Control Engine Status Check ---
        val serviceId = getServiceIdForEvent(notifEvent)
        if (serviceId != null && !ServiceControlEngine.isServiceEnabled(serviceId)) {
            Log.d(TAG, "Event ${event.eventType} suppressed: Parent Service [$serviceId] is DISABLED.")
            return ValidationResult(isValid = false, reason = "SUPPRESSED_SERVICE_DISABLED", action = "DISCARD")
        }

        // --- STEP 6: Capability & Permission Check ---
        if (notifEvent != null) {
            if (!CapabilityDetector.isCapabilitySupported(context, notifEvent)) {
                return ValidationResult(isValid = false, reason = "SUPPRESSED_UNSUPPORTED_HARDWARE", action = "DISCARD")
            }
            if (!PermissionManager.isPermissionGrantedForEvent(context, notifEvent)) {
                return ValidationResult(isValid = false, reason = "SUPPRESSED_MISSING_PERMISSION", action = "DISCARD")
            }
        }

        // --- STEP 7: Sensor Confidence Verification ---
        if (event.confidenceScore < 0.5f) {
            Log.w(TAG, "Low confidence score (${event.confidenceScore}) for ${event.eventType}")
            return ValidationResult(isValid = false, reason = "SUPPRESSED_LOW_CONFIDENCE", action = "DISCARD")
        }

        // --- STEP 8: State Dependency Verification ---
        if (notifEvent == NotificationEvent.BATTERY_FULL || notifEvent == NotificationEvent.OVERCHARGE_STARTED) {
            if (!isDeviceCharging(context)) {
                Log.w(TAG, "State Dependency Failed: $notifEvent received while device is NOT charging.")
                return ValidationResult(isValid = false, reason = "FAILED_STATE_DEPENDENCY_NOT_CHARGING", action = "DISCARD")
            }
        }

        return ValidationResult(
            isValid = true,
            reason = "VALIDATION_SUCCESS",
            action = "PROCEED"
        )
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
}

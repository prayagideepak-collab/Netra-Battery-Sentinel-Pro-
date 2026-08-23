package com.example.engines.notification.modules

import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEvent

object PriorityManager {

    fun getEventPriority(event: NotificationEvent, override: EventPriority? = null): EventPriority {
        if (override != null) return override

        return when (event) {
            NotificationEvent.BATTERY_CRITICAL_FAILURE,
            NotificationEvent.TEMPERATURE_EMERGENCY,
            NotificationEvent.EXTERNAL_HEAT_SOURCE,
            NotificationEvent.FIRE_RISK,
            NotificationEvent.BATTERY_TEMP_OVER_43,
            NotificationEvent.SYSTEM_EMERGENCY,
            NotificationEvent.HARDWARE_PROTECTION_ALERTS -> EventPriority.EMERGENCY

            NotificationEvent.TEMPERATURE_CRITICAL,
            NotificationEvent.MAGNETIC_EMERGENCY,
            NotificationEvent.WEATHER_EXTREME,
            NotificationEvent.DEVICE_BATTERY_CRITICAL,
            NotificationEvent.CRITICAL_BATTERY_ALERTS -> EventPriority.CRITICAL

            NotificationEvent.TEMPERATURE_WARNING,
            NotificationEvent.MAGNETIC_CRITICAL,
            NotificationEvent.MAGNETIC_WARNING,
            NotificationEvent.STRONG_MAGNETIC_FIELD,
            NotificationEvent.CONTINUOUS_MAGNETIC_EXPOSURE,
            NotificationEvent.WEATHER_GOVERNMENT,
            NotificationEvent.HEATWAVE,
            NotificationEvent.THUNDERSTORM,
            NotificationEvent.HIGH_WIND,
            NotificationEvent.OVERCHARGE_STARTED,
            NotificationEvent.OVERCHARGE_REMINDER,
            NotificationEvent.LOW_BATTERY_ALERTS,
            NotificationEvent.SLOW_CHARGING_DETECTED,
            NotificationEvent.CHARGING_INTERRUPTED,
            NotificationEvent.DEVICE_BATTERY_LOW,
            NotificationEvent.BLUETOOTH_LOW_BATTERY,
            NotificationEvent.DATABASE_REPAIR -> EventPriority.WARNING

            NotificationEvent.BATTERY_PERCENTAGE,
            NotificationEvent.BATTERY_FULL,
            NotificationEvent.BATTERY_HEALTH_UPDATES,
            NotificationEvent.BATTERY_STATUS_CHANGES,
            NotificationEvent.CHARGER_CONNECTED,
            NotificationEvent.CHARGER_DISCONNECTED,
            NotificationEvent.CHARGING_TYPE_CHANGED,
            NotificationEvent.FAST_CHARGING_DETECTED,
            NotificationEvent.DEVICE_CONNECTED,
            NotificationEvent.DEVICE_DISCONNECTED,
            NotificationEvent.BLUETOOTH_CONNECTED,
            NotificationEvent.BLUETOOTH_DISCONNECTED,
            NotificationEvent.HEAVY_RAIN,
            NotificationEvent.DENSE_FOG,
            NotificationEvent.SYSTEM_BACKUP_COMPLETE,
            NotificationEvent.SYSTEM_EXPORT_COMPLETE,
            NotificationEvent.SYSTEM_RESTORE_COMPLETE,
            NotificationEvent.SYSTEM_UPDATE_INSTALLED,
            NotificationEvent.HEALTH_MONITOR_MESSAGES -> EventPriority.INFORMATION

            NotificationEvent.TEMPERATURE_NORMAL,
            NotificationEvent.MAGNETIC_NORMAL -> EventPriority.BACKGROUND

            else -> EventPriority.INFORMATION
        }
    }

    fun isHigherOrEqualPriority(p1: EventPriority, p2: EventPriority): Boolean {
        return p1.value >= p2.value
    }
}

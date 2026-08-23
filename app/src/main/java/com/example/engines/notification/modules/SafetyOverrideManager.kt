package com.example.engines.notification.modules

import com.example.engines.notification.NotificationEvent

object SafetyOverrideManager {
    private val safetyEvents = setOf(
        NotificationEvent.BATTERY_TEMP_OVER_43,
        NotificationEvent.EXTERNAL_HEAT_SOURCE,
        NotificationEvent.FIRE_RISK,
        NotificationEvent.BATTERY_CRITICAL_FAILURE,
        NotificationEvent.SYSTEM_EMERGENCY,
        NotificationEvent.HARDWARE_PROTECTION_ALERTS,
        NotificationEvent.TEMPERATURE_EMERGENCY,
        NotificationEvent.MAGNETIC_EMERGENCY,
        NotificationEvent.WEATHER_EXTREME
    )

    fun isSafetyOverride(event: NotificationEvent): Boolean {
        return safetyEvents.contains(event)
    }

    fun isLocked(event: NotificationEvent): Boolean {
        return isSafetyOverride(event)
    }
}

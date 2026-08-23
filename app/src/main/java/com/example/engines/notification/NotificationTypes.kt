package com.example.engines.notification

enum class NotificationCategory {
    BATTERY, CHARGING, TEMPERATURE, MAGNETIC, BLUETOOTH, WEATHER, SYSTEM, SAFETY
}

enum class EventPriority(val value: Int) {
    BACKGROUND(1),
    INFORMATION(2),
    WARNING(3),
    CRITICAL(4),
    EMERGENCY(5)
}

enum class NotificationEvent {
    // Battery
    BATTERY_PERCENTAGE,
    LOW_BATTERY_ALERTS,
    CRITICAL_BATTERY_ALERTS,
    BATTERY_FULL,
    OVERCHARGE_STARTED,
    OVERCHARGE_REMINDER,
    BATTERY_HEALTH_UPDATES,
    BATTERY_STATUS_CHANGES,

    // Charging
    CHARGER_CONNECTED,
    CHARGER_DISCONNECTED,
    CHARGING_TYPE_CHANGED,
    FAST_CHARGING_DETECTED,
    NORMAL_CHARGING_DETECTED,
    DATA_TRANSFER_CHARGING_DETECTED,
    SLOW_CHARGING_DETECTED,
    CHARGING_INTERRUPTED,

    // Temperature
    TEMPERATURE_NORMAL,
    TEMPERATURE_WARNING,
    TEMPERATURE_CRITICAL,
    TEMPERATURE_EMERGENCY,

    // Magnetic
    MAGNETIC_NORMAL,
    MAGNETIC_WARNING,
    STRONG_MAGNETIC_FIELD,
    CONTINUOUS_MAGNETIC_EXPOSURE,
    MAGNETIC_CRITICAL,
    MAGNETIC_EMERGENCY,

    // Bluetooth
    DEVICE_CONNECTED,
    DEVICE_DISCONNECTED,
    DEVICE_BATTERY_LOW,
    DEVICE_BATTERY_CRITICAL,
    BLUETOOTH_CONNECTED,
    BLUETOOTH_DISCONNECTED,
    BLUETOOTH_LOW_BATTERY,

    // Weather
    WEATHER_GOVERNMENT,
    HEATWAVE,
    THUNDERSTORM,
    HEAVY_RAIN,
    HIGH_WIND,
    DENSE_FOG,
    WEATHER_EXTREME,

    // System
    SYSTEM_BACKUP_COMPLETE,
    SYSTEM_EXPORT_COMPLETE,
    SYSTEM_RESTORE_COMPLETE,
    SYSTEM_UPDATE_INSTALLED,
    DATABASE_REPAIR,
    HEALTH_MONITOR_MESSAGES,

    // Safety (Locked Alerts)
    BATTERY_TEMP_OVER_43,
    EXTERNAL_HEAT_SOURCE,
    FIRE_RISK,
    BATTERY_CRITICAL_FAILURE,
    SYSTEM_EMERGENCY,
    HARDWARE_PROTECTION_ALERTS
}

data class NotificationPreference(
    val category: NotificationCategory,
    val event: NotificationEvent,
    var notificationEnabled: Boolean = true,
    var announcementEnabled: Boolean = true,
    val isLocked: Boolean = false,
    val defaultPriority: EventPriority = EventPriority.INFORMATION
)

data class NotificationEventData(
    val event: NotificationEvent,
    val title: String,
    val details: String,
    val iconResId: Int = android.R.drawable.ic_dialog_info,
    val notificationId: Int = System.currentTimeMillis().toInt(),
    val source: String = "NPE",
    val overridePriority: EventPriority? = null,
    val timestamp: Long = System.currentTimeMillis()
)

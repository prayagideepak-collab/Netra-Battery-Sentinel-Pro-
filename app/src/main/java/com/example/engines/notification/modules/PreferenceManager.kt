package com.example.engines.notification.modules

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationCategory
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.NotificationPreference
import java.util.concurrent.ConcurrentHashMap

object PreferenceManager {
    private const val TAG = "NPE_PreferenceManager"
    private const val PREF_NAME = "npe_notification_preferences"

    private val preferencesMap = ConcurrentHashMap<NotificationEvent, NotificationPreference>()
    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadDefaults()
        loadSavedPreferences()
        Log.i(TAG, "PreferenceManager initialized with ${preferencesMap.size} preferences.")
    }

    private fun loadDefaults() {
        preferencesMap.clear()

        // Battery
        addDefault(NotificationCategory.BATTERY, NotificationEvent.BATTERY_PERCENTAGE, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.BATTERY, NotificationEvent.LOW_BATTERY_ALERTS, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.BATTERY, NotificationEvent.CRITICAL_BATTERY_ALERTS, notif = true, ann = true, locked = false, priority = EventPriority.CRITICAL)
        addDefault(NotificationCategory.BATTERY, NotificationEvent.BATTERY_FULL, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.BATTERY, NotificationEvent.OVERCHARGE_STARTED, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.BATTERY, NotificationEvent.OVERCHARGE_REMINDER, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.BATTERY, NotificationEvent.BATTERY_HEALTH_UPDATES, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.BATTERY, NotificationEvent.BATTERY_STATUS_CHANGES, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)

        // Charging
        addDefault(NotificationCategory.CHARGING, NotificationEvent.CHARGER_CONNECTED, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.CHARGING, NotificationEvent.CHARGER_DISCONNECTED, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.CHARGING, NotificationEvent.CHARGING_TYPE_CHANGED, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.CHARGING, NotificationEvent.FAST_CHARGING_DETECTED, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.CHARGING, NotificationEvent.NORMAL_CHARGING_DETECTED, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.CHARGING, NotificationEvent.DATA_TRANSFER_CHARGING_DETECTED, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.CHARGING, NotificationEvent.SLOW_CHARGING_DETECTED, notif = true, ann = false, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.CHARGING, NotificationEvent.CHARGING_INTERRUPTED, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)

        // Temperature
        addDefault(NotificationCategory.TEMPERATURE, NotificationEvent.TEMPERATURE_NORMAL, notif = false, ann = false, locked = false, priority = EventPriority.BACKGROUND)
        addDefault(NotificationCategory.TEMPERATURE, NotificationEvent.TEMPERATURE_WARNING, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.TEMPERATURE, NotificationEvent.TEMPERATURE_CRITICAL, notif = true, ann = true, locked = true, priority = EventPriority.CRITICAL)
        addDefault(NotificationCategory.TEMPERATURE, NotificationEvent.TEMPERATURE_EMERGENCY, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)

        // Magnetic
        addDefault(NotificationCategory.MAGNETIC, NotificationEvent.MAGNETIC_NORMAL, notif = false, ann = false, locked = false, priority = EventPriority.BACKGROUND)
        addDefault(NotificationCategory.MAGNETIC, NotificationEvent.MAGNETIC_WARNING, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.MAGNETIC, NotificationEvent.STRONG_MAGNETIC_FIELD, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.MAGNETIC, NotificationEvent.CONTINUOUS_MAGNETIC_EXPOSURE, notif = true, ann = false, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.MAGNETIC, NotificationEvent.MAGNETIC_CRITICAL, notif = true, ann = true, locked = false, priority = EventPriority.CRITICAL)
        addDefault(NotificationCategory.MAGNETIC, NotificationEvent.MAGNETIC_EMERGENCY, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)

        // Bluetooth
        addDefault(NotificationCategory.BLUETOOTH, NotificationEvent.DEVICE_CONNECTED, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.BLUETOOTH, NotificationEvent.DEVICE_DISCONNECTED, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.BLUETOOTH, NotificationEvent.DEVICE_BATTERY_LOW, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.BLUETOOTH, NotificationEvent.DEVICE_BATTERY_CRITICAL, notif = true, ann = true, locked = false, priority = EventPriority.CRITICAL)
        addDefault(NotificationCategory.BLUETOOTH, NotificationEvent.BLUETOOTH_CONNECTED, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.BLUETOOTH, NotificationEvent.BLUETOOTH_DISCONNECTED, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.BLUETOOTH, NotificationEvent.BLUETOOTH_LOW_BATTERY, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)

        // Weather
        addDefault(NotificationCategory.WEATHER, NotificationEvent.WEATHER_GOVERNMENT, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.WEATHER, NotificationEvent.HEATWAVE, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.WEATHER, NotificationEvent.THUNDERSTORM, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.WEATHER, NotificationEvent.HEAVY_RAIN, notif = true, ann = true, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.WEATHER, NotificationEvent.HIGH_WIND, notif = true, ann = true, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.WEATHER, NotificationEvent.DENSE_FOG, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.WEATHER, NotificationEvent.WEATHER_EXTREME, notif = true, ann = true, locked = true, priority = EventPriority.CRITICAL)

        // System
        addDefault(NotificationCategory.SYSTEM, NotificationEvent.SYSTEM_BACKUP_COMPLETE, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.SYSTEM, NotificationEvent.SYSTEM_EXPORT_COMPLETE, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.SYSTEM, NotificationEvent.SYSTEM_RESTORE_COMPLETE, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.SYSTEM, NotificationEvent.SYSTEM_UPDATE_INSTALLED, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)
        addDefault(NotificationCategory.SYSTEM, NotificationEvent.DATABASE_REPAIR, notif = true, ann = false, locked = false, priority = EventPriority.WARNING)
        addDefault(NotificationCategory.SYSTEM, NotificationEvent.HEALTH_MONITOR_MESSAGES, notif = true, ann = false, locked = false, priority = EventPriority.INFORMATION)

        // Safety (Locked)
        addDefault(NotificationCategory.SAFETY, NotificationEvent.BATTERY_TEMP_OVER_43, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)
        addDefault(NotificationCategory.SAFETY, NotificationEvent.EXTERNAL_HEAT_SOURCE, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)
        addDefault(NotificationCategory.SAFETY, NotificationEvent.FIRE_RISK, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)
        addDefault(NotificationCategory.SAFETY, NotificationEvent.BATTERY_CRITICAL_FAILURE, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)
        addDefault(NotificationCategory.SAFETY, NotificationEvent.SYSTEM_EMERGENCY, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)
        addDefault(NotificationCategory.SAFETY, NotificationEvent.HARDWARE_PROTECTION_ALERTS, notif = true, ann = true, locked = true, priority = EventPriority.EMERGENCY)
    }

    private fun addDefault(
        category: NotificationCategory,
        event: NotificationEvent,
        notif: Boolean,
        ann: Boolean,
        locked: Boolean,
        priority: EventPriority
    ) {
        preferencesMap[event] = NotificationPreference(
            category = category,
            event = event,
            notificationEnabled = notif,
            announcementEnabled = ann,
            isLocked = locked,
            defaultPriority = priority
        )
    }

    private fun loadSavedPreferences() {
        val shared = prefs ?: return
        preferencesMap.values.forEach { pref ->
            if (!pref.isLocked) {
                val notifKey = "notif_${pref.event.name}"
                val annKey = "ann_${pref.event.name}"
                if (shared.contains(notifKey)) {
                    pref.notificationEnabled = shared.getBoolean(notifKey, pref.notificationEnabled)
                }
                if (shared.contains(annKey)) {
                    pref.announcementEnabled = shared.getBoolean(annKey, pref.announcementEnabled)
                }
            }
        }
    }

    fun getPreference(event: NotificationEvent): NotificationPreference? {
        return preferencesMap[event]
    }

    fun getAllPreferences(): List<NotificationPreference> {
        return preferencesMap.values.toList()
    }

    fun updatePreference(event: NotificationEvent, notifEnabled: Boolean, annEnabled: Boolean): Boolean {
        val pref = preferencesMap[event] ?: return false
        if (pref.isLocked) {
            Log.w(TAG, "Cannot modify locked safety preference for event: $event")
            return false
        }
        pref.notificationEnabled = notifEnabled
        pref.announcementEnabled = annEnabled

        prefs?.edit()?.apply {
            putBoolean("notif_${event.name}", notifEnabled)
            putBoolean("ann_${event.name}", annEnabled)
            apply()
        }
        Log.i(TAG, "Updated preference for $event -> Notif: $notifEnabled, Ann: $annEnabled")
        return true
    }

    fun restoreDefaults() {
        prefs?.edit()?.clear()?.apply()
        loadDefaults()
        Log.i(TAG, "Restored default preferences for NPE.")
    }
}

package com.example.battery.widget

import android.content.Context

enum class WidgetUpdateReason {
    PERIODIC,
    BATTERY_STATE_CHANGE,
    POWER_CONNECTED,
    POWER_DISCONNECTED,
    CRITICAL_STATE
}

/**
 * Centralized widget update rate-limiting and policy controller.
 */
object WidgetUpdatePolicy {
    private const val PREFS_NAME = "netra_widget_policy_prefs"
    private const val KEY_LAST_UPDATE_TIME = "last_widget_update_timestamp"
    private const val MIN_UPDATE_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes adaptive cadence

    @Synchronized
    fun shouldUpdate(
        context: Context,
        reason: WidgetUpdateReason,
        force: Boolean = false
    ): Boolean {
        if (force) return true

        // Immediate updates allowed for critical events, battery state changes, or charger plug/unplug
        when (reason) {
            WidgetUpdateReason.POWER_CONNECTED,
            WidgetUpdateReason.POWER_DISCONNECTED,
            WidgetUpdateReason.CRITICAL_STATE,
            WidgetUpdateReason.BATTERY_STATE_CHANGE -> return true
            else -> {}
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
        val now = System.currentTimeMillis()

        if (now - lastUpdate >= MIN_UPDATE_INTERVAL_MS) {
            return true
        }

        return false
    }

    @Synchronized
    fun recordUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis()).apply()
    }
}

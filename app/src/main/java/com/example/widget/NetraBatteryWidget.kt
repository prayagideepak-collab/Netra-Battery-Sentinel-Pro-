package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.service.BatteryService
import com.example.service.BatteryState
import com.example.util.TimeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Netra Battery Widget (4x4)
 * Made with ❤️ by Prayagi Ji
 */
class NetraBatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = BatteryService.liveBatteryState.value
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, state)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, state: BatteryState) {
            val views = RemoteViews(context.packageName, R.layout.large_widget_layout)

            // Percentage
            views.setTextViewText(R.id.widget_percentage, "${state.percentage}%")

            // Status Text & speed
            val statusText = if (state.isCharging) {
                "⚡ Charging (${state.chargingType})"
            } else {
                "🔋 Discharging"
            }
            views.setTextViewText(R.id.widget_status, statusText)

            // Estimated Remaining Time
            val isOvercharging = state.isCharging && state.overchargeDurationMs > 0
            val remainingMs = if (isOvercharging) {
                state.overchargeDurationMs
            } else if (state.remainingTimeMs >= 0L) {
                state.remainingTimeMs
            } else if (state.isCharging) {
                TimeManager.calculateChargingEtaMs(state.percentage, state.timeTo100Min)
            } else {
                TimeManager.calculateDischargeRemainingMs(state.percentage, state.speed)
            }
            val remainingText = when {
                remainingMs > 0L -> "⏳ ${TimeManager.formatDurationMs(remainingMs)}"
                remainingMs == 0L && state.isCharging && state.percentage >= 100 -> "⏳ Full (100%)"
                remainingMs == 0L && !state.isCharging && state.percentage <= 0 -> "⏳ Empty (0%)"
                else -> "⏳ Calculating..."
            }
            views.setTextViewText(R.id.widget_remaining_time, remainingText)


            // Health
            views.setTextViewText(R.id.widget_health, "❤️ Health: ${state.health} (${state.healthPercentage}%)")

            // Temperature
            views.setTextViewText(R.id.widget_temp, String.format(Locale.US, "🌡️ Temp: %.1f°C", state.temperature))

            // Current (mA)
            views.setTextViewText(R.id.widget_current, "🔌 Current: ${state.currentNow} mA")

            // Voltage (V)
            views.setTextViewText(R.id.widget_voltage, String.format(Locale.US, "⚡ Volt: %.2f V", state.voltage / 1000f))

            // Power (W)
            views.setTextViewText(R.id.widget_power, String.format(Locale.US, "⚡ Power: %.1f W", state.powerWatt))

            // Magnetic Status
            val magText = String.format(
                Locale.US,
                "🧲 Mag Zone: %s (%.1f uT)",
                state.magneticSafetyZone,
                state.magneticFieldMagnitude
            )
            views.setTextViewText(R.id.widget_magnetic_field, magText)

            // Timestamps: Local IST & Sync time
            val istFormat = SimpleDateFormat("HH:mm 'IST'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            val syncFormat = SimpleDateFormat("HH:mm", Locale.US)
            val now = Date()

            views.setTextViewText(R.id.widget_time, "🕒 ${istFormat.format(now)}")
            views.setTextViewText(R.id.widget_last_updated, "🔄 Sync: ${syncFormat.format(now)}")

            // Setup click to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("nav_to", "dashboard")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1002, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

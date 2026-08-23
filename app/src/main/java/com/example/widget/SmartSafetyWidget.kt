package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.service.BatteryService
import com.example.service.BatteryState
import java.util.Locale

class SmartSafetyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = BatteryService.liveBatteryState.value
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, state)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, state: BatteryState) {
            val views = RemoteViews(context.packageName, R.layout.smart_safety_widget_layout)

            // Percentage
            views.setTextViewText(R.id.widget_percentage, "${state.percentage}%")

            // Temperature
            views.setTextViewText(R.id.widget_temp, String.format(Locale.US, "🌡️ %.1f°C", state.temperature))

            // Safety Status Color Classification
            val (statusText, statusColor) = when {
                state.temperature < 38f -> "🟢 Safe" to Color.parseColor("#4CAF50")
                state.temperature < 42f -> "🟡 Warm" to Color.parseColor("#FFEB3B")
                state.temperature < 45f -> "🟠 High" to Color.parseColor("#FF9800")
                else -> "🔴 Critical" to Color.parseColor("#F44336")
            }
            views.setTextViewText(R.id.widget_safety_status, statusText)
            views.setTextColor(R.id.widget_safety_status, statusColor)

            // Health
            views.setTextViewText(R.id.widget_health, "❤️ Health: ${state.health} (${state.healthPercentage}%)")

            // Magnetic Status
            val magText = String.format(
                Locale.US,
                "🧲 Mag: %s (%.1f uT)",
                state.magneticSafetyZone,
                state.magneticFieldMagnitude
            )
            views.setTextViewText(R.id.widget_magnetic_status, magText)

            // Setup click to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("nav_to", "dashboard")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1003, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

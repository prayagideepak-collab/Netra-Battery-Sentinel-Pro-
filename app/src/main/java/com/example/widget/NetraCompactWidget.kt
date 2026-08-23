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

/**
 * Netra Compact Widget (2x2)
 * Made with ❤️ by Prayagi Ji
 */
class NetraCompactWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = BatteryService.liveBatteryState.value
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, state)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, state: BatteryState) {
            val views = RemoteViews(context.packageName, R.layout.small_widget_layout)
            
            // Percentage
            views.setTextViewText(R.id.widget_percentage, "${state.percentage}%")
            
            // Status Icon & Text
            val (icon, statusText) = if (state.isCharging) {
                "⚡" to "Charging"
            } else {
                "🔋" to "Discharging"
            }
            views.setTextViewText(R.id.widget_icon, icon)
            views.setTextViewText(R.id.widget_status, statusText)

            // Setup click to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("nav_to", "dashboard")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1001, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_content, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

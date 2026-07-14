package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.service.BatteryService
import com.example.service.BatteryState
import java.util.Locale

class AmpereFlowWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = BatteryService.liveBatteryState.value
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, state)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context, state: BatteryState) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // 1. Medium Widget (AmpereFlowWidget)
            try {
                val mediumWidget = ComponentName(context, AmpereFlowWidget::class.java)
                val mediumIds = appWidgetManager.getAppWidgetIds(mediumWidget)
                mediumIds.forEach { id ->
                    updateAppWidget(context, appWidgetManager, id, state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Small Widget
            try {
                val smallWidget = ComponentName(context, SmallWidget::class.java)
                val smallIds = appWidgetManager.getAppWidgetIds(smallWidget)
                smallIds.forEach { id ->
                    SmallWidget.updateAppWidget(context, appWidgetManager, id, state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Large Widget
            try {
                val largeWidget = ComponentName(context, LargeWidget::class.java)
                val largeIds = appWidgetManager.getAppWidgetIds(largeWidget)
                largeIds.forEach { id ->
                    LargeWidget.updateAppWidget(context, appWidgetManager, id, state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. Smart Safety Widget
            try {
                val safetyWidget = ComponentName(context, SmartSafetyWidget::class.java)
                val safetyIds = appWidgetManager.getAppWidgetIds(safetyWidget)
                safetyIds.forEach { id ->
                    SmartSafetyWidget.updateAppWidget(context, appWidgetManager, id, state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            state: BatteryState
        ) {
            val views = RemoteViews(context.packageName, R.layout.ampere_flow_widget_layout)

            // Percentage
            views.setTextViewText(R.id.widget_percentage, "${state.percentage}%")

            // Status Text
            val statusText = if (state.isCharging) {
                "⚡ Charging (${state.chargingType})"
            } else {
                "🔋 Discharging"
            }
            views.setTextViewText(R.id.widget_status, statusText)

            // Health
            views.setTextViewText(R.id.widget_health, "❤️ Health: ${state.health}")

            // Details Text
            val detailsText = String.format(
                Locale.US,
                "🌡️ Temp: %.1f°C",
                state.temperature
            )
            views.setTextViewText(R.id.widget_details, detailsText)

            // Setup click to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("nav_to", "dashboard")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 1000, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

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
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

import com.example.battery.widget.WidgetUpdatePolicy
import com.example.battery.widget.WidgetUpdateReason

/**
 * Netra Smart Widget (4x2)
 * Made with ❤️ by Prayagi Ji
 * Central master update coordinator for all Netra widgets.
 */
class NetraSmartWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = BatteryService.liveBatteryState.value
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId, state)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UPDATE_WIDGETS) {
            val state = BatteryService.liveBatteryState.value
            val isChargingChange = intent.getBooleanExtra("is_charging_changed", false)
            val reason = if (isChargingChange) WidgetUpdateReason.POWER_CONNECTED else WidgetUpdateReason.BATTERY_STATE_CHANGE
            updateAllWidgets(context, state, force = false, reason = reason)
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_UPDATE_WIDGETS = "com.example.widget.ACTION_UPDATE_WIDGETS"

        fun updateAllWidgets(
            context: Context,
            state: BatteryState,
            force: Boolean = false,
            reason: WidgetUpdateReason = WidgetUpdateReason.PERIODIC
        ) {
            if (!WidgetUpdatePolicy.shouldUpdate(context, reason, force)) {
                return
            }
            WidgetUpdatePolicy.recordUpdate(context)

            val appWidgetManager = AppWidgetManager.getInstance(context)

            // 1. Smart Widget (NetraSmartWidget)
            try {
                val smartWidget = ComponentName(context, NetraSmartWidget::class.java)
                val smartIds = appWidgetManager.getAppWidgetIds(smartWidget)
                smartIds.forEach { id ->
                    updateAppWidget(context, appWidgetManager, id, state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Compact Widget
            try {
                val compactWidget = ComponentName(context, NetraCompactWidget::class.java)
                val compactIds = appWidgetManager.getAppWidgetIds(compactWidget)
                compactIds.forEach { id ->
                    NetraCompactWidget.updateAppWidget(context, appWidgetManager, id, state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Battery Widget
            try {
                val batteryWidget = ComponentName(context, NetraBatteryWidget::class.java)
                val batteryIds = appWidgetManager.getAppWidgetIds(batteryWidget)
                batteryIds.forEach { id ->
                    NetraBatteryWidget.updateAppWidget(context, appWidgetManager, id, state)
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

            // 5. Jetpack Glance Battery Widget
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    BatteryGlanceWidget().updateAll(context)
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
            val statusText = if (state.isCharging && state.overchargeDurationMs > 0) {
                 "⚡ Overcharged: ${com.example.util.TimeManager.formatDurationMs(state.overchargeDurationMs)}"
            } else if (state.isCharging) {
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

            // Magnetic Status
            val magText = String.format(
                Locale.US,
                "🧲 Mag: %s (%.1f uT)",
                state.magneticSafetyZone,
                state.magneticFieldMagnitude
            )
            views.setTextViewText(R.id.widget_magnetic, magText)

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

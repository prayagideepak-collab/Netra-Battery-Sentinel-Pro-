package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.service.BatteryService
import com.example.service.BatteryState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jetpack Glance AppWidget Provider for Netra Battery System.
 * Displays real-time battery percentage, charging status, health, temperature, and electric telemetry.
 * Strictly synchronized with main app BatteryService cycle and handles data unavailability gracefully.
 */
class BatteryGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                GlanceWidgetContent(context)
            }
        }
    }

    @Composable
    private fun GlanceWidgetContent(context: Context) {
        val state = BatteryService.liveBatteryState.value
        val isDataAvailable = state.percentage > 0 && state.voltage > 0

        val isCharging = state.isCharging
        val percentage = state.percentage
        val health = state.health
        val temp = state.temperature
        val voltage = state.voltage / 1000f
        val power = state.powerWatt
        val current = state.currentNow

        val displayPercentage = if (isDataAvailable) "$percentage%" else "Data unavailable"

        val statusText = if (!isDataAvailable) {
            "⚠️ Data unavailable"
        } else if (isCharging) {
            "⚡ Charging (${state.chargingType})"
        } else {
            "🔋 Discharging"
        }

        val healthTempText = if (isDataAvailable) {
            "❤️ $health (${state.healthPercentage}%) • 🌡️ ${String.format(Locale.US, "%.1f°C", temp)}"
        } else {
            "❤️ Health: Data unavailable • 🌡️ Temp: Data unavailable"
        }

        val voltageText = if (isDataAvailable && voltage > 0) {
            "⚡ ${String.format(Locale.US, "%.2fV", voltage)}"
        } else {
            "⚡ Data unavailable"
        }

        val currentText = if (isDataAvailable) {
            "🔌 ${current}mA"
        } else {
            "🔌 Data unavailable"
        }

        val powerText = if (isDataAvailable) {
            "💡 ${String.format(Locale.US, "%.1fW", power)}"
        } else {
            "💡 Data unavailable"
        }

        val primaryColor = when {
            !isDataAvailable -> Color(0xFF94A3B8)
            percentage <= 20 -> Color(0xFFFF3548)
            percentage <= 50 -> Color(0xFFFF8C00)
            percentage <= 80 -> Color(0xFF00D2FF)
            else -> Color(0xFF10B981)
        }

        val backgroundColor = Color(0xFF0B121C)
        val cardBackgroundColor = Color(0xFF162132)
        val textPrimaryColor = Color(0xFFF1F5F9)
        val textSecondaryColor = Color(0xFF94A3B8)

        val syncFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val now = Date()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(backgroundColor))
                .padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top
        ) {
            // Top Header: Title & Sync Time
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NETRA SENTINEL GLANCE",
                    style = TextStyle(
                        color = ColorProvider(primaryColor),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "Sync ${syncFormat.format(now)}",
                    style = TextStyle(
                        color = ColorProvider(textSecondaryColor),
                        fontSize = 9.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Main Metrics Card
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(cardBackgroundColor))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayPercentage,
                    style = TextStyle(
                        color = ColorProvider(textPrimaryColor),
                        fontSize = if (isDataAvailable) 26.sp else 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.width(10.dp))

                Column {
                    Text(
                        text = statusText,
                        style = TextStyle(
                            color = ColorProvider(primaryColor),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = healthTempText,
                        style = TextStyle(
                            color = ColorProvider(textSecondaryColor),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Real-time Progress Indicator
            LinearProgressIndicator(
                progress = if (isDataAvailable) (percentage / 100f).coerceIn(0f, 1f) else 0f,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                color = ColorProvider(primaryColor),
                backgroundColor = ColorProvider(Color(0xFF233248))
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Telemetry Footer
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = voltageText,
                    style = TextStyle(color = ColorProvider(textSecondaryColor), fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = currentText,
                    style = TextStyle(color = ColorProvider(textSecondaryColor), fontSize = 10.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = powerText,
                    style = TextStyle(color = ColorProvider(textSecondaryColor), fontSize = 10.sp)
                )
            }
        }
    }
}

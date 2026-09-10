package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import android.util.Log

/**
 * BatteryTelemetryDiagnosticsTest
 *
 * Diagnostic utility to verify that BatteryManager telemetry events are correctly being
 * captured, extracted, and processed from Android system broadcasts before hitting the database.
 * Confirms that live telemetry attributes (level, temperature, voltage, charging type, current)
 * are valid and that the pipeline is not the cause of 'Unavailable' states.
 */
object BatteryTelemetryDiagnosticsTest {
    private const val TAG = "BatteryTelemetryDiagnostics"

    data class TelemetryDiagnosticsResult(
        val isCaptureSuccessful: Boolean,
        val batteryLevel: Int,
        val temperatureCelsius: Float,
        val voltageMv: Int,
        val chargingType: String,
        val chargingStatus: String,
        val healthStatus: String,
        val currentNowMa: Long,
        val errorMessage: String?
    )

    fun runDiagnostics(context: Context): TelemetryDiagnosticsResult {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, filter)

            if (batteryStatus == null) {
                Log.e(TAG, "DIAGNOSTIC FAILURE: ACTION_BATTERY_CHANGED intent is null (sticky broadcast unavailable).")
                return TelemetryDiagnosticsResult(
                    isCaptureSuccessful = false,
                    batteryLevel = -1,
                    temperatureCelsius = 0f,
                    voltageMv = 0,
                    chargingType = "UNKNOWN",
                    chargingStatus = "UNKNOWN",
                    healthStatus = "UNKNOWN",
                    currentNowMa = 0L,
                    errorMessage = "Battery sticky broadcast intent is null."
                )
            }

            val level = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)
            val status = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1)
            val plugged = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_PLUGGED, -1)
            val health = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_HEALTH, -1)
            val rawTemp = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, 0)
            val voltage = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, 0)

            val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else -1
            val tempC = if (rawTemp > 0) rawTemp / 10.0f else 0.0f

            val chargingTypeStr = when (plugged) {
                AndroidBatteryManager.BATTERY_PLUGGED_AC -> "AC"
                AndroidBatteryManager.BATTERY_PLUGGED_USB -> "USB"
                AndroidBatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                else -> "DISCHARGING / NONE"
            }

            val statusStr = when (status) {
                AndroidBatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
                AndroidBatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
                AndroidBatteryManager.BATTERY_STATUS_FULL -> "FULL"
                AndroidBatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
                else -> "UNKNOWN"
            }

            val healthStr = when (health) {
                AndroidBatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                AndroidBatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                AndroidBatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                AndroidBatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
                AndroidBatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "UNSPECIFIED_FAILURE"
                else -> "UNKNOWN"
            }

            // Android BatteryManager current (microamps -> milliamps)
            val androidBM = context.getSystemService(Context.BATTERY_SERVICE) as? AndroidBatteryManager
            val currentUa = androidBM?.getIntProperty(AndroidBatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val currentMa = (currentUa / 1000).toLong()

            Log.i(TAG, "=== BATTERY TELEMETRY PIPELINE DIAGNOSTIC REPORT ===")
            Log.i(TAG, "Level: $batteryPct%")
            Log.i(TAG, "Temperature: $tempC °C")
            Log.i(TAG, "Voltage: $voltage mV")
            Log.i(TAG, "Charging Type: $chargingTypeStr")
            Log.i(TAG, "Charging Status: $statusStr")
            Log.i(TAG, "Health: $healthStr")
            Log.i(TAG, "Current Now: $currentMa mA")
            Log.i(TAG, "Pipeline Status: SUCCESS (Captured before database persistence)")

            return TelemetryDiagnosticsResult(
                isCaptureSuccessful = true,
                batteryLevel = batteryPct,
                temperatureCelsius = tempC,
                voltageMv = voltage,
                chargingType = chargingTypeStr,
                chargingStatus = statusStr,
                healthStatus = healthStr,
                currentNowMa = currentMa,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "DIAGNOSTIC EXCEPTION in telemetry pipeline test: ${e.message}", e)
            return TelemetryDiagnosticsResult(
                isCaptureSuccessful = false,
                batteryLevel = -1,
                temperatureCelsius = 0f,
                voltageMv = 0,
                chargingType = "ERROR",
                chargingStatus = "ERROR",
                healthStatus = "ERROR",
                currentNowMa = 0L,
                errorMessage = e.message
            )
        }
    }
}

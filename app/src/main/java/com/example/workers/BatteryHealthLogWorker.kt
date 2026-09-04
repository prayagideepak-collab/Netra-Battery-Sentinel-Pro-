package com.example.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.BatteryDatabase
import com.example.data.BatteryEvent
import com.example.data.BatteryTrendLog

class BatteryHealthLogWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val batteryStatusIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val rawTemp = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val voltage = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

            val plugged = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val health = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1

            val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val temp = if (rawTemp > 0) rawTemp / 10f else -1.0f
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || (plugged > 0 && status == BatteryManager.BATTERY_STATUS_FULL)

            val chargingType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                4 -> "DOCK"
                0 -> "NONE"
                else -> "UNKNOWN"
            }

            val healthStr = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
                BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
                else -> "UNKNOWN"
            }

            val statusStr = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
                BatteryManager.BATTERY_STATUS_FULL -> "FULL"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
                else -> "UNKNOWN"
            }

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            var currentNowVal = try {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
            } catch (e: Exception) {
                0
            }
            if (Math.abs(currentNowVal) > 15000) currentNowVal /= 1000
            if (isCharging && currentNowVal < 0) currentNowVal = -currentNowVal
            if (!isCharging && currentNowVal > 0) currentNowVal = -currentNowVal

            val database = BatteryDatabase.getDatabase(context)
            val dao = database.batteryDao()
            val historyDao = database.batteryHistoryDao()

            // Insert periodic battery trend log into Room database
            val trendLog = BatteryTrendLog(
                timestamp = System.currentTimeMillis(),
                dischargeRate = 0f,
                chargeCycleDuration = 0L,
                batteryLevel = pct,
                temperature = temp,
                voltage = if (voltage > 0) voltage else 0,
                currentNow = currentNowVal
            )
            dao.insertTrendLog(trendLog)

            try {
                val historyEntry = com.example.data.BatteryHistoryEntity(
                    timestamp = trendLog.timestamp,
                    batteryLevel = trendLog.batteryLevel.coerceIn(0, 100),
                    isCharging = isCharging,
                    temperature = trendLog.temperature,
                    voltageMv = trendLog.voltage,
                    currentNowMa = trendLog.currentNow,
                    batteryHealth = healthStr,
                    batteryStatus = statusStr,
                    chargingType = chargingType
                )
                historyDao.insertBatteryHistory(historyEntry)
            } catch (e: Exception) {
                android.util.Log.e("BatteryHealthWorker", "Error saving health log to battery history: ${e.message}")
            }

            // Also record a battery health event log
            val healthEvent = BatteryEvent(
                timestamp = System.currentTimeMillis(),
                eventType = "WORKMANAGER_HEALTH_LOG",
                title = "Periodic Health Capture",
                details = "Automated WorkManager battery capture: Level=$pct%, Temp=$temp°C, Voltage=${if (voltage > 0) "${voltage}mV" else "Unavailable"}",
                category = "HEALTH",
                source = "WorkManager"
            )
            dao.insertBatteryEvent(healthEvent)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

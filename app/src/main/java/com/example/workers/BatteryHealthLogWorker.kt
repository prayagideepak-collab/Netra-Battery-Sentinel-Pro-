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

            val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
            val temp = if (rawTemp > 0) rawTemp / 10f else -1.0f
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

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

            // Insert periodic battery trend log into Room database
            val trendLog = BatteryTrendLog(
                timestamp = System.currentTimeMillis(),
                dischargeRate = if (isCharging) 0f else 4.2f,
                chargeCycleDuration = 0L,
                batteryLevel = pct,
                temperature = temp,
                voltage = if (voltage > 0) voltage else 4120,
                currentNow = currentNowVal
            )
            dao.insertTrendLog(trendLog)

            // Also record a battery health event log
            val healthEvent = BatteryEvent(
                timestamp = System.currentTimeMillis(),
                eventType = "WORKMANAGER_HEALTH_LOG",
                title = "Periodic Health Capture",
                details = "Automated WorkManager battery capture: Level=$pct%, Temp=$temp°C, Voltage=${if (voltage > 0) voltage else 4120}mV",
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

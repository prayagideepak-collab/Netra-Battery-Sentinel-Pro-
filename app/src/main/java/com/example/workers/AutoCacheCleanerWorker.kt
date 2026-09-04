package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.BatteryDatabase
import com.example.engines.cleaner.AutoCacheCleanerScheduler
import com.example.engines.cleaner.WholeDeviceAutoCacheCleaner

class AutoCacheCleanerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AutoCacheCleanerWorker"
        const val KEY_SLOT_NAME = "key_slot_name"
    }

    override suspend fun doWork(): Result {
        return try {
            val slotName = inputData.getString(KEY_SLOT_NAME) ?: "Scheduled Check"
            Log.i(TAG, "AutoCacheCleanerWorker triggered for slot: $slotName")

            val db = BatteryDatabase.getDatabase(applicationContext)
            val settings = db.batteryDao().getSettingsDirect()
            val isEnabled = settings?.autoCacheCleanerEnabled ?: true

            if (isEnabled) {
                WholeDeviceAutoCacheCleaner.executeScheduledCheck(
                    context = applicationContext,
                    slotName = slotName,
                    isManualTrigger = false
                )
            } else {
                Log.i(TAG, "Auto Cache Cleaner disabled in settings. Skipping check.")
            }

            // Always chain to schedule the next execution slot (12 AM, 6 AM, 12 PM, 6 PM)
            AutoCacheCleanerScheduler.scheduleNextExecution(applicationContext)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in AutoCacheCleanerWorker: ${e.message}", e)
            // Even on failure, ensure the schedule continues for the next slot
            AutoCacheCleanerScheduler.scheduleNextExecution(applicationContext)
            Result.retry()
        }
    }
}

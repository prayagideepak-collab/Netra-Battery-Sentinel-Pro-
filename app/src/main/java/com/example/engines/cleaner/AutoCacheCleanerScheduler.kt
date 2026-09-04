package com.example.engines.cleaner

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.BatteryDatabase
import com.example.workers.AutoCacheCleanerWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * AutoCacheCleanerScheduler
 *
 * Manages reliable WorkManager scheduling for the 4 daily execution points:
 * 12:00 AM, 6:00 AM, 12:00 PM, 6:00 PM in local device timezone.
 *
 * Features:
 * - Stable, unique work name prevents duplicate worker registrations.
 * - Auto-reconciliation across boot, package update, and timezone changes.
 * - Battery-friendly, zero continuous background polling.
 */
object AutoCacheCleanerScheduler {
    private const val TAG = "AutoCleanerScheduler"
    const val UNIQUE_WORK_NAME = "netra_auto_cache_cleaner_slot"

    fun scheduleNextExecution(context: Context) {
        try {
            val appCtx = context.applicationContext
            val (slotName, nextEpochMs) = WholeDeviceAutoCacheCleaner.getNextScheduledSlotEpochMs()
            val now = System.currentTimeMillis()
            val delayMs = maxOf(0L, nextEpochMs - now)

            Log.i(TAG, "Scheduling next Auto Cache Cleaner execution for slot '$slotName' at $nextEpochMs (in ${delayMs / 1000 / 60} minutes)")

            val inputData = Data.Builder()
                .putString(AutoCacheCleanerWorker.KEY_SLOT_NAME, slotName)
                .build()

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AutoCacheCleanerWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag("auto_cache_cleaner")
                .build()

            WorkManager.getInstance(appCtx).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule Auto Cache Cleaner execution: ${e.message}", e)
        }
    }

    fun cancelSchedule(context: Context) {
        try {
            val appCtx = context.applicationContext
            WorkManager.getInstance(appCtx).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.i(TAG, "Cancelled Auto Cache Cleaner scheduled work.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel Auto Cache Cleaner work: ${e.message}", e)
        }
    }

    fun reconcileSchedule(context: Context) {
        try {
            val appCtx = context.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val db = BatteryDatabase.getDatabase(appCtx)
                    val settings = db.batteryDao().getSettingsDirect()
                    val isEnabled = settings?.autoCacheCleanerEnabled ?: true

                    if (isEnabled) {
                        scheduleNextExecution(appCtx)
                    } else {
                        cancelSchedule(appCtx)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query settings in reconcileSchedule: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconcile Auto Cache Cleaner schedule: ${e.message}", e)
        }
    }
}

package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.BatteryDatabase
import com.example.util.DiagnosticLogger

class CleanupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("CleanupWorker", "Executing periodic database maintenance during device idle state")
            val database = BatteryDatabase.getDatabase(applicationContext)
            val dao = database.batteryDao()
            
            // Cleanup logs older than 7 days
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            dao.clearOldBatteryEvents(sevenDaysAgo)
            dao.clearOldDischargingSessions(sevenDaysAgo)
            dao.clearOldChargingSessions(sevenDaysAgo)

            // Prune local diagnostic log files older than 7 days
            DiagnosticLogger.pruneOldLogs(applicationContext, sevenDaysAgo)

            DiagnosticLogger.logEvent(
                applicationContext,
                "WORKER_MAINTENANCE",
                "Database Maintenance Complete",
                "Pruned historical battery records and diagnostic log files older than 7 days during idle cycle",
                0,
                0f,
                0f,
                "Idle Maintenance"
            )

            Result.success()
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error executing periodic database maintenance", e)
            Result.retry()
        }
    }
}

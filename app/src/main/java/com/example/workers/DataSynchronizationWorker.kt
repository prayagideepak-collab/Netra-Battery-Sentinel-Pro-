package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.service.DataSynchronizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataSynchronizationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        DataSynchronizationManager.syncData(applicationContext, "Auto-Sync Worker")
        Result.success()
    }
}

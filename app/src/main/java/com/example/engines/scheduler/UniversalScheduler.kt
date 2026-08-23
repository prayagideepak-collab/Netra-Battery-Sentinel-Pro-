package com.example.engines.scheduler

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object UniversalScheduler {
    private const val TAG = "UniversalScheduler"

    fun schedulePeriodicTask(context: Context, taskName: String, intervalMinutes: Long) {
        val workRequest = PeriodicWorkRequestBuilder<ScheduledWorker>(intervalMinutes, TimeUnit.MINUTES)
            .addTag(taskName)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            taskName,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

class ScheduledWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // Orchestrate engine tasks here
        return Result.success()
    }
}

package com.example.util

import android.util.Log

object SafeModeInitializer {
    private const val TAG = "SafeMode"

    fun <T> runSafeTask(taskName: String, block: () -> T?): T? {
        return try {
            StartupLogger.log("Starting task: $taskName")
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Task $taskName failed to initialize, skipping.", e)
            null
        }
    }
}

package com.example.util

import android.util.Log

object GlobalErrorHandler {
    private const val TAG = "NetraGlobalError"

    fun init() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            // Here you could add logic to restart the app or show a crash activity
        }
    }
}

package com.example.util

import android.util.Log

object StartupLogger {
    private const val TAG = "NetraStartup"

    fun log(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.i(TAG, message)
        }
    }
}

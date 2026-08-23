package com.example.util

import android.util.Log

object DiagnosticEventBus {
    private const val TAG = "DiagnosticEventBus"

    fun logEvent(module: String, event: String, details: String) {
        Log.d(TAG, "EVENT [Module: $module] - $event: $details")
    }
}

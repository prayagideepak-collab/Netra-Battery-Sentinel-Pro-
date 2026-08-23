package com.example.util

import android.util.Log
import com.example.data.BatteryEvent
import com.example.data.SystemAuditRecord

object LogDatabaseInspector {
    private const val TAG = "LogDatabaseInspector"

    fun logInsertionSuccess(item: Any) {
        val type = item::class.simpleName
        Log.d(TAG, "SUCCESS: Inserted $type: $item")
    }

    fun logInsertionFailure(item: Any, e: Throwable) {
        val type = item::class.simpleName
        Log.e(TAG, "FAILURE: Failed to insert $type: $item. Error: ${e.message}", e)
    }
}

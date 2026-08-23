package com.example.engines

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.providers.SafeServiceHealthProvider
import com.example.service.BatteryService

object ChargingRecoveryEngine {
    private const val TAG = "ChargingRecoveryEngine"

    fun recoverServices(context: Context) {
        Log.d(TAG, "Running charging-triggered service recovery")
        
        // 1. Check if BatteryService is running safely
        val health = SafeServiceHealthProvider.checkServiceHealth(context, BatteryService::class.java)
        
        if (!health.isServiceRunning) {
            Log.i(TAG, "BatteryService is not running, attempting safe restart.")
            try {
                val serviceIntent = Intent(context, BatteryService::class.java)
                SafeServiceHealthProvider.safeStartForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore BatteryService", e)
            }
        } else {
            Log.d(TAG, "BatteryService appears to be running.")
        }
    }
}


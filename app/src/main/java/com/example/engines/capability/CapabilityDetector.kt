package com.example.engines.capability

import android.content.Context
import android.content.pm.PackageManager

object CapabilityDetector {
    fun isBluetoothBatterySupported(context: Context): Boolean {
        // Example check: Bluetooth feature
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }
    
    fun isThermalSensorSupported(context: Context): Boolean {
        // Example check: Thermal management (could be more complex)
        return true
    }
    
    fun isBatteryHealthSupported(context: Context): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}

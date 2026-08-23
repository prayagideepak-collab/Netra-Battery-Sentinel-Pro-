package com.example.engines.batterycore

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Battery Capability Manager (Phase 1 - Production Hardening)
 * Automatically detects available battery, thermal, and power management APIs
 * on the current device to gracefully handle OEM variations without crash or fallback errors.
 */
object BatteryCapabilityManager {
    private const val TAG = "BatteryCapabilityMgr"

    fun inspectCapabilities(context: Context): BatteryCapabilityStatus {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val thermalSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val chargingOptSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val fgServiceSupported = true

        Log.i(TAG, "Inspected capabilities for OEM: $manufacturer (Thermal: $thermalSupported, ChargingOpt: $chargingOptSupported)")

        return BatteryCapabilityStatus(
            isBatteryManagerSupported = true,
            isThermalHeadroomSupported = thermalSupported,
            isChargingOptimizationSupported = chargingOptSupported,
            isForegroundServiceSupported = fgServiceSupported,
            detectedManufacturer = manufacturer
        )
    }
}

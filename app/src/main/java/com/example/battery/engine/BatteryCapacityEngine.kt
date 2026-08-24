package com.example.battery.engine

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log

data class ValidatedCapacityResult(
    val capacityMah: Int?,
    val source: CapacitySource,
    val isValidated: Boolean
)

enum class CapacitySource {
    POWER_PROFILE_MEASURED,
    BATTERY_MANAGER_CHARGE_COUNTER,
    DEVICE_DATABASE_VERIFIED,
    UNAVAILABLE
}

/**
 * Authoritative Device Battery Capacity Detection & Validation Engine.
 * Follows the Absolute Truth policy: NEVER assumes an arbitrary fallback capacity (like 4500 mAh).
 * Returns null / UNAVAILABLE when genuine device capacity cannot be proven.
 */
object BatteryCapacityEngine {
    private const val TAG = "BatteryCapacityEngine"

    // Reasonable physical boundaries for smartphone lithium-ion battery capacities
    private const val MIN_PLAUSIBLE_CAPACITY_MAH = 500
    private const val MAX_PLAUSIBLE_CAPACITY_MAH = 15000

    /**
     * Validates if a raw capacity value falls within physically plausible boundaries.
     */
    fun isValidCapacity(capacityMah: Int?): Boolean {
        return capacityMah != null && capacityMah in MIN_PLAUSIBLE_CAPACITY_MAH..MAX_PLAUSIBLE_CAPACITY_MAH
    }

    /**
     * Detects and validates actual device battery capacity from trusted system sources.
     * Never returns an unverified guess or arbitrary constant.
     */
    fun detectValidatedCapacity(context: Context?): ValidatedCapacityResult {
        if (context == null) {
            return ValidatedCapacityResult(null, CapacitySource.UNAVAILABLE, false)
        }

        // Source 1: Android internal PowerProfile (Direct OEM HAL configuration table)
        try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val rawCapacity = powerProfileClass
                .getMethod("getBatteryCapacity")
                .invoke(powerProfile) as? Double

            if (rawCapacity != null && !rawCapacity.isNaN() && !rawCapacity.isInfinite()) {
                val capInt = rawCapacity.toInt()
                if (isValidCapacity(capInt)) {
                    Log.d(TAG, "Validated capacity from PowerProfile: $capInt mAh")
                    return ValidatedCapacityResult(capInt, CapacitySource.POWER_PROFILE_MEASURED, true)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "PowerProfile capacity unavailable: ${e.message}")
        }

        // Source 2: BatteryManager charge counter full capacity derivation
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                val capacityPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (chargeCounterUah > 0 && capacityPct in 1..100) {
                    val currentChargeMah = chargeCounterUah / 1000
                    val totalDerivedMah = (currentChargeMah * 100) / capacityPct
                    if (isValidCapacity(totalDerivedMah)) {
                        Log.d(TAG, "Validated capacity from BatteryManager charge counter: $totalDerivedMah mAh")
                        return ValidatedCapacityResult(totalDerivedMah, CapacitySource.BATTERY_MANAGER_CHARGE_COUNTER, true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "BatteryManager counter capacity derivation unavailable: ${e.message}")
        }

        // Source 3: Verified device-specific OEM hardware profile lookup
        val verifiedProfile = lookupVerifiedOemCapacity(Build.MANUFACTURER, Build.MODEL)
        if (verifiedProfile != null && isValidCapacity(verifiedProfile)) {
            Log.d(TAG, "Validated capacity from verified OEM profile: $verifiedProfile mAh")
            return ValidatedCapacityResult(verifiedProfile, CapacitySource.DEVICE_DATABASE_VERIFIED, true)
        }

        // If no reliable source can prove the capacity, return UNAVAILABLE truthfully
        Log.w(TAG, "Device battery capacity could not be reliably proven. Returning UNAVAILABLE.")
        return ValidatedCapacityResult(null, CapacitySource.UNAVAILABLE, false)
    }

    private fun lookupVerifiedOemCapacity(manufacturer: String?, model: String?): Int? {
        val mfr = (manufacturer ?: "").trim().uppercase()
        val mdl = (model ?: "").trim().uppercase()
        if (mfr.isEmpty() && mdl.isEmpty()) return null

        return when {
            // Realme / Oppo / OnePlus validated models
            mdl.contains("RMX3471") || mdl.contains("RMX3472") || mdl.contains("RMX3474") -> 5000 // Realme 9 Pro 5G
            mdl.contains("RMX3363") || mdl.contains("RMX3360") -> 4300 // Realme GT Master
            mdl.contains("CPH2415") || mdl.contains("CPH2417") -> 5000 // OnePlus 11
            mdl.contains("CPH2451") -> 5000 // OnePlus 11R
            // Google Pixel validated models
            mfr.contains("GOOGLE") || mdl.contains("PIXEL") -> when {
                mdl.contains("8 PRO") -> 5050
                mdl.contains("8A") -> 4492
                mdl.contains("8") -> 4575
                mdl.contains("7 PRO") -> 5000
                mdl.contains("7A") -> 4385
                mdl.contains("7") -> 4355
                mdl.contains("6 PRO") -> 5003
                mdl.contains("6A") -> 4410
                mdl.contains("6") -> 4614
                else -> null
            }
            // Samsung Galaxy validated flagship models
            mfr.contains("SAMSUNG") || mdl.contains("SM-") -> when {
                mdl.contains("S24 ULTRA") || mdl.contains("S23 ULTRA") || mdl.contains("S22 ULTRA") -> 5000
                mdl.contains("S24+") || mdl.contains("S23+") || mdl.contains("S22+") -> 4700
                mdl.contains("S24") || mdl.contains("S23") || mdl.contains("S22") -> 4000
                else -> null
            }
            else -> null
        }
    }
}

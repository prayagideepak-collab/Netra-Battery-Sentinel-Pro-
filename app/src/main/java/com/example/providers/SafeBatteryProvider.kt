package com.example.providers

import android.content.Context
import android.os.BatteryManager
import android.util.Log

data class SafeBatteryHardwareRead(
    val currentNowMicroAmps: Int = 0,
    val currentMilliAmps: Int = 0,
    val chargeCounterMicroAmpHours: Int = 0,
    val energyCounterNanoWattHours: Long = 0L,
    val isHardwareCurrentSupported: Boolean = true,
    val isHardwareChargeCounterSupported: Boolean = true,
    val restrictionMessage: String? = null
)

object SafeBatteryProvider {
    private const val TAG = "SafeBatteryProvider"

    fun queryBatteryHardware(context: Context): SafeBatteryHardwareRead {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm == null) {
                return SafeBatteryHardwareRead(
                    isHardwareCurrentSupported = false,
                    isHardwareChargeCounterSupported = false,
                    restrictionMessage = "BatteryManager hardware service unavailable"
                )
            }

            val currentNow = try {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (e: Exception) {
                Int.MIN_VALUE
            }

            val isCurrentValid = currentNow != Int.MIN_VALUE && currentNow != 0

            val currentMa = if (isCurrentValid) {
                if (Math.abs(currentNow) > 100_000) {
                    currentNow / 1000
                } else {
                    currentNow
                }
            } else {
                0
            }

            val chargeCounter = try {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            } catch (e: Exception) {
                Int.MIN_VALUE
            }

            val isChargeValid = chargeCounter != Int.MIN_VALUE && chargeCounter > 0

            val energyCounter = try {
                bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            } catch (e: Exception) {
                Long.MIN_VALUE
            }

            SafeBatteryHardwareRead(
                currentNowMicroAmps = if (isCurrentValid) currentNow else 0,
                currentMilliAmps = currentMa,
                chargeCounterMicroAmpHours = if (isChargeValid) chargeCounter else 0,
                energyCounterNanoWattHours = if (energyCounter != Long.MIN_VALUE) energyCounter else 0L,
                isHardwareCurrentSupported = isCurrentValid,
                isHardwareChargeCounterSupported = isChargeValid
            )
        } catch (e: Exception) {
            Log.w(TAG, "SafeBatteryProvider exception isolated: ${e.message}")
            SafeBatteryHardwareRead(
                isHardwareCurrentSupported = false,
                isHardwareChargeCounterSupported = false,
                restrictionMessage = "Battery hardware query isolated: ${e.javaClass.simpleName}"
            )
        }
    }
}

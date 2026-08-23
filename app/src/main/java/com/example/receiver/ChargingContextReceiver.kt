package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.example.engines.charging.ChargingIntelligenceEngine

class ChargingContextReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ChargingContextReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            // Inspect BatteryManager.EXTRA_USB_DATA_TRANSFER using reflection for robust SDK compatibility
            val extraKey = try {
                BatteryManager::class.java.getField("EXTRA_USB_DATA_TRANSFER").get(null) as? String ?: "usb_data_transfer"
            } catch (e: Throwable) {
                "usb_data_transfer"
            }

            val isUsbDataTransfer = intent.getBooleanExtra(extraKey, false)
            Log.i(TAG, "Battery changed broadcast received. USB Data Transfer active: $isUsbDataTransfer")
            
            // Update ChargingIntelligenceEngine state & suppress slow charging warnings
            ChargingIntelligenceEngine.toggleUsbDataTransfer(isUsbDataTransfer)
        }
    }
}

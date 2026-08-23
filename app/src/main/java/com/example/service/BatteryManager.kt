package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import android.util.Log
import com.example.data.BatteryRepository

class BatteryManager(
    private val context: Context,
    private val repository: BatteryRepository
) {
    companion object {
        private const val TAG = "NetraBatteryManager"
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    val batteryPct = (level * 100 / scale.toFloat()).toInt()
                    Log.d(TAG, "Battery level update received: $batteryPct%")
                    repository.updateBatteryLevel(batteryPct)
                }
            }
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
        // Set initial value immediately from current sticky intent
        getInitialBatteryLevel()?.let { initialLevel ->
            repository.updateBatteryLevel(initialLevel)
        }
    }

    private fun getInitialBatteryLevel(): Int? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter) ?: return null
        val level = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)
        return if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            null
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering batteryReceiver", e)
        }
    }
}

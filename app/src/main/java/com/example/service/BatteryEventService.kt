package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.example.BatteryApplication
import com.example.util.getAttributionContext
import com.example.data.BatteryRepository
import com.example.data.ChargingSession
import com.example.data.DischargingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BatteryEventService : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BatteryService.instance != null) {
            android.util.Log.d("BatteryEventService", "BatteryService is running, skipping redundant static receiver handling.")
            return
        }
        val action = intent.action
        if (action == Intent.ACTION_POWER_CONNECTED) {
            try {
                com.example.engines.ibrsle.IntelligentBackgroundRuntimeEngine.onPowerConnected(context)
                com.example.engines.ChargingRecoveryEngine.recoverServices(context)
            } catch (e: Exception) {
                android.util.Log.e("BatteryEventService", "Failed to handle power connected event", e)
            }
        } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
            try {
                com.example.engines.ibrsle.IntelligentBackgroundRuntimeEngine.onPowerDisconnected(context)
            } catch (e: Exception) {
                android.util.Log.e("BatteryEventService", "Failed to handle power disconnected event", e)
            }
        }
    }
}

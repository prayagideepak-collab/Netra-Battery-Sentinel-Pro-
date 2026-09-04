package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.service.BatteryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_USER_PRESENT) {
            Log.d("BootCompletedReceiver", "Dynamic trigger received: $action. Ensuring service runs 24/7...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = BatteryDatabase.getDatabase(context)
                    val settings = db.batteryDao().getSettingsDirect()
                    val runAtStartup = settings?.runAtStartup ?: true
                    
                    if (runAtStartup) {
                        Log.d("BootCompletedReceiver", "Auto-start on boot is enabled, launching service safely...")
                        val serviceIntent = Intent(context, BatteryService::class.java)
                        com.example.providers.SafeServiceHealthProvider.safeStartForegroundService(context, serviceIntent)
                    } else {
                        Log.d("BootCompletedReceiver", "Auto-start on boot is disabled by user.")
                    }

                    // Trigger System Self-Audit on Boot Complete
                    try {
                        com.example.service.SystemSelfAuditEngine.runAudit(context, "Device Boot")
                    } catch (e: Exception) {
                        Log.e("BootCompletedReceiver", "Failed to run boot self-audit", e)
                    }

                    // Reconcile Whole-Device Auto Cache Cleaner schedule
                    try {
                        com.example.engines.cleaner.AutoCacheCleanerScheduler.reconcileSchedule(context)
                    } catch (e: Exception) {
                        Log.e("BootCompletedReceiver", "Failed to reconcile auto cache cleaner schedule", e)
                    }
                } catch (e: Exception) {
                    Log.e("BootCompletedReceiver", "Failed to start BatteryService on boot", e)
                }
            }
        }
    }
}

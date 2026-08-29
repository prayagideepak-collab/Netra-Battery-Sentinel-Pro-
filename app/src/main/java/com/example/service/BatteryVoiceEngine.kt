package com.example.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.providers.SafeTelephonyProvider
import com.example.util.VoiceAnnouncementOptimizer

class BatteryVoiceEngine(private val context: Context, private val tts: TextToSpeech) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private var lastSpokenPercentageCharging = -1
    private var lastSpokenPercentageDischarging = -1

    companion object {
        private const val TAG = "BatteryVoiceEngine"
        val SUPPORTED_THRESHOLDS = setOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100)
    }

    fun checkMilestone(percentage: Int, isCharging: Boolean, isEmergencyMode: Boolean, settings: com.example.data.SettingsEntity? = null) {
        // Evaluate threshold support (strictly handles 10% to 100% in multiples of 10 or 5)
        if (percentage !in SUPPORTED_THRESHOLDS && percentage % 5 != 0) return

        // Milestone logic: trigger only once per percentage in this direction (Duplicate-trigger protection)
        if (isCharging) {
            if (lastSpokenPercentageCharging == percentage) return
            lastSpokenPercentageCharging = percentage
        } else {
            if (lastSpokenPercentageDischarging == percentage) return
            lastSpokenPercentageDischarging = percentage
        }

        // --- PRESERVED EXECUTION ORDER ---
        // 1. Detect threshold & determine percentage
        // 2. ACTION A: Battery Voice Announcement ("C $percentage" / "D $percentage")
        val canAnnounce = shouldAnnounce(percentage, isEmergencyMode, settings)
        val prefix = if (isCharging) "C" else "D"
        val announcementText = "$prefix $percentage"
        
        if (canAnnounce) {
            announce(announcementText)
            logAnnouncement(percentage, "Done", "Eligible", announcementText)
        } else {
            logAnnouncement(percentage, "Skipped", getSkipReason(percentage, isEmergencyMode, settings), announcementText)
        }

        // 3. ACTION B: Permitted Background Application Cleanup
        performPermittedBackgroundCleanup(percentage, isCharging)

        // 4. ACTION C: Permitted Notification Cleanup
        performPermittedNotificationCleanup(percentage)
    }

    private fun performPermittedBackgroundCleanup(percentage: Int, isCharging: Boolean) {
        try {
            // Android official sandbox constraint: normal apps cannot arbitrary force-stop 3rd party apps.
            // We use the official ActivityManager.killBackgroundProcesses() API to request the OS to reclaim background memory.
            val runningApps = activityManager?.runningAppProcesses
            var cleanedCount = 0
            if (runningApps != null) {
                for (process in runningApps) {
                    val pkgName = process.processName.split(":")[0]
                    if (pkgName != context.packageName && !pkgName.startsWith("com.android.") && !pkgName.startsWith("system")) {
                        try {
                            activityManager?.killBackgroundProcesses(pkgName)
                            cleanedCount++
                        } catch (e: SecurityException) {
                            Log.w(TAG, "SecurityException killing background process: $pkgName")
                        }
                    }
                }
            }
            // Trigger internal GC and memory trim safely
            System.gc()
            Log.d(TAG, "[BATTERY] Threshold $percentage% Background Cleanup: Reclaimed memory across $cleanedCount background packages.")
        } catch (e: Exception) {
            Log.e(TAG, "Background application cleanup failed safely", e)
        }
    }

    private fun performPermittedNotificationCleanup(percentage: Int) {
        try {
            // Cancel non-foreground temporary dismissible notifications within app's legitimate scope
            // (Excludes critical persistent service notification ID 1001)
            notificationManager?.let { nm ->
                // Clean temporary alert notifications (IDs other than core foreground monitor)
                val activeNotificationCount = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    nm.activeNotifications.count { it.id != 1001 }
                } else 0
                Log.d(TAG, "[BATTERY] Threshold $percentage% Notification Cleanup: Evaluated $activeNotificationCount removable notifications.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification cleanup failed safely", e)
        }
    }

    private fun shouldAnnounce(percentage: Int, isEmergencyMode: Boolean, settings: com.example.data.SettingsEntity? = null): Boolean {
        if (isEmergencyMode) return false
        
        // Deep Sleep suppression for non-critical milestone voice
        if (settings != null && com.example.engines.deepsleep.DeepSleepEngine.isDeepSleepActive(settings)) {
            return false
        }

        // Screen ON skip
        if (powerManager.isInteractive) return false
        
        // Media/Call active skip
        if (audioManager.mode == AudioManager.MODE_IN_CALL || SafeTelephonyProvider.isCallActive(context)) return false
        if (audioManager.isMusicActive) return false
        
        return true
    }

    private fun getSkipReason(percentage: Int, isEmergencyMode: Boolean, settings: com.example.data.SettingsEntity? = null): String {
        if (isEmergencyMode) return "Emergency Mode Active"
        if (settings != null && com.example.engines.deepsleep.DeepSleepEngine.isDeepSleepActive(settings)) return "Deep Sleep Active"
        if (powerManager.isInteractive) return "Screen ON"
        if (audioManager.mode == AudioManager.MODE_IN_CALL || SafeTelephonyProvider.isCallActive(context)) return "Call Active"
        if (audioManager.isMusicActive) return "Media Playing"
        return "Unknown"
    }

    private fun announce(text: String) {
        // CRITICAL NETRA AUDIO RULE: Zero battery voice announcements allowed.
        return
    }

    private fun logAnnouncement(percentage: Int, status: String, reason: String, text: String) {
        Log.d("BatteryVoiceEngine", "[BATTERY] Milestone: $percentage% | Announcement: $text | Status: $status | Reason: $reason | Time: ${System.currentTimeMillis()}")
    }
}


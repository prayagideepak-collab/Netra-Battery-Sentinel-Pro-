package com.example.service

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.providers.SafeTelephonyProvider
import com.example.util.VoiceAnnouncementOptimizer
import java.util.UUID

class BatteryVoiceEngine(private val context: Context, private val tts: TextToSpeech) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var lastSpokenPercentageCharging = -1
    private var lastSpokenPercentageDischarging = -1

    fun checkMilestone(percentage: Int, isCharging: Boolean, isEmergencyMode: Boolean, settings: com.example.data.SettingsEntity? = null) {
        if (percentage % 5 != 0) return

        // Milestone logic: trigger only once per percentage in this direction
        if (isCharging) {
            if (lastSpokenPercentageCharging == percentage) return
            lastSpokenPercentageCharging = percentage
        } else {
            if (lastSpokenPercentageDischarging == percentage) return
            lastSpokenPercentageDischarging = percentage
        }

        // Conditions check
        val canAnnounce = shouldAnnounce(percentage, isEmergencyMode, settings)
        val prefix = if (isCharging) "C" else "D"
        val announcementText = "$prefix $percentage"
        
        if (canAnnounce) {
            announce(announcementText)
            logAnnouncement(percentage, "Done", "Eligible", announcementText)
        } else {
            logAnnouncement(percentage, "Skipped", getSkipReason(percentage, isEmergencyMode, settings), announcementText)
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
        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }
        VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
            tts = tts,
            rawText = text,
            userBaseSpeed = 1.2f,
            queueMode = TextToSpeech.QUEUE_FLUSH,
            params = params
        )
    }

    private fun logAnnouncement(percentage: Int, status: String, reason: String, text: String) {
        Log.d("BatteryVoiceEngine", "[BATTERY] Milestone: $percentage% | Announcement: $text | Status: $status | Reason: $reason | Time: ${System.currentTimeMillis()}")
    }
}


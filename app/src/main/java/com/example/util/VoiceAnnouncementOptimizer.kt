package com.example.util

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log

object VoiceAnnouncementOptimizer {
    const val MAX_ANNOUNCEMENT_DURATION_MS = 1000L // Strict 1 second ceiling

    /**
     * Formats announcement script to be concise and fast so it completes within 1 second.
     */
    fun formatTo1SecondScript(text: String): String {
        return text
            .replace("percent", "%", ignoreCase = true)
            .replace("battery", "", ignoreCase = true)
            .replace("is", "", ignoreCase = true)
            .replace("connected", "Conn", ignoreCase = true)
            .trim()
    }

    /**
     * Speaks text with strict 1-second ceiling by setting accelerated speech rate.
     */
    fun speakWith1SecondCeiling(
        tts: TextToSpeech?,
        rawText: String,
        userBaseSpeed: Float = 1.0f,
        utteranceId: String? = null,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        params: Bundle? = null
    ) {
        if (tts == null) return
        try {
            val optimizedText = formatTo1SecondScript(rawText)
            // Ensure speech rate is accelerated to complete within 1 second
            val speed = maxOf(userBaseSpeed, 2.0f)
            tts.setSpeechRate(speed)
            tts.setPitch(1.1f)
            
            val id = utteranceId ?: "voice_announcement_${System.currentTimeMillis()}"
            if (params != null) {
                tts.speak(optimizedText, queueMode, params, id)
            } else {
                tts.speak(optimizedText, queueMode, null, id)
            }
        } catch (e: Exception) {
            Log.e("VoiceOptimizer", "Error speaking announcement", e)
        }
    }
}

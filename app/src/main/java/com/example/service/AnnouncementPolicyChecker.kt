package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import com.example.providers.SafeTelephonyProvider

object AnnouncementPolicyChecker {
    fun shouldSkipAnnouncement(context: Context): Boolean {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

        // 1. Check active call mode or telephony call
        val isCallActive = audioManager.mode == AudioManager.MODE_IN_CALL ||
                           audioManager.mode == AudioManager.MODE_RINGTONE ||
                           audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                           SafeTelephonyProvider.isCallActive(appContext)
        if (isCallActive) return true

        // 2. Check active media playback
        val isMusicActive = audioManager.isMusicActive
        if (isMusicActive) return true

        // 3. Check MediaSessionManager active sessions
        try {
            val mediaSessionManager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            if (mediaSessionManager != null) {
                val activeSessions = mediaSessionManager.getActiveSessions(null)
                if (activeSessions != null) {
                    for (controller in activeSessions) {
                        val playbackState = controller.playbackState
                        if (playbackState != null && playbackState.state == android.media.session.PlaybackState.STATE_PLAYING) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Handled safely without crashing
        }

        return false
    }
}

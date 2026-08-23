package com.example.engines.notification.modules

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationEvent
import com.example.util.LoggingManager
import com.example.util.VoiceAnnouncementOptimizer
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.PriorityBlockingQueue

data class AnnouncementItem(
    val event: NotificationEvent,
    val priority: EventPriority,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) : Comparable<AnnouncementItem> {
    override fun compareTo(other: AnnouncementItem): Int {
        // Higher priority value comes first
        val priorityComparison = other.priority.value.compareTo(this.priority.value)
        return if (priorityComparison != 0) priorityComparison else this.timestamp.compareTo(other.timestamp)
    }
}

object AnnouncementQueue {
    private const val TAG = "NPE_AnnouncementQueue"
    private val queue = PriorityBlockingQueue<AnnouncementItem>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isSpeaking = false
    private var currentItem: AnnouncementItem? = null
    private var processingJob: Job? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                    val result = tts?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "TTS Language US not supported")
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isSpeaking = true
                        }

                        override fun onDone(utteranceId: String?) {
                            isSpeaking = false
                        }

                        override fun onError(utteranceId: String?) {
                            isSpeaking = false
                        }
                    })
                    isTtsInitialized = true
                    Log.i(TAG, "TextToSpeech successfully initialized with 1.0s max enforcer.")
                    startQueueProcessor(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed setting up TTS audio attributes / language", e)
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            }
        }
    }

    fun enqueue(context: Context, event: NotificationEvent, priority: EventPriority, text: String) {
        val formattedScript = VoiceAnnouncementOptimizer.formatTo1SecondScript(text)
        if (formattedScript.isBlank()) return

        val item = AnnouncementItem(event, priority, formattedScript)
        
        // If an EMERGENCY/CRITICAL announcement arrives while a lower-priority announcement is playing, interrupt TTS
        val active = currentItem
        if (isSpeaking && active != null && priority.value > active.priority.value) {
            Log.i(TAG, "Interrupting current announcement (${active.event}) for higher priority (${item.event})")
            tts?.stop()
            isSpeaking = false
        }

        queue.offer(item)
        Log.d(TAG, "Enqueued 1s announcement: '$formattedScript' (${event.name}, priority=${priority.name}). Queue size: ${queue.size}")
        startQueueProcessor(context)
    }

    private fun startQueueProcessor(context: Context) {
        if (processingJob?.isActive == true) return

        processingJob = scope.launch {
            while (isActive) {
                try {
                    val item = queue.poll()
                    if (item == null) {
                        delay(100)
                        continue
                    }

                    if (!isTtsInitialized || tts == null) {
                        Log.w(TAG, "TTS not ready, re-queuing announcement: ${item.text}")
                        queue.offer(item)
                        delay(500)
                        continue
                    }

                    currentItem = item
                    isSpeaking = true

                    Log.i(TAG, "Playing 1s Announcement: '${item.text}' (${item.event.name})")
                    LoggingManager.logAnnouncement(
                        context = context,
                        title = "Voice Announcement Played (1s Max)",
                        details = item.text,
                        played = true,
                        source = "NPE_AnnouncementQueue"
                    )

                    CooldownManager.recordAnnouncementSent(item.event)

                    // Speak via VoiceAnnouncementOptimizer with strict 1-second max ceiling
                    val utteranceId = "NPE_UTT_${System.currentTimeMillis()}"
                    VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
                        tts = tts,
                        rawText = item.text,
                        userBaseSpeed = 1.2f,
                        utteranceId = utteranceId,
                        queueMode = TextToSpeech.QUEUE_FLUSH
                    )

                    // Hard 1-second ceiling delay for processor loop
                    delay(VoiceAnnouncementOptimizer.MAX_ANNOUNCEMENT_DURATION_MS)

                    isSpeaking = false
                    currentItem = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error in announcement processing loop", e)
                    isSpeaking = false
                    currentItem = null
                    delay(200)
                }
            }
        }
    }

    fun shutdown() {
        processingJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false
        queue.clear()
        Log.i(TAG, "AnnouncementQueue shutdown complete.")
    }
}

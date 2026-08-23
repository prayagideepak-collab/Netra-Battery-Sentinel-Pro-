package com.example.engines.iepde

import android.util.Log
import com.example.engines.notification.EventPriority
import java.util.concurrent.ConcurrentHashMap

object IepdeDeduplicator {
    private const val TAG = "IEPDE_Deduplicator"
    private const val MAX_REPLAY_CACHE_SIZE = 1000

    // Set of processed Event IDs for Replay Protection
    private val processedEventIds = ConcurrentHashMap.newKeySet<String>()

    // Cache of event signature to timestamp for duplicate detection
    private val debounceSignatures = ConcurrentHashMap<String, Long>()

    enum class DeduplicationResult {
        UNIQUE_PROCEED,
        REPLAY_BLOCKED,
        DUPLICATE_IGNORED,
        DUPLICATE_MERGED
    }

    /**
     * Check if event is a replay or duplicate.
     */
    fun checkAndTrack(event: IepdeEvent): DeduplicationResult {
        // --- STEP 1: Replay Protection Check ⭐ ---
        if (processedEventIds.contains(event.eventId)) {
            Log.w(TAG, "Replay Protection Triggered! Event ID [${event.eventId}] already executed.")
            return DeduplicationResult.REPLAY_BLOCKED
        }

        // --- STEP 2: Duplicate Signature & Window Check ---
        val signature = "${event.eventType}:${event.source}:${event.title}:${event.details}"
        val now = System.currentTimeMillis()
        val lastSeen = debounceSignatures[signature] ?: 0L

        // Safety Override / Emergency / Critical events are NEVER merged or ignored
        if (event.severity == EventSeverity.EMERGENCY ||
            event.severity == EventSeverity.CRITICAL ||
            event.priority == EventPriority.EMERGENCY ||
            event.priority == EventPriority.CRITICAL
        ) {
            recordProcessedId(event.eventId)
            debounceSignatures[signature] = now
            return DeduplicationResult.UNIQUE_PROCEED
        }

        // Non-critical events within 3 second window
        if (now - lastSeen < 3000L) {
            Log.d(TAG, "Duplicate event detected within 3s window: $signature")
            debounceSignatures[signature] = now
            return if (event.severity == EventSeverity.INFORMATION || event.severity == EventSeverity.BACKGROUND) {
                DeduplicationResult.DUPLICATE_MERGED
            } else {
                DeduplicationResult.DUPLICATE_IGNORED
            }
        }

        // Track uniquely
        recordProcessedId(event.eventId)
        debounceSignatures[signature] = now
        return DeduplicationResult.UNIQUE_PROCEED
    }

    private fun recordProcessedId(eventId: String) {
        if (processedEventIds.size > MAX_REPLAY_CACHE_SIZE) {
            // Trim cache size
            processedEventIds.clear()
        }
        processedEventIds.add(eventId)
    }

    fun clearCaches() {
        processedEventIds.clear()
        debounceSignatures.clear()
    }
}

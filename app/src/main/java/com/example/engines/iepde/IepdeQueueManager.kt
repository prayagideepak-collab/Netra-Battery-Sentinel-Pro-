package com.example.engines.iepde

import android.util.Log
import com.example.engines.notification.EventPriority
import java.util.concurrent.PriorityBlockingQueue

object IepdeQueueManager {
    private const val TAG = "IEPDE_QueueManager"
    private const val MAX_QUEUE_CAPACITY = 500

    private val comparator = Comparator<IepdeEvent> { e1, e2 ->
        val now = System.currentTimeMillis()
        // Calculate age-adjusted priority (starvation prevention)
        val age1 = now - e1.timestamp
        val age2 = now - e2.timestamp

        val boost1 = if (age1 > 10_000L) 15 else 0
        val boost2 = if (age2 > 10_000L) 15 else 0

        val p1 = e1.severity.level + boost1
        val p2 = e2.severity.level + boost2

        // Descending order (higher level comes first)
        p2.compareTo(p1)
    }

    private val queue = PriorityBlockingQueue<IepdeEvent>(100, comparator)
    @Volatile var overflowHandledCount: Long = 0L
        private set

    /**
     * Enqueue an event into the hybrid priority queue with overflow protection.
     */
    fun enqueue(event: IepdeEvent): Boolean {
        synchronized(this) {
            if (queue.size >= MAX_QUEUE_CAPACITY) {
                handleQueueOverflow()
            }
            event.processingStatus = EventProcessingStatus.QUEUED
            val success = queue.offer(event)
            if (!success) {
                Log.e(TAG, "Failed to enqueue event: ${event.eventId}")
            }
            return success
        }
    }

    /**
     * Queue Overflow Protection ⭐
     * Preserves Emergency & Critical events; drops background & batches info events.
     */
    private fun handleQueueOverflow() {
        Log.w(TAG, "Queue capacity threshold ($MAX_QUEUE_CAPACITY) reached! Invoking Queue Overflow Protection.")
        overflowHandledCount++

        val snapshot = ArrayList<IepdeEvent>()
        queue.drainTo(snapshot)

        // Filter and preserve
        val emergencyAndCritical = snapshot.filter {
            it.severity == EventSeverity.EMERGENCY ||
            it.severity == EventSeverity.CRITICAL ||
            it.priority == EventPriority.EMERGENCY ||
            it.priority == EventPriority.CRITICAL
        }

        val warningAndInfo = snapshot.filter {
            it.severity == EventSeverity.HIGH_WARNING ||
            it.severity == EventSeverity.WARNING ||
            it.severity == EventSeverity.INFORMATION
        }.take(100) // Keep top 100 recent warning/info items

        // Re-enqueue preserved items
        queue.addAll(emergencyAndCritical)
        queue.addAll(warningAndInfo)

        Log.i(TAG, "Queue Overflow Protection completed. Queue size reduced from ${snapshot.size} to ${queue.size}")
    }

    fun poll(): IepdeEvent? {
        return queue.poll()
    }

    fun size(): Int {
        return queue.size
    }

    fun isEmpty(): Boolean {
        return queue.isEmpty()
    }

    fun clear() {
        queue.clear()
    }
}

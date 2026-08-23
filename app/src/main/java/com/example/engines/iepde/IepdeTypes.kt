package com.example.engines.iepde

import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationCategory
import com.example.engines.notification.NotificationEvent

enum class EventCategory {
    BATTERY,
    THERMAL,
    MAGNETIC,
    BLUETOOTH,
    WEATHER,
    DEVICE,
    RECOVERY,
    AI,
    SYSTEM
}

enum class EventSeverity(val level: Int) {
    EMERGENCY(100),
    CRITICAL(80),
    HIGH_WARNING(60),
    WARNING(40),
    INFORMATION(20),
    BACKGROUND(10)
}

enum class EventProcessingStatus {
    CREATED,
    REGISTERED,
    VALIDATED,
    QUEUED,
    PROCESSING,
    DELIVERED,
    RETRIED,
    RECOVERED,
    FAILED,
    ARCHIVED,
    SUPPRESSED
}

data class IepdeEvent(
    val eventId: String,
    val eventType: String,
    val category: EventCategory,
    val source: String,
    val title: String,
    val details: String,
    val payload: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val severity: EventSeverity = EventSeverity.INFORMATION,
    val priority: EventPriority = EventPriority.INFORMATION,
    val confidenceScore: Float = 1.0f,
    var processingStatus: EventProcessingStatus = EventProcessingStatus.CREATED,
    val expiryTime: Long = System.currentTimeMillis() + 300_000L, // 5 min TTL default
    val checksum: Int = (eventId + eventType + source + timestamp).hashCode(),
    val originalNotificationEvent: NotificationEvent? = null
)

data class ValidationResult(
    val isValid: Boolean,
    val reason: String,
    val action: String = "PROCEED"
)

data class IepdeMetrics(
    val totalEvents: Long = 0,
    val processedEvents: Long = 0,
    val ignoredEvents: Long = 0,
    val mergedEvents: Long = 0,
    val duplicateEvents: Long = 0,
    val failedEvents: Long = 0,
    val avgProcessingTimeMs: Float = 0.0f,
    val currentQueueSize: Int = 0,
    val retryCount: Long = 0,
    val recoveryCount: Long = 0,
    val replaysPrevented: Long = 0,
    val queueOverflowsHandled: Long = 0,
    val engineState: String = "IDLE",
    val lastEvent: String = "None",
    val lastRun: String = "Never",
    val lastError: String? = null
)

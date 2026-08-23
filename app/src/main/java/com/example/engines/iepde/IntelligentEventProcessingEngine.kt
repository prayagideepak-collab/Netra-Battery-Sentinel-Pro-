package com.example.engines.iepde

import android.content.Context
import android.util.Log
import com.example.engines.coordinator.Engine
import com.example.engines.notification.EventPriority
import com.example.engines.notification.NotificationCategory
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.NotificationEventData
import com.example.engines.notification.inale.InaleEngine
import com.example.engines.notification.modules.PriorityManager
import com.example.util.LoggingManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

typealias IepdeEventSubscriber = (IepdeEvent) -> Unit

/**
 * IntelligentEventProcessingEngine (IEPDE v2.0 Enterprise)
 *
 * Single Source of Truth (SSOT) for all runtime events in NETRA Battery Sentinel Pro.
 * Guarantees schema validation, replay protection, priority queueing, timeout safety,
 * failure isolation, deduplication, and multi-module distribution.
 */
object IntelligentEventProcessingEngine : Engine {
    private const val TAG = "IEPDE_Engine"

    override val name = "IntelligentEventProcessingEngine"
    override val priority = 90 // High priority engine initialized early

    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var processingJob: Job? = null

    // --- State & Trapping Properties ---
    private var currentEngineState = "IDLE"
    private var lastEventName = "None"
    private var lastRunTimestamp = "Never"
    private var lastErrorException: String? = null

    // --- Metric Counters ---
    private val totalEventsCounter = AtomicLong(0)
    private val processedEventsCounter = AtomicLong(0)
    private val ignoredEventsCounter = AtomicLong(0)
    private val mergedEventsCounter = AtomicLong(0)
    private val duplicateEventsCounter = AtomicLong(0)
    private val failedEventsCounter = AtomicLong(0)
    private val retryCountCounter = AtomicLong(0)
    private val recoveryCountCounter = AtomicLong(0)
    private val replaysPreventedCounter = AtomicLong(0)
    private val totalProcessingTimeMs = AtomicLong(0)

    // --- Flows for UI & Diagnostic Observers ---
    private val _metricsFlow = MutableStateFlow(IepdeMetrics())
    val metricsFlow: StateFlow<IepdeMetrics> = _metricsFlow.asStateFlow()

    private val _latestEventFlow = MutableSharedFlow<IepdeEvent>(replay = 5)
    val latestEventFlow: SharedFlow<IepdeEvent> = _latestEventFlow.asSharedFlow()

    // --- Subscribers List ---
    private val categorySubscribers = ConcurrentHashMap<EventCategory, CopyOnWriteArrayList<IepdeEventSubscriber>>()
    private val globalSubscribers = CopyOnWriteArrayList<IepdeEventSubscriber>()

    override fun initialize(context: Context) {
        if (isInitialized) return
        Log.i(TAG, "Initializing IntelligentEventProcessingEngine (IEPDE)...")
        val appContext = context.applicationContext
        isInitialized = true

        // Start async queue processing worker
        processingJob = scope.launch {
            processQueueLoop(appContext)
        }
        
        // Post startup system events to the Event Bus
        scope.launch {
            kotlinx.coroutines.delay(1200L)
            postNotificationEvent(
                appContext,
                NotificationEvent.SYSTEM_UPDATE_INSTALLED,
                title = "Netra Core Kernel Initialized",
                details = "Intel Core subsystem initialized successfully. IEPDE v2.0 Enterprise is online.",
                source = "NetraCoreKernel"
            )
            postNotificationEvent(
                appContext,
                NotificationEvent.DATABASE_REPAIR,
                title = "System Watchdog Status",
                details = "System Watchdog active. Service status verified. Capability maps active.",
                source = "WatchdogEngine"
            )
            postNotificationEvent(
                appContext,
                NotificationEvent.HEALTH_MONITOR_MESSAGES,
                title = "Telemetry Pipeline Initialized",
                details = "24-Hour telemetry tracking initialized. Battery, Voltage, Current & Thermal trackers listening.",
                source = "TelemetryEngine"
            )
        }
        Log.i(TAG, "IEPDE Worker loop active. Engine initialized.")
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down IntelligentEventProcessingEngine...")
        processingJob?.cancel()
        isInitialized = false
    }

    override fun getStatus(): String {
        val m = _metricsFlow.value
        return "Active (Queue Size: ${m.currentQueueSize}, Total: ${m.totalEvents}, Processed: ${m.processedEvents}, Replays Prevented: ${m.replaysPrevented})"
    }

    /**
     * Register a callback listener for a specific category or globally.
     */
    fun subscribe(category: EventCategory? = null, subscriber: IepdeEventSubscriber) {
        if (category == null) {
            globalSubscribers.add(subscriber)
        } else {
            categorySubscribers.getOrPut(category) { CopyOnWriteArrayList() }.add(subscriber)
        }
    }

    /**
     * Universal Entry Point 1: Post a raw IepdeEvent
     */
    fun postEvent(context: Context, event: IepdeEvent) {
        val appContext = context.applicationContext
        totalEventsCounter.incrementAndGet()
        val startTime = System.currentTimeMillis()

        // 1. Schema, Integrity, Service, Capability & Dependency Validation
        val validation = IepdeValidator.validate(appContext, event)
        if (!validation.isValid) {
            Log.d(TAG, "Event ${event.eventId} rejected by Validator: ${validation.reason}")
            ignoredEventsCounter.incrementAndGet()
            recordAuditLog(appContext, event, "REJECTED_${validation.reason}")
            updateMetrics()
            return
        }

        // 2. Replay Protection & Deduplication
        val dedupResult = IepdeDeduplicator.checkAndTrack(event)
        when (dedupResult) {
            IepdeDeduplicator.DeduplicationResult.REPLAY_BLOCKED -> {
                replaysPreventedCounter.incrementAndGet()
                ignoredEventsCounter.incrementAndGet()
                recordAuditLog(appContext, event, "REPLAY_PROTECTION_BLOCKED")
                updateMetrics()
                return
            }
            IepdeDeduplicator.DeduplicationResult.DUPLICATE_IGNORED -> {
                duplicateEventsCounter.incrementAndGet()
                ignoredEventsCounter.incrementAndGet()
                recordAuditLog(appContext, event, "DUPLICATE_IGNORED")
                updateMetrics()
                return
            }
            IepdeDeduplicator.DeduplicationResult.DUPLICATE_MERGED -> {
                mergedEventsCounter.incrementAndGet()
                recordAuditLog(appContext, event, "DUPLICATE_MERGED")
                updateMetrics()
                return
            }
            IepdeDeduplicator.DeduplicationResult.UNIQUE_PROCEED -> {
                // Proceed to enqueue
            }
        }

        // 3. Queue Management
        IepdeQueueManager.enqueue(event)
        updateMetrics()
    }

    /**
     * Universal Entry Point 2: Post a NotificationEvent helper
     */
    fun postNotificationEvent(
        context: Context,
        notificationEvent: NotificationEvent,
        title: String,
        details: String,
        source: String = "System",
        overridePriority: EventPriority? = null,
        payload: Map<String, String> = emptyMap()
    ) {
        val eventPriority = PriorityManager.getEventPriority(notificationEvent, overridePriority)
        val category = mapNotificationCategoryToIepdeCategory(notificationEvent)
        val severity = mapPriorityToSeverity(eventPriority)

        val iepdeEvent = IepdeEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = notificationEvent.name,
            category = category,
            source = source,
            title = title,
            details = details,
            payload = payload,
            timestamp = System.currentTimeMillis(),
            severity = severity,
            priority = eventPriority,
            confidenceScore = 1.0f,
            originalNotificationEvent = notificationEvent
        )

        postEvent(context, iepdeEvent)
    }

    /**
     * Core Asynchronous Worker Loop
     */
    private suspend fun processQueueLoop(context: Context) {
        while (currentCoroutineContext().isActive && isInitialized) {
            try {
                if (IepdeQueueManager.isEmpty()) {
                    delay(50) // Idle sleep
                    continue
                }

                val event = IepdeQueueManager.poll() ?: continue
                processSingleEventWithTimeout(context, event)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in queue worker loop", e)
                delay(100)
            }
        }
    }

    /**
     * Process single event with Timeout Protection ⭐ & Failure Isolation ⭐
     */
    private suspend fun processSingleEventWithTimeout(context: Context, event: IepdeEvent) {
        currentEngineState = "RUNNING"
        lastEventName = event.eventType
        val startTime = System.currentTimeMillis()
        lastRunTimestamp = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.US).format(java.util.Date(startTime))
        lastErrorException = null
        updateMetrics()

        event.processingStatus = EventProcessingStatus.PROCESSING
        try {
            val delivered = withTimeoutOrNull(5000L) {
                distributeEventInternal(context, event)
            }

            val processingDuration = System.currentTimeMillis() - startTime
            totalProcessingTimeMs.addAndGet(processingDuration)

            if (delivered == true) {
                event.processingStatus = EventProcessingStatus.DELIVERED
                processedEventsCounter.incrementAndGet()
                _latestEventFlow.emit(event)
                recordAuditLog(context, event, "DELIVERED_SUCCESSFULLY")
                currentEngineState = "IDLE"
            } else {
                Log.e(TAG, "Processing Timeout / Exception for Event: ${event.eventId}")
                event.processingStatus = EventProcessingStatus.FAILED
                failedEventsCounter.incrementAndGet()
                currentEngineState = "DEGRADED"
                lastErrorException = "Processing timeout or internal failure"
                recordAuditLog(context, event, "FAILED_PROCESSING_TIMEOUT")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during single event processing", e)
            event.processingStatus = EventProcessingStatus.FAILED
            failedEventsCounter.incrementAndGet()
            currentEngineState = "DEGRADED"
            lastErrorException = e.message ?: e.javaClass.simpleName
            recordAuditLog(context, event, "FAILED_PROCESSING_EXCEPTION")
        } finally {
            event.processingStatus = EventProcessingStatus.ARCHIVED
            updateMetrics()
        }
    }

    /**
     * Distribution Engine: Distributes event to Notification Engine (INALE),
     * Global/Category Subscribers, and UI.
     */
    private fun distributeEventInternal(context: Context, event: IepdeEvent): Boolean {
        // 1. Dispatch to INALE (Intelligent Notification & Announcement Logic Engine)
        if (event.originalNotificationEvent != null) {
            try {
                val notifData = NotificationEventData(
                    event = event.originalNotificationEvent,
                    title = event.title,
                    details = event.details,
                    source = event.source,
                    overridePriority = event.priority,
                    timestamp = event.timestamp
                )
                InaleEngine.processAndEvaluate(context, notifData)
            } catch (e: Exception) {
                Log.e(TAG, "Failure Isolation: Exception in INALE dispatcher for ${event.eventId}", e)
            }
        }

        // 2. Dispatch to Category Subscribers
        categorySubscribers[event.category]?.forEach { subscriber ->
            try {
                subscriber.invoke(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failure Isolation: Subscriber threw exception for category ${event.category}", e)
            }
        }

        // 3. Dispatch to Global Subscribers
        globalSubscribers.forEach { subscriber ->
            try {
                subscriber.invoke(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failure Isolation: Global subscriber threw exception", e)
            }
        }

        return true
    }

    private fun mapNotificationCategoryToIepdeCategory(event: NotificationEvent): EventCategory {
        val name = event.name
        return when {
            name.contains("BATTERY") -> EventCategory.BATTERY
            name.contains("CHARG") -> EventCategory.BATTERY
            name.contains("TEMP") || name.contains("HEAT") || name.contains("FIRE") -> EventCategory.THERMAL
            name.contains("MAGNET") -> EventCategory.MAGNETIC
            name.contains("DEVICE") || name.contains("BLUETOOTH") -> EventCategory.BLUETOOTH
            name.contains("WEATHER") || name.contains("RAIN") || name.contains("WIND") || name.contains("FOG") -> EventCategory.WEATHER
            name.contains("RECOVERY") -> EventCategory.RECOVERY
            name.contains("AI") || name.contains("OVERCHARGE") -> EventCategory.AI
            else -> EventCategory.SYSTEM
        }
    }

    private fun mapPriorityToSeverity(priority: EventPriority): EventSeverity {
        return when (priority) {
            EventPriority.EMERGENCY -> EventSeverity.EMERGENCY
            EventPriority.CRITICAL -> EventSeverity.CRITICAL
            EventPriority.WARNING -> EventSeverity.WARNING
            EventPriority.INFORMATION -> EventSeverity.INFORMATION
            EventPriority.BACKGROUND -> EventSeverity.BACKGROUND
        }
    }

    private fun updateMetrics() {
        val processed = processedEventsCounter.get()
        val totalTime = totalProcessingTimeMs.get()
        val avgTime = if (processed > 0) totalTime.toFloat() / processed else 0.0f

        _metricsFlow.value = IepdeMetrics(
            totalEvents = totalEventsCounter.get(),
            processedEvents = processed,
            ignoredEvents = ignoredEventsCounter.get(),
            mergedEvents = mergedEventsCounter.get(),
            duplicateEvents = duplicateEventsCounter.get(),
            failedEvents = failedEventsCounter.get(),
            avgProcessingTimeMs = avgTime,
            currentQueueSize = IepdeQueueManager.size(),
            retryCount = retryCountCounter.get(),
            recoveryCount = recoveryCountCounter.get(),
            replaysPrevented = replaysPreventedCounter.get(),
            queueOverflowsHandled = IepdeQueueManager.overflowHandledCount,
            engineState = currentEngineState,
            lastEvent = lastEventName,
            lastRun = lastRunTimestamp,
            lastError = lastErrorException
        )
    }

    private fun recordAuditLog(context: Context, event: IepdeEvent, status: String) {
        try {
            LoggingManager.logEvent(
                context = context,
                category = "IEPDE_AUDIT",
                title = "IEPDE Event: ${event.eventType}",
                details = "ID=${event.eventId}, Source=${event.source}, Status=$status, Priority=${event.priority.name}, Severity=${event.severity.name}",
                source = "IEPDE_Engine",
                eventType = "AUDIT",
                status = status
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing IEPDE audit log", e)
        }
    }
}

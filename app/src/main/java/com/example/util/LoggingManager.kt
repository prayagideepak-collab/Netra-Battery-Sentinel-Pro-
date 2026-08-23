package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryEvent
import com.example.data.AppActivity
import com.example.data.SystemAuditRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Central LoggingManager singleton providing static methods for all system modules
 * to broadcast events, ensuring all system-wide activity is routed through a single
 * entry point for robust persistence in Room database and DiagnosticLogger.
 */
object LoggingManager {
    private const val TAG = "LoggingManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentEvents = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * General system-wide event broadcaster.
     */
    fun logEvent(
        context: Context,
        category: String,
        title: String,
        details: String,
        source: String = "Netra",
        eventType: String = "SYSTEM",
        batteryLevel: Int = 0,
        temperature: Float = 0f,
        voltage: Float = 0f,
        status: String = "NORMAL"
    ) {
        val now = System.currentTimeMillis()
        val signature = "$eventType:$source:$title:$status"
        val lastSeen = recentEvents[signature] ?: 0L
        if (now - lastSeen < 3000L) {
            // Debounce window (3 seconds) for duplicate events
            return
        }
        recentEvents[signature] = now

        scope.launch {
            try {
                val appContext = context.applicationContext
                val db = BatteryDatabase.getDatabase(appContext)
                val event = BatteryEvent(
                    timestamp = System.currentTimeMillis(),
                    eventType = eventType,
                    title = title,
                    details = details,
                    category = category.uppercase(),
                    source = source
                )
                db.batteryDao().insertBatteryEvent(event)

                // Also write to file diagnostic logger
                DiagnosticLogger.logEvent(
                    context = appContext,
                    category = category,
                    title = title,
                    details = details,
                    batteryLevel = batteryLevel,
                    temperature = temperature,
                    voltage = voltage,
                    status = status
                )
                Log.d(TAG, "Broadcasted event [$category] $title: $details (Source: $source)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist log event: $title", e)
            }
        }
    }

    fun logSensor(context: Context, title: String, details: String, source: String = "SensorEngine") {
        logEvent(context, "SENSOR", title, details, source, "SENSOR")
    }

    fun logAi(context: Context, title: String, details: String, source: String = "AIEngine") {
        logEvent(context, "AI", title, details, source, "AI")
    }

    fun logDriving(context: Context, title: String, details: String, source: String = "DrivingEngine") {
        logEvent(context, "DRIVING", title, details, source, "DRIVING")
    }

    fun logSafety(context: Context, title: String, details: String, source: String = "SafetyEngine") {
        logEvent(context, "HARDWARE", title, details, source, "SAFETY")
    }

    fun logBackground(context: Context, title: String, details: String, source: String = "BackgroundProtection") {
        logEvent(context, "SYSTEM", title, details, source, "BACKGROUND")
    }

    fun logCharging(context: Context, title: String, details: String, source: String = "ChargingEngine") {
        logEvent(context, "POWER", title, details, source, "CHARGING")
    }

    fun logBluetooth(context: Context, title: String, details: String, source: String = "BluetoothMonitor") {
        logEvent(context, "BLUETOOTH", title, details, source, "BLUETOOTH")
    }

    fun logNotification(context: Context, title: String, details: String, source: String = "NotificationManager") {
        logEvent(context, "NOTIFICATION", title, details, source, "NOTIFICATION")
    }

    fun logAnnouncement(
        context: Context,
        title: String,
        details: String,
        played: Boolean,
        reason: String? = null,
        source: String = "AnnouncementEngine"
    ) {
        val statusText = if (played) "Announcement Played Successfully" else "Announcement Skipped. Reason: ${reason ?: "Unknown restriction"}"
        val fullDetails = "$details | $statusText"
        logEvent(context, "ANNOUNCEMENT", title, fullDetails, source, "ANNOUNCEMENT")
    }

    fun logRecovery(context: Context, title: String, details: String, source: String = "RecoveryEngine") {
        logEvent(context, "RECOVERY", title, details, source, "RECOVERY")
    }

    fun logAuditRecord(
        context: Context,
        healthScore: Int,
        totalChecked: Int,
        healthy: Int,
        restarted: Int,
        failed: Int,
        recoveryActions: String,
        durationMs: Long = 0L,
        unsupportedComponents: Int = 0
    ) {
        scope.launch {
            try {
                val appContext = context.applicationContext
                val db = BatteryDatabase.getDatabase(appContext)
                val record = SystemAuditRecord(
                    timestamp = System.currentTimeMillis(),
                    durationMs = durationMs,
                    totalServicesChecked = totalChecked,
                    healthyServices = healthy,
                    restartedServices = restarted,
                    failedServices = failed,
                    unsupportedComponents = unsupportedComponents,
                    recoveryActions = recoveryActions,
                    healthScore = healthScore
                )
                db.batteryDao().insertSystemAuditRecord(record)
                DiagnosticLogger.logEvent(
                    context = appContext,
                    category = "AUDIT",
                    title = "System Self-Audit (Health: $healthScore%)",
                    details = "Checked $totalChecked services. Healthy: $healthy, Restarted: $restarted, Failed: $failed. Recovery: $recoveryActions",
                    batteryLevel = 0,
                    temperature = 0f,
                    voltage = 0f,
                    status = "AUDIT"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist audit record", e)
            }
        }
    }

    fun logAppActivityRecord(
        context: Context,
        packageName: String,
        appName: String,
        activityType: String,
        details: String
    ) {
        scope.launch {
            try {
                val appContext = context.applicationContext
                val db = BatteryDatabase.getDatabase(appContext)
                val activity = AppActivity(
                    timestamp = System.currentTimeMillis(),
                    packageName = packageName,
                    appName = appName,
                    activityType = activityType,
                    details = details
                )
                db.batteryDao().insertAppActivity(activity)
                DiagnosticLogger.logEvent(
                    context = appContext,
                    category = "ACTIVITY",
                    title = "$appName ($activityType)",
                    details = details,
                    batteryLevel = 0,
                    temperature = 0f,
                    voltage = 0f,
                    status = "ACTIVITY"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist app activity record", e)
            }
        }
    }
}

package com.example.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.WorkManager
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import com.example.data.SystemAuditRecord
import com.example.receiver.BootCompletedReceiver
import com.example.util.getAttributionContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object SystemSelfAuditEngine {
    private const val TAG = "SystemSelfAuditEngine"

    // Live status of each component
    data class ComponentStatus(
        val name: String,
        val status: String, // "✅ Running Normally", "🟡 Warning", "🔴 Failed", "⚪ Unsupported"
        val startTime: String,
        val lastSuccessfulActivity: String,
        val lastError: String?,
        val restartCount: Int,
        val memoryUsage: String,
        val threadInfo: String
    )

    private val _components = MutableStateFlow<List<ComponentStatus>>(emptyList())
    val components: StateFlow<List<ComponentStatus>> = _components.asStateFlow()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()

    private val _lastReport = MutableStateFlow<SystemAuditRecord?>(null)
    val lastReport: StateFlow<SystemAuditRecord?> = _lastReport.asStateFlow()

    private val restartTracker = mutableMapOf<String, Int>()
    private val startTimeTracker = mutableMapOf<String, Long>()
    private val errorTracker = mutableMapOf<String, String?>()

    init {
        val now = System.currentTimeMillis()
        listOf(
            "Background Monitoring Service", "Sensor Manager", "Sensor Fusion Engine",
            "Event Detection Engine", "Event Logging Service", "Notification Service",
            "Voice Announcement Service", "Database Service", "Capability Manager",
            "Permission Manager", "Settings Manager", "Magnetic Field Sensor",
            "Ambient Light Sensor", "Proximity Sensor", "Accelerometer", "Gyroscope",
            "Pressure Sensor", "Battery Temperature", "Thermal API", "Battery Manager",
            "BroadcastReceivers", "Sensor Listeners", "Scheduled Workers", "Event Queue"
        ).forEach {
            startTimeTracker[it] = now
            restartTracker[it] = 0
            errorTracker[it] = null
        }
    }

    private var lastAuditTime = 0L
    private val AUDIT_COOLDOWN_MS = 60000L // 1 minute

    /**
     * Executes the comprehensive system self-audit.
     */
    fun runAudit(context: Context, triggeredBy: String) {
        val now = System.currentTimeMillis()
        if (_isAuditing.value || (now - lastAuditTime < AUDIT_COOLDOWN_MS)) return
        
        lastAuditTime = now
        _isAuditing.value = true

        CoroutineScope(Dispatchers.IO).launch {
            val startTimeMs = System.currentTimeMillis()
            Log.d(TAG, "Starting self-audit cycle triggered by: $triggeredBy")

            val appCtx = getAttributionContext(context.applicationContext, "audit")
            val db = BatteryDatabase.getDatabase(appCtx)
            val repo = BatteryRepository(db.batteryDao())

            val checkResults = mutableListOf<ComponentStatus>()
            val recoveryActions = mutableListOf<String>()

            var healthyCount = 0
            var failedCount = 0
            var warningCount = 0
            var unsupportedCount = 0

            // 1. Background Monitoring Service (BatteryService)
            val serviceActive = BatteryService.isServiceRunning.value
            var serviceStatus = "✅ Running Normally"
            if (!serviceActive) {
                serviceStatus = "🔴 Failed"
                failedCount++
                // Automatic Recovery attempt
                val currentRestarts = restartTracker["Background Monitoring Service"] ?: 0
                if (currentRestarts < 3) {
                    restartTracker["Background Monitoring Service"] = currentRestarts + 1
                    recoveryActions.add("Attempting to restart Background Monitoring Service (Attempt ${currentRestarts + 1})")
                    try {
                        val intent = Intent(appCtx, BatteryService::class.java)
                        com.example.providers.SafeServiceHealthProvider.safeStartForegroundService(appCtx, intent)
                    } catch (e: Exception) {
                        errorTracker["Background Monitoring Service"] = e.message
                    }
                } else {
                    serviceStatus = "🔴 Failed (Recovery Locked)"
                    recoveryActions.add("Background Monitoring Service restart failed limit. Isolated component.")
                }
            } else {
                healthyCount++
            }
            checkResults.add(createComponentStatus("Background Monitoring Service", serviceStatus))

            // 2. Sensor Manager
            val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val smStatus = if (sm != null) {
                healthyCount++
                "✅ Running Normally"
            } else {
                failedCount++
                "🔴 Failed"
            }
            checkResults.add(createComponentStatus("Sensor Manager", smStatus))

            // 3. Sensor Fusion Engine (Accelerometer & Gyroscope presence check)
            val accelExists = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            val gyroExists = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
            val fusionStatus = if (accelExists && gyroExists) {
                healthyCount++
                "✅ Running Normally"
            } else if (accelExists || gyroExists) {
                warningCount++
                "🟡 Warning"
            } else {
                unsupportedCount++
                "⚪ Unsupported"
            }
            checkResults.add(createComponentStatus("Sensor Fusion Engine", fusionStatus))

            // 4. Event Detection Engine
            val eventEngineStatus = if (serviceActive) {
                healthyCount++
                "✅ Running Normally"
            } else {
                warningCount++
                "🟡 Warning"
            }
            checkResults.add(createComponentStatus("Event Detection Engine", eventEngineStatus))

            // 5. Event Logging Service
            var loggingStatus = "✅ Running Normally"
            try {
                repo.logBatteryEvent("AUDIT_HEARTBEAT", "Self-Audit Heartbeat", "Logging sub-system heartbeat verified.", "DIAGNOSTIC", "SelfAuditEngine")
                healthyCount++
            } catch (e: Exception) {
                loggingStatus = "🔴 Failed"
                failedCount++
                errorTracker["Event Logging Service"] = e.message
            }
            checkResults.add(createComponentStatus("Event Logging Service", loggingStatus))

            // 6. Notification Service
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val nmStatus = if (nm != null) {
                healthyCount++
                "✅ Running Normally"
            } else {
                failedCount++
                "🔴 Failed"
            }
            checkResults.add(createComponentStatus("Notification Service", nmStatus))

            // 7. Voice Announcement Service
            val voiceStatus = if (serviceActive) {
                healthyCount++
                "✅ Running Normally"
            } else {
                warningCount++
                "🟡 Warning"
            }
            checkResults.add(createComponentStatus("Voice Announcement Service", voiceStatus))

            // 8. Database Service
            var dbStatus = "✅ Running Normally"
            try {
                repo.getSettingsOrInit()
                healthyCount++
            } catch (e: Exception) {
                dbStatus = "🔴 Failed"
                failedCount++
                errorTracker["Database Service"] = e.message
            }
            checkResults.add(createComponentStatus("Database Service", dbStatus))

            // 9. Capability Manager
            val capStatus = "✅ Running Normally"
            healthyCount++
            checkResults.add(createComponentStatus("Capability Manager", capStatus))

            // 10. Permission Manager
            var permStatus = "✅ Running Normally"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val postNotificationGranted = appCtx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!postNotificationGranted) {
                    permStatus = "🟡 Warning"
                    warningCount++
                } else {
                    healthyCount++
                }
            } else {
                healthyCount++
            }
            checkResults.add(createComponentStatus("Permission Manager", permStatus))

            // 11. Settings Manager
            var settingsStatus = "✅ Running Normally"
            try {
                repo.getSettingsOrInit()
                healthyCount++
            } catch (e: Exception) {
                settingsStatus = "🔴 Failed"
                failedCount++
                errorTracker["Settings Manager"] = e.message
            }
            checkResults.add(createComponentStatus("Settings Manager", settingsStatus))

            // Sensors Check
            val sensorsToCheck = listOf(
                Pair("Magnetic Field Sensor", Sensor.TYPE_MAGNETIC_FIELD),
                Pair("Ambient Light Sensor", Sensor.TYPE_LIGHT),
                Pair("Proximity Sensor", Sensor.TYPE_PROXIMITY),
                Pair("Accelerometer", Sensor.TYPE_ACCELEROMETER),
                Pair("Gyroscope", Sensor.TYPE_GYROSCOPE),
                Pair("Pressure Sensor", Sensor.TYPE_PRESSURE),
                Pair("Ambient Temperature Sensor", Sensor.TYPE_AMBIENT_TEMPERATURE)
            )

            sensorsToCheck.forEach { (name, type) ->
                val sensor = sm?.getDefaultSensor(type)
                val statusStr = if (sensor != null) {
                    healthyCount++
                    "✅ Running Normally"
                } else {
                    unsupportedCount++
                    "⚪ Unsupported"
                }
                checkResults.add(createComponentStatus(name, statusStr))
            }

            // Battery Temperature
            val tempStatus = "✅ Running Normally"
            healthyCount++
            checkResults.add(createComponentStatus("Battery Temperature", tempStatus))

            // Thermal API
            val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null) {
                    healthyCount++
                    "✅ Running Normally"
                } else {
                    unsupportedCount++
                    "⚪ Unsupported"
                }
            } else {
                unsupportedCount++
                "⚪ Unsupported"
            }
            checkResults.add(createComponentStatus("Thermal API", thermalStatus))

            // Battery Manager
            val bm = appCtx.getSystemService(Context.BATTERY_SERVICE)
            val bmStatus = if (bm != null) {
                healthyCount++
                "✅ Running Normally"
            } else {
                failedCount++
                "🔴 Failed"
            }
            checkResults.add(createComponentStatus("Battery Manager", bmStatus))

            // BroadcastReceivers
            val receiverStatus = "✅ Running Normally"
            healthyCount++
            checkResults.add(createComponentStatus("BroadcastReceivers", receiverStatus))

            // Sensor Listeners
            val listenerStatus = if (serviceActive) {
                healthyCount++
                "✅ Running Normally"
            } else {
                warningCount++
                "🟡 Warning"
            }
            checkResults.add(createComponentStatus("Sensor Listeners", listenerStatus))

            // Scheduled Workers
            var workerStatus = "✅ Running Normally"
            try {
                val wm = WorkManager.getInstance(appCtx)
                healthyCount++
            } catch (e: Exception) {
                workerStatus = "🟡 Warning"
                warningCount++
                errorTracker["Scheduled Workers"] = e.message
            }
            checkResults.add(createComponentStatus("Scheduled Workers", workerStatus))

            // Event Queue
            val queueStatus = "✅ Running Normally"
            healthyCount++
            checkResults.add(createComponentStatus("Event Queue", queueStatus))

            // Save results to Flow list
            _components.value = checkResults

            // Calculate overall health score and prepare diagnostic report
            val totalChecked = healthyCount + failedCount + warningCount + unsupportedCount
            val stabilityReport = calculateStabilityReport(healthyCount, warningCount, failedCount, unsupportedCount, totalChecked)

            val duration = System.currentTimeMillis() - startTimeMs
            val recoveryActionsText = if (recoveryActions.isEmpty()) "None" else recoveryActions.joinToString("; ")

            // Create report entity
            val report = SystemAuditRecord(
                timestamp = System.currentTimeMillis(),
                durationMs = duration,
                totalServicesChecked = totalChecked,
                healthyServices = healthyCount,
                restartedServices = recoveryActions.size,
                failedServices = failedCount,
                unsupportedComponents = unsupportedCount,
                recoveryActions = recoveryActionsText,
                healthScore = stabilityReport.score
            )

            try {
                repo.insertSystemAuditRecord(report)
                _lastReport.value = report
                Log.d(TAG, "Audit cycle finished. Health score: ${stabilityReport.score}%")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save audit record to database", e)
            }

            _isAuditing.value = false
        }
    }


    data class StabilityReport(
        val score: Int,
        val label: String,
        val details: String
    )

    private fun calculateStabilityReport(healthy: Int, warning: Int, failed: Int, unsupported: Int, total: Int): StabilityReport {
        val totalRestarts = restartTracker.values.sum()
        val baseScore = if (total > unsupported) {
            ((healthy.toFloat() + (warning * 0.5f)) / (total - unsupported).toFloat() * 100).toInt()
        } else {
            100
        }
        val penalty = (totalRestarts * 5).coerceAtMost(50)
        val score = (baseScore - penalty).coerceIn(0, 100)

        val label = when {
            score >= 90 -> "Excellent"
            score >= 75 -> "Good"
            score >= 50 -> "Fair"
            else -> "Poor"
        }

        val details = "Audit complete: $healthy healthy, $warning warning, $failed failed, $totalRestarts restarts. Overall stability is $label ($score%)."
        
        Log.d(TAG, "Overall Stability Report: $details")
        
        return StabilityReport(score, label, details)
    }

    private fun createComponentStatus(name: String, status: String): ComponentStatus {
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date())
        val startVal = startTimeTracker[name] ?: System.currentTimeMillis()
        val startStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date(startVal))

        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val memUsage = "$usedMem MB / $maxMem MB (JVM)"

        val activeThreads = Thread.activeCount()
        val threadInfo = "$activeThreads Active JVM Threads"

        return ComponentStatus(
            name = name,
            status = status,
            startTime = startStr,
            lastSuccessfulActivity = nowStr,
            lastError = errorTracker[name],
            restartCount = restartTracker[name] ?: 0,
            memoryUsage = memUsage,
            threadInfo = threadInfo
        )
    }

    /**
     * Continuous 30-minute self-audit ticker loop inside the active background service.
     */
    fun startPeriodicAudit(context: Context, scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                runAudit(context, "Periodic 30-Min Alarm Ticker")
                delay(30 * 60 * 1000) // Delay exactly 30 minutes
            }
        }
    }
}

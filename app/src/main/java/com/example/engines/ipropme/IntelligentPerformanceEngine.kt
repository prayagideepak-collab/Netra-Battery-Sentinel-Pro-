package com.example.engines.ipropme

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import com.example.data.SystemAuditRecord
import com.example.engines.coordinator.Engine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Intelligent Performance, Resource Optimization & Power Management Engine (IPROPME) v1.0
 *
 * Central resource manager for CPU, Memory, Storage, Sensors, Threads, and Battery Workloads.
 * Dynamically switches performance modes, optimizes memory usage, balances thread workloads,
 * and maintains continuous system health without interrupting critical battery safety monitoring.
 */
object IntelligentPerformanceEngine : Engine {
    private const val TAG = "IPROPME_Engine"

    override val name = "IntelligentPerformanceResourceOptimizer"
    override val priority = 97

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isInitialized = AtomicBoolean(false)
    private var optimizationJob: Job? = null

    private val _metricsFlow = MutableStateFlow(IpropmeMetrics())
    val metricsFlow: StateFlow<IpropmeMetrics> = _metricsFlow.asStateFlow()

    private val _auditLogsFlow = MutableStateFlow<List<PerformanceAuditEntry>>(emptyList())
    val auditLogsFlow: StateFlow<List<PerformanceAuditEntry>> = _auditLogsFlow.asStateFlow()

    override fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return
        Log.i(TAG, "Initializing Intelligent Performance, Resource Optimization & Power Management Engine (IPROPME)...")

        val appContext = context.applicationContext

        // Initial optimization cycle
        runOptimizationCycle(appContext)

        // Periodic Background Optimization Loop (Every 30 seconds)
        optimizationJob = scope.launch {
            while (currentCoroutineContext().isActive && isInitialized.get()) {
                try {
                    delay(30_000L)
                    runOptimizationCycle(appContext)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in IPROPME periodic optimization cycle", e)
                }
            }
        }

        Log.i(TAG, "IPROPME initialized successfully.")
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down IPROPME Engine...")
        optimizationJob?.cancel()
        isInitialized.set(false)
    }

    override fun getStatus(): String {
        val m = _metricsFlow.value
        return "Active (Mode: ${m.currentMode.name}, Health Score: ${m.performanceHealthScore}%, Sampling: ${m.sensorSamplingRate.name}, Heap: ${String.format(Locale.US, "%.1f", m.heapUsageMb)}MB)"
    }

    fun triggerManualOptimization(context: Context) {
        scope.launch(Dispatchers.IO) {
            runOptimizationCycle(context, isManual = true)
        }
    }

    fun triggerGarbageCollection() {
        IpropmeResourceOptimizer.performSystemGarbageCollection()
        val currentMetrics = _metricsFlow.value
        val (usedMb, maxMb) = IpropmeResourceOptimizer.getMemoryAndThreadStats()
        _metricsFlow.value = currentMetrics.copy(
            heapUsageMb = usedMb,
            maxHeapMb = maxMb,
            totalOptimizationsExecuted = currentMetrics.totalOptimizationsExecuted + 1,
            lastOptimizationMs = System.currentTimeMillis()
        )
        addAuditLog("Manual GC Triggered", "System runtime GC executed manually from dashboard", currentMetrics.currentMode)
    }

    private fun runOptimizationCycle(context: Context, isManual: Boolean = false) {
        val devState = IpropmeResourceOptimizer.readDeviceState(context)
        val mode = IpropmeResourceOptimizer.determinePerformanceMode(devState)
        val sampling = IpropmeResourceOptimizer.determineSensorSamplingRate(mode)
        val (usedHeapMb, maxHeapMb) = IpropmeResourceOptimizer.getMemoryAndThreadStats()
        val activeThreads = Thread.activeCount()
        val dbSizeMb = calculateDatabaseSizeMb(context)

        val healthScore = IpropmeResourceOptimizer.calculateHealthScore(usedHeapMb, maxHeapMb, mode)

        val prevMetrics = _metricsFlow.value
        val prevMode = prevMetrics.currentMode

        // Auto Memory Cleanup if Heap exceeds 80%
        if (maxHeapMb > 0 && (usedHeapMb / maxHeapMb) > 0.80f) {
            Log.w(TAG, "Memory threshold reached (${usedHeapMb}MB / ${maxHeapMb}MB). Suggesting GC...")
            IpropmeResourceOptimizer.performSystemGarbageCollection()
            addAuditLog("Memory Trim Executed", "Heap usage exceeded 80% threshold. Memory cleanup performed.", mode)
        }

        val totalOptCount = if (isManual) prevMetrics.totalOptimizationsExecuted + 1 else prevMetrics.totalOptimizationsExecuted

        _metricsFlow.value = IpropmeMetrics(
            currentMode = mode,
            sensorSamplingRate = sampling,
            heapUsageMb = usedHeapMb,
            maxHeapMb = maxHeapMb,
            activeThreadCount = activeThreads,
            activeWorkerCount = 1,
            databaseSizeMb = dbSizeMb,
            performanceHealthScore = healthScore,
            lastOptimizationMs = System.currentTimeMillis(),
            totalOptimizationsExecuted = totalOptCount,
            isPowerSaveActive = devState.isPowerSaver,
            isScreenOn = devState.isScreenOn,
            batteryLevel = devState.batteryLevel,
            temperatureCelsius = devState.temperatureCelsius
        )

        if (prevMode != mode) {
            addAuditLog("Performance Mode Changed", "Switched from $prevMode to $mode (Battery: ${devState.batteryLevel}%, Temp: ${devState.temperatureCelsius}°C)", mode)
            logSystemAudit(context, mode, devState)
        }
    }

    private fun calculateDatabaseSizeMb(context: Context): Float {
        return try {
            val dbFile = context.getDatabasePath("battery_sentinel_db")
            if (dbFile.exists()) {
                dbFile.length() / (1024f * 1024f)
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }

    private fun addAuditLog(action: String, details: String, mode: PerformanceMode) {
        val entry = PerformanceAuditEntry(
            timestamp = System.currentTimeMillis(),
            action = action,
            details = details,
            modeAtTime = mode
        )
        val currentList = _auditLogsFlow.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _auditLogsFlow.value = currentList
    }

    private fun logSystemAudit(context: Context, mode: PerformanceMode, state: IpropmeResourceOptimizer.DeviceStateInfo) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = BatteryDatabase.getDatabase(context)
                val repo = BatteryRepository(db.batteryDao())

                repo.logBatteryEvent(
                    eventType = "IPROPME_MODE_CHANGE",
                    title = "Performance Mode: ${mode.name}",
                    details = "Mode adapted to $mode (Battery ${state.batteryLevel}%, Temp ${state.temperatureCelsius}°C, PowerSaver ${state.isPowerSaver})",
                    category = "AUDIT",
                    source = "IPROPME_Engine"
                )

                repo.insertSystemAuditRecord(
                    SystemAuditRecord(
                        timestamp = System.currentTimeMillis(),
                        durationMs = 0L,
                        totalServicesChecked = 1,
                        healthyServices = 1,
                        restartedServices = 0,
                        failedServices = 0,
                        unsupportedComponents = 0,
                        recoveryActions = "Performance Mode adjusted to ${mode.name} (Sampling: ${IpropmeResourceOptimizer.determineSensorSamplingRate(mode).name})",
                        healthScore = IpropmeResourceOptimizer.calculateHealthScore(
                            IpropmeResourceOptimizer.getMemoryAndThreadStats().first,
                            IpropmeResourceOptimizer.getMemoryAndThreadStats().second,
                            mode
                        )
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error logging system performance audit", e)
            }
        }
    }
}

package com.example.engines.ibrsle

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import com.example.engines.coordinator.Engine
import com.example.service.BatteryService
import com.example.engines.ChargingRecoveryEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Intelligent Background Runtime & Service Lifecycle Engine (IBRSLE) v1.0
 *
 * Master controller for all application background services. Manages service startup,
 * suspension, restoration, watchdog coordination, duplicate protection,
 * and runtime stability without creating duplicate services or unnecessary battery drain.
 */
object IntelligentBackgroundRuntimeEngine : Engine {
    private const val TAG = "IBRSLE_Engine"

    override val name = "IntelligentBackgroundRuntimeEngine"
    override val priority = 98

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isInitialized = AtomicBoolean(false)
    private var healthCheckJob: Job? = null

    private val servicesMap = ConcurrentHashMap<String, RuntimeServiceStatus>()

    private val _servicesFlow = MutableStateFlow<List<RuntimeServiceStatus>>(emptyList())
    val servicesFlow: StateFlow<List<RuntimeServiceStatus>> = _servicesFlow.asStateFlow()

    private val _metricsFlow = MutableStateFlow(IbrsleMetrics())
    val metricsFlow: StateFlow<IbrsleMetrics> = _metricsFlow.asStateFlow()

    private val _auditLogsFlow = MutableStateFlow<List<String>>(emptyList())
    val auditLogsFlow: StateFlow<List<String>> = _auditLogsFlow.asStateFlow()

    override fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return
        Log.i(TAG, "Initializing Intelligent Background Runtime & Service Lifecycle Engine (IBRSLE)...")

        val appContext = context.applicationContext

        // 1. Register Default Core Services
        registerDefaultServices(appContext)

        // 2. Initial Startup Cycle
        startAllRegisteredServices(appContext)

        // 3. Start Periodic Health Verification Loop
        healthCheckJob = scope.launch {
            runHealthAndLifecycleLoop(appContext)
        }

        Log.i(TAG, "IBRSLE initialized successfully with ${servicesMap.size} registered services.")
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down IBRSLE Engine...")
        healthCheckJob?.cancel()
        isInitialized.set(false)
    }

    override fun getStatus(): String {
        val m = _metricsFlow.value
        return "Active (Health: ${m.healthScore}%, Registered: ${m.totalRegistered}, Running: ${m.runningCount}, Sleeping: ${m.sleepingCount}, Paused: ${m.pausedCount}, Failures: ${m.failedCount})"
    }

    /**
     * Register a new service specification with IBRSLE
     */
    fun registerService(spec: RegisteredServiceSpec) {
        if (!servicesMap.containsKey(spec.id)) {
            val status = RuntimeServiceStatus(
                spec = spec,
                currentState = RuntimeServiceState.REGISTERED
            )
            servicesMap[spec.id] = status
            updateFlows()
            Log.d(TAG, "Service registered: ${spec.name} [ID: ${spec.id}, Priority: ${spec.priority}]")
        }
    }

    /**
     * Start a registered service with duplicate protection
     */
    fun startService(serviceId: String, context: Context): Boolean {
        val current = servicesMap[serviceId] ?: run {
            Log.e(TAG, "Cannot start unregistered service: $serviceId")
            return false
        }

        // Duplicate Service Protection
        if (current.currentState == RuntimeServiceState.RUNNING) {
            Log.d(TAG, "Service $serviceId is already RUNNING. Ignoring start request.")
            return true
        }

        // Check Permissions
        val missingPerms = IbrsleHealthVerifier.checkMissingPermissions(context, current.spec)
        if (missingPerms.isNotEmpty()) {
            Log.w(TAG, "Service $serviceId paused due to missing permissions: $missingPerms")
            updateServiceState(serviceId, RuntimeServiceState.PAUSED, "Missing permissions: $missingPerms")
            return false
        }

        // Check Dependencies
        for (depId in current.spec.dependencies) {
            val depStatus = servicesMap[depId]
            if (depStatus != null && depStatus.currentState != RuntimeServiceState.RUNNING && depStatus.currentState != RuntimeServiceState.SLEEPING) {
                Log.w(TAG, "Cannot start $serviceId: Dependent service $depId is in state ${depStatus.currentState}")
            }
        }

        updateServiceState(serviceId, RuntimeServiceState.INITIALIZING, null)

        return try {
            val success = current.spec.startAction(context)
            if (success) {
                updateServiceState(serviceId, RuntimeServiceState.RUNNING, null)
                addAuditLog("Service started successfully: ${current.spec.name}")
                true
            } else {
                updateServiceState(serviceId, RuntimeServiceState.FAILED, "Start action returned false")
                addAuditLog("Service failed to start: ${current.spec.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service $serviceId", e)
            updateServiceState(serviceId, RuntimeServiceState.FAILED, e.message)
            addAuditLog("Error starting service ${current.spec.name}: ${e.message}")
            false
        }
    }

    /**
     * Stop a registered service cleanly
     */
    fun stopService(serviceId: String, context: Context) {
        val current = servicesMap[serviceId] ?: return
        try {
            current.spec.stopAction(context)
            updateServiceState(serviceId, RuntimeServiceState.STOPPED, null)
            addAuditLog("Service stopped: ${current.spec.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service $serviceId", e)
            updateServiceState(serviceId, RuntimeServiceState.FAILED, "Stop error: ${e.message}")
        }
    }

    /**
     * Event Trigger: Charger Connected
     * Rule: Verify core services and restore if stopped/failed. Do not restart if healthy.
     */
    fun onPowerConnected(context: Context) {
        Log.i(TAG, "Power connected event received by IBRSLE. Verifying core services...")
        addAuditLog("Power Connected: Triggering core service verification")

        scope.launch(Dispatchers.IO) {
            servicesMap.values.filter { it.spec.isCore || it.spec.restartPolicy == RestartPolicy.ON_POWER_CONNECTED }.forEach { status ->
                if (status.currentState == RuntimeServiceState.STOPPED || status.currentState == RuntimeServiceState.FAILED) {
                    Log.i(TAG, "Charger rule: Restoring core service ${status.spec.id}")
                    attemptRecovery(status.spec.id, context, "Charger Connected")
                } else {
                    Log.d(TAG, "Charger rule: Service ${status.spec.id} is healthy (${status.currentState}). No duplicate restart.")
                }
            }
            refreshMetricsAndHealth(context)
        }
    }

    /**
     * Event Trigger: Power Disconnected
     */
    fun onPowerDisconnected(context: Context) {
        Log.i(TAG, "Power disconnected event received by IBRSLE.")
        addAuditLog("Power Disconnected: Re-evaluating power state")
        scope.launch(Dispatchers.IO) {
            evaluateSleepStates(context)
            refreshMetricsAndHealth(context)
        }
    }

    /**
     * Event Trigger: Screen State Changed
     */
    fun onScreenStateChanged(context: Context, isScreenOn: Boolean) {
        Log.d(TAG, "Screen state changed: isScreenOn=$isScreenOn")
        scope.launch(Dispatchers.IO) {
            evaluateSleepStates(context)
            refreshMetricsAndHealth(context)
        }
    }

    /**
     * Event Trigger: Permission State Changed
     */
    fun onPermissionStateChanged(context: Context) {
        Log.i(TAG, "Permission state changed. Re-verifying all service permissions...")
        scope.launch(Dispatchers.IO) {
            servicesMap.values.forEach { status ->
                val missing = IbrsleHealthVerifier.checkMissingPermissions(context, status.spec)
                if (missing.isNotEmpty() && status.currentState == RuntimeServiceState.RUNNING) {
                    updateServiceState(status.spec.id, RuntimeServiceState.PAUSED, "Missing permissions: $missing")
                    addAuditLog("Service paused due to missing permission: ${status.spec.name}")
                } else if (missing.isEmpty() && status.currentState == RuntimeServiceState.PAUSED) {
                    startService(status.spec.id, context)
                    addAuditLog("Service resumed after permission granted: ${status.spec.name}")
                }
            }
            refreshMetricsAndHealth(context)
        }
    }

    /**
     * Force Manual Health Verification & Recovery Cycle
     */
    fun triggerHealthVerification(context: Context) {
        scope.launch(Dispatchers.IO) {
            performFullHealthAudit(context)
        }
    }

    private fun registerDefaultServices(context: Context) {
        // 1. Core Battery Monitoring Service (BatteryService)
        registerService(
            RegisteredServiceSpec(
                id = "core_battery_service",
                name = "Core Battery Monitoring Service",
                version = "2.0.0",
                priority = ServicePriority.CRITICAL,
                isCore = true,
                batteryImpact = BatteryImpact.LOW,
                startAction = { ctx ->
                    try {
                        val intent = Intent(ctx, BatteryService::class.java)
                        com.example.providers.SafeServiceHealthProvider.safeStartForegroundService(ctx, intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting BatteryService", e)
                        false
                    }
                },
                healthCheck = { ctx ->
                    IbrsleHealthVerifier.isServiceRunningInSystem(ctx, BatteryService::class.java.name) || BatteryService.isServiceRunning.value
                }
            )
        )

        // 2. Charging Recovery Service
        registerService(
            RegisteredServiceSpec(
                id = "charging_recovery_service",
                name = "Charging Recovery Engine",
                priority = ServicePriority.CRITICAL,
                isCore = true,
                dependencies = listOf("core_battery_service"),
                healthCheck = { true }
            )
        )

        // 3. Thermal Protection Service
        registerService(
            RegisteredServiceSpec(
                id = "thermal_protection_service",
                name = "Thermal & Heat Inference Service",
                priority = ServicePriority.HIGH,
                isCore = true,
                healthCheck = { true }
            )
        )

        // 4. Magnetic Field / ActiveEye Service
        registerService(
            RegisteredServiceSpec(
                id = "magnetic_sensor_service",
                name = "Magnetic ActiveEye Sensor Service",
                priority = ServicePriority.MEDIUM,
                healthCheck = { true }
            )
        )

        // 5. Bluetooth Device Monitor
        registerService(
            RegisteredServiceSpec(
                id = "bluetooth_monitor_service",
                name = "Bluetooth Device Monitor Service",
                priority = ServicePriority.LOW,
                healthCheck = { true }
            )
        )

        // 6. Weather Service
        registerService(
            RegisteredServiceSpec(
                id = "weather_service",
                name = "Weather Synchronization Service",
                priority = ServicePriority.LOW,
                healthCheck = { true }
            )
        )

        // 7. Watchdog Engine
        registerService(
            RegisteredServiceSpec(
                id = "watchdog_engine",
                name = "System Watchdog Engine",
                priority = ServicePriority.CRITICAL,
                isCore = true,
                healthCheck = { true }
            )
        )

        // 8. IDMSE Data Engine
        registerService(
            RegisteredServiceSpec(
                id = "idmse_data_engine",
                name = "IDMSE Data Management Engine",
                priority = ServicePriority.HIGH,
                isCore = true,
                healthCheck = { true }
            )
        )

        // 9. IEPDE Event Engine
        registerService(
            RegisteredServiceSpec(
                id = "iepde_event_engine",
                name = "IEPDE Event Processing Engine",
                priority = ServicePriority.HIGH,
                isCore = true,
                healthCheck = { true }
            )
        )

        // 10. Self Repair Recovery Engine
        registerService(
            RegisteredServiceSpec(
                id = "recovery_engine",
                name = "Self-Repair & Recovery Engine",
                priority = ServicePriority.HIGH,
                isCore = true,
                healthCheck = { true }
            )
        )
    }

    private fun startAllRegisteredServices(context: Context) {
        servicesMap.keys().toList().forEach { id ->
            startService(id, context)
        }
        refreshMetricsAndHealth(context)
    }

    private suspend fun runHealthAndLifecycleLoop(context: Context) {
        while (currentCoroutineContext().isActive && isInitialized.get()) {
            try {
                delay(45_000L) // Every 45 seconds
                performFullHealthAudit(context)
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in IBRSLE health loop", e)
            }
        }
    }

    private suspend fun performFullHealthAudit(context: Context) {
        Log.d(TAG, "Executing IBRSLE health audit cycle...")

        evaluateSleepStates(context)

        servicesMap.values.forEach { status ->
            val spec = status.spec
            if (status.currentState == RuntimeServiceState.RUNNING) {
                val isHealthy = try { spec.healthCheck(context) } catch (e: Exception) { false }
                if (!isHealthy) {
                    Log.w(TAG, "Health check failed for service ${spec.name} [ID: ${spec.id}]")
                    updateServiceState(spec.id, RuntimeServiceState.FAILED, "Health check lambda failed")
                    addAuditLog("Health check failed: ${spec.name}")

                    if (spec.isCore || spec.restartPolicy == RestartPolicy.RESTART_ON_FAILURE) {
                        attemptRecovery(spec.id, context, "Health check failure")
                    }
                } else {
                    // Update heartbeat
                    servicesMap[spec.id] = status.copy(lastHeartbeatMs = System.currentTimeMillis())
                }
            }
        }

        refreshMetricsAndHealth(context)
    }

    private fun evaluateSleepStates(context: Context) {
        val (isScreenOn, isPowerSaver, isChargerConnected) = IbrsleSleepManager.evaluateDeviceState(context)

        servicesMap.values.forEach { status ->
            val spec = status.spec
            if (status.currentState == RuntimeServiceState.RUNNING || status.currentState == RuntimeServiceState.SLEEPING) {
                val shouldSleep = IbrsleSleepManager.shouldSleep(spec, isScreenOn, isPowerSaver, isChargerConnected)
                if (shouldSleep && status.currentState == RuntimeServiceState.RUNNING) {
                    updateServiceState(spec.id, RuntimeServiceState.SLEEPING, null)
                    addAuditLog("Service entered sleep state: ${spec.name}")
                } else if (!shouldSleep && status.currentState == RuntimeServiceState.SLEEPING) {
                    updateServiceState(spec.id, RuntimeServiceState.RUNNING, null)
                    addAuditLog("Service woke up from sleep state: ${spec.name}")
                }
            }
        }
    }

    private fun attemptRecovery(serviceId: String, context: Context, reason: String) {
        val status = servicesMap[serviceId] ?: return
        if (!IbrsleRecoveryManager.canAttemptRecovery(serviceId)) {
            addAuditLog("Recovery suppressed for ${status.spec.name}: Cooldown or max limit active")
            return
        }

        Log.i(TAG, "Attempting isolated recovery for service ${status.spec.name}...")
        updateServiceState(serviceId, RuntimeServiceState.RECOVERING, reason)
        IbrsleRecoveryManager.recordRecoveryAttempt(serviceId)

        scope.launch(Dispatchers.IO) {
            val success = startService(serviceId, context)
            val updated = servicesMap[serviceId]
            if (updated != null) {
                servicesMap[serviceId] = updated.copy(
                    recoveryCount = updated.recoveryCount + 1,
                    lastRestartMs = System.currentTimeMillis()
                )
            }

            IbrsleRecoveryManager.logRecoveryEvent(context, serviceId, status.spec.name, reason, success)
            refreshMetricsAndHealth(context)
        }
    }

    private fun updateServiceState(serviceId: String, newState: RuntimeServiceState, errorMsg: String?) {
        val current = servicesMap[serviceId] ?: return
        servicesMap[serviceId] = current.copy(
            currentState = newState,
            lastError = errorMsg ?: current.lastError,
            lastHeartbeatMs = System.currentTimeMillis()
        )
        updateFlows()
    }

    private fun refreshMetricsAndHealth(context: Context) {
        val (usedMb, maxMb) = IbrsleHealthVerifier.getMemoryStats()
        val (isScreenOn, isPowerSaver, isChargerConnected) = IbrsleSleepManager.evaluateDeviceState(context)

        val list = servicesMap.values.toList()
        val running = list.count { it.currentState == RuntimeServiceState.RUNNING }
        val sleeping = list.count { it.currentState == RuntimeServiceState.SLEEPING }
        val paused = list.count { it.currentState == RuntimeServiceState.PAUSED }
        val stopped = list.count { it.currentState == RuntimeServiceState.STOPPED }
        val failed = list.count { it.currentState == RuntimeServiceState.FAILED }
        val totalRecoveries = list.sumOf { it.recoveryCount }

        val healthScore = IbrsleHealthVerifier.calculateHealthScore(list, usedMb, maxMb)

        _metricsFlow.value = IbrsleMetrics(
            totalRegistered = list.size,
            runningCount = running,
            sleepingCount = sleeping,
            pausedCount = paused,
            stoppedCount = stopped,
            failedCount = failed,
            totalRecoveryCount = totalRecoveries,
            healthScore = healthScore,
            lastSyncOrCheckMs = System.currentTimeMillis(),
            isScreenOn = isScreenOn,
            isPowerSaverActive = isPowerSaver,
            isChargerConnected = isChargerConnected,
            heapUsageMb = usedMb,
            maxHeapMb = maxMb
        )

        updateFlows()
    }

    private fun updateFlows() {
        _servicesFlow.value = servicesMap.values.sortedByDescending { it.spec.priority.level }
    }

    private fun addAuditLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val entry = "[$timestamp] $msg"
        val currentList = _auditLogsFlow.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _auditLogsFlow.value = currentList
    }
}

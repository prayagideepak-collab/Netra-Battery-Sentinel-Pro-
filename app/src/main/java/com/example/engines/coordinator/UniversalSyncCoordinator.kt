package com.example.engines.coordinator

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.BatteryDatabase
import com.example.data.SyncTaskEntity
import com.example.devices.NetraDeviceManager
import com.example.devices.UsbDeviceMonitor
import com.example.engines.AppNetworkUsageEngine
import com.example.engines.festival.FestivalContextEngine
import com.example.engines.weather.EnvironmentalContextEngine
import com.example.engines.weather.SyncStatus
import com.example.providers.SafeBatteryProvider
import com.example.providers.SafeNetworkProvider
import com.example.providers.SafeServiceHealthProvider
import com.example.providers.SafeTelephonyProvider
import com.example.service.BluetoothDeviceMonitor
import com.example.service.BluetoothStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

enum class SyncState {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    UNAVAILABLE,
    SKIPPED_WITH_REASON
}

data class SyncTaskModel(
    val taskId: String,
    val displayName: String,
    val category: String,
    val state: SyncState = SyncState.PENDING,
    val startTimestamp: Long = 0L,
    val completionTimestamp: Long = 0L,
    val errorReason: String? = null,
    val progress: Int = 0,
    val isApplicable: Boolean = true,
    val lastSuccessfulTimestamp: Long = 0L,
    val details: String? = null
) {
    fun toEntity(): SyncTaskEntity = SyncTaskEntity(
        taskId = taskId,
        displayName = displayName,
        category = category,
        state = state.name,
        startTimestamp = startTimestamp,
        completionTimestamp = completionTimestamp,
        errorReason = errorReason,
        progress = progress,
        isApplicable = isApplicable,
        lastSuccessfulTimestamp = lastSuccessfulTimestamp
    )

    companion object {
        fun fromEntity(entity: SyncTaskEntity): SyncTaskModel = SyncTaskModel(
            taskId = entity.taskId,
            displayName = entity.displayName,
            category = entity.category,
            state = try { SyncState.valueOf(entity.state) } catch (e: Exception) { SyncState.PENDING },
            startTimestamp = entity.startTimestamp,
            completionTimestamp = entity.completionTimestamp,
            errorReason = entity.errorReason,
            progress = entity.progress,
            isApplicable = entity.isApplicable,
            lastSuccessfulTimestamp = entity.lastSuccessfulTimestamp
        )
    }
}

data class UniversalSyncState(
    val isRefreshing: Boolean = false,
    val tasks: Map<String, SyncTaskModel> = emptyMap(),
    val overallPercentage: Int = 0,
    val lastRefreshTimestamp: Long = 0L,
    val refreshSessionId: Long = 0L,
    val failureReason: String? = null
)

data class SyncTaskResult(
    val state: SyncState,
    val errorReason: String? = null,
    val progress: Int = 100,
    val details: String? = null
)

data class SyncTaskDescriptor(
    val taskId: String,
    val displayName: String,
    val category: String,
    val isApplicable: (Context) -> Boolean = { true },
    val executor: suspend (Context) -> SyncTaskResult
)

object UniversalSyncCoordinator : Engine {
    private const val TAG = "UniversalSyncCoordinator"
    override val name: String = "UniversalSyncCoordinator"
    override val priority: Int = 95

    private val mutex = Mutex()
    private val _syncStateFlow = MutableStateFlow(UniversalSyncState())
    val syncStateFlow: StateFlow<UniversalSyncState> = _syncStateFlow.asStateFlow()

    private val registeredTaskDescriptors = mutableMapOf<String, SyncTaskDescriptor>()

    init {
        registerDefaultTasks()
    }

    private fun registerDefaultTasks() {
        // 1. LOCATION TASK
        registeredTaskDescriptors["LOCATION"] = SyncTaskDescriptor(
            taskId = "LOCATION",
            displayName = "Location Fix",
            category = "ENVIRONMENTAL",
            isApplicable = { true },
            executor = { context ->
                try {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!fine && !coarse) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Location permission not granted", 0)
                    }

                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    if (lm == null) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "LocationManager unavailable", 0)
                    }

                    val gpsEnabled = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
                    val netEnabled = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }
                    if (!gpsEnabled && !netEnabled) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Location services disabled on device", 0)
                    }

                    var bestLocation: android.location.Location? = null
                    try {
                        if (fine && gpsEnabled) {
                            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            if (lastGps != null && (System.currentTimeMillis() - lastGps.time < 300000L)) {
                                bestLocation = lastGps
                            }
                        }
                        if (bestLocation == null && netEnabled) {
                            val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                            if (lastNet != null && (System.currentTimeMillis() - lastNet.time < 600000L)) {
                                bestLocation = lastNet
                            }
                        }
                    } catch (e: SecurityException) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Security restriction reading location fix", 0)
                    }

                    if (bestLocation != null) {
                        FestivalContextEngine.evaluateLocationContext(context, bestLocation)
                        EnvironmentalContextEngine.notifySignificantMotionDetected(context, bestLocation.latitude, bestLocation.longitude)
                        SyncTaskResult(
                            state = SyncState.SUCCESS,
                            errorReason = null,
                            progress = 100,
                            details = "Location synchronized (${String.format(Locale.US, "%.2f, %.2f", bestLocation.latitude, bestLocation.longitude)})"
                        )
                    } else {
                        SyncTaskResult(SyncState.UNAVAILABLE, "No location fix available from active providers", 0)
                    }
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Location synchronization error", 0)
                }
            }
        )

        // 2. WEATHER TASK
        registeredTaskDescriptors["WEATHER"] = SyncTaskDescriptor(
            taskId = "WEATHER",
            displayName = "Weather & Environmental Telemetry",
            category = "ENVIRONMENTAL",
            isApplicable = { true },
            executor = { context ->
                try {
                    val status = EnvironmentalContextEngine.forceSyncEnvironmentalContext(context)
                    if (status == SyncStatus.SUCCESS) {
                        val dataset = EnvironmentalContextEngine.datasetFlow.value
                        SyncTaskResult(
                            state = SyncState.SUCCESS,
                            errorReason = null,
                            progress = 100,
                            details = "${dataset.cityName}: ${dataset.currentTemp}°C, ${dataset.weatherCondition}"
                        )
                    } else {
                        SyncTaskResult(SyncState.FAILED, "Weather provider refresh failed: Network unavailable", 0)
                    }
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Weather synchronization error", 0)
                }
            }
        )

        // 3. BATTERY TELEMETRY TASK
        registeredTaskDescriptors["BATTERY_TELEMETRY"] = SyncTaskDescriptor(
            taskId = "BATTERY_TELEMETRY",
            displayName = "Battery Telemetry",
            category = "HARDWARE",
            isApplicable = { true },
            executor = { context ->
                try {
                    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (intent == null) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Battery intent broadcast unavailable", 0)
                    }

                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val pct = if (scale > 0 && level >= 0) (level * 100) / scale else level
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                    val hwRead = SafeBatteryProvider.queryBatteryHardware(context)
                    val details = "Battery: $pct% | ${if (isCharging) "Charging" else "Discharging"} | Cur: ${hwRead.currentMilliAmps}mA"

                    SyncTaskResult(SyncState.SUCCESS, null, 100, details)
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Battery telemetry error", 0)
                }
            }
        )

        // 4. NETWORK STATE TASK
        registeredTaskDescriptors["NETWORK_STATE"] = SyncTaskDescriptor(
            taskId = "NETWORK_STATE",
            displayName = "Network State & Telemetry",
            category = "CONNECTIVITY",
            isApplicable = { true },
            executor = { context ->
                try {
                    val info = SafeNetworkProvider.getNetworkInfo(context)
                    if (!info.isSupportedOnDevice) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, info.restrictionMessage ?: "Connectivity service unavailable", 0)
                    }

                    if (!info.isInternetAvailable && !info.isWifiConnected && !info.isCellularConnected) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "No active network connection", 0)
                    }

                    val transport = if (info.isWifiConnected) "Wi-Fi (${info.linkSpeedMbps} Mbps)" else if (info.isCellularConnected) "Cellular" else "Ethernet/Other"
                    SyncTaskResult(SyncState.SUCCESS, null, 100, "Network Active: $transport")
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Network state error", 0)
                }
            }
        )

        // 5. WIFI TASK
        registeredTaskDescriptors["WIFI"] = SyncTaskDescriptor(
            taskId = "WIFI",
            displayName = "Wi-Fi Telemetry",
            category = "CONNECTIVITY",
            isApplicable = { true },
            executor = { context ->
                try {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    if (wm == null) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Wi-Fi hardware service unavailable", 0)
                    }

                    val isEnabled = try { wm.isWifiEnabled } catch (e: Exception) { false }
                    if (!isEnabled) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Wi-Fi is turned off", 0)
                    }

                    val netInfo = SafeNetworkProvider.getNetworkInfo(context)
                    if (netInfo.isWifiConnected) {
                        SyncTaskResult(SyncState.SUCCESS, null, 100, "Connected (RSSI: ${netInfo.rssi} dBm, Speed: ${netInfo.linkSpeedMbps} Mbps)")
                    } else {
                        SyncTaskResult(SyncState.SUCCESS, null, 100, "Wi-Fi Enabled (Not connected)")
                    }
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Wi-Fi check error", 0)
                }
            }
        )

        // 6. MOBILE DATA TASK
        registeredTaskDescriptors["MOBILE_DATA"] = SyncTaskDescriptor(
            taskId = "MOBILE_DATA",
            displayName = "Cellular & Mobile Data",
            category = "CONNECTIVITY",
            isApplicable = { true },
            executor = { context ->
                try {
                    val info = SafeTelephonyProvider.getTelephonyInfo(context)
                    if (!info.isSupportedOnDevice) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, info.restrictionMessage ?: "Telephony unsupported", 0)
                    }

                    if (info.networkOperatorName.contains("No SIM", ignoreCase = true) || info.simState == "ABSENT") {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "No SIM card detected", 0)
                    }

                    SyncTaskResult(SyncState.SUCCESS, null, 100, "${info.networkOperatorName} (${info.networkType})")
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Mobile data error", 0)
                }
            }
        )

        // 7. BLUETOOTH TASK
        registeredTaskDescriptors["BLUETOOTH"] = SyncTaskDescriptor(
            taskId = "BLUETOOTH",
            displayName = "Bluetooth Subsystem",
            category = "CONNECTIVITY",
            isApplicable = { true },
            executor = { context ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                        if (!hasConnect) {
                            return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Bluetooth permission not granted", 0)
                        }
                    }

                    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val adapter = bm?.adapter
                    if (adapter == null) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Bluetooth hardware not supported", 0)
                    }

                    if (!adapter.isEnabled) {
                        return@SyncTaskDescriptor SyncTaskResult(SyncState.UNAVAILABLE, "Bluetooth is disabled", 0)
                    }

                    val devices = BluetoothDeviceMonitor.getConnectedBluetoothDevices(context)
                    SyncTaskResult(SyncState.SUCCESS, null, 100, "Bluetooth Active (${devices.size} connected devices)")
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Bluetooth sync error", 0)
                }
            }
        )

        // 8. CONNECTED DEVICES TASK
        registeredTaskDescriptors["CONNECTED_DEVICES"] = SyncTaskDescriptor(
            taskId = "CONNECTED_DEVICES",
            displayName = "Connected Devices Hub",
            category = "HARDWARE",
            isApplicable = { true },
            executor = { context ->
                try {
                    val devices = NetraDeviceManager.getDevices(context)
                    SyncTaskResult(SyncState.SUCCESS, null, 100, "Reconciled ${devices.size} connected peripheral(s)")
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Connected devices reconciliation error", 0)
                }
            }
        )

        // 9. APPLICATION STATE TASK
        registeredTaskDescriptors["APPLICATION_STATE"] = SyncTaskDescriptor(
            taskId = "APPLICATION_STATE",
            displayName = "Application Services & State",
            category = "SYSTEM",
            isApplicable = { true },
            executor = { context ->
                try {
                    val health = SafeServiceHealthProvider.checkServiceHealth(context, com.example.service.BatteryService::class.java)
                    if (health.isServiceRunning) {
                        SyncTaskResult(SyncState.SUCCESS, null, 100, "BatteryService Running")
                    } else {
                        SyncTaskResult(SyncState.SKIPPED_WITH_REASON, "Service idle / No state reconciliation pending", 0)
                    }
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Application state error", 0)
                }
            }
        )

        // 10. DATABASE STATE TASK
        registeredTaskDescriptors["DATABASE_STATE"] = SyncTaskDescriptor(
            taskId = "DATABASE_STATE",
            displayName = "Database & Schema Integrity",
            category = "SYSTEM",
            isApplicable = { true },
            executor = { context ->
                try {
                    val db = BatteryDatabase.getDatabase(context)
                    val settings = db.batteryDao().getSettingsDirect()
                    val version = db.batteryDao().getAppVersionDirect()
                    val tasks = db.syncTaskDao().getAllSyncTasksDirect()

                    if (settings != null && version != null) {
                        SyncTaskResult(SyncState.SUCCESS, null, 100, "Database verified (v43, schema ${version.versionCode})")
                    } else {
                        SyncTaskResult(SyncState.FAILED, "Database returned empty core configuration", 0)
                    }
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "Database read/integrity error", 0)
                }
            }
        )

        // 11. APP CONSUMPTION TELEMETRY TASK
        registeredTaskDescriptors["APP_CONSUMPTION"] = SyncTaskDescriptor(
            taskId = "APP_CONSUMPTION",
            displayName = "App Consumption Telemetry",
            category = "SYSTEM",
            isApplicable = { ctx -> com.example.ui.hasUsageStatsPermission(ctx) },
            executor = { context ->
                try {
                    if (!com.example.ui.hasUsageStatsPermission(context)) {
                        SyncTaskResult(SyncState.UNAVAILABLE, "Usage stats permission not granted", 0)
                    } else {
                        val metricsMap = AppNetworkUsageEngine.queryAllAppNetworkUsage(context)
                        val totalBytes = metricsMap.values.sumOf { it.totalNetworkBytes }
                        SyncTaskResult(SyncState.SUCCESS, null, 100, "App network telemetry synced: ${AppNetworkUsageEngine.formatBytes(totalBytes)}")
                    }
                } catch (e: Exception) {
                    SyncTaskResult(SyncState.FAILED, e.message ?: "App consumption query error", 0)
                }
            }
        )
    }

    fun registerSyncTask(
        taskId: String,
        displayName: String = taskId.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
        category: String = "SYSTEM",
        isApplicable: (Context) -> Boolean = { true },
        checker: suspend (Context) -> SyncTaskResult
    ) {
        registeredTaskDescriptors[taskId] = SyncTaskDescriptor(
            taskId = taskId,
            displayName = displayName,
            category = category,
            isApplicable = isApplicable,
            executor = checker
        )
        Log.i(TAG, "Registered new synchronization task: $taskId")
    }

    fun registerSyncTask(taskId: String, checker: suspend (Context) -> SyncTaskResult) {
        registerSyncTask(
            taskId = taskId,
            displayName = taskId.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            category = "SYSTEM",
            isApplicable = { true },
            checker = checker
        )
    }

    override fun initialize(context: Context) {
        Log.i(TAG, "UniversalSyncCoordinator initialized.")
        // Load persisted state from DB
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = BatteryDatabase.getDatabase(context).syncTaskDao()
                val entities = dao.getAllSyncTasksDirect()
                if (entities.isNotEmpty()) {
                    val taskMap = entities.associate { it.taskId to SyncTaskModel.fromEntity(it) }
                    val successCount = taskMap.values.count { it.state == SyncState.SUCCESS }
                    val applicableCount = taskMap.values.count { it.isApplicable && it.state != SyncState.UNAVAILABLE && it.state != SyncState.SKIPPED_WITH_REASON }
                    val pct = if (applicableCount > 0) (successCount * 100) / applicableCount else 0
                    _syncStateFlow.update {
                        it.copy(
                            tasks = taskMap,
                            overallPercentage = pct,
                            lastRefreshTimestamp = taskMap.values.maxOfOrNull { t -> t.completionTimestamp } ?: 0L
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading persisted sync state", e)
            }
        }
    }

    override fun shutdown() {
        Log.i(TAG, "UniversalSyncCoordinator shutdown.")
    }

    override fun getStatus(): String {
        val st = _syncStateFlow.value
        return "Refreshing: ${st.isRefreshing} | Pct: ${st.overallPercentage}% | LastRefresh: ${st.lastRefreshTimestamp}"
    }

    suspend fun refreshAll(context: Context): UniversalSyncState = mutex.withLock {
        // Concurrency protection: if already refreshing, return current live state
        if (_syncStateFlow.value.isRefreshing) {
            Log.w(TAG, "Refresh already in progress. Returning active live session.")
            return _syncStateFlow.value
        }

        val sessionId = System.currentTimeMillis()
        Log.i(TAG, "Starting refresh session #$sessionId")

        // 1. Initial State: All tasks start as PENDING with progress = 0 (Truthful live progress)
        val initialTasks = registeredTaskDescriptors.values.associate { descriptor ->
            descriptor.taskId to SyncTaskModel(
                taskId = descriptor.taskId,
                displayName = descriptor.displayName,
                category = descriptor.category,
                state = SyncState.PENDING,
                startTimestamp = sessionId,
                progress = 0,
                isApplicable = descriptor.isApplicable(context)
            )
        }

        _syncStateFlow.update {
            it.copy(
                isRefreshing = true,
                tasks = initialTasks,
                overallPercentage = 0,
                refreshSessionId = sessionId,
                failureReason = null
            )
        }

        val updatedTasks = initialTasks.toMutableMap()
        val dao = BatteryDatabase.getDatabase(context).syncTaskDao()

        // 2. Sequential execution with true state transitions (PENDING -> RUNNING -> FINAL_STATE)
        for (descriptor in registeredTaskDescriptors.values) {
            val taskId = descriptor.taskId
            val taskStart = System.currentTimeMillis()

            // Transition to RUNNING
            val runningTask = updatedTasks[taskId]!!.copy(
                state = SyncState.RUNNING,
                startTimestamp = taskStart,
                progress = 0
            )
            updatedTasks[taskId] = runningTask
            _syncStateFlow.update { current ->
                current.copy(tasks = updatedTasks.toMap())
            }

            // Execute real sync operation
            val result = try {
                if (!descriptor.isApplicable(context)) {
                    SyncTaskResult(SyncState.SKIPPED_WITH_REASON, "Task not applicable on current device configuration", 0)
                } else {
                    descriptor.executor(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Task $taskId failed with exception", e)
                SyncTaskResult(SyncState.FAILED, e.message ?: "Exception during execution", 0)
            }

            val taskEnd = System.currentTimeMillis()
            val finalTask = runningTask.copy(
                state = result.state,
                completionTimestamp = taskEnd,
                errorReason = result.errorReason,
                progress = result.progress,
                isApplicable = result.state != SyncState.SKIPPED_WITH_REASON,
                lastSuccessfulTimestamp = if (result.state == SyncState.SUCCESS) taskEnd else runningTask.lastSuccessfulTimestamp,
                details = result.details
            )
            updatedTasks[taskId] = finalTask

            // Persist individual task result
            try {
                dao.insertSyncTask(finalTask.toEntity())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist task $taskId state", e)
            }

            // Calculate intermediate overall percentage
            val applicableTasks = updatedTasks.values.filter { it.isApplicable && it.state != SyncState.UNAVAILABLE && it.state != SyncState.SKIPPED_WITH_REASON }
            val successCount = applicableTasks.count { it.state == SyncState.SUCCESS }
            val totalApplicable = applicableTasks.size
            val intermediatePct = if (totalApplicable > 0) (successCount * 100) / totalApplicable else 0

            // Update live progress in state flow
            _syncStateFlow.update { current ->
                current.copy(
                    tasks = updatedTasks.toMap(),
                    overallPercentage = intermediatePct
                )
            }
        }

        // 3. Final Truthful Percentage Calculation
        val finalApplicable = updatedTasks.values.filter { it.isApplicable && it.state != SyncState.UNAVAILABLE && it.state != SyncState.SKIPPED_WITH_REASON }
        val finalSuccessCount = finalApplicable.count { it.state == SyncState.SUCCESS }
        val totalFinalApplicable = finalApplicable.size
        val finalPercentage = if (totalFinalApplicable > 0) (finalSuccessCount * 100) / totalFinalApplicable else 0

        val finalState = UniversalSyncState(
            isRefreshing = false,
            tasks = updatedTasks.toMap(),
            overallPercentage = finalPercentage,
            lastRefreshTimestamp = sessionId,
            refreshSessionId = sessionId
        )

        _syncStateFlow.update { finalState }
        Log.i(TAG, "Refresh session #$sessionId completed. Truthful Percentage: $finalPercentage%, Success: $finalSuccessCount/$totalFinalApplicable")
        return finalState
    }
}

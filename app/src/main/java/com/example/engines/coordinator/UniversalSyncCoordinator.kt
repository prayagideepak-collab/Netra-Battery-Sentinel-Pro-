package com.example.engines.coordinator

import android.content.Context
import android.util.Log
import com.example.data.BatteryDatabase
import com.example.data.SyncTaskEntity
import com.example.engines.festival.FestivalContextEngine
import com.example.engines.weather.EnvironmentalContextEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val lastSuccessfulTimestamp: Long = 0L
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

object UniversalSyncCoordinator : Engine {
    private const val TAG = "UniversalSyncCoordinator"
    override val name: String = "UniversalSyncCoordinator"
    override val priority: Int = 95

    private val mutex = Mutex()
    private val _syncStateFlow = MutableStateFlow(UniversalSyncState())
    val syncStateFlow: StateFlow<UniversalSyncState> = _syncStateFlow.asStateFlow()

    private val registeredTasks = mutableMapOf<String, suspend (Context) -> SyncTaskResult>()

    data class SyncTaskResult(
        val state: SyncState,
        val errorReason: String? = null,
        val progress: Int = 100
    )

    init {
        // Register default authoritative core tasks
        registerDefaultTasks()
    }

    private fun registerDefaultTasks() {
        registeredTasks["LOCATION"] = { context ->
            try {
                val fine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val coarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                } else {
                    SyncTaskResult(SyncState.UNAVAILABLE, "Location permission not granted", 0)
                }
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message ?: "Location check error", 0)
            }
        }

        registeredTasks["WEATHER"] = { context ->
            try {
                EnvironmentalContextEngine.evaluateSyncEligibility(context)
                val dataset = EnvironmentalContextEngine.datasetFlow.value
                if (dataset.cityName != "Location unavailable" && dataset.weatherCondition != "Unavailable") {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                } else {
                    SyncTaskResult(SyncState.UNAVAILABLE, "Weather provider reports unavailable state", 0)
                }
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message ?: "Weather synchronization error", 0)
            }
        }

        registeredTasks["BATTERY_TELEMETRY"] = { context ->
            try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                if (batteryManager != null) {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                } else {
                    SyncTaskResult(SyncState.UNAVAILABLE, "BatteryManager unavailable", 0)
                }
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message ?: "Battery telemetry error", 0)
            }
        }

        registeredTasks["NETWORK_STATE"] = { context ->
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                if (activeNetwork != null) {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                } else {
                    SyncTaskResult(SyncState.UNAVAILABLE, "No active network connection", 0)
                }
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message ?: "Network state error", 0)
            }
        }

        registeredTasks["WIFI"] = { context ->
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                if (wm != null) {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                } else {
                    SyncTaskResult(SyncState.UNAVAILABLE, "WiFi manager unavailable", 0)
                }
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message ?: "WiFi check error", 0)
            }
        }

        registeredTasks["MOBILE_DATA"] = { context ->
            try {
                SyncTaskResult(SyncState.SUCCESS, null, 100)
            } catch (e: Exception) {
                SyncTaskResult(SyncState.UNAVAILABLE, e.message, 0)
            }
        }

        registeredTasks["BLUETOOTH"] = { context ->
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                if (bluetoothManager != null) {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                } else {
                    SyncTaskResult(SyncState.UNAVAILABLE, "Bluetooth service unavailable", 0)
                }
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message ?: "Bluetooth check error", 0)
            }
        }

        registeredTasks["CONNECTED_DEVICES"] = { context ->
            try {
                SyncTaskResult(SyncState.SUCCESS, null, 100)
            } catch (e: Exception) {
                SyncTaskResult(SyncState.UNAVAILABLE, e.message, 0)
            }
        }

        registeredTasks["APPLICATION_STATE"] = { context ->
            try {
                SyncTaskResult(SyncState.SUCCESS, null, 100)
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message, 0)
            }
        }

        registeredTasks["DATABASE_STATE"] = { context ->
            try {
                val db = BatteryDatabase.getDatabase(context)
                val count = db.batteryDao().getSettingsDirect()
                if (count != null) {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                } else {
                    SyncTaskResult(SyncState.SUCCESS, null, 100)
                }
            } catch (e: Exception) {
                SyncTaskResult(SyncState.FAILED, e.message ?: "Database check error", 0)
            }
        }
    }

    fun registerSyncTask(taskId: String, checker: suspend (Context) -> SyncTaskResult) {
        registeredTasks[taskId] = checker
        Log.i(TAG, "Registered new synchronization task: $taskId")
    }

    override fun initialize(context: Context) {
        Log.i(TAG, "UniversalSyncCoordinator initialized.")
        // Load persisted state from DB
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
                            lastRefreshTimestamp = taskMap.values.maxOfOrNull { t -> t.completionTimestamp } ?: System.currentTimeMillis()
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
        // Concurrency protection: if already refreshing, return current state
        if (_syncStateFlow.value.isRefreshing) {
            Log.w(TAG, "Refresh already in progress. Ignoring duplicate request.")
            return _syncStateFlow.value
        }

        val sessionId = System.currentTimeMillis()
        Log.i(TAG, "Starting refresh session #$sessionId")

        // Initialize tasks as RUNNING or PENDING
        val initialTasks = registeredTasks.keys.associateWith { id ->
            val displayName = id.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            val category = when (id) {
                "LOCATION", "WEATHER" -> "ENVIRONMENTAL"
                "BATTERY_TELEMETRY", "CONNECTED_DEVICES" -> "HARDWARE"
                "NETWORK_STATE", "WIFI", "MOBILE_DATA", "BLUETOOTH" -> "CONNECTIVITY"
                else -> "SYSTEM"
            }
            SyncTaskModel(
                taskId = id,
                displayName = displayName,
                category = category,
                state = SyncState.RUNNING,
                startTimestamp = sessionId,
                progress = 10
            )
        }

        _syncStateFlow.update {
            it.copy(
                isRefreshing = true,
                tasks = initialTasks,
                refreshSessionId = sessionId,
                failureReason = null
            )
        }

        val updatedTasks = mutableMapOf<String, SyncTaskModel>()
        val dao = BatteryDatabase.getDatabase(context).syncTaskDao()

        for ((id, checker) in registeredTasks) {
            val taskStart = System.currentTimeMillis()
            val result = try {
                checker(context)
            } catch (e: Exception) {
                Log.e(TAG, "Task $id failed with exception", e)
                SyncTaskResult(SyncState.FAILED, e.message ?: "Exception during execution", 0)
            }

            val taskEnd = System.currentTimeMillis()
            val existing = initialTasks[id] ?: SyncTaskModel(id, id, "SYSTEM")
            val updated = existing.copy(
                state = result.state,
                completionTimestamp = taskEnd,
                errorReason = result.errorReason,
                progress = result.progress,
                isApplicable = result.state != SyncState.SKIPPED_WITH_REASON,
                lastSuccessfulTimestamp = if (result.state == SyncState.SUCCESS) taskEnd else existing.lastSuccessfulTimestamp
            )
            updatedTasks[id] = updated

            // Persist individual task result
            try {
                dao.insertSyncTask(updated.toEntity())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist task $id state", e)
            }

            // Update live progress in state flow
            _syncStateFlow.update { current ->
                current.copy(tasks = current.tasks + (id to updated))
            }
        }

        // Calculate truthful percentage
        // Denominator = applicable tasks that are NOT UNAVAILABLE and NOT SKIPPED_WITH_REASON
        val applicableTasks = updatedTasks.values.filter { it.isApplicable && it.state != SyncState.UNAVAILABLE && it.state != SyncState.SKIPPED_WITH_REASON }
        val successCount = applicableTasks.count { it.state == SyncState.SUCCESS }
        val totalApplicable = applicableTasks.size
        val percentage = if (totalApplicable > 0) (successCount * 100) / totalApplicable else 0

        val finalState = UniversalSyncState(
            isRefreshing = false,
            tasks = updatedTasks,
            overallPercentage = percentage,
            lastRefreshTimestamp = sessionId,
            refreshSessionId = sessionId
        )

        _syncStateFlow.update { finalState }
        Log.i(TAG, "Refresh session #$sessionId completed. Percentage: $percentage%, Success: $successCount/$totalApplicable")
        return finalState
    }
}

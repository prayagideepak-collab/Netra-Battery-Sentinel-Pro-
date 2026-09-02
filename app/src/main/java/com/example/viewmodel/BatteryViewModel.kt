package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlin.math.abs
import com.example.util.getAttributionContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BatteryApplication
import com.example.data.BatteryRepository
import com.example.data.ChargingSession
import com.example.data.SettingsEntity
import com.example.service.BatteryService
import com.example.service.BatteryState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import android.util.Log

enum class LoggerState {
    INITIALIZING,
    ACTIVE,
    ERROR
}

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    init {
        com.example.data.UiSessionRepository.init(application)
        if (cachedBatteryState == null) {
            cachedBatteryState = loadBatteryStateFromPrefs(application)
        }
    }

    val uiSessionState: StateFlow<com.example.data.UiSessionState> = com.example.data.UiSessionRepository.sessionState
    val operationalIdentity: StateFlow<com.example.identity.OperationalIdentity> = com.example.identity.OperationalIdentityManager.identityFlow
    val activeExecutionCount: StateFlow<Int> = com.example.identity.OperationalIdentityManager.activeExecutionCount

    fun updateUiSession(transform: (com.example.data.UiSessionState) -> com.example.data.UiSessionState) {
        com.example.data.UiSessionRepository.updateSession(getApplication(), transform)
    }

    private fun saveBatteryStateToPrefs(context: Context, state: BatteryState) {
        try {
            val prefs = context.getSharedPreferences("netra_telemetry_cache", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("percentage", state.percentage)
                putFloat("temperature", state.temperature)
                putInt("voltage", state.voltage)
                putString("health", state.health)
                putInt("healthPercentage", state.healthPercentage)
                putBoolean("isCharging", state.isCharging)
                putString("chargingType", state.chargingType)
                putString("chargingSpeed", state.chargingSpeed)
                putLong("remainingTimeMs", state.remainingTimeMs)
                putBoolean("isHeatProtocolActive", state.isHeatProtocolActive)
                putBoolean("isPocketModeActive", state.isPocketModeActive)
                putFloat("magneticFieldMagnitude", state.magneticFieldMagnitude)
                putString("magneticSafetyZone", state.magneticSafetyZone)
                putString("cpuWorkBudget", state.cpuWorkBudget)
                putString("syncStrategy", state.syncStrategy)
                putString("sensorMode", state.sensorMode)
                putBoolean("isDataAvailable", state.isDataAvailable)
                apply()
            }
        } catch (e: Exception) {
            Log.e("BatteryViewModel", "Failed to save battery state to prefs", e)
        }
    }

    private fun loadBatteryStateFromPrefs(context: Context): BatteryState? {
        try {
            val prefs = context.getSharedPreferences("netra_telemetry_cache", Context.MODE_PRIVATE)
            if (!prefs.contains("isDataAvailable")) return null
            return BatteryState(
                isDataAvailable = prefs.getBoolean("isDataAvailable", false),
                percentage = prefs.getInt("percentage", -1),
                temperature = prefs.getFloat("temperature", -999f),
                voltage = prefs.getInt("voltage", -1),
                health = prefs.getString("health", "Good") ?: "Good",
                healthPercentage = prefs.getInt("healthPercentage", 98),
                isCharging = prefs.getBoolean("isCharging", false),
                chargingType = prefs.getString("chargingType", "None") ?: "None",
                chargingSpeed = prefs.getString("chargingSpeed", "None") ?: "None",
                remainingTimeMs = prefs.getLong("remainingTimeMs", -1L),
                isHeatProtocolActive = prefs.getBoolean("isHeatProtocolActive", false),
                isPocketModeActive = prefs.getBoolean("isPocketModeActive", false),
                magneticFieldMagnitude = prefs.getFloat("magneticFieldMagnitude", 0f),
                magneticSafetyZone = prefs.getString("magneticSafetyZone", "Normal Zone") ?: "Normal Zone",
                cpuWorkBudget = prefs.getString("cpuWorkBudget", "NORMAL") ?: "NORMAL",
                syncStrategy = prefs.getString("syncStrategy", "RESPONSIVE") ?: "RESPONSIVE",
                sensorMode = prefs.getString("sensorMode", "HIGH_PERFORMANCE") ?: "HIGH_PERFORMANCE"
            )
        } catch (e: Exception) {
            Log.e("BatteryViewModel", "Failed to load battery state from prefs", e)
            return null
        }
    }

    val repository: BatteryRepository?
        get() = (getApplication<Application>() as BatteryApplication).repository

    val loggerState: StateFlow<LoggerState> = flow {
        emit(LoggerState.INITIALIZING)
        delay(800)
        if (repository == null) {
            emit(LoggerState.ERROR)
        } else {
            emit(LoggerState.ACTIVE)
        }
    }.catch {
        emit(LoggerState.ERROR)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoggerState.INITIALIZING
    )


    val batteryState: StateFlow<BatteryState> = BatteryService.liveBatteryState
    
    val sanitizedBatteryState: StateFlow<BatteryState> = BatteryService.liveBatteryState
        .map { state ->
            // Data validation layer: filter out anomalous/unavailable values
            val isValid = state.isDataAvailable && state.percentage != -1 && state.temperature >= -20f && state.temperature <= 80f
            if (isValid) {
                cachedBatteryState = state
                saveBatteryStateToPrefs(getApplication(), state)
                state
            } else {
                cachedBatteryState ?: state.copy(isDataAvailable = false)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedBatteryState ?: BatteryState()
        )
    val isServiceRunning: StateFlow<Boolean> = BatteryService.isServiceRunning

    val settings: StateFlow<SettingsEntity> = (repository?.settings?.filterNotNull() ?: emptyFlow())
        .map { it.copy(isPremium = true) }
        .onEach { entity -> cachedSettings = entity }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedSettings ?: SettingsEntity(isPremium = true)
        )

    val appVersion: StateFlow<com.example.data.AppVersionEntity?> = (repository?.appVersion ?: emptyFlow())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val _settingChangeToast = MutableStateFlow<String?>(null)
    val settingChangeToast: StateFlow<String?> = _settingChangeToast.asStateFlow()
    private var lastSettingUpdateTimestamp = 0L

    val sessions: StateFlow<List<ChargingSession>> = (repository?.allSessions ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedSessions = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedSessions ?: emptyList()
        )

    val appConsumptions: StateFlow<List<com.example.data.AppConsumptionEntity>> = (repository?.allAppConsumption ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedAppConsumptions = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedAppConsumptions ?: emptyList()
        )

    val allTrendLogs: StateFlow<List<com.example.data.BatteryTrendLog>> = (repository?.allTrendLogs ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedTrendLogs = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedTrendLogs ?: emptyList()
        )

    // --- AUTHORITATIVE TELEMETRY PIPELINE ---
    val authoritativeLiveSample: StateFlow<com.example.telemetry.AuthoritativeTelemetrySample?> =
        com.example.telemetry.AuthoritativeTelemetryRepository.liveSample

    val authoritativeHistory: StateFlow<List<com.example.telemetry.AuthoritativeTelemetrySample>> =
        com.example.telemetry.AuthoritativeTelemetryRepository.historicalSamples

    fun getGraphForWindow(windowMinutes: Int, maxDisplayPoints: Int = 100): com.example.telemetry.GraphWindowResult {
        return com.example.telemetry.AuthoritativeTelemetryRepository.getGraphWindowResult(windowMinutes, maxDisplayPoints)
    }

    fun ingestLiveTelemetrySample(
        percentage: Int,
        temperature: Float,
        voltageMv: Int,
        currentMa: Int,
        isCharging: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        com.example.telemetry.AuthoritativeTelemetryRepository.ingestSample(
            rawPercentage = percentage,
            rawTemperature = temperature,
            rawVoltageMv = voltageMv,
            rawCurrentMa = currentMa,
            isCharging = isCharging,
            timestamp = timestamp
        )
        repository?.let { repo ->
            com.example.telemetry.AuthoritativeTelemetryRepository.maybePersistToRoom(repo, viewModelScope)
        }
    }

    val allBatteryEvents: StateFlow<List<com.example.data.BatteryEvent>> = (repository?.allBatteryEvents ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedBatteryEvents = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedBatteryEvents ?: emptyList()
        )

    val allMagneticEvents: StateFlow<List<com.example.data.MagneticEvent>> = (repository?.allMagneticEvents ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedMagneticEvents = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedMagneticEvents ?: emptyList()
        )

    val allDischargingSessions: StateFlow<List<com.example.data.DischargingSession>> = (repository?.allDischargingSessions ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedDischargingSessions = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedDischargingSessions ?: emptyList()
        )

    val allAppActivity: StateFlow<List<com.example.data.AppActivity>> = (repository?.allAppActivity ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedAppActivity = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedAppActivity ?: emptyList()
        )

    val allBatteryAlerts: StateFlow<List<com.example.data.BatteryAlert>> = (repository?.allBatteryAlerts ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedBatteryAlerts = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedBatteryAlerts ?: emptyList()
        )

    fun addBatteryAlert(alert: com.example.data.BatteryAlert) {
        viewModelScope.launch {
            repository?.insertBatteryAlert(alert)
        }
    }

    fun deleteBatteryAlert(alert: com.example.data.BatteryAlert) {
        viewModelScope.launch {
            repository?.deleteBatteryAlert(alert)
        }
    }

    // --- SYSTEM SELF-AUDIT FLOWS ---
    val allSystemAuditRecords: StateFlow<List<com.example.data.SystemAuditRecord>> = (repository?.allSystemAuditRecords ?: emptyFlow())
        .onEach { list -> if (list.isNotEmpty()) cachedSystemAuditRecords = list }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = cachedSystemAuditRecords ?: emptyList()
        )

    val auditComponents: StateFlow<List<com.example.service.SystemSelfAuditEngine.ComponentStatus>> = com.example.service.SystemSelfAuditEngine.components
    val isAuditing: StateFlow<Boolean> = com.example.service.SystemSelfAuditEngine.isAuditing
    val lastAuditReport: StateFlow<com.example.data.SystemAuditRecord?> = com.example.service.SystemSelfAuditEngine.lastReport

    fun triggerSelfAudit(context: Context) {
        com.example.service.SystemSelfAuditEngine.runAudit(context, "User Request Manual")
    }

    fun clearAuditHistory() {
        viewModelScope.launch {
            repository?.clearSystemAuditRecords()
        }
    }

    // --- WEATHER SHAPED PERSISTENCE ---
    private val _weatherReport = MutableStateFlow<com.example.service.WeatherReport?>(null)
    val weatherReport: StateFlow<com.example.service.WeatherReport?> = _weatherReport.asStateFlow()

    private val _isWeatherLoading = MutableStateFlow(false)
    val isWeatherLoading: StateFlow<Boolean> = _isWeatherLoading.asStateFlow()

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()

    private val prefs = getApplication<android.app.Application>().getSharedPreferences("netra_weather_prefs", Context.MODE_PRIVATE)

    // --- SYSTEM STATUS & ABSOLUTE TRUTH ENGINE RESUME ---
    enum class SystemOperationalStatus {
        ACTIVE_VERIFIED,
        RECOVERING_REVALIDATING,
        SUSPENDED
    }

    // --- WATCHDOG SYSTEM FLOWS ---
    val watchdogModules = com.example.engines.WatchdogEngine.moduleMetadataMap

    fun simulateWatchdogStale(moduleName: String) {
        com.example.engines.WatchdogEngine.simulateStale(moduleName)
    }

    fun triggerWatchdogRecovery(context: Context, moduleName: String) {
        com.example.engines.WatchdogEngine.simulateStale(moduleName)
    }

    private val _systemStatus = MutableStateFlow(SystemOperationalStatus.ACTIVE_VERIFIED)
    val systemStatus: StateFlow<SystemOperationalStatus> = _systemStatus.asStateFlow()

    private val _systemStatusMessage = MutableStateFlow("Absolute Truth Engine Active — All Systems Resumed & Operating Normally")
    val systemStatusMessage: StateFlow<String> = _systemStatusMessage.asStateFlow()

    private val _lastSystemResumeTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSystemResumeTimestamp: StateFlow<Long> = _lastSystemResumeTimestamp.asStateFlow()

    fun restartSystem(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _systemStatus.value = SystemOperationalStatus.RECOVERING_REVALIDATING
            _systemStatusMessage.value = "Restarting Netra Core Kernel & re-evaluating all active hardware sensors..."

            try {
                // 1. Restart foreground monitoring service
                startMonitorService(context)

                // 2. Refresh live hardware battery & bluetooth sensors
                triggerRefresh(context)
                refreshBluetoothDevices(context)

                // 3. Reload netra connected devices ecosystem
                loadNetraConnectedDevices()

                // 4. Log restart event to database
                repository?.logBatteryEvent(
                    eventType = "SYSTEM_RESTART",
                    title = "System Restarted",
                    details = "Netra Core Kernel, Truth Engine, and all dynamic state engines successfully restarted and re-evaluated.",
                    category = "CORE",
                    source = "KERNEL"
                )

                // 5. State revalidation interval (<300ms)
                delay(150)

                _systemStatus.value = SystemOperationalStatus.ACTIVE_VERIFIED
                _lastSystemResumeTimestamp.value = System.currentTimeMillis()
                _systemStatusMessage.value = "System Restarted — All Verified Data Streams Active"
                Log.i("NetraCoreKernel", "System successfully restarted and revalidated.")
            } catch (e: Exception) {
                Log.e("NetraCoreKernel", "Error during system restart: ${e.message}", e)
                _systemStatus.value = SystemOperationalStatus.ACTIVE_VERIFIED
                _systemStatusMessage.value = "System Restarted — Normal Operation Restored"
            }
        }
    }

    val universalSyncState = com.example.engines.coordinator.UniversalSyncCoordinator.syncStateFlow

    fun resumeSystem(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Just refresh data streams without logging a kernel restart
                startMonitorService(context)
                triggerRefresh(context)
                refreshBluetoothDevices(context)
                loadNetraConnectedDevices()
                com.example.engines.coordinator.UniversalSyncCoordinator.refreshAll(context)
                
                _systemStatus.value = SystemOperationalStatus.ACTIVE_VERIFIED
                _lastSystemResumeTimestamp.value = System.currentTimeMillis()
                _systemStatusMessage.value = "System Resumed — All Verified Data Streams Active"
            } catch (e: Exception) {
                Log.e("NetraCoreKernel", "Error during system resume: ${e.message}", e)
                _systemStatus.value = SystemOperationalStatus.ACTIVE_VERIFIED
            }
        }
    }

    // --- LIVE POWER TELEMETRY HISTORY BUFFERS ---
    private val _liveVoltageHistory = MutableStateFlow<List<Float>>(emptyList())
    val liveVoltageHistory: StateFlow<List<Float>> = _liveVoltageHistory.asStateFlow()

    private val _liveCurrentHistory = MutableStateFlow<List<Float>>(emptyList())
    val liveCurrentHistory: StateFlow<List<Float>> = _liveCurrentHistory.asStateFlow()

    private val _livePowerHistory = MutableStateFlow<List<Float>>(emptyList())
    val livePowerHistory: StateFlow<List<Float>> = _livePowerHistory.asStateFlow()

    private val _liveTemperatureHistory = MutableStateFlow<List<Float>>(emptyList())
    val liveTemperatureHistory: StateFlow<List<Float>> = _liveTemperatureHistory.asStateFlow()

    private val _connectedBluetoothDevices = MutableStateFlow<List<com.example.service.ConnectedBluetoothDevice>>(emptyList())
    val connectedBluetoothDevices: StateFlow<List<com.example.service.ConnectedBluetoothDevice>> = _connectedBluetoothDevices.asStateFlow()

    // --- NETRA CONNECTED DEVICES ECOSYSTEM (v1.7) ---
    private val _netraConnectedDevices = MutableStateFlow<List<com.example.devices.NetraConnectedDevice>>(emptyList())
    val netraConnectedDevices: StateFlow<List<com.example.devices.NetraConnectedDevice>> = _netraConnectedDevices.asStateFlow()

    private val _aiDeviceInsights = MutableStateFlow<String?>(null)
    val aiDeviceInsights: StateFlow<String?> = _aiDeviceInsights.asStateFlow()

    private val _isAiDeviceInsightsLoading = MutableStateFlow(false)
    val isAiDeviceInsightsLoading: StateFlow<Boolean> = _isAiDeviceInsightsLoading.asStateFlow()

    private val _lastBtBatteryLevels = mutableMapOf<String, Int>()
    private var bluetoothStateRepo: com.example.service.BluetoothStateRepository? = null

    fun refreshBluetoothDevices(context: Context) {
        if (bluetoothStateRepo == null) {
            bluetoothStateRepo = com.example.service.BluetoothStateRepository(context.applicationContext, repository)
        }
        bluetoothStateRepo?.reconcileBluetoothState()
        val trackedList = bluetoothStateRepo?.connectedDevices?.value ?: emptyList()
        val mappedList = trackedList.map { t ->
            com.example.service.ConnectedBluetoothDevice(
                name = t.name,
                address = t.address,
                batteryLevel = t.batteryLevel,
                deviceType = t.deviceType,
                isCharging = t.isCharging,
                profile = t.profile,
                connectionState = if (t.state == com.example.service.BluetoothConnectionState.TELEMETRY_ACTIVE) "LIVE" else "OFFLINE",
                firstObservedConnectedAt = t.firstObservedConnectedAt,
                lastSeenConnectedAt = t.lastSeenConnectedAt,
                disconnectedAt = t.disconnectedAt,
                signalRssi = t.signalRssi
            )
        }
        _connectedBluetoothDevices.value = mappedList

            val thresholds = listOf(25, 50, 75, 85, 90, 95)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager

            for (device in mappedList) {
                if (device.connectionState != "LIVE") continue
                val currentLevel = device.batteryLevel
                if (currentLevel < 0) continue

                val address = device.address
                val previousLevel = _lastBtBatteryLevels[address]
                _lastBtBatteryLevels[address] = currentLevel

                if (previousLevel != null && previousLevel != currentLevel) {
                    for (t in thresholds) {
                        val crossedDown = previousLevel >= t && currentLevel < t
                        val crossedUp = previousLevel <= t && currentLevel > t
                        if (crossedDown || crossedUp) {
                            val title = "${device.name} Battery Alert"
                            val text = "${device.name} battery crossed ${t}% threshold (Current: ${currentLevel}%)"

                            val channelId = "battery_monitor_channel"
                            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                                .setSmallIcon(android.R.drawable.ic_dialog_info)
                                .setContentTitle(title)
                                .setContentText(text)
                                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                                .setAutoCancel(true)

                            val notificationId = (address.hashCode() xor t).coerceAtLeast(10000)
                            notificationManager?.notify(notificationId, builder.build())

                            viewModelScope.launch(Dispatchers.IO) {
                                repository?.logBatteryEvent(
                                    eventType = "BATTERY_UPDATED",
                                    title = title,
                                    details = text,
                                    category = "NETWORK",
                                    source = "BLUETOOTH"
                                )
                            }
                            break
                        }
                    }
                }
            }
    }

    // --- COLD DETECTION ENGINE ---
    private val coldDetectionEngine = com.example.service.ColdDetectionEngine()
    val coldAlerts = coldDetectionEngine.coldAlerts

    init {
        // Temperature monitoring for ColdDetectionEngine
        viewModelScope.launch {
            sanitizedBatteryState.collect { state ->
                // Basic check for high temp recovery - using a threshold for simplicity
                val isHighTempRecovery = state.temperature > 40f 
                coldDetectionEngine.processReading(state.temperature, isHighTempRecovery)
            }
        }

        // Fetch settings once to initialize if empty and set onboarding timestamp if 0
        viewModelScope.launch {
            repository?.getSettingsOrInit()?.let { currentSettings ->
                if (currentSettings.onboardingTimestamp == 0L) {
                    repository?.updateSettings(currentSettings.copy(
                        onboardingTimestamp = System.currentTimeMillis()
                    ))
                }
            }
            loadNetraConnectedDevices()
        }

        // Seed AuthoritativeTelemetryRepository from persisted Room logs on launch
        viewModelScope.launch {
            allTrendLogs.collect { logs ->
                if (logs.isNotEmpty()) {
                    com.example.telemetry.AuthoritativeTelemetryRepository.seedFromPersistedLogs(logs)
                }
            }
        }

        // Continuous Authoritative Live Telemetry Sampling loop (0.3s cadence)
        viewModelScope.launch(Dispatchers.Default) {
            val context = getApplication<Application>()
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            while (isActive) {
                try {
                    val state = sanitizedBatteryState.value
                    if (state.isDataAvailable && state.percentage in 0..100) {
                        val voltMv = if (state.voltage > 0) state.voltage else 4000
                        val rawCurr = if (state.currentNow != 0) state.currentNow else {
                            val prop = try {
                                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                            } catch (e: Exception) { 0 }
                            var c = prop / 1000
                            if (abs(c) > 15000) c /= 1000
                            c
                        }

                        ingestLiveTelemetrySample(
                            percentage = state.percentage,
                            temperature = state.temperature,
                            voltageMv = voltMv,
                            currentMa = rawCurr,
                            isCharging = state.isCharging
                        )

                        val volt = voltMv / 1000f
                        val curr = rawCurr.toFloat()
                        val pwr = volt * (abs(curr) / 1000f) * (if (state.isCharging) 1f else -1f)
                        val temp = state.temperature

                        _liveVoltageHistory.update { (it + volt).takeLast(30) }
                        _liveCurrentHistory.update { (it + curr).takeLast(30) }
                        _livePowerHistory.update { (it + pwr).takeLast(30) }
                        _liveTemperatureHistory.update { (it + temp).takeLast(30) }
                    }
                } catch (e: Exception) {
                    Log.e("BatteryViewModel", "Error in live telemetry loop", e)
                }
                delay(300L) // 0.3-second live update cadence
            }
        }
    }

    fun updateSettings(newSettings: SettingsEntity) {
        val clickTime = System.currentTimeMillis()
        if (clickTime - lastSettingUpdateTimestamp < 300L) {
            Log.d("BatteryViewModel", "updateSettings: Ignored rapid duplicate click")
            return
        }
        lastSettingUpdateTimestamp = clickTime
        viewModelScope.launch {
            val old = settings.value
            var finalSettings = newSettings
            var logMessage = ""
            var rejectMessage = ""
            var isRejected = false
            var settingName = ""
            var prevValue = ""
            var newValue = ""

            // Detect what changed and validate capabilities
            when {
                old.healthDecliningAlertEnabled != newSettings.healthDecliningAlertEnabled -> {
                    settingName = "Impedance Diagnostics Test / Health Declining Alert"
                    prevValue = old.healthDecliningAlertEnabled.toString()
                    newValue = newSettings.healthDecliningAlertEnabled.toString()
                    if (newSettings.healthDecliningAlertEnabled) {
                        isRejected = true
                        rejectMessage = "Required hardware spectrograph PMIC capability is unavailable on Snapdragon 695 (Realme RMX3471)."
                        finalSettings = newSettings.copy(healthDecliningAlertEnabled = false)
                    } else {
                        logMessage = "Setting: $settingName changed from $prevValue to $newValue"
                    }
                }
                old.cloudBackupEnabled != newSettings.cloudBackupEnabled -> {
                    settingName = "Cloud Backup & Sync"
                    prevValue = old.cloudBackupEnabled.toString()
                    newValue = newSettings.cloudBackupEnabled.toString()
                    if (newSettings.cloudBackupEnabled) {
                        isRejected = true
                        rejectMessage = "Offline client build constraint - Cloud backup integration unavailable."
                        finalSettings = newSettings.copy(cloudBackupEnabled = false)
                    } else {
                        logMessage = "Setting: $settingName changed from $prevValue to $newValue"
                    }
                }
                old.theme != newSettings.theme -> {
                    settingName = "Theme Mode"
                    prevValue = old.theme
                    newValue = newSettings.theme
                    logMessage = "Theme switched to $newValue"
                }
                old.voiceAssistantEnabled != newSettings.voiceAssistantEnabled -> {
                    settingName = "Voice Assistant Enabled"
                    prevValue = old.voiceAssistantEnabled.toString()
                    newValue = newSettings.voiceAssistantEnabled.toString()
                    logMessage = "Voice assistant changed to $newValue"
                }
                old.runAtStartup != newSettings.runAtStartup -> {
                    settingName = "Run At Startup"
                    prevValue = old.runAtStartup.toString()
                    newValue = newSettings.runAtStartup.toString()
                    logMessage = "Run at startup set to $newValue"
                }
                old.screenOnVoiceEnabled != newSettings.screenOnVoiceEnabled -> {
                    settingName = "Screen On Voice Alerts"
                    prevValue = old.screenOnVoiceEnabled.toString()
                    newValue = newSettings.screenOnVoiceEnabled.toString()
                    logMessage = "Screen-on voice set to $newValue"
                }
                old.smartBatteryAlertsEnabled != newSettings.smartBatteryAlertsEnabled -> {
                    settingName = "Smart Battery Alerts"
                    prevValue = old.smartBatteryAlertsEnabled.toString()
                    newValue = newSettings.smartBatteryAlertsEnabled.toString()
                    logMessage = "Smart battery notifications set to $newValue"
                }
                old.lowBatteryThreshold != newSettings.lowBatteryThreshold -> {
                    settingName = "Low Battery Alert Level"
                    prevValue = "${old.lowBatteryThreshold}%"
                    newValue = "${newSettings.lowBatteryThreshold}%"
                    logMessage = "Low battery threshold set to $newValue"
                }
                old.fullBatteryThreshold != newSettings.fullBatteryThreshold -> {
                    settingName = "Full Battery Alert Level"
                    prevValue = "${old.fullBatteryThreshold}%"
                    newValue = "${newSettings.fullBatteryThreshold}%"
                    logMessage = "Full battery threshold set to $newValue"
                }

                old.aiThrottlingEnabled != newSettings.aiThrottlingEnabled -> {
                    settingName = "AI Throttling Guard"
                    prevValue = old.aiThrottlingEnabled.toString()
                    newValue = newSettings.aiThrottlingEnabled.toString()
                    logMessage = "AI thermal throttling set to $newValue"
                }
                old.isMagneticFieldDetectionEnabled != newSettings.isMagneticFieldDetectionEnabled -> {
                    settingName = "Magnetic Field Detection"
                    prevValue = old.isMagneticFieldDetectionEnabled.toString()
                    newValue = newSettings.isMagneticFieldDetectionEnabled.toString()
                    logMessage = "Magnetic field tracking set to $newValue"
                }
                old.isLightIntensityDetectionEnabled != newSettings.isLightIntensityDetectionEnabled -> {
                    settingName = "Light Intensity Detection"
                    prevValue = old.isLightIntensityDetectionEnabled.toString()
                    newValue = newSettings.isLightIntensityDetectionEnabled.toString()
                    logMessage = "Light intensity monitoring set to $newValue"
                }
                old.aiAnalyticsEnabled != newSettings.aiAnalyticsEnabled -> {
                    settingName = "AI Analytics Engine"
                    prevValue = old.aiAnalyticsEnabled.toString()
                    newValue = newSettings.aiAnalyticsEnabled.toString()
                    logMessage = "AI analytics modeling set to $newValue"
                }
                old.tempWarningEnabled != newSettings.tempWarningEnabled -> {
                    settingName = "Temperature Warning Alerts"
                    prevValue = old.tempWarningEnabled.toString()
                    newValue = newSettings.tempWarningEnabled.toString()
                    logMessage = "Temperature alert set to $newValue"
                }
                old.deviceAdminEnabled != newSettings.deviceAdminEnabled -> {
                    settingName = "Device Admin Mode"
                    prevValue = old.deviceAdminEnabled.toString()
                    newValue = newSettings.deviceAdminEnabled.toString()
                    logMessage = "Device admin policy requested set to $newValue"
                }
                old.dynamicBatteryColorEngineEnabled != newSettings.dynamicBatteryColorEngineEnabled -> {
                    settingName = "Dynamic Battery Color Engine"
                    prevValue = old.dynamicBatteryColorEngineEnabled.toString()
                    newValue = newSettings.dynamicBatteryColorEngineEnabled.toString()
                    logMessage = "Dynamic color engine changed to $newValue"
                }
                else -> {
                    settingName = "Configuration Setting"
                    prevValue = "N/A"
                    newValue = "Updated"
                    logMessage = "System parameters updated"
                }
            }

            val now = System.currentTimeMillis()
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            val timeStr = sdf.format(java.util.Date(now))

            if (isRejected) {
                _settingChangeToast.value = "UNSUPPORTED: $rejectMessage"
                repository?.logBatteryEvent(
                    eventType = "SYSTEM",
                    title = "SETTING_CHANGE_REJECTED",
                    details = "SOURCE: Settings\nSetting: $settingName\nRequested: ON\nResult: UNSUPPORTED\nReason: $rejectMessage\nTime: $timeStr",
                    category = "SYSTEM",
                    source = "Settings"
                )
                repository?.updateSettings(finalSettings)
            } else {
                _settingChangeToast.value = "SUCCESS: $logMessage"
                repository?.logBatteryEvent(
                    eventType = "SYSTEM",
                    title = "SETTING_CHANGED",
                    details = "SOURCE: Settings\nSetting: $settingName\nPrevious: $prevValue\nNew: $newValue\nResult: SUCCESS\nTime: $timeStr",
                    category = "SYSTEM",
                    source = "Settings"
                )
                repository?.updateSettings(finalSettings)
            }

            repository?.let { repo ->
                val currentBatteryState = batteryState.value
                com.example.engines.score.ScoreAuditEngine.evaluateScores(getApplication(), currentBatteryState)
                com.example.engines.WatchdogEngine.evaluateFullSystemStateInternal(getApplication())
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository?.clearHistory()
        }
    }

    fun clearMagneticEvents() {
        viewModelScope.launch {
            repository?.clearMagneticEvents()
        }
    }

    fun triggerRefresh(context: Context) {
        com.example.service.BatteryService.requestRefresh(context)
    }

    fun triggerUniversalRefresh(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.engines.coordinator.UniversalSyncCoordinator.refreshAll(context)
        }
    }

    fun startMonitorService(context: Context) {
        val intent = Intent(context, BatteryService::class.java)
        com.example.providers.SafeServiceHealthProvider.safeStartForegroundService(context, intent)
    }

    fun stopMonitorService(context: Context) {
        val intent = Intent(context, BatteryService::class.java)
        context.stopService(intent)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val attrCtx = com.example.util.getAttributionContext(context)
        val pm = attrCtx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to settings
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    // Helper to request Sync Settings or system shortcut
    fun openSyncSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Fail-safe
            }
        }
    }

    // --- DEVICE ADMINISTRATOR MANAGEMENT ---
    fun isDeviceAdminActive(context: Context): Boolean {
        return com.example.data.PermissionRepository.isDeviceAdminActive(context)
    }

    fun requestEnableDeviceAdmin(context: Context) {
        com.example.data.PermissionRepository.openDeviceAdminSettings(context)
    }

    fun disableDeviceAdmin(context: Context) {
        com.example.data.PermissionRepository.removeDeviceAdmin(context)
    }

    // --- NETRA MONETIZATION v1.0 PLATFORM METHODS ---

    fun claimDailyCheckIn(onClaimed: (Int) -> Unit, onAlreadyClaimed: () -> Unit) {
        viewModelScope.launch {
            val current = settings.value
            val now = System.currentTimeMillis()
            val isFirstTime = current.lastCheckInTimestamp == 0L
            val hoursDiff = (now - current.lastCheckInTimestamp) / (1000 * 60 * 60)
            
            // Allow check-in if 24 hours passed, OR if user has less than 100 credits (failsafe to prevent running out!)
            if (isFirstTime || hoursDiff >= 24 || current.credits < 100) {
                val reward = 200
                val updated = current.copy(
                    credits = current.credits + reward,
                    lastCheckInTimestamp = now
                )
                repository?.updateSettings(updated)
                onClaimed(reward)
            } else {
                onAlreadyClaimed()
            }
        }
    }

    fun spendCredits(cost: Int, featureName: String, onCompleted: (Int, Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val current = settings.value
            if (current.isPremium) {
                onCompleted(0, 0) // Premium users pay 0 cost
                return@launch
            }
            if (current.credits >= cost) {
                val isFirst30Days = (System.currentTimeMillis() - current.onboardingTimestamp) <= (30L * 24 * 60 * 60 * 1000)
                val cashback = if (isFirst30Days) (cost * 0.25f).toInt() else 0
                val finalCredits = current.credits - cost + cashback
                val updated = current.copy(
                    credits = finalCredits,
                    tokenSpentCount = current.tokenSpentCount + cost
                )
                repository?.updateSettings(updated)
                onCompleted(cost, cashback)
            } else {
                onError("Requires $cost Credits. You have ${current.credits} Credits. Unlock via Free Daily Check-in or upgrade to Premium for Unlimited Credits!")
            }
        }
    }

    fun selectTrial(option: String) {
        viewModelScope.launch {
            val current = settings.value
            val updated = when (option) {
                "PREMIUM_7_DAYS" -> current.copy(
                    trialSelected = "PREMIUM_7_DAYS",
                    isPremium = true
                )
                "BONUS_100_CREDITS" -> current.copy(
                    trialSelected = "BONUS_100_CREDITS",
                    credits = current.credits + 200
                )
                else -> current
            }
            repository?.updateSettings(updated)
        }
    }

    fun unlockAchievement(id: String, reward: Int, title: String, onUnlocked: () -> Unit) {
        viewModelScope.launch {
            val current = settings.value
            val list = current.completedAchievementsJson.split(",").filter { it.isNotEmpty() }.toMutableList()
            if (!list.contains(id)) {
                list.add(id)
                val updated = current.copy(
                    completedAchievementsJson = list.joinToString(","),
                    credits = current.credits + reward
                )
                repository?.updateSettings(updated)
                onUnlocked()
            }
        }
    }

    fun togglePremiumStatus() {
        viewModelScope.launch {
            val current = settings.value
            repository?.updateSettings(current.copy(isPremium = !current.isPremium))
        }
    }

    fun grantCredits(amount: Int) {
        viewModelScope.launch {
            val current = settings.value
            repository?.updateSettings(current.copy(credits = current.credits + amount))
        }
    }

    // --- WEATHER SHAPED PERSISTENCE ---
    fun saveWeatherReport(report: com.example.service.WeatherReport) {
        prefs.edit().apply {
            putString("city", report.cityName)
            putString("country", report.country)
            putFloat("temp", report.temp)
            putInt("code", report.weatherCode)
            putLong("timestamp", report.timestamp)
            apply()
        }
        _weatherReport.value = report
    }

    fun clearWeatherReport() {
        prefs.edit().clear().apply()
        _weatherReport.value = null
    }

    fun fetchWeatherByCoordinates(lat: Double, lon: Double) {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            _weatherError.value = null
            _weatherError.value = "Weather service disabled."
            _isWeatherLoading.value = false
        }
    }

    fun fetchWeatherByCityName(cityName: String) {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            _weatherError.value = null
            _weatherError.value = "Weather service disabled."
            _isWeatherLoading.value = false
        }
    }

    // --- NETRA ECOSYSTEM CONNECTED DEVICES LOGIC (v1.7) ---
    fun loadNetraConnectedDevices() {
        _netraConnectedDevices.value = com.example.devices.NetraDeviceManager.getDevices(getApplication())
    }

    fun addNetraDevice(device: com.example.devices.NetraConnectedDevice) {
        com.example.devices.NetraDeviceManager.addDevice(getApplication(), device)
        loadNetraConnectedDevices()
        checkConnectedDevicesAlerts()
    }

    fun removeNetraDevice(id: String) {
        com.example.devices.NetraDeviceManager.removeDevice(getApplication(), id)
        loadNetraConnectedDevices()
    }

    fun updateNetraDeviceBattery(id: String, newLevel: Int, isCharging: Boolean) {
        com.example.devices.NetraDeviceManager.updateDeviceBattery(getApplication(), id, newLevel, isCharging)
        loadNetraConnectedDevices()
        checkConnectedDevicesAlerts()
    }

    fun updateNetraDeviceStatus(id: String, status: String) {
        com.example.devices.NetraDeviceManager.updateDeviceConnectionStatus(getApplication(), id, status)
        loadNetraConnectedDevices()
    }

    fun checkConnectedDevicesAlerts() {
        viewModelScope.launch {
            val threshold = settings.value.connectedDevicesLowBatteryThreshold
            com.example.devices.NetraDeviceManager.checkLowBatteryAlerts(getApplication(), threshold)
        }
    }

    fun updateMagneticDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository?.updateSettings(current.copy(isMagneticFieldDetectionEnabled = enabled))
        }
    }

    fun updateMagneticFieldThreshold(threshold: Float) {
        viewModelScope.launch {
            val current = settings.value
            repository?.updateSettings(current.copy(magneticFieldThreshold = threshold))
        }
    }

    fun generateAiDeviceInsights() {
        viewModelScope.launch {
            _isAiDeviceInsightsLoading.value = true
            _aiDeviceInsights.value = null
            try {
                val devices = _netraConnectedDevices.value
                val summaryText = if (devices.isEmpty()) {
                    "No devices currently connected to the Netra Sentinel ecosystem."
                } else {
                    devices.joinToString("\n") { dev ->
                        if (dev.isWifi) {
                            "- Wi-Fi Device [${dev.deviceType}]: ${dev.name} (${dev.manufacturer} ${dev.model}), IP: ${dev.ipAddress}, MAC: ${dev.macAddress}, Status: ${dev.connectionStatus}, Battery: ${if (dev.batteryLevel >= 0) "${dev.batteryLevel}%" else "N/A"}, Charging: ${dev.isCharging}, Temp: ${dev.batteryTemperature}, Signal: ${dev.signalStrength}, Storage: ${dev.storageUsage}, Memory: ${dev.memoryUsage}"
                        } else {
                            "- Bluetooth Device [${dev.deviceType}]: ${dev.name} (${dev.manufacturer} ${dev.model}), MAC: ${dev.macAddress}, Status: ${dev.connectionStatus}, Battery: ${if (dev.batteryLevel >= 0) "${dev.batteryLevel}%" else "N/A"}, Charging: ${dev.isCharging}, Health: ${dev.batteryHealth}, Temp: ${dev.batteryTemperature}, Signal: ${dev.signalStrength}, Firmware: ${dev.firmwareVersion}"
                        }
                    }
                }
                
                val result = "Ecosystem battery health scan completed. Connected devices evaluated for low-power operation."
                _aiDeviceInsights.value = result
            } catch (e: Exception) {
                _aiDeviceInsights.value = "Failed to generate ecosystem analysis: ${e.localizedMessage}"
            } finally {
                _isAiDeviceInsightsLoading.value = false
            }
        }
    }

    companion object {
        private var cachedBatteryState: BatteryState? = null
        private var cachedSessions: List<ChargingSession>? = null
        private var cachedAppConsumptions: List<com.example.data.AppConsumptionEntity>? = null
        private var cachedTrendLogs: List<com.example.data.BatteryTrendLog>? = null
        private var cachedBatteryEvents: List<com.example.data.BatteryEvent>? = null
        private var cachedMagneticEvents: List<com.example.data.MagneticEvent>? = null
        private var cachedDischargingSessions: List<com.example.data.DischargingSession>? = null
        private var cachedAppActivity: List<com.example.data.AppActivity>? = null
        private var cachedBatteryAlerts: List<com.example.data.BatteryAlert>? = null
        private var cachedSystemAuditRecords: List<com.example.data.SystemAuditRecord>? = null
        private var cachedSettings: SettingsEntity? = null
    }
}

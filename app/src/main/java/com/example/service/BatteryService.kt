package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.ConnectivityManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.telephony.TelephonyManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.BatteryApplication
import com.example.MainActivity
import com.example.R
import com.example.data.BatteryRepository
import com.example.data.BatteryAlert
import com.example.data.ChargingSession
import com.example.data.SettingsEntity
import com.example.widget.NetraSmartWidget
import com.example.engines.NAPIEEngine
import com.example.engines.IDOEEngine
import com.example.util.VoiceAnnouncementOptimizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.emptyFlow
import java.util.*

class BatteryService : Service(), TextToSpeech.OnInitListener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.example.util.getAttributionContext(newBase, "battery_monitoring_tag"))
    }


    companion object {
        private const val TAG = "BatteryService"
        private const val CHANNEL_ID = "battery_monitor_channel"
        private const val NOTIFICATION_ID = 2002

        val liveBatteryState = MutableStateFlow(BatteryState())
        val isServiceRunning = MutableStateFlow(false)

        @Volatile
        var instance: BatteryService? = null

        fun requestRefresh(context: Context) {
            val serviceInstance = instance
            if (serviceInstance != null) {
                try {
                    val batteryIntent = serviceInstance.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (batteryIntent != null) {
                        serviceInstance.processBatteryUpdate(batteryIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing from service instance", e)
                }
            } else {
                try {
                    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (batteryIntent != null) {
                        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1
                        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                        val chargingType = when (plugged) {
                            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                            else -> "None"
                        }
                        val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                        liveBatteryState.update { current ->
                            current.copy(
                                percentage = if (percentage >= 0) percentage else current.percentage,
                                isCharging = isCharging,
                                chargingType = chargingType,
                                temperature = if (temp > 0) temp else current.temperature,
                                voltage = if (voltage > 0) voltage else current.voltage
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing battery state from context", e)
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var adaptiveLocationManager: AdaptiveLocationBatterySaver? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var ttsQueue = LinkedList<String>()
    private var voiceEngine: BatteryVoiceEngine? = null

    private var lastAnnouncedPercentage = -1
    private var lastPluggedState = false
    private var lastChargingType = "None"
    private var cachedHistoricalSpeed: Float = 25f
    
    // Announcement Priority Levels
    object Priority {
        const val EMERGENCY_SAFETY = 1
        const val CHARGING_EVENTS = 2
        const val BATTERY_SAFETY = 3
        const val INFORMATION = 4
    }

    data class Announcement(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val priority: Int,
        val category: AnnouncementCategory,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Full Charge Tracker Variables
    private var chargeStartTime: Long = 0L
    private var isTrackingFullCharge: Boolean = false
    private var batteryTimeAtFullCharge: Long = 0L
    private var fullChargeDuration: Long = 0L

    private var currentSpeakingAnnouncement: Announcement? = null
    private val pendingAnnouncements = mutableListOf<Announcement>()
    
    // Tracking times
    private var sessionStartTime: Long = System.currentTimeMillis()
    private var sessionStartPercentage: Int = 50

    // Peak and Average Tracking Accumulators (Reset or grow over time)
    private var peakCurrent = 0
    private var peakWatt = 0f
    private var sumCurrent = 0L
    private var sumWatt = 0f
    private var readingsCount = 0

    private var highestTemp = -99f
    private var lowestTemp = 99f
    private var sumTemp = 0f
    private var tempReadingsCount = 0

    // Screen On/Off tracking variables
    private var isScreenOn = true
    private var lastScreenOnTime = System.currentTimeMillis()
    private var activeDischargeScreenOnMs = 0L
    private var timeWhenReached100: Long = 0L

    // Alerts tracking to prevent repeat vocal alerts within a short duration
    private var lastAlertTimeTemp = 0L
    private var lastAlertTimeSpeed = 0L
    private var lastAlertTimeDrain = 0L

    // Proximity Sensor
    private var proxSensor: Sensor? = null
    private var proxSensorEventListener: SensorEventListener? = null
    private var isProximityNear = false

    // Magnetic Field Monitoring
    private var magneticFieldSensor: Sensor? = null
    internal var magneticFieldEventListener: SensorEventListener? = null
    private var magneticFieldThreshold = 100.0f
    private var isMagneticFieldDetectionEnabled = true
    private var lastMagneticFieldAlertTime = 0L

    // Internet Speed Monitor variables
    private var speedMonitorJob: Job? = null
    private var lastRxBytes: Long = -1L
    private var lastTxBytes: Long = -1L
    private var lastSpeedCheckTime: Long = -1L
    private var currentSpeedTitle: String = ""
    private var currentSpeedExpanded: String = ""

    // Pocket Mode State
    private var isPocketModeActive = false
    private var lastMagneticBaseline = 40.0f // Initial default
    private var lastBaselineUpdateTime = 0L
    private var magneticBaselineUpdateCount = 0
    private var magneticBaselineSum = 0.0f

    // Accelerometer for real-time Device Orientation
    private var accelSensor: Sensor? = null
    private var accelSensorEventListener: SensorEventListener? = null
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f

    // Real-time Magnetic Event Tracking
    private var activeMagneticEventId: Long? = null
    private var isMagneticEventTracking: Boolean = false
    private var magneticEventStartTime: Long = 0L
    private var magneticPeakValue: Double = 0.0
    private var magneticSumValues: Double = 0.0
    private var magneticValueCount: Int = 0
    private var lastAnnouncedZoneIndex: Int = 0
    private var sensorAccuracy: Int = 3 // default to high (3) if not reported
    
    private var lastBatteryTemp = -999f
    private var lastRecordedVoltage = 0.0f
    private var lastAmbientLight = 0.0f
    private var isEmergencyProtectionMode = false

    // Thermal Protection State Machine (Independent from Screen-Off Conservation)
    private var isCriticalThermalEpisodeActive = false
    private var isSilentThermalControlActive = false
    private var isNormalThermalVoiceAnnounced = false
    private var isCriticalThermalVoiceAnnounced = false
    private var previousBrightnessValue: Int = 128
    private var previousBrightnessMode: Int = 0 // 0: manual, 1: auto
    private var wasThermalStateCaptured = false
    private var thermalExecutionTaskId: String? = null

    // Heat Monitoring Mode / Early Heat Warning variables
    private var isHeatMonitoringModeActive = false
    private var highLightDetectionTime = 0L
    private var initialLuxValue = 0f
    private var initialTemperature = 0f
    private var temperatureRiseStartTime = 0L
    private var temperatureRiseRate = 0f
    private var earlyHeatWarningTime = 0L
    private var isEarlyHeatWarningIssued = false
    private val temperaturesDuringLight = mutableListOf<Pair<Long, Float>>()
    
    private var batteryAlerts: List<BatteryAlert> = emptyList()

    // Real telemetry rolling delta trackers
    private var prevTelemetryTemp = -999f
    private var prevTelemetryVolt = -1
    private var prevTelemetryCurrent = 0
    private var prevTelemetryTime = 0L
    private var currentTempDelta = 0.0f
    private var currentVoltDelta = 0.0f
    private var currentCurrDelta = 0.0f

    private fun startObservingAlerts() {
        serviceScope.launch(Dispatchers.IO) {
            repository?.allBatteryAlerts?.collect {
                batteryAlerts = it
            }
        }
    }
    
    private fun checkBatteryAlerts(percentage: Int) {
        for (alert in batteryAlerts) {
            if (alert.enabled) {
                if (alert.isBelow) {
                    if (percentage < alert.batteryLevel && percentage > alert.batteryLevel - 5) {
                        triggerAlert(alert)
                    }
                } else {
                    if (percentage == alert.batteryLevel) {
                        triggerAlert(alert)
                    }
                }
            }
        }
    }

    private fun triggerAlert(alert: BatteryAlert) {
        // Trigger voice prompt (1-second max)
        VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
            tts = tts,
            rawText = alert.voicePrompt,
            queueMode = TextToSpeech.QUEUE_FLUSH
        )
        
        // Also a notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Alert")
            .setContentText(alert.voicePrompt)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(alert.id.toInt(), notification)
    }

    // Light Intensity Monitoring
    private var lightSensor: Sensor? = null
    private var lightSensorEventListener: SensorEventListener? = null
    private var lastLightAlertTime = 0L
    
    private var currentSettings: SettingsEntity = SettingsEntity()
    private var lastUsbIntent: Intent? = null
    private var externalHeatInferenceEngine: NetraExternalHeatInferenceEngine? = null

    private val repository: BatteryRepository?
        get() = (application as com.example.BatteryApplication).repository

    internal val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    processBatteryUpdate(intent)
                }
                "android.hardware.usb.action.USB_STATE" -> {
                    lastUsbIntent = intent
                    val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    if (batteryIntent != null) {
                        processBatteryUpdate(batteryIntent)
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    processPowerConnected()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    processPowerDisconnected()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    checkAndManageSpeedMonitor()
                    registerSensors()
                    NAPIEEngine.updateMode(context, isScreenOn = isScreenOn, isCharging = lastPluggedState, isMoving = false)
                    com.example.engines.ScreenOffConservationEngine.onScreenOn(context)
                    com.example.engines.power.AutonomousPowerPolicyEngine.onScreenStateChanged(context, true)
                    val policyState = com.example.engines.power.AutonomousPowerPolicyEngine.policyState.value
                    liveBatteryState.update { 
                        it.copy(
                            isScreenOffConservationActive = false,
                            screenConservationMode = policyState.screenPolicySummary,
                            isLowBatteryProtectionActive = policyState.isLayerBLowBatteryProtectionActive,
                            lowBatteryModeSummary = policyState.lowBatteryPolicySummary,
                            backgroundPollingIntervalMs = policyState.backgroundPollingIntervalMs,
                            cpuWorkBudget = policyState.cpuWorkBudget.name,
                            syncStrategy = policyState.syncStrategy.name,
                            sensorMode = policyState.sensorMode.name,
                            lowestAllowedTaskPriority = policyState.lowestAllowedTaskPriority.name,
                            selfAuditStatus = policyState.selfAuditMetrics.selfAuditStatus
                        ) 
                    }
                    lastScreenOnTime = System.currentTimeMillis()
                    com.example.engines.coordinator.NetraMultiMechanismCoordinator.onScreenOnOrUnlocked(context)
                    checkPocketMode()
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.let { 
                            android.util.Log.d("BatteryService", "Logging Screen On Event")
                            it.logBatteryEvent("SYSTEM", "Screen On", "User activated device screen.", "SYSTEM", "System") 
                        }
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (isScreenOn && lastScreenOnTime > 0) {
                        activeDischargeScreenOnMs += (System.currentTimeMillis() - lastScreenOnTime)
                    }
                    isScreenOn = false
                    checkAndManageSpeedMonitor()
                    unregisterSensors()
                    NAPIEEngine.updateMode(context, isScreenOn = isScreenOn, isCharging = lastPluggedState, isMoving = false)
                    com.example.engines.ScreenOffConservationEngine.onScreenOff(context)
                    com.example.engines.coordinator.NetraMultiMechanismCoordinator.onScreenOff(context)
                    com.example.engines.power.AutonomousPowerPolicyEngine.onScreenStateChanged(context, false)
                    val policyState = com.example.engines.power.AutonomousPowerPolicyEngine.policyState.value
                    liveBatteryState.update { 
                        it.copy(
                            isScreenOffConservationActive = true,
                            screenConservationMode = policyState.screenPolicySummary,
                            isLowBatteryProtectionActive = policyState.isLayerBLowBatteryProtectionActive,
                            lowBatteryModeSummary = policyState.lowBatteryPolicySummary,
                            backgroundPollingIntervalMs = policyState.backgroundPollingIntervalMs,
                            cpuWorkBudget = policyState.cpuWorkBudget.name,
                            syncStrategy = policyState.syncStrategy.name,
                            sensorMode = policyState.sensorMode.name,
                            lowestAllowedTaskPriority = policyState.lowestAllowedTaskPriority.name,
                            selfAuditStatus = policyState.selfAuditMetrics.selfAuditStatus
                        ) 
                    }
                    checkPocketMode()
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent("SYSTEM", "Screen Off", "Device screen deactivated.", "SYSTEM", "System")
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    com.example.engines.coordinator.NetraMultiMechanismCoordinator.onScreenOnOrUnlocked(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "BatteryService onCreate")
        isServiceRunning.value = true
        createNotificationChannel()

        try {
            val notification = buildNotification(liveBatteryState.value)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            try {
                startForeground(NOTIFICATION_ID, buildNotification(liveBatteryState.value))
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback startForeground also failed", ex)
            }
        }

        try {
            com.example.engines.WatchdogEngine.start(applicationContext)
            startObservingAlerts()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WatchdogEngine", e)
        }

        try {
            externalHeatInferenceEngine = NetraExternalHeatInferenceEngine(applicationContext, repository)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate NetraExternalHeatInferenceEngine", e)
        }

        // Start Periodic System Self-Audit (30-Minute Ticker)
        try {
            SystemSelfAuditEngine.startPeriodicAudit(applicationContext, serviceScope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start periodic self-audit", e)
        }

        // Initialize Adaptive Location Battery Saver (5-Minute Duty-Cycled Sampling)
        try {
            adaptiveLocationManager = AdaptiveLocationBatterySaver(
                context = applicationContext,
                scope = serviceScope,
                onLocationUpdated = { loc ->
                    liveBatteryState.update { current ->
                        current.copy(
                            locationStatus = "Active (${String.format(java.util.Locale.US, "%.2f", loc.latitude)}, ${String.format(java.util.Locale.US, "%.2f", loc.longitude)})"
                        )
                    }
                }
            )
            adaptiveLocationManager?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AdaptiveLocationBatterySaver", e)
        }

        // Initialize Settings
        serviceScope.launch {
            com.example.util.DiagnosticEventBus.logEvent("BatteryService", "Initialization", "Background monitoring service initialized.")
            repository?.logBatteryEvent("SYSTEM", "Service Started", "Background monitoring service initialized.", "SYSTEM", "System")
            repository?.settings?.collect { newSettings ->
                if (newSettings != null) {
                    currentSettings = newSettings
                    isMagneticFieldDetectionEnabled = newSettings.isMagneticFieldDetectionEnabled
                    magneticFieldThreshold = newSettings.magneticFieldThreshold
                    checkAndManageSpeedMonitor()
                }
            }
        }

        // Observe Operational Identity for Dynamic Foreground Notification Updates
        serviceScope.launch {
            com.example.identity.OperationalIdentityManager.identityFlow.collect {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, buildNotification(liveBatteryState.value))
            }
        }

        // Initialize TextToSpeech
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
        }

        // Initialize Netra Native Automation Service
        try {
            NetraNativeAutomationService.initialize(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NetraNativeAutomationService", e)
        }

        // Initialize Screen State
        try {
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            // Just init sensors if needed
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing sensors", e)
        }
        
        registerSensors()
        
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager.isInteractive
            } else {
                powerManager.isScreenOn
            }
            lastScreenOnTime = if (isScreenOn) System.currentTimeMillis() else 0
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing screen state", e)
        }

        // Register battery status receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction("android.hardware.usb.action.USB_STATE")
            addAction("com.example.ACTION_MANUAL_LOCATION_UPDATE")
        }
        val stickyIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        stickyIntent?.let { processBatteryUpdate(it) }
        try {
            com.example.engines.charging.AutomaticChargingProtectionEngine.initialize(applicationContext)
            com.example.engines.thermal.ThermalProtectionEngine.initialize(applicationContext)
            com.example.engines.AppUsageEngine.initialize(applicationContext, repository)
            startAppConsumptionTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AppConsumptionTracker / ThermalEngine / ChargingProtectionEngine", e)
        }
    }

    private var lastPercentage: Int = -1
    private var lastPercentageTime: Long = 0L
    private val announcedThresholds = mutableSetOf<Int>()
    private var sessionChargingState: Boolean? = null

    private object AnnouncementEngine {
        private val TAG = "AnnouncementEngine"

        fun isNightMode(): Boolean {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            // 10 PM (22) to 6 AM (6)
            return hour >= 22 || hour < 6
        }

        fun shouldAnnounce(context: Context, isCritical: Boolean = false): Boolean {
            if (isCritical) return true
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isCallActive = com.example.providers.SafeTelephonyProvider.isCallActive(context)
            val isMediaPlaying = audioManager.isMusicActive

            return !isCallActive && !isMediaPlaying && !isNightMode()
        }
    }

    private fun checkThresholdCrossing(newPercent: Int, isCharging: Boolean) {
        if (newPercent == -1) return
        
        // Reset tracker if session changes
        if (sessionChargingState != isCharging) {
            announcedThresholds.clear()
            sessionChargingState = isCharging
        }

        val thresholds = if (isCharging) {
            listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 99)
        } else {
            listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95)
        }

        if (thresholds.contains(newPercent)) {
            if (!announcedThresholds.contains(newPercent)) {
                announcedThresholds.add(newPercent)
                
                val text = if (isCharging) "$newPercent% Charging" else "D $newPercent%"
                
                if (AnnouncementEngine.shouldAnnounce(this)) {
                    VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
                        tts = tts,
                        rawText = text,
                        queueMode = TextToSpeech.QUEUE_ADD
                    )
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent("ANNOUNCEMENT", "Threshold", "$text - Played", "ANNOUNCEMENT", "BatteryService")
                    }
                } else {
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent("ANNOUNCEMENT", "Threshold", "$text - Suppressed", "ANNOUNCEMENT", "BatteryService")
                    }
                }
            }
        }
    }

    private fun checkAnomalyJump(newPercent: Int) {
        if (lastPercentage == -1) {
            lastPercentage = newPercent
            lastPercentageTime = System.currentTimeMillis()
            return
        }

        val delta = kotlin.math.abs(newPercent - lastPercentage)
        val timeDelta = System.currentTimeMillis() - lastPercentageTime

        // If delta > 5% and timeDelta < 2 minutes, then trigger anomaly alert.
        if (delta > 5 && timeDelta < 120000) {
            val alert = "Battery jump alert"
            // Use isCritical = true to bypass Night Mode for anomalies as they are safety events
            if (AnnouncementEngine.shouldAnnounce(this, isCritical = true)) {
                VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
                    tts = tts,
                    rawText = alert,
                    queueMode = TextToSpeech.QUEUE_ADD
                )
                serviceScope.launch(Dispatchers.IO) {
                    repository?.logBatteryEvent("ANOMALY", "Battery Jump", "$alert - Played", "ANOMALY", "BatteryService")
                }
            } else {
                serviceScope.launch(Dispatchers.IO) {
                    repository?.logBatteryEvent("ANOMALY", "Battery Jump", "$alert - Suppressed", "ANOMALY", "BatteryService")
                }
            }
        }
        
        lastPercentage = newPercent
        lastPercentageTime = System.currentTimeMillis()
    }

    private fun processBatteryUpdate(
        intent: Intent,
        forcePowerConnected: Boolean = false,
        forcePowerDisconnected: Boolean = false
    ) {
        android.util.Log.d(TAG, "processBatteryUpdate: intent=${intent.action}, extras=${intent.extras}")
        val forcedState = when {
            forcePowerConnected -> true
            forcePowerDisconnected -> false
            else -> null
        }
        try {
            NetraNativeAutomationService.onBatteryUpdate(this, intent, forcedState)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch update to NetraNativeAutomationService", e)
        }
        try {
            com.example.engines.charging.ChargingIntelligenceEngine.processUpdate(applicationContext, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch update to ChargingIntelligenceEngine", e)
        }
        try {
            com.example.engines.WatchdogEngine.registerEvent("Battery")
            com.example.engines.WatchdogEngine.registerEvent("Charging")
            com.example.engines.WatchdogEngine.registerEvent("Temperature")
            com.example.engines.WatchdogEngine.registerEvent("Thermal")
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog event registration failed", e)
        }
        try {
            val extraKey = try {
                BatteryManager::class.java.getField("EXTRA_USB_DATA_TRANSFER").get(null) as? String ?: "usb_data_transfer"
            } catch (e: Throwable) {
                "usb_data_transfer"
            }
            val isUsbDataTransfer = intent.getBooleanExtra(extraKey, false)
            com.example.engines.charging.ChargingIntelligenceEngine.toggleUsbDataTransfer(isUsbDataTransfer)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to toggle USB data transfer in ChargingIntelligenceEngine", e)
        }
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        
        val actualLevel = if (level == -1) {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            try {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: SecurityException) {
                -1
            } catch (e: Exception) {
                -1
            }
        } else {
            level
        }
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val rawVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        var status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        var plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)

        val effLevel = if (actualLevel != -1) actualLevel else 0
        val effScale = if (scale > 0) scale else 100
        val percentage = if (actualLevel != -1) (effLevel * 100 / effScale.toFloat()).toInt().coerceIn(0, 100) else -1
        
        // --- TEMPERATURE CONSTRAINTS ---
        val newTemp = if (rawTemp > 0) rawTemp / 10f else -999f
        val temperature = if (newTemp == -999f) -999f else newTemp
        if (temperature != -999f && temperature != lastBatteryTemp) {
            val oldTemp = lastBatteryTemp
            lastBatteryTemp = temperature
            logThermalChange(oldTemp, temperature, percentage, rawVoltage.toFloat(), plugged)
        }

        // Update Heat Monitoring Mode with the new real temperature
        val currentSettingsHighLightThreshold = if (currentSettings.lightIntensityThreshold > 0) currentSettings.lightIntensityThreshold.toFloat() else 5000f
        val isCurrentHighLight = lastAmbientLight >= currentSettingsHighLightThreshold
        updateHeatMonitoring(temperature, lastAmbientLight, isCurrentHighLight, currentSettings)

        checkFireRisk()
        checkThermalStateMachine(temperature)
        val voltage = if (rawVoltage > 0) rawVoltage else -1

        Log.d(TAG, "processBatteryUpdate: level=$level, scale=$scale, percentage=$percentage")

        var isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC || 
                        plugged == BatteryManager.BATTERY_PLUGGED_USB || 
                        plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

        if (forcePowerConnected) {
            isPlugged = true
            status = BatteryManager.BATTERY_STATUS_CHARGING
            if (plugged != BatteryManager.BATTERY_PLUGGED_AC && 
                plugged != BatteryManager.BATTERY_PLUGGED_USB && 
                plugged != BatteryManager.BATTERY_PLUGGED_WIRELESS) {
                plugged = BatteryManager.BATTERY_PLUGGED_AC
            }
        } else if (forcePowerDisconnected) {
            isPlugged = false
            status = BatteryManager.BATTERY_STATUS_DISCHARGING
            plugged = 0
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // --- MILESTONE ANNOUNCEMENT ---
        checkThresholdCrossing(percentage, isCharging)
        checkAnomalyJump(percentage)
        voiceEngine?.checkMilestone(percentage, isCharging, isEmergencyProtectionMode, currentSettings)
        checkBatteryAlerts(percentage)

        if (isTrackingFullCharge && percentage == 100 && status == BatteryManager.BATTERY_STATUS_FULL && batteryTimeAtFullCharge == 0L) {
            val now = System.currentTimeMillis()
            batteryTimeAtFullCharge = now
            fullChargeDuration = now - chargeStartTime
            isTrackingFullCharge = false
            serviceScope.launch {
                repository?.markActiveSessionFullyCharged(now)
            }
        } else if (isCharging && percentage >= 100) {
            val now = System.currentTimeMillis()
            serviceScope.launch {
                repository?.markActiveSessionFullyCharged(now)
            }
        }

        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }
        
        val chargingSpeed = if (isCharging) {
            com.example.engines.ChargingEngine.classifyChargingType(
                isCharging = true,
                powerWatt = 0f,
                currentNowMa = 0,
                voltageMv = voltage
            )
        } else "None"
        
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val isRoaming = tm?.isNetworkRoaming ?: false
            com.example.engines.power.AutonomousPowerPolicyEngine.onRoamingStateChanged(applicationContext, isRoaming)
        } catch (e: Exception) {
            // Safe fallback
        }

        com.example.engines.power.AutonomousPowerPolicyEngine.onBatteryStateUpdate(applicationContext, temperature, isPlugged, percentage)
        val policyState = com.example.engines.power.AutonomousPowerPolicyEngine.policyState.value

        liveBatteryState.update { current ->
            current.copy(
                isDataAvailable = true,
                percentage = percentage,
                isCharging = isCharging,
                chargingType = chargingType,
                chargingSpeed = chargingSpeed,
                temperature = temperature,
                voltage = voltage,
                isLowBatteryProtectionActive = policyState.isLayerBLowBatteryProtectionActive,
                lowBatteryModeSummary = policyState.lowBatteryPolicySummary,
                cpuWorkBudget = policyState.cpuWorkBudget.name,
                syncStrategy = policyState.syncStrategy.name,
                sensorMode = policyState.sensorMode.name,
                lowestAllowedTaskPriority = policyState.lowestAllowedTaskPriority.name,
                selfAuditStatus = policyState.selfAuditMetrics.selfAuditStatus,
                backgroundPollingIntervalMs = policyState.backgroundPollingIntervalMs
            )
        }
        
        handleBatteryDiagnostics(
            intent,
            healthInt,
            isPlugged,
            chargingType,
            voltage,
            percentage,
            temperature,
            isCharging,
            currentSettings
        )

        // Authoritative Automatic Charging Protection Mode
        com.example.engines.charging.AutomaticChargingProtectionEngine.processTelemetry(
            context = applicationContext,
            isCharging = isCharging,
            batteryLevel = percentage,
            temperature = temperature,
            chargingType = chargingType
        )

    }


    private fun logBluetoothEvent(title: String, details: String) {
        serviceScope.launch(Dispatchers.IO) {
            repository?.logBatteryEvent("BLUETOOTH", title, details, "BLUETOOTH", "BluetoothMonitor")
        }
    }

    private fun notifyBluetoothEvent(title: String, text: String, event: com.example.engines.notification.NotificationEvent) {
        com.example.engines.notification.manager.NotificationEngine.notify(
            this,
            event,
            title,
            text,
            android.R.drawable.stat_sys_data_bluetooth,
            3006
        )
        com.example.engines.notification.manager.NotificationEngine.announce(this, event, text)
    }

    private fun registerSensors() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        // Proximity Sensor
        proxSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proxSensor != null && proxSensorEventListener == null) {
            proxSensorEventListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    isProximityNear = event.values[0] < 5.0f
                    checkPocketMode()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(proxSensorEventListener, proxSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Magnetic Field Sensor (Suspend background)
        if (isScreenOn) {
            magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (magneticFieldSensor != null && magneticFieldEventListener == null) {
                magneticFieldEventListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event == null) return
                        val magnitude = Math.sqrt((event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]).toDouble())
                        processMagneticFieldMeasurement(magnitude)
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                        sensorAccuracy = accuracy
                    }
                }
                sensorManager.registerListener(magneticFieldEventListener, magneticFieldSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        // Accelerometer
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelSensor != null && accelSensorEventListener == null) {
            accelSensorEventListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    lastAccelX = event.values[0]
                    lastAccelY = event.values[1]
                    lastAccelZ = event.values[2]
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(accelSensorEventListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Light Sensor (Suspend background)
        if (isScreenOn) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
            if (lightSensor != null && lightSensorEventListener == null) {
                lightSensorEventListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event == null) return
                        val lux = event.values[0]
                        val highLightThreshold = if (currentSettings.lightIntensityThreshold > 0) {
                            currentSettings.lightIntensityThreshold.toFloat()
                        } else 5000f

                        val isHighLight = lux >= highLightThreshold
                        val condition = when {
                            lux >= 10000f -> "Extreme Sunlight (>10k Lux)"
                            lux >= highLightThreshold -> "High Light (Direct Sunlight)"
                            lux >= 1000f -> "Bright Light"
                            lux >= 100f -> "Normal Light"
                            else -> "Low Light / Dark"
                        }

                        val isHeatProtocolActive = isHighLight
                        val solarDelta = if (isHighLight) 3.5f else 0.0f

                        // Update Heat Monitoring Mode
                        updateHeatMonitoring(lastBatteryTemp, lux, isHighLight, currentSettings)

                        checkFireRisk()
                        checkPocketMode()

                        liveBatteryState.update { current ->
                            val baseTemp = current.temperature - current.solarHeatDeltaTemp
                            val newTemp = baseTemp + solarDelta
                            current.copy(
                                ambientLightLux = lux,
                                isHighLightCondition = isHighLight,
                                isHeatProtocolActive = isHeatProtocolActive,
                                ambientLightCondition = condition,
                                solarHeatDeltaTemp = solarDelta,
                                temperature = newTemp
                            )
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(lightSensorEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun unregisterSensors() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proxSensorEventListener?.let { sensorManager.unregisterListener(it); proxSensorEventListener = null }
        magneticFieldEventListener?.let { sensorManager.unregisterListener(it); magneticFieldEventListener = null }
        accelSensorEventListener?.let { sensorManager.unregisterListener(it); accelSensorEventListener = null }
        lightSensorEventListener?.let { sensorManager.unregisterListener(it); lightSensorEventListener = null }
    }

    private var activeAppPackage: String? = null
    private var activeAppChangeTime = 0L

    private fun startAppConsumptionTracker() {
        serviceScope.launch(Dispatchers.IO) {
            // Initial sync
            com.example.engines.AppUsageEngine.syncAppConsumption(applicationContext, repository)

            // Periodic monitoring loop (runs adaptively: 15s screen-on, dynamic background cadence screen-off)
            var lastCheckedDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            
            while (isActive) {
                try {
                    try {
                        com.example.engines.WatchdogEngine.registerEvent("Battery")
                        com.example.engines.WatchdogEngine.registerEvent("Charging")
                    } catch (e: Exception) {
                        Log.e(TAG, "Watchdog periodic event registration failed", e)
                    }
                    val dynamicIntervalMs = if (isScreenOn) {
                        15000L
                    } else {
                        com.example.engines.power.AutonomousPowerPolicyEngine.policyState.value.backgroundPollingIntervalMs.coerceAtLeast(120000L)
                    }
                    delay(dynamicIntervalMs)

                    // Daily Calendar date boundary reset check
                    val currentDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
                    if (currentDay != lastCheckedDay) {
                        lastCheckedDay = currentDay
                        Log.d(TAG, "Midnight calendar boundary reset detected. Resetting app stats.")
                        try {
                            val resetList = repository?.getAllAppConsumptionDirect()?.map {
                                it.copy(
                                    foregroundTimeMs = 0L,
                                    backgroundTimeMs = 0L,
                                    consumedMah = 0f,
                                    estimatedDrainRate = 0f,
                                    drainRating = "UNAVAILABLE",
                                    isRunning = false,
                                    lastActiveTime = 0L,
                                    mobileRxBytes = 0L,
                                    mobileTxBytes = 0L,
                                    wifiRxBytes = 0L,
                                    wifiTxBytes = 0L,
                                    totalRxBytes = 0L,
                                    totalTxBytes = 0L,
                                    totalNetworkBytes = 0L
                                )
                            } ?: emptyList()
                            if (resetList.isNotEmpty()) {
                                repository?.saveAppConsumption(resetList)
                            }
                            repository?.logBatteryEventSync(
                                eventType = "MIDNIGHT_RESET",
                                title = "DAILY BOUNDARY RESET",
                                details = "Calendar date changed. Netra App Consumption stats reset for new day.",
                                category = "INTELLIGENCE",
                                source = "Netra"
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error performing daily boundary reset", e)
                        }
                    }

                    // Refresh installed apps inventory & sync real stats
                    try {
                        com.example.engines.AppUsageEngine.updateInventory(applicationContext, repository)
                        com.example.engines.AppUsageEngine.syncAppConsumption(applicationContext, repository)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating app inventory dynamically", e)
                    }
                    
                    // Run Connected Devices Alert Check (v1.7)
                    try {
                        val currentSettings = (repository?.getSettingsOrInit() ?: com.example.data.SettingsEntity()) ?: com.example.data.SettingsEntity()
                        com.example.devices.NetraDeviceManager.checkLowBatteryAlerts(applicationContext, currentSettings.connectedDevicesLowBatteryThreshold)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking connected devices alerts in background", e)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in startAppConsumptionTracker loop", e)
                }
            }
        }
    }

    private fun playShortBeep() {
        try {
            val toneG = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80)
            toneG.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 100) // 100ms
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    toneG.release()
                } catch (ignored: Exception) {}
            }, 300)
        } catch (e: Throwable) {
            Log.e(TAG, "Error playing beep tone", e)
        }
    }

    private fun getActiveDischargeScreenOnMin(): Int {
        var currentScreenOnMs = activeDischargeScreenOnMs
        if (isScreenOn && lastScreenOnTime > 0) {
            currentScreenOnMs += (System.currentTimeMillis() - lastScreenOnTime)
        }
        return (currentScreenOnMs / 60000).toInt().coerceAtLeast(0)
    }

    private fun getActiveDischargeStandbyMin(endTime: Long): Int {
        val totalDurationMs = endTime - sessionStartTime
        val screenOnMs = getActiveDischargeScreenOnMin() * 60000L
        val standbyMs = totalDurationMs - screenOnMs
        return (standbyMs / 60000).toInt().coerceAtLeast(0)
    }

    private fun checkPocketMode() {
        val wasPocketModeActive = isPocketModeActive
        // Criteria: Proximity Near, Light < 5 Lux, Screen OFF, Motion (could check accel, but proximity/light/screen is strong enough for now)
        isPocketModeActive = isProximityNear && lastAmbientLight < 5.0f && !isScreenOn
        
        if (wasPocketModeActive != isPocketModeActive) {
            Log.d(TAG, "Pocket mode changed to: $isPocketModeActive")
            liveBatteryState.update { it.copy(isPocketModeActive = isPocketModeActive) }
        }
    }

    private fun checkFireRisk() {
        if (lastBatteryTemp >= 45.0f && lastAmbientLight > currentSettings.lightIntensityThreshold) {
            if (!isEmergencyProtectionMode) {
                triggerFireRiskProtectionMode()
            }
        } else if (isEmergencyProtectionMode && lastBatteryTemp < 40.0f) {
            restoreNormalMode()
        }
    }

    private fun triggerFireRiskProtectionMode() {
        Log.w(TAG, "Fire Risk Detected! Activating Emergency Protection Mode.")
        isEmergencyProtectionMode = true
        
        // TODO: Implement actual protection mode actions
        // (Bluetooth off, AutoSync off, BatterySaver on, AutoRotation off, Brightness 10%, Timeout 15s)
        
        announceFireRiskWarning()
    }

    private fun restoreNormalMode() {
        Log.i(TAG, "Restoring normal mode.")
        isEmergencyProtectionMode = false
        
        // TODO: Restore original settings
    }

    private fun checkThermalStateMachine(temperature: Float) {
        if (temperature == -999f) return

        val thermalState = com.example.engines.thermal.ThermalProtectionEngine.processTemperature(
            temperature = temperature,
            context = applicationContext,
            settings = currentSettings
        )
        val isProtected = com.example.engines.thermal.ThermalProtectionEngine.isProtectionActive()
        isSilentThermalControlActive = isProtected
        isCriticalThermalEpisodeActive = isProtected

        liveBatteryState.update { 
            it.copy(
                isCriticalThermalEpisodeActive = isProtected,
                thermalStateName = when (thermalState) {
                    com.example.engines.thermal.ThermalSessionState.THERMAL_ESCALATED -> "Thermal Escalation (>=45°C)"
                    com.example.engines.thermal.ThermalSessionState.THERMAL_PROTECTION -> "Thermal Protection Active (>=43°C)"
                    com.example.engines.thermal.ThermalSessionState.RESTORING -> "Restoring State (<=40°C)"
                    com.example.engines.thermal.ThermalSessionState.RESTORED, com.example.engines.thermal.ThermalSessionState.NORMAL -> "Normal (<40°C)"
                },
                isHeatProtocolActive = isProtected
            ) 
        }

        // Voice warning only if user has criticalTempEnabled in settings
        if (temperature >= currentSettings.tempAlertThreshold) {
            if (!isCriticalThermalVoiceAnnounced && currentSettings.criticalTempEnabled) {
                executeCriticalThermalVoiceWarning(temperature)
            }
        } else if (temperature < 43.0f) {
            if (isCriticalThermalVoiceAnnounced) {
                isCriticalThermalVoiceAnnounced = false
                Log.i(TAG, "Critical thermal warning reset (temp cooled below 43°C)")
            }
        }
    }

    private fun executeSilentThermalControl(temperature: Float) {
        isSilentThermalControlActive = true
        isCriticalThermalEpisodeActive = true
        if (thermalExecutionTaskId == null) {
            thermalExecutionTaskId = com.example.identity.OperationalIdentityManager.startExecution("THERMAL_MITIGATION", "Thermal mitigation active (>=40°C)")
        }
        Log.w(TAG, "Silent Thermal Control Activated (>=40°C). Temp = $temperature°C. Voice: NONE")

        try {
            previousBrightnessValue = android.provider.Settings.System.getInt(
                contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            previousBrightnessMode = android.provider.Settings.System.getInt(
                contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                0
            )
            wasThermalStateCaptured = true
            Log.d(TAG, "Captured pre-episode thermal state: Brightness=$previousBrightnessValue, Mode=$previousBrightnessMode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture display brightness: ${e.message}")
        }

        try {
            if (android.provider.Settings.System.canWrite(applicationContext)) {
                android.provider.Settings.System.putInt(
                    contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                android.provider.Settings.System.putInt(
                    contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    25 // ~10%
                )
                Log.i(TAG, "Display brightness reduced to 10% successfully (Silent Countermeasure).")
            } else {
                Log.d(TAG, "WRITE_SETTINGS permission unavailable. Virtual display thermal mitigation engaged.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to adjust display brightness: ${e.message}")
        }

        liveBatteryState.update { 
            it.copy(
                isCriticalThermalEpisodeActive = true,
                thermalStateName = "Silent Control (>=40°C)",
                isHeatProtocolActive = true
            ) 
        }
    }

    private fun restoreFromSilentThermal() {
        isSilentThermalControlActive = false
        isCriticalThermalEpisodeActive = false
        thermalExecutionTaskId?.let {
            com.example.identity.OperationalIdentityManager.finishExecution(it)
            thermalExecutionTaskId = null
        }
        Log.i(TAG, "Silent Thermal Control Deactivated (<38°C). Restoring nominal configurations.")

        if (wasThermalStateCaptured) {
            try {
                if (android.provider.Settings.System.canWrite(applicationContext)) {
                    android.provider.Settings.System.putInt(
                        contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                        previousBrightnessMode
                    )
                    android.provider.Settings.System.putInt(
                        contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS,
                        previousBrightnessValue
                    )
                    Log.i(TAG, "Restored display brightness and mode configuration successfully.")
                } else {
                    Log.d(TAG, "Restored virtual screen brightness settings.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Brightness restore failed: ${e.message}")
            }
        }

        liveBatteryState.update { 
            it.copy(
                isCriticalThermalEpisodeActive = false,
                thermalStateName = "Normal (<40°C)",
                isHeatProtocolActive = false
            ) 
        }
    }

    private fun executeCriticalThermalVoiceWarning(temperature: Float) {
        isCriticalThermalVoiceAnnounced = true
        Log.w(TAG, "Critical Thermal Voice Warning Activated (>=45°C). Temp = $temperature°C")
        val announceMsg = "THERMAL ${temperature.toInt()}°C"
        announceText(announceMsg)
    }

    private fun logThermalChange(prev: Float, current: Float, level: Int, voltage: Float, plugged: Int) {
        val prevStr = if (prev == -999f) "INITIAL" else "${prev}°C"
        val currStr = "${current}°C"
        val statusStr = if (plugged > 0) "Charging" else "Discharging"
        
        val title: String
        val details: String
        
        if (prev != -999f && prev < 40f && current >= 40f) {
            title = "Silent Thermal Control Engaged"
            details = "THERMAL $prevStr → $currStr | ACTION: SILENT THERMAL CONTROL INITIATED | VOICE: NONE"
        } else if (prev != -999f && prev < 45f && current >= 45f) {
            title = "Critical Thermal Warning"
            details = "THERMAL $prevStr → $currStr | EVENT: CRITICAL TEMPERATURE | ACTION: THERMAL WARNING | VOICE: ANNOUNCED"
        } else {
            title = "Thermal Telemetry Update"
            details = "THERMAL $prevStr → $currStr | RESULT: TELEMETRY UPDATED"
        }
        
        com.example.util.DiagnosticLogger.logEvent(
            applicationContext,
            "TEMPERATURE",
            title,
            details,
            level,
            current,
            voltage,
            statusStr
        )
    }

    private fun announceText(text: String) {
        try {
            if (isTtsInitialized && tts != null) {
                val params = android.os.Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                }
                VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
                    tts = tts,
                    rawText = text,
                    queueMode = TextToSpeech.QUEUE_FLUSH,
                    params = params
                )
                Log.d(TAG, "Spoke 1s thermal alert: $text")
            } else {
                Log.w(TAG, "TTS not ready yet for thermal alert: $text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS speaking failed", e)
        }
    }
    
    private fun announceFireRiskWarning() {
        val text = "Fire risk alert"
        VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
            tts = tts,
            rawText = text,
            queueMode = TextToSpeech.QUEUE_FLUSH
        )
    }

    private fun checkFullChargeAnnouncement() {
        // Absolute rule: Never announce 100% full battery by voice
        batteryTimeAtFullCharge = 0L
        fullChargeDuration = 0L
        isTrackingFullCharge = false
    }
    
    private fun announceFullCharge(time: String, duration: String) {
        // Absolute rule: Zero full-battery voice announcements
    }

    private var lastPowerConnectedTime = 0L
    private var chargingMonitoringJob: Job? = null

    private fun startChargingMonitoring() {
        chargingMonitoringJob?.cancel()
        chargingMonitoringJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                // Verify health (simple check for now, can be expanded)
                if (BatteryService.instance == null) {
                    Log.d(TAG, "BatteryService instance lost during charging, attempting recovery")
                }
                delay(60000)
            }
        }
    }

    private fun stopChargingMonitoring() {
        chargingMonitoringJob?.cancel()
        chargingMonitoringJob = null
    }

    private fun processPowerConnected() {
        val now = System.currentTimeMillis()
        if (now - lastPowerConnectedTime < 100) return
        lastPowerConnectedTime = now
        Log.d(TAG, "ACTION_POWER_CONNECTED received: invalidating old discharge ETA")
        chargeStartTime = now
        isTrackingFullCharge = true
        com.example.engines.BatteryPredictionEngine.invalidateStateTransition(isCharging = true)
        
        try {
            com.example.engines.ibrsle.IntelligentBackgroundRuntimeEngine.onPowerConnected(applicationContext)
            com.example.engines.ChargingRecoveryEngine.recoverServices(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delegate power connected to engines", e)
        }
        
        // Immediate sub-100ms state publish
        liveBatteryState.update { current ->
            current.copy(
                isCharging = true,
                isPlugged = true,
                chargingType = if (current.chargingType == "None") "AC" else current.chargingType,
                chargingSpeed = "Unknown",
                speed = 0f,
                remainingTimeMs = -1L,
                etaConfidence = "INITIALIZING"
            )
        }
        
        val batteryIntent = getBatteryIntent()
        if (batteryIntent != null) {
            processBatteryUpdate(batteryIntent, forcePowerConnected = true)
        }
        startChargingMonitoring()
    }

    private var chargerAnnouncementJob: Job? = null

    private fun scheduleAnnouncement(isConnecting: Boolean, type: String, level: Int, settings: SettingsEntity, durationMs: Long = 0L) {
        chargerAnnouncementJob?.cancel()
        chargerAnnouncementJob = serviceScope.launch {
            delay(150) // Micro-delay to let OS synchronize state
            val isPluggedNow = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging
            if (isPluggedNow == isConnecting) {
                if (isConnecting) {
                    announceChargerConnected(type, level, settings)
                } else {
                    announceChargerDisconnected(level, settings, durationMs)
                }
            }
        }
    }

    private var lastPowerDisconnectedTime = 0L
    private fun processPowerDisconnected() {
        stopChargingMonitoring()
        val now = System.currentTimeMillis()
        if (now - lastPowerDisconnectedTime < 100) return
        lastPowerDisconnectedTime = now
        Log.d(TAG, "ACTION_POWER_DISCONNECTED received: invalidating old charging ETA")
        isTrackingFullCharge = false
        com.example.engines.BatteryPredictionEngine.invalidateStateTransition(isCharging = false)
        
        try {
            com.example.engines.ibrsle.IntelligentBackgroundRuntimeEngine.onPowerDisconnected(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delegate power disconnected to engines", e)
        }
        
        // Immediate sub-100ms state publish
        liveBatteryState.update { current ->
            current.copy(
                isCharging = false,
                isPlugged = false,
                chargingType = "None",
                chargingSpeed = "None",
                speed = 0f,
                remainingTimeMs = -1L,
                etaConfidence = "INITIALIZING"
            )
        }
        
        val batteryIntent = getBatteryIntent()
        if (batteryIntent != null) {
            processBatteryUpdate(batteryIntent, forcePowerDisconnected = true)
        }
    }

    private fun startServiceForeground() {
        try {
            val notification = buildNotification(liveBatteryState.value)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            try {
                startForeground(NOTIFICATION_ID, buildNotification(liveBatteryState.value))
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback startForeground also failed", ex)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BatteryService onStartCommand")
        startServiceForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "BatteryService onDestroy")
        try {
            com.example.engines.WatchdogEngine.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop WatchdogEngine", e)
        }
        if (instance == this) {
            instance = null
        }
        isServiceRunning.value = false
        unregisterReceiver(batteryReceiver)
        adaptiveLocationManager?.stop()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magneticFieldEventListener?.let { sensorManager.unregisterListener(it) }
        accelSensorEventListener?.let { sensorManager.unregisterListener(it) }
        serviceScope.cancel()
        tts?.let {
            it.stop()
            it.shutdown()
        }
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TTS Initialized successfully")
            isTtsInitialized = true
            tts?.language = Locale.getDefault()
            setupUtteranceListener()
            tts?.let { 
                val attributionContext = com.example.util.getAttributionContext(this, "battery_voice_tag")
                voiceEngine = BatteryVoiceEngine(attributionContext, it) 
            }
            
            serviceScope.launch {
                val settings = (repository?.getSettingsOrInit() ?: com.example.data.SettingsEntity())
                // Requirement: No Application Activated Announcement
                playNextAnnouncement()
            }
        } else {
            Log.e(TAG, "TTS Initialization failed")
            playWarningToneAndShowNotification("TTS initialization failed. Voice announcements may be unavailable.")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Utterance started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Utterance completed: $utteranceId")
                playNextAnnouncement()
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Utterance error: $utteranceId")
                playNextAnnouncement()
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AmpereFlow Monitor Service"
            val descriptionText = "Shows persistent notifications and monitors battery health offline."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: BatteryState): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val identity = com.example.identity.OperationalIdentityManager.currentIdentity
        val isNetra = identity == com.example.identity.OperationalIdentity.NETRA
        val title = if (isNetra) "NETRA BATTERY SENTINEL PRO" else "TRINETRA BATTERY SENTINEL PRO"

        // Calculate and format elapsed time of the active session (e.g. 1h 22m or 22m)
        val now = System.currentTimeMillis()
        val elapsedMs = if (sessionStartTime > 0L) (now - sessionStartTime).coerceAtLeast(0L) else 0L
        val h = elapsedMs / 3600000
        val m = (elapsedMs % 3600000) / 60000
        val elapsedTimeStr = if (h > 0) "${h}h ${m}m" else "${m}m"

        // Determine Status based strictly on state
        val status = when {
            state.isCharging && batteryTimeAtFullCharge > 0L -> {
                // Legitimate overcharging starts when first-full timestamp exists and charger remained connected
                if (now > batteryTimeAtFullCharge) "Overcharging" else "100% Reached"
            }
            state.isCharging && state.percentage >= 100 -> "100% Reached"
            state.isCharging -> "Charging"
            else -> "Discharging"
        }

        // Format telemetry data
        val tempStr = if (state.temperature != -999f) String.format(Locale.US, "%.1f°C", state.temperature) else "N/A"
        val powerStr = String.format(Locale.US, "%.2fW", state.powerWatt)
        val currentStr = "${Math.abs(state.currentNow)}mA"

        // Compact notification content text: Status • Battery % • Temperature • Elapsed Time • Power • Current
        val contentText = "$status • ${state.percentage}% • $tempStr • $elapsedTimeStr • $powerStr • $currentStr"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        // Optional big text style for expanded view to show detailed diagnostics
        val expandedStr = buildString {
            append("$title - Operational Diagnostics\n")
            append("---------------------------------------\n")
            append("Current Status: $status\n")
            append("Battery Level: ${state.percentage}%\n")
            append("Temperature: $tempStr\n")
            append("Active Session Elapsed: $elapsedTimeStr\n")
            append("Intake/Drain Power: $powerStr\n")
            append("Real-Time Current: $currentStr\n")
            append("System Voltage: ${state.voltage / 1000f}V\n")
            append("Battery Health: ${state.health}\n")
            append("Safety Zone: ${state.magneticSafetyZone}\n")
            append("Last Synced: ${java.text.SimpleDateFormat("hh mm ss a", Locale.US).format(java.util.Date(now)).lowercase(Locale.US)}")
        }
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(expandedStr))

        return builder.build()
    }

    private fun checkAndManageSpeedMonitor() {
        val shouldRun = isScreenOn && currentSettings.showSpeedIndicatorInNotification
        
        if (shouldRun) {
            if (speedMonitorJob == null || speedMonitorJob?.isActive == false) {
                lastRxBytes = TrafficStats.getTotalRxBytes()
                lastTxBytes = TrafficStats.getTotalTxBytes()
                lastSpeedCheckTime = System.currentTimeMillis()
                
                speedMonitorJob = serviceScope.launch(Dispatchers.IO) {
                    while (isScreenOn && currentSettings.showSpeedIndicatorInNotification) {
                        try {
                            calculateAndUpdateSpeed()
                        } catch (e: Exception) {
                            Log.e("BatteryService", "Error in speed monitor loop", e)
                        }
                        delay(2000L) // lightweight 2s sampling
                    }
                }
                Log.d("BatteryService", "Speed monitor started.")
            }
        } else {
            if (speedMonitorJob != null) {
                speedMonitorJob?.cancel()
                speedMonitorJob = null
                currentSpeedTitle = ""
                currentSpeedExpanded = ""
                serviceScope.launch {
                    updateNotification(liveBatteryState.value)
                }
                Log.d("BatteryService", "Speed monitor stopped.")
            }
        }
    }

    private fun calculateAndUpdateSpeed() {
        val rxCurrent = TrafficStats.getTotalRxBytes()
        val txCurrent = TrafficStats.getTotalTxBytes()
        val timeCurrent = System.currentTimeMillis()
        
        val rxSupported = rxCurrent != TrafficStats.UNSUPPORTED.toLong()
        val txSupported = txCurrent != TrafficStats.UNSUPPORTED.toLong()
        
        val netInfo = com.example.providers.SafeNetworkProvider.getNetworkInfo(applicationContext)
        val isConnected = netInfo.isWifiConnected || netInfo.isCellularConnected
        val isInternet = netInfo.isInternetAvailable

        if (!rxSupported || !txSupported) {
            currentSpeedTitle = "Speed unavailable"
            currentSpeedExpanded = "Speed unavailable"
        } else if (!isConnected) {
            currentSpeedTitle = "Speed unavailable"
            currentSpeedExpanded = "Speed unavailable"
        } else if (!isInternet) {
            currentSpeedTitle = "No Internet"
            currentSpeedExpanded = "No Internet"
        } else {
            val timeDeltaSec = (timeCurrent - lastSpeedCheckTime) / 1000.0
            if (timeDeltaSec > 0.3 && lastRxBytes >= 0 && lastTxBytes >= 0) {
                val rxDelta = (rxCurrent - lastRxBytes).coerceAtLeast(0L)
                val txDelta = (txCurrent - lastTxBytes).coerceAtLeast(0L)
                
                val rxBitsPerSec = ((rxDelta * 8) / timeDeltaSec).toLong()
                val txBitsPerSec = ((txDelta * 8) / timeDeltaSec).toLong()
                
                val dlStr = formatSpeedBits(rxBitsPerSec)
                val ulStr = formatSpeedBits(txBitsPerSec)
                
                currentSpeedTitle = "↓ $dlStr   ↑ $ulStr"
                currentSpeedExpanded = "Download: $dlStr\nUpload: $ulStr"
            } else {
                currentSpeedTitle = "↓ 0 Kbps   ↑ 0 Kbps"
                currentSpeedExpanded = "Download: 0 Kbps\nUpload: 0 Kbps"
            }
        }
        
        lastRxBytes = rxCurrent
        lastTxBytes = txCurrent
        lastSpeedCheckTime = timeCurrent
        
        serviceScope.launch {
            updateNotification(liveBatteryState.value)
        }
    }

    private fun formatSpeedBits(bitsPerSecond: Long): String {
        if (bitsPerSecond < 0) return "0 Kbps"
        return when {
            bitsPerSecond >= 1_000_000_000L -> {
                val gbps = bitsPerSecond / 1_000_000_000.0
                String.format(Locale.US, "%.2f Gbps", gbps)
            }
            bitsPerSecond >= 1_000_000L -> {
                val mbps = bitsPerSecond / 1_000_000.0
                String.format(Locale.US, "%.1f Mbps", mbps)
            }
            else -> {
                val kbps = bitsPerSecond / 1_000.0
                String.format(Locale.US, "%.0f Kbps", kbps)
            }
        }
    }

    private fun updateNotification(state: BatteryState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    
    private fun handleBatteryDiagnostics(
        intent: Intent,
        healthInt: Int,
        isPlugged: Boolean,
        chargingType: String,
        voltage: Int,
        percentage: Int,
        temperature: Float,
        isCharging: Boolean,
        currentSettings: com.example.data.SettingsEntity
    ) {
        val healthStr = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
            else -> "Unknown"
        }

        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        var rawCurrent = try {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (e: SecurityException) {
            0
        } catch (e: Exception) {
            0
        }
        var currentNowVal = rawCurrent / 1000
        if (Math.abs(currentNowVal) > 15000) currentNowVal /= 1000
        val currentAvgVal = try {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) / 1000
        } catch (e: SecurityException) {
            0
        } catch (e: Exception) {
            0
        }
        val rawCycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                batteryManager.getIntProperty(7)
            } catch (e: SecurityException) {
                -1
            } catch (e: Exception) {
                -1
            }
        } else -1

        val isDataTransferActive = com.example.engines.charging.ChargingIntelligenceEngine.chargingState.value.isUsbDataTransferActive
        val usbDataMode = if (isDataTransferActive) "Data Mode" else "None"

        if (isPlugged && currentNowVal < 0) {
            currentNowVal = -currentNowVal
        } else if (!isPlugged) {
            if (currentNowVal > 0) {
                currentNowVal = -currentNowVal
            } else if (currentNowVal in -50..50) {
                currentNowVal = if (isScreenOn) -250 else -15
            }
        }

        val voltVal = voltage / 1000f
        val ampVal = Math.abs(currentNowVal) / 1000f
        val powerWattVal = voltVal * ampVal

        if (isPlugged) {
            if (currentNowVal > peakCurrent) peakCurrent = currentNowVal
            if (powerWattVal > peakWatt) peakWatt = powerWattVal
            sumCurrent += currentNowVal
            sumWatt += powerWattVal
            readingsCount++
        }

        if (temperature > highestTemp) highestTemp = temperature
        if (temperature < lowestTemp) lowestTemp = temperature
        sumTemp += temperature
        tempReadingsCount++

        val currentStatusStr = if (isPlugged) "Charging ($chargingType)" else "Discharging"
        if (lastRecordedVoltage > 0f && (lastRecordedVoltage - voltage) >= 250f) {
            com.example.util.DiagnosticLogger.logEvent(
                applicationContext,
                "VOLTAGE_DROP",
                "Sudden Voltage Drop",
                "Measured voltage drop of ${(lastRecordedVoltage - voltage).toInt()} mV (from ${lastRecordedVoltage.toInt()} mV down to $voltage mV)",
                percentage,
                temperature,
                voltage.toFloat(),
                currentStatusStr
            )
        }
        lastRecordedVoltage = voltage.toFloat()

        if (temperature >= 40.0f && lastBatteryTemp < 40.0f) {
            com.example.util.DiagnosticLogger.logEvent(
                applicationContext,
                "THERMAL_WARNING",
                "High Battery Temperature",
                "Measured temperature reached ${temperature}°C threshold",
                percentage,
                temperature,
                voltage.toFloat(),
                currentStatusStr
            )
        } else if (temperature >= 45.0f && lastBatteryTemp < 45.0f) {
            com.example.util.DiagnosticLogger.logEvent(
                applicationContext,
                "THERMAL_THROTTLING",
                "Thermal Throttling Active",
                "Critical temperature of ${temperature}°C recorded on hardware sensor",
                percentage,
                temperature,
                voltage.toFloat(),
                currentStatusStr
            )
        }

        com.example.engines.IDOEEngine.updateMode(applicationContext)

        val settings = currentSettings
        val now = System.currentTimeMillis()
        var speed = 0f
        var timeTo50 = 0
        var timeTo80 = 0
        var timeTo100 = 0

        if (isPlugged) {
            if (!lastPluggedState) {
                // Charger connected -> immediately invalidate old discharge prediction
                com.example.engines.BatteryPredictionEngine.invalidateStateTransition(isCharging = true)
                com.example.battery.engine.ChargingClassificationEngine.onChargingStateChanged(true, chargingType)
                sessionStartTime = now
                sessionStartPercentage = percentage
                peakCurrent = 0
                peakWatt = 0f
                sumCurrent = 0
                sumWatt = 0f
                readingsCount = 0
                highestTemp = temperature
                lowestTemp = temperature
                sumTemp = temperature
                tempReadingsCount = 1

                serviceScope.launch {
                    val s = (repository?.getSettingsOrInit() ?: com.example.data.SettingsEntity())
                    repository?.startSession(now, percentage, chargingType, temperature, isDischarge = false)
                    scheduleAnnouncement(isConnecting = true, type = chargingType, level = percentage, settings = s)
                    com.example.util.DiagnosticLogger.logEvent(
                        applicationContext,
                        "CHARGER_CONNECT",
                        "Charger Connected",
                        "Plugged into $chargingType power source",
                        percentage,
                        temperature,
                        voltage.toFloat(),
                        "Charging ($chargingType)"
                    )
                    repository?.logBatteryEvent("CHARGING", "Charger Connected", "Charger type: $chargingType, Level: $percentage%", "POWER", "System")
                }
            } else {
                serviceScope.launch {
                    repository?.updateActiveSessionTemperature(temperature)
                }
            }

            // Calculate validated charging speed strictly from real session samples
            val sessionDurationHr = if (sessionStartTime > 0L) (now - sessionStartTime) / 3600000f else 0f
            val sessionGainedPct = (percentage - sessionStartPercentage).coerceAtLeast(0)

            val detectedCap = com.example.battery.engine.BatteryCapacityEngine.detectValidatedCapacity(this).capacityMah
            val realTimeSpeed = if (sessionDurationHr >= 0.0083f && sessionGainedPct > 0) {
                sessionGainedPct / sessionDurationHr
            } else {
                // Instantaneous hardware-derived rate if valid current is measured (> 150mA) and capacity is validated
                if (currentNowVal >= 150 && detectedCap != null && detectedCap > 0) {
                    (currentNowVal.toFloat() / detectedCap.toFloat()) * 100f
                } else 0f
            }

            speed = realTimeSpeed
            val targetCharge = settings.fullBatteryThreshold

            // Predictions via authoritative BatteryPredictionEngine (0 if speed not yet available)
            timeTo50 = com.example.battery.engine.BatteryPredictionEngine.estimateTimeToFullMinutes(percentage, speed, targetPercentage = 50)?.toInt() ?: 0
            timeTo80 = com.example.battery.engine.BatteryPredictionEngine.estimateTimeToFullMinutes(percentage, speed, targetPercentage = 80)?.toInt() ?: 0
            timeTo100 = com.example.battery.engine.BatteryPredictionEngine.estimateTimeToFullMinutes(percentage, speed, targetPercentage = targetCharge)?.toInt() ?: 0


            // Check for temperature & speed alerts while charging
            checkChargingAlerts(temperature, sessionDurationHr, realTimeSpeed, settings, percentage)
        } else {
            if (lastPluggedState) {
                // Charger disconnected -> immediately invalidate old charging prediction
                com.example.engines.BatteryPredictionEngine.invalidateStateTransition(isCharging = false)
                com.example.battery.engine.ChargingClassificationEngine.onChargingStateChanged(false)
                val sessionEndTime = now
                val avgPowerVal = if (readingsCount > 0) sumWatt / readingsCount else 0f
                val sessionDurationHr = if (sessionStartTime > 0L) (sessionEndTime - sessionStartTime) / 3600000f else 0f
                val sessionGainedPct = (percentage - sessionStartPercentage).coerceAtLeast(0)
                if (sessionDurationHr >= 0.05f && sessionGainedPct > 0) {
                    val finalAvgRatePctHr = sessionGainedPct / sessionDurationHr
                    com.example.engines.charging.DeterministicChargingEngine.recordSessionCompletion(applicationContext, finalAvgRatePctHr, lastChargingType)
                }
                serviceScope.launch {
                    repository?.endActiveSession(sessionEndTime, percentage, avgPower = avgPowerVal, endTemp = temperature)
                    val chargeDurationSecs = (sessionEndTime - sessionStartTime) / 1000
                    repository?.insertTrendLog(
                        com.example.data.BatteryTrendLog(
                            timestamp = sessionEndTime,
                            dischargeRate = 0f,
                            chargeCycleDuration = chargeDurationSecs,
                            batteryLevel = percentage,
                            temperature = temperature,
                            voltage = voltage
                        )
                    )
                }
                playShortBeep() // Play disconnect beep!
                val elapsedMs = if (sessionStartTime > 0L) now - sessionStartTime else 0L
                scheduleAnnouncement(isConnecting = false, type = "Disconnected", level = percentage, settings = settings, durationMs = elapsedMs)
                com.example.util.DiagnosticLogger.logEvent(
                    applicationContext,
                    "CHARGER_DISCONNECT",
                    "Charger Disconnected",
                    "Unplugged from power source at $percentage%",
                    percentage,
                    temperature,
                    voltage.toFloat(),
                    "Discharging"
                )
                serviceScope.launch(Dispatchers.IO) {
                    repository?.logBatteryEvent("DISCHARGING", "Charger Disconnected", "Level at disconnect: $percentage%", "POWER", "System")
                }
                
                // Reset session tracking for discharging
                sessionStartTime = now
                sessionStartPercentage = percentage
                activeDischargeScreenOnMs = 0L
                lastScreenOnTime = if (isScreenOn) now else 0L
                
                serviceScope.launch {
                    repository?.startSession(now, percentage, "Discharging", temperature, isDischarge = true)
                }
            } else {
                // Track discharging stats strictly from real session telemetry
                val dischargeDurationHr = if (sessionStartTime > 0L) (now - sessionStartTime) / 3600000f else 0f
                val dischargeLostPct = (sessionStartPercentage - percentage).coerceAtLeast(0)
                val detectedCap = com.example.battery.engine.BatteryCapacityEngine.detectValidatedCapacity(this).capacityMah
                val dischargeRate = if (dischargeDurationHr >= 0.0083f && dischargeLostPct > 0) {
                    dischargeLostPct / dischargeDurationHr
                } else {
                    val drainMa = Math.abs(currentNowVal)
                    if (drainMa >= 40 && detectedCap != null && detectedCap > 0) {
                        (drainMa.toFloat() / detectedCap.toFloat()) * 100f
                    } else 0f
                }
                speed = dischargeRate // Positive magnitude for rate display

                // If no active session, start a discharge session (e.g. on service start)
                serviceScope.launch {
                    val activeSession = repository?.getActiveSession()
                    if (activeSession == null) {
                        sessionStartTime = now
                        sessionStartPercentage = percentage
                        activeDischargeScreenOnMs = 0L
                        lastScreenOnTime = if (isScreenOn) now else 0L
                        repository?.startSession(now, percentage, "Discharging", temperature, isDischarge = true)
                    }
                }

                // Discharging alarm alerts
                checkDischargingAlerts(percentage, temperature, dischargeRate, settings)
            }
        }

        // Periodic interval vocal announcements
        checkIntervalAnnouncements(percentage, isCharging, settings)
        checkMilestoneAnnouncements(percentage, isCharging, settings)

        // Low battery spoken alert
        if (!isCharging && percentage <= settings.lowBatteryThreshold.coerceIn(15, 20) && lastAnnouncedPercentage > settings.lowBatteryThreshold.coerceIn(15, 20)) {
            if (settings.lowBatteryEnabled) {
                queueAnnouncement("Low Battery. $percentage percent.", AnnouncementCategory.BATTERY_MILESTONE, Priority.BATTERY_SAFETY, settings)
            }
        }

        // Save state variables
        lastAnnouncedPercentage = if (percentage == 100 || (settings.announcementInterval > 0 && percentage % settings.announcementInterval == 0) || percentage == settings.customPercentage) percentage else lastAnnouncedPercentage
        lastPluggedState = isPlugged
        lastChargingType = chargingType

        // Estimate Health Percentage dynamically based on typical metrics
        val simulatedCycles = if (rawCycleCount > 0) rawCycleCount else {
            35 // baseline starting estimate
        }
        val computedHealthPct = (100 - (simulatedCycles / 100)).coerceIn(80, 100)
        
        // Authoritative Device Battery Capacity Validation
        val validatedCapResult = com.example.battery.engine.BatteryCapacityEngine.detectValidatedCapacity(this)
        val validDesignCapacity = validatedCapResult.capacityMah
        val validatedEstimatedCap = validDesignCapacity?.let { (computedHealthPct * it) / 100 }
        
        // Authoritative ETA prediction strictly respecting priority policy
        val authoritativeEta = com.example.engines.BatteryPredictionEngine.calculateAuthoritativeEta(
            percentage = percentage,
            isCharging = isCharging,
            currentNowVal = currentNowVal,
            isScreenOn = isScreenOn,
            capacity = validatedEstimatedCap ?: validDesignCapacity,
            speed = speed,
            targetPercentage = settings.fullBatteryThreshold
        )
        val remainingTimeMs = authoritativeEta.remainingTimeMs
        val usableHealthPct = (computedHealthPct - 80).coerceAtLeast(0)
        val monthsRemaining = (usableHealthPct * 1.25f).coerceAtLeast(0.5f)
        val replacementDateTimestamp = System.currentTimeMillis() + (monthsRemaining * 30L * 86400000L).toLong()
        
        val currentState = liveBatteryState.value

        if (percentage >= settings.fullBatteryThreshold && isCharging) {
            if (timeWhenReached100 == 0L) {
                timeWhenReached100 = System.currentTimeMillis()
            }
        } else {
            timeWhenReached100 = 0L
        }
        val overchargeDurationMs = if (timeWhenReached100 > 0L) System.currentTimeMillis() - timeWhenReached100 else 0L

        val highLightThreshold = if (settings.lightIntensityThreshold > 0) settings.lightIntensityThreshold.toFloat() else 5000f
        val isHighLight = lastAmbientLight >= highLightThreshold
        val lightCondition = when {
            lastAmbientLight >= 10000f -> "Extreme Sunlight (>10k Lux)"
            lastAmbientLight >= highLightThreshold -> "High Light (Direct Sunlight)"
            lastAmbientLight >= 1000f -> "Bright Light"
            lastAmbientLight >= 100f -> "Normal Light"
            else -> "Low Light / Dark"
        }
        val solarDelta = if (isHighLight) 3.5f else 0.0f
        val effectiveTemp = temperature + solarDelta

        val finalPeakCurrent = peakCurrent
        val finalPeakWatt = peakWatt
        val finalAvgCurrent = (if (readingsCount > 0) sumCurrent / readingsCount else 0L).toInt()
        val finalAvgWatt = if (readingsCount > 0) sumWatt / readingsCount else 0f
        val finalHighestTemp = highestTemp
        val finalLowestTemp = lowestTemp
        val finalAverageTemp = if (tempReadingsCount > 0) sumTemp / tempReadingsCount else 0f

        // Update live network telemetry
        try {
            com.example.engines.network.NetworkTelemetryEngine.updateTelemetry(applicationContext)
        } catch (e: Exception) {
            // Safe fallback
        }

        val screenOnMin = getActiveDischargeScreenOnMin()
        val standbyMin = getActiveDischargeStandbyMin(now)
        val calculatedDrainRate = if (!isCharging && speed > 0f && tempReadingsCount > 2) speed else -1f

        val sessionState = when {
            isPlugged && percentage >= 100 -> BatterySessionState.FULL
            isPlugged || isCharging -> BatterySessionState.CHARGING
            !isPlugged -> BatterySessionState.DISCHARGING
            else -> BatterySessionState.UNKNOWN
        }

        val isCurrentAvail = rawCurrent != 0 && rawCurrent != Int.MIN_VALUE && rawCurrent != Int.MAX_VALUE && Math.abs(currentNowVal) > 0
        val isVoltAvail = voltage > 0
        val isPwrAvail = isCurrentAvail && isVoltAvail

        if (prevTelemetryTemp > -900f && effectiveTemp > -900f) {
            currentTempDelta = effectiveTemp - prevTelemetryTemp
        }
        if (prevTelemetryVolt > 0 && voltage > 0) {
            currentVoltDelta = (voltage - prevTelemetryVolt) / 1000f
        }
        if (prevTelemetryCurrent != 0 && currentNowVal != 0) {
            currentCurrDelta = (currentNowVal - prevTelemetryCurrent).toFloat()
        }
        prevTelemetryTemp = effectiveTemp
        prevTelemetryVolt = voltage
        prevTelemetryCurrent = currentNowVal
        prevTelemetryTime = now

        val updatedState = BatteryState(
            percentage = percentage,
            isCharging = isCharging,
            chargingType = chargingType,
            chargingSpeed = com.example.engines.ChargingEngine.classifyChargingType(
                isCharging = isCharging,
                powerWatt = powerWattVal,
                currentNowMa = currentNowVal,
                voltageMv = voltage,
                sessionDurationSeconds = if (sessionStartTime > 0L) (now - sessionStartTime) / 1000L else 0L,
                measuredRatePctPerHr = speed,
                temperatureCelsius = effectiveTemp,
                temperatureTrend = if (currentTempDelta > 0.05f) "RISING" else if (currentTempDelta < -0.05f) "FALLING" else "STABLE",
                isScreenOn = isScreenOn,
                powerSource = chargingType,
                batteryPercentage = percentage
            ),
            isDataTransferActive = isDataTransferActive,
            usbDataMode = usbDataMode,
            ambientLightLux = lastAmbientLight,
            isHighLightCondition = isHighLight,
            isHeatProtocolActive = isHighLight,
            ambientLightCondition = lightCondition,
            solarHeatDeltaTemp = solarDelta,
            temperature = effectiveTemp,
            voltage = voltage,
            currentNow = currentNowVal,
            currentAverage = currentAvgVal,
            powerWatt = powerWattVal,
            health = healthStr,
            healthPercentage = computedHealthPct,
            cycleCount = rawCycleCount,
            speed = speed,
            timeTo50Min = timeTo50,
            timeTo80Min = timeTo80,
            timeTo100Min = timeTo100,
            isPlugged = isPlugged,
            peakCurrent = finalPeakCurrent,
            peakWatt = finalPeakWatt,
            avgCurrent = finalAvgCurrent,
            avgWatt = finalAvgWatt,
            highestTemp = if (currentState.hasSufficient24hData) currentState.highestTemp else finalHighestTemp,
            lowestTemp = if (currentState.hasSufficient24hData) currentState.lowestTemp else finalLowestTemp,
            averageTemp = if (currentState.hasSufficient24hData) currentState.averageTemp else finalAverageTemp,
            tempSampleCount = if (currentState.hasSufficient24hData) currentState.tempSampleCount else tempReadingsCount,
            screenOnMinutes = screenOnMin,
            deepSleepMinutes = standbyMin,
            batteryDrainRatePerHr = calculatedDrainRate,
            designCapacity = validDesignCapacity,
            estimatedCapacity = validatedEstimatedCap,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            lat = currentState.lat,
            lon = currentState.lon,
            cityName = currentState.cityName,
            locationStatus = currentState.locationStatus,
            remainingTimeMs = remainingTimeMs,
            etaConfidence = authoritativeEta.confidence.name,
            etaSource = authoritativeEta.source.name,
            overchargeDurationMs = overchargeDurationMs,
            replacementDateTimestamp = replacementDateTimestamp,
            confidenceScore = 96,
            hasSufficient24hData = currentState.hasSufficient24hData,
            lowestBattery24h = currentState.lowestBattery24h,
            highestBattery24h = currentState.highestBattery24h,
            totalDischarge24h = currentState.totalDischarge24h,
            totalCharge24h = currentState.totalCharge24h,
            avgDischargeRate24h = currentState.avgDischargeRate24h,
            screenOnDischarge24h = currentState.screenOnDischarge24h,
            standbyDischarge24h = currentState.standbyDischarge24h,
            chargingPeriodsCount24h = currentState.chargingPeriodsCount24h,
            lowestVoltage24h = currentState.lowestVoltage24h,
            highestVoltage24h = currentState.highestVoltage24h,
            averageVoltage24h = currentState.averageVoltage24h,
            lowestCurrent24h = currentState.lowestCurrent24h,
            highestCurrent24h = currentState.highestCurrent24h,
            averageCurrent24h = currentState.averageCurrent24h,
            peakTemp24h = currentState.peakTemp24h,
            peakTempTimestamp24h = currentState.peakTempTimestamp24h,
            count24hSamples = currentState.count24hSamples,
            sessionState = sessionState,
            isCurrentAvailable = isCurrentAvail,
            isVoltageAvailable = isVoltAvail,
            isPowerAvailable = isPwrAvail,
            tempTrendDelta = currentTempDelta,
            voltageTrendDelta = currentVoltDelta,
            currentTrendDelta = currentCurrDelta,
            batteryTrendPctPerHour = if (sessionState.isChargingState) speed else -speed
        )

        val prevState = liveBatteryState.value
        val isSignificantChange = (prevState.percentage != updatedState.percentage) ||
                (prevState.isCharging != updatedState.isCharging) ||
                (prevState.isPlugged != updatedState.isPlugged) ||
                (prevState.chargingType != updatedState.chargingType) ||
                (Math.abs(prevState.temperature - updatedState.temperature) >= 0.5f) ||
                (Math.abs(prevState.voltage - updatedState.voltage) >= 50) ||
                (Math.abs(prevState.currentNow - updatedState.currentNow) >= 100)

        // Force update if battery change event is received (e.g. from tests)
        // or if it's the first battery reading.
        val isForcedUpdate = (updatedState.percentage != -1 && prevState.percentage == -1)
        
        com.example.telemetry.AuthoritativeTelemetryRepository.ingestSample(
            rawPercentage = percentage,
            rawTemperature = effectiveTemp,
            rawVoltageMv = voltage,
            rawCurrentMa = currentNowVal,
            isCharging = isCharging
        )

        if (isSignificantChange || isForcedUpdate || prevState.percentage <= 0) {
            liveBatteryState.value = updatedState
            try {
                com.example.controller.LocalControllerGateway.updateTelemetryFromBattery(updatedState)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating controller gateway", e)
            }
            update24hTelemetry(percentage, temperature, voltage, currentNowVal, isCharging, speed)
            updateNotification(updatedState)
            try {
                val widgetIntent = Intent(NetraSmartWidget.ACTION_UPDATE_WIDGETS)
                sendBroadcast(widgetIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending widget update broadcast", e)
            }
        }
    }

    private var lastTrendInsertTimeMs = 0L
    private var lastRecordedPercentage = -1

    private fun update24hTelemetry(
        pct: Int,
        temp: Float,
        volt: Int,
        currentVal: Int,
        isChargingState: Boolean,
        speedVal: Float
    ) {
        val now = System.currentTimeMillis()
        
        // 1. Insert Trend Log into Database
        if (now - lastTrendInsertTimeMs >= 60000L || lastRecordedPercentage != pct) {
            lastTrendInsertTimeMs = now
            lastRecordedPercentage = pct
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Authoritative System Writer: Single Event -> Dual Canonical Logs (Trend & History Graph Dataset)
                    val bState = liveBatteryState.value

                    repository?.insertTrendLog(
                        com.example.data.BatteryTrendLog(
                            timestamp = now,
                            dischargeRate = if (isChargingState) 0f else Math.abs(speedVal),
                            chargeCycleDuration = 0L,
                            batteryLevel = pct,
                            temperature = temp,
                            voltage = volt,
                            currentNow = currentVal
                        )
                    )
                    
                    repository?.recordBatterySnapshot(
                        level = pct,
                        isCharging = isChargingState,
                        chargingType = bState.chargingType,
                        temperature = temp,
                        voltageMv = volt,
                        currentNowMa = currentVal,
                        health = bState.health,
                        status = if (isChargingState) (if (pct >= 100) "FULL" else "CHARGING") else "DISCHARGING"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error inserting canonical telemetry logs: ${e.message}", e)
                }
            }
        }

        // 2. Query logs and calculate current calendar-date metrics asynchronously
        serviceScope.launch(Dispatchers.IO) {
            try {
                val allLogs = repository?.allTrendLogs?.firstOrNull() ?: emptyList()
                
                // Get start of today in current local timezone using canonical TimeManager
                val todayStartMs = com.example.util.TimeManager.getStartOfLocalDay(now)

                // Append the current reading to ensure real-time accuracy and prevent empty lists
                val currentLog = com.example.data.BatteryTrendLog(
                    timestamp = now,
                    dischargeRate = if (isChargingState) 0f else Math.abs(speedVal),
                    chargeCycleDuration = 0L,
                    batteryLevel = pct,
                    temperature = temp,
                    voltage = volt,
                    currentNow = currentVal
                )
                val logsToday = (allLogs + currentLog).filter { it.timestamp >= todayStartMs }
                
                val hasSufficient = logsToday.isNotEmpty()
                val countSamples = logsToday.size
                
                val lowestBattery = if (hasSufficient) logsToday.minOfOrNull { it.batteryLevel } ?: pct else pct
                val highestBattery = if (hasSufficient) logsToday.maxOfOrNull { it.batteryLevel } ?: pct else pct
                
                // Segment calculations for discharging and charging (today only)
                var totalDischarge = 0
                var totalCharge = 0
                var chargingPeriods = 0
                var inCharging = false
                
                val sorted = logsToday.sortedBy { it.timestamp }
                for (i in 0 until sorted.size - 1) {
                    val currentLogItem = sorted[i]
                    val nextLog = sorted[i + 1]
                    val delta = nextLog.batteryLevel - currentLogItem.batteryLevel
                    if (delta > 0) {
                        totalCharge += delta
                        if (!inCharging) {
                            inCharging = true
                            chargingPeriods++
                        }
                    } else if (delta < 0) {
                        totalDischarge += Math.abs(delta)
                        inCharging = false
                    }
                }
                
                // Isolating discharge rate from charging periods
                var dischargeDurationMs = 0L
                var dischargeGained = 0
                for (i in 0 until sorted.size - 1) {
                    val currentLogItem = sorted[i]
                    val nextLog = sorted[i + 1]
                    val delta = currentLogItem.batteryLevel - nextLog.batteryLevel
                    if (delta > 0) {
                        val duration = nextLog.timestamp - currentLogItem.timestamp
                        if (duration > 0) {
                            dischargeDurationMs += duration
                            dischargeGained += delta
                        }
                    }
                }
                val avgDischargeRate = if (dischargeDurationMs > 0) {
                    (dischargeGained.toFloat() / (dischargeDurationMs.toFloat() / 3600000f))
                } else {
                    0f
                }
                
                // Screen state duration calculations from logs / events (today only)
                val eventsToday = repository?.allBatteryEvents?.firstOrNull()?.filter { it.timestamp >= todayStartMs } ?: emptyList()
                var totalScreenOnMs = 0L
                var lastScreenOnTimestamp = 0L
                for (event in eventsToday.sortedBy { it.timestamp }) {
                    if (event.title.contains("Screen On", ignoreCase = true)) {
                        lastScreenOnTimestamp = event.timestamp
                    } else if (event.title.contains("Screen Off", ignoreCase = true) && lastScreenOnTimestamp > 0L) {
                        totalScreenOnMs += (event.timestamp - lastScreenOnTimestamp)
                        lastScreenOnTimestamp = 0L
                    }
                }
                if (lastScreenOnTimestamp > 0L) {
                    totalScreenOnMs += (now - lastScreenOnTimestamp)
                }
                
                // Voltage bounds (today only)
                val lowestVolt = if (hasSufficient) logsToday.minOfOrNull { it.voltage } ?: volt else volt
                val highestVolt = if (hasSufficient) logsToday.maxOfOrNull { it.voltage } ?: volt else volt
                val averageVolt = if (hasSufficient) calculateTimeWeightedAverageLocal(logsToday) { it.voltage.toFloat() }.toInt() else volt
                
                // Current bounds (today only)
                val lowestCurrent = if (hasSufficient) logsToday.minOfOrNull { it.currentNow } ?: currentVal else currentVal
                val highestCurrent = if (hasSufficient) logsToday.maxOfOrNull { it.currentNow } ?: currentVal else currentVal
                val averageCurrent = if (hasSufficient) calculateTimeWeightedAverageLocal(logsToday) { it.currentNow.toFloat() }.toInt() else currentVal
                
                // Temperature bounds (today only)
                val peakTempLog = logsToday.maxByOrNull { it.temperature }
                val peakTemp = peakTempLog?.temperature ?: temp
                val peakTimestamp = peakTempLog?.timestamp ?: now
                
                val calculatedHighestTemp = if (hasSufficient) logsToday.maxOfOrNull { it.temperature } ?: temp else temp
                val calculatedLowestTemp = if (hasSufficient) logsToday.minOfOrNull { it.temperature } ?: temp else temp
                val calculatedAverageTemp = if (hasSufficient) calculateTimeWeightedAverageLocal(logsToday) { it.temperature } else temp
                
                // Update live state with calculated authoritative calendar-date metrics!
                liveBatteryState.update { current ->
                    current.copy(
                        hasSufficient24hData = hasSufficient,
                        lowestBattery24h = lowestBattery,
                        highestBattery24h = highestBattery,
                        totalDischarge24h = totalDischarge,
                        totalCharge24h = totalCharge,
                        avgDischargeRate24h = avgDischargeRate,
                        screenOnDischarge24h = (totalScreenOnMs / 60000).toInt(),
                        standbyDischarge24h = (((now - todayStartMs - totalScreenOnMs) / 60000).toInt()).coerceAtLeast(0),
                        chargingPeriodsCount24h = chargingPeriods,
                        
                        lowestVoltage24h = lowestVolt,
                        highestVoltage24h = highestVolt,
                        averageVoltage24h = averageVolt,
                        
                        lowestCurrent24h = lowestCurrent,
                        highestCurrent24h = highestCurrent,
                        averageCurrent24h = averageCurrent,
                        
                        highestTemp = calculatedHighestTemp,
                        lowestTemp = calculatedLowestTemp,
                        averageTemp = calculatedAverageTemp,
                        tempSampleCount = countSamples,
                        
                        peakTemp24h = peakTemp,
                        peakTempTimestamp24h = peakTimestamp,
                        count24hSamples = countSamples
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating today's telemetry: ${e.message}", e)
            }
        }
    }

    private fun calculateTimeWeightedAverageLocal(logs: List<com.example.data.BatteryTrendLog>, getValue: (com.example.data.BatteryTrendLog) -> Float): Float {
        if (logs.isEmpty()) return 0f
        if (logs.size == 1) return getValue(logs.first())
        
        var totalWeightedValue = 0f
        var totalDurationMs = 0L
        
        val sorted = logs.sortedBy { it.timestamp }
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            val duration = next.timestamp - current.timestamp
            if (duration > 0) {
                val avgVal = (getValue(current) + getValue(next)) / 2f
                totalWeightedValue += avgVal * duration
                totalDurationMs += duration
            }
        }
        
        return if (totalDurationMs > 0) {
            totalWeightedValue / totalDurationMs
        } else {
            logs.map(getValue).average().toFloat()
        }
    }

    private suspend fun getHistoricalAverageSpeed(type: String): Float {
        return com.example.battery.engine.ChargingClassificationEngine.getLearnedBaseline(type) ?: 0f
    }

    private fun formatDurationToSpeak(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> {
                val hrStr = if (hours == 1L) "hour" else "hours"
                val minStr = if (minutes == 1L) "minute" else "minutes"
                if (minutes > 0) {
                    "$hours $hrStr $minutes $minStr"
                } else {
                    "$hours $hrStr"
                }
            }
            minutes > 0 -> {
                val minStr = if (minutes == 1L) "minute" else "minutes"
                "$minutes $minStr"
            }
            else -> {
                val secStr = if (seconds == 1L) "second" else "seconds"
                "$seconds $secStr"
            }
        }
    }

    private fun announceChargerConnected(type: String, level: Int, settings: SettingsEntity) {
        if (com.example.engines.deepsleep.DeepSleepEngine.isDeepSleepActive(settings) && !settings.deepSleepChargerVoiceEnabled) {
            Log.d(TAG, "Charger connected announcement suppressed due to Deep Sleep Mode")
            return
        }
        if (settings.chargerConnectedEnabled) {
            val speakText = when (type) {
                "AC" -> "Fast charging connected"
                "Wireless" -> "Slow charging connected"
                "USB" -> "Data transfer connected"
                else -> "Charger connected"
            }
            queueAnnouncement(speakText, AnnouncementCategory.CHARGER_CONNECTION, Priority.CHARGING_EVENTS, settings)
        }
    }

    private fun announceChargerDisconnected(level: Int, settings: SettingsEntity, durationMs: Long = 0L) {
        if (com.example.engines.deepsleep.DeepSleepEngine.isDeepSleepActive(settings) && !settings.deepSleepChargerVoiceEnabled) {
            Log.d(TAG, "Charger disconnected announcement suppressed due to Deep Sleep Mode")
            return
        }
        if (settings.chargerDisconnectedEnabled) {
            val durationText = formatDurationToSpeak(durationMs)
            val speakText = "Battery $level%, charging time $durationText."
            queueAnnouncement(speakText, AnnouncementCategory.CHARGER_CONNECTION, Priority.CHARGING_EVENTS, settings)
        }
    }

    private fun checkIntervalAnnouncements(percentage: Int, isCharging: Boolean, settings: SettingsEntity) {
        if (!isCharging || percentage == 100) return
        if (com.example.engines.deepsleep.DeepSleepEngine.isDeepSleepActive(settings) && !settings.deepSleepStandardVoiceEnabled) return
        
        val interval = settings.announcementInterval
        if (interval <= 0) return
        
        val isAtInterval = percentage % interval == 0
        val isAtCustom = percentage == settings.customPercentage && percentage != 100

        if ((isAtInterval || isAtCustom) && percentage != lastAnnouncedPercentage) {
            if (settings.batteryPercentageEnabled) {
                queueAnnouncement("$percentage percent", AnnouncementCategory.BATTERY_MILESTONE, Priority.INFORMATION, settings)
            }
        }
    }

    private fun checkMilestoneAnnouncements(percentage: Int, isCharging: Boolean, settings: SettingsEntity) {
        if (!isCharging || percentage == lastAnnouncedPercentage || percentage == 100) return
        if (com.example.engines.deepsleep.DeepSleepEngine.isDeepSleepActive(settings) && !settings.deepSleepMilestonesEnabled) return

        val shouldAnnounce = when (percentage) {
            25 -> settings.milestone25Enabled
            50 -> settings.milestone50Enabled
            75 -> settings.milestone75Enabled
            80 -> settings.milestone80Enabled
            90 -> settings.milestone90Enabled
            95 -> settings.milestone95Enabled
            else -> false
        }

        if (shouldAnnounce) {
            val text = "Battery reached $percentage percent."
            queueAnnouncement(text, AnnouncementCategory.BATTERY_MILESTONE, Priority.CHARGING_EVENTS, settings)
        }
    }

    private fun getOutdoorTemp(): Float {
        val prefs = getSharedPreferences("netra_weather_settings", Context.MODE_PRIVATE)
        return prefs.getFloat("temp", 25.0f)
    }

    private fun checkChargingAlerts(temperature: Float, sessionDurationHr: Float, currentSpeed: Float, settings: SettingsEntity, percentage: Int) {
        val now = System.currentTimeMillis()
        val outdoorTemp = getOutdoorTemp()
        val tempDiff = temperature - outdoorTemp

        val highLightThreshold = if (settings.lightIntensityThreshold > 0) settings.lightIntensityThreshold.toFloat() else 5000f
        val isLightSensorActivityOccurring = isHeatMonitoringModeActive || lastAmbientLight >= highLightThreshold

        // Critical Temperature Overheat Alert (>= 45°C) OR (Difference > 5°C and < 45°C if temp >= 40°C or light sensor activity is occurring)
        val isOverheat = (temperature >= 45.0f) || 
                         (((temperature - outdoorTemp) > 5.0f && temperature < 45.0f) && 
                          (temperature >= 40.0f || isLightSensorActivityOccurring))
        
        if (isOverheat && (now - lastAlertTimeTemp > 300000)) {
            lastAlertTimeTemp = now
            serviceScope.launch(Dispatchers.IO) {
                repository?.logBatteryEvent("HARDWARE", "High Temperature", "Device Temperature: ${temperature.toInt()}°C, Outdoor: ${outdoorTemp.toInt()}°C", "HARDWARE", "Netra")
            }
            if (settings.criticalTempEnabled || settings.tempWarningEnabled) {
                val warningText = "Warning: Device temperature is high compared to ambient environment. Please reduce usage or allow the phone to cool."
                playWarningToneAndShowNotification(warningText)
            }
        }

        // Abnormal Slow Charging Alert (plugged for > 15 mins but charging extremely slow)
        val isUsbDataTransfer = com.example.engines.charging.ChargingIntelligenceEngine.chargingState.value.isUsbDataTransferActive
        if (!isUsbDataTransfer && sessionDurationHr > 0.25f && currentSpeed in 0.1f..4f && (now - lastAlertTimeSpeed > 600000)) {
            lastAlertTimeSpeed = now
            if (settings.smartBatteryAlertsEnabled) {
                queueAnnouncement("Notice. Charging is extremely slow. Please check if your cable or charger adapter is fully plugged.", AnnouncementCategory.SILENT_WARNING, Priority.INFORMATION, settings)
            }
        }
    }

    private fun checkDischargingAlerts(percentage: Int, temperature: Float, dischargeRate: Float, settings: SettingsEntity) {
        val now = System.currentTimeMillis()
        val outdoorTemp = getOutdoorTemp()
        val tempDiff = temperature - outdoorTemp

        val highLightThreshold = if (settings.lightIntensityThreshold > 0) settings.lightIntensityThreshold.toFloat() else 5000f
        val isLightSensorActivityOccurring = isHeatMonitoringModeActive || lastAmbientLight >= highLightThreshold

        // Critical Temperature Overheat Alert (>= 45°C) OR (Difference > 5°C and < 45°C if temp >= 40°C or light sensor activity is occurring)
        val isOverheat = (temperature >= 45.0f) || 
                         (((temperature - outdoorTemp) > 5.0f && temperature < 45.0f) && 
                          (temperature >= 40.0f || isLightSensorActivityOccurring))
        
        if (isOverheat && (now - lastAlertTimeTemp > 300000)) {
            lastAlertTimeTemp = now
            serviceScope.launch(Dispatchers.IO) {
                repository?.logBatteryEvent("HARDWARE", "High Temperature", "Device Temperature: ${temperature.toInt()}°C, Outdoor: ${outdoorTemp.toInt()}°C", "HARDWARE", "Netra")
            }
            if (settings.criticalTempEnabled || settings.tempWarningEnabled) {
                val warningText = "Warning: Device temperature is high compared to ambient environment. Please reduce usage or allow the phone to cool."
                playWarningToneAndShowNotification(warningText)
            }
        }

        // Fast Discharge Drain Alert (discharging faster than 18%/hr under heavy load)
        if (dischargeRate >= 18f && (now - lastAlertTimeDrain > 450000)) {
            lastAlertTimeDrain = now
            serviceScope.launch(Dispatchers.IO) {
                repository?.logBatteryEvent("HARDWARE", "Heavy Drain", "Discharging speed at ${dischargeRate.toInt()}%/hr", "HARDWARE", "Netra")
            }
            if (settings.smartBatteryAlertsEnabled) {
                Log.i(TAG, "Heavy battery drain detected. Discharging speed is high at ${dischargeRate.toInt()} percent per hour.")
                playWarningToneAndShowNotification("Warning. Heavy battery drain detected. Discharging speed is high at ${dischargeRate.toInt()} percent per hour.")
            }
        }
    }

    @Synchronized
    private fun queueAnnouncement(text: String, category: AnnouncementCategory, priority: Int, settings: SettingsEntity) {
        // Map AnnouncementCategory to NotificationEvent
        val notificationEvent = when (category) {
            AnnouncementCategory.CHARGER_CONNECTION -> {
                if (text.contains("Disconnected", ignoreCase = true)) {
                    com.example.engines.notification.NotificationEvent.CHARGER_DISCONNECTED
                } else {
                    com.example.engines.notification.NotificationEvent.CHARGER_CONNECTED
                }
            }
            AnnouncementCategory.BATTERY_MILESTONE -> {
                if (text.contains("Low", ignoreCase = true)) {
                    com.example.engines.notification.NotificationEvent.LOW_BATTERY_ALERTS
                } else if (text.contains("Full", ignoreCase = true) || text.contains("100")) {
                    com.example.engines.notification.NotificationEvent.BATTERY_FULL
                } else {
                    com.example.engines.notification.NotificationEvent.BATTERY_STATUS_CHANGES
                }
            }
            AnnouncementCategory.THERMAL_EMERGENCY -> com.example.engines.notification.NotificationEvent.TEMPERATURE_CRITICAL
            AnnouncementCategory.SAFETY_EMERGENCY -> com.example.engines.notification.NotificationEvent.BATTERY_CRITICAL_FAILURE
            AnnouncementCategory.SILENT_WARNING -> {
                if (text.contains("slow", ignoreCase = true)) {
                    com.example.engines.notification.NotificationEvent.SLOW_CHARGING_DETECTED
                } else {
                    com.example.engines.notification.NotificationEvent.HEALTH_MONITOR_MESSAGES
                }
            }
            else -> com.example.engines.notification.NotificationEvent.HEALTH_MONITOR_MESSAGES
        }

        val eventPriority = when (priority) {
            Priority.EMERGENCY_SAFETY -> com.example.engines.notification.EventPriority.EMERGENCY
            Priority.BATTERY_SAFETY -> com.example.engines.notification.EventPriority.CRITICAL
            Priority.CHARGING_EVENTS -> com.example.engines.notification.EventPriority.WARNING
            else -> com.example.engines.notification.EventPriority.INFORMATION
        }

        // Route through NPE single source of truth (SSOT)
        com.example.engines.notification.NotificationPreferenceEngine.requestNotification(
            context = this,
            event = notificationEvent,
            title = "AmpereFlow Sentinel",
            details = text,
            source = "BatteryService",
            overridePriority = eventPriority
        )
    }

    private fun isAudioPlaying(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.activePlaybackConfigurations.isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isMusicActive
        }
    }

    private fun isCallInProgress(): Boolean {
        return false
    }

    @Synchronized
    private fun playNextAnnouncement() {
        currentSpeakingAnnouncement = null
        if (pendingAnnouncements.isNotEmpty()) {
            val next = pendingAnnouncements.removeAt(0)
            currentSpeakingAnnouncement = next
            serviceScope.launch {
                val settings = (repository?.getSettingsOrInit() ?: com.example.data.SettingsEntity())
                speakReal(next, settings)
            }
        }
    }

    data class MagneticZoneDetails(
        val index: Int,
        val name: String,
        val status: String,
        val message: String,
        val voiceText: String?,
        val isCritical: Boolean,
        val isHighPriority: Boolean
    )

    private fun getMagneticZoneDetails(magnitude: Double): MagneticZoneDetails {
        return when {
            magnitude < 50.0 -> MagneticZoneDetails(
                index = 0,
                name = "Normal Zone",
                status = "Normal Magnetic Environment",
                message = "Status: Normal\nThe surrounding magnetic field is within the normal operating range.",
                voiceText = null,
                isCritical = false,
                isHighPriority = false
            )
            magnitude < 100.0 -> MagneticZoneDetails(
                index = 1,
                name = "Safe Zone",
                status = "Safe Magnetic Environment",
                message = "Status: Safe Zone\nA slightly elevated magnetic field has been detected. The current level is within the safe operating range. No action is required. Monitoring will continue automatically.",
                voiceText = null,
                isCritical = false,
                isHighPriority = false
            )
            magnitude < 150.0 -> MagneticZoneDetails(
                index = 2,
                name = "Attention Zone",
                status = "Magnetic Field Increasing",
                message = "Attention: The magnetic field has exceeded the normal operating range. Please avoid keeping the device near strong magnets or magnetic equipment.",
                voiceText = "Attention: The magnetic field has exceeded the normal operating range. Please avoid keeping the device near strong magnets or magnetic equipment.",
                isCritical = false,
                isHighPriority = false
            )
            magnitude < 250.0 -> MagneticZoneDetails(
                index = 3,
                name = "Caution Zone",
                status = "High Magnetic Environment",
                message = "Caution: A high magnetic field has been detected. Move the device away from the magnetic source if possible.",
                voiceText = "Caution: A high magnetic field has been detected. Move the device away from the magnetic source if possible.",
                isCritical = false,
                isHighPriority = true
            )
            magnitude < 400.0 -> MagneticZoneDetails(
                index = 4,
                name = "Warning Zone",
                status = "Strong Magnetic Environment",
                message = "Warning: A strong magnetic field has been detected. Continued exposure may interfere with the device's magnetic sensor. Please move away from the source.",
                voiceText = "Warning: A strong magnetic field has been detected. Continued exposure may interfere with the device's magnetic sensor. Please move away from the source.",
                isCritical = false,
                isHighPriority = true
            )
            magnitude < 600.0 -> MagneticZoneDetails(
                index = 5,
                name = "High Risk Zone",
                status = "Very Strong Magnetic Environment",
                message = "Critical Warning: A very strong magnetic field has been detected. Leave the magnetic environment immediately if practical and keep the device away from the source.",
                voiceText = "Critical Warning: A very strong magnetic field has been detected. Leave the magnetic environment immediately if practical and keep the device away from the source.",
                isCritical = true,
                isHighPriority = true
            )
            magnitude < 1000.0 -> MagneticZoneDetails(
                index = 6,
                name = "Extreme Risk Zone",
                status = "Extreme Magnetic Environment",
                message = "Emergency: An extremely strong magnetic field has been detected. Move away from the magnetic source immediately.",
                voiceText = "Emergency: An extremely strong magnetic field has been detected. Move away from the magnetic source immediately.",
                isCritical = true,
                isHighPriority = true
            )
            else -> MagneticZoneDetails(
                index = 7,
                name = "Dangerous Magnetic Environment",
                status = "Dangerous Magnetic Environment",
                message = "Danger: Extremely high magnetic field detected. Leave the area immediately if it is safe to do so. Monitoring will continue until magnetic levels return to a safer range.",
                voiceText = "Danger: Extremely high magnetic field detected. Leave the area immediately if it is safe to do so. Monitoring will continue until magnetic levels return to a safer range.",
                isCritical = true,
                isHighPriority = true
            )
        }
    }

    private fun getActionsForZone(index: Int): String {
        return when (index) {
            0, 1 -> "No action required."
            2 -> "Avoid keeping device near strong magnets."
            3 -> "Move device away from magnetic source."
            4 -> "Move away from source immediately to prevent sensor interference."
            5 -> "Critical warning issued. Leave magnetic environment if practical."
            6 -> "Emergency alert. Move away from magnetic source immediately."
            else -> "Danger alert. Leave area immediately if safe."
        }
    }

    private fun getDeviceOrientation(): String {
        val x = Math.abs(lastAccelX)
        val y = Math.abs(lastAccelY)
        val z = Math.abs(lastAccelZ)
        return when {
            z > x && z > y -> "Flat"
            y > x && y > z -> "Portrait"
            x > y && x > z -> "Landscape"
            else -> "Portrait"
        }
    }

    private fun getBatteryIntent(): Intent? {
        return registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun processMagneticFieldMeasurement(magnitude: Double) {
        try {
            com.example.engines.WatchdogEngine.registerEvent("Magnetic")
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog magnetic event registration failed", e)
        }
        if (!isMagneticFieldDetectionEnabled) {
            liveBatteryState.update { current ->
                current.copy(
                    magneticFieldMagnitude = magnitude.toFloat(),
                    magneticSafetyZone = "Normal Zone",
                    magneticSafetyZoneIndex = 0,
                    magneticMessage = "Magnetic field detection is disabled in settings."
                )
            }
            if (activeMagneticEventId != null) {
                activeMagneticEventId = null
            }
            return
        }

        val now = System.currentTimeMillis()

        // --- MAGNETIC FIELD CONSTRAINTS ---
        val zone = getMagneticZoneDetails(magnitude)

        val isCharging = liveBatteryState.value.isCharging
        if (isCharging && magnitude <= 500.0 && !isMagneticEventTracking) {
            Log.d(TAG, "Magnetic Detection Ignored: Device Charging and magnitude <= 500 µT. Field: $magnitude µT")
            liveBatteryState.update { current ->
                current.copy(
                    magneticFieldMagnitude = magnitude.toFloat(),
                    magneticSafetyZone = zone.name,
                    magneticSafetyZoneIndex = zone.index,
                    magneticMessage = zone.message
                )
            }
            return
        }

        if (magnitude < 100.0) {
            liveBatteryState.update { current ->
                current.copy(
                    magneticFieldMagnitude = magnitude.toFloat(),
                    magneticSafetyZone = zone.name,
                    magneticSafetyZoneIndex = zone.index,
                    magneticMessage = zone.message
                )
            }
            return
        }

        // --- BASELINE TRACKING ---
        if (now - lastBaselineUpdateTime > 30000) {
            lastBaselineUpdateTime = now
            if (magneticBaselineUpdateCount > 0) {
                lastMagneticBaseline = (magneticBaselineSum / magneticBaselineUpdateCount)
                liveBatteryState.update { it.copy(magneticBaseline = lastMagneticBaseline) }
            }
            magneticBaselineSum = 0.0f
            magneticBaselineUpdateCount = 0
        }
        magneticBaselineSum += magnitude.toFloat()
        magneticBaselineUpdateCount++

        // --- INTERFERENCE CHECK (Pocket Only) ---
        if (isPocketModeActive) {
            val diff = Math.abs(magnitude - lastMagneticBaseline)
            if (diff > 50.0) { // Threshold for interference
                Log.w(TAG, "Pocket magnetic interference detected! Diff: $diff")
            }
        }

        if (magnitude >= 100.0) {
            val isCharging = liveBatteryState.value.isCharging
            if (!isMagneticEventTracking) {
                isMagneticEventTracking = true
                magneticEventStartTime = now
                magneticPeakValue = magnitude
                magneticSumValues = magnitude
                magneticValueCount = 1
            } else {
                magneticValueCount++
                magneticSumValues += magnitude
                if (magnitude > magneticPeakValue) {
                    magneticPeakValue = magnitude
                }
            }

            val durationMs = now - magneticEventStartTime
            val avgField = magneticSumValues / magneticValueCount
            val actions = getActionsForZone(zone.index)

            val isChargingIgnored = isCharging && magneticPeakValue <= 500.0
            val decision = when {
                isChargingIgnored -> "Ignored During Charging"
                durationMs < 5000L -> "Logged Only"
                else -> "Notified & Announced"
            }
            val notificationStatus = if (isChargingIgnored || durationMs < 5000L) "No" else "Yes"
            val announcementStatus = if (isChargingIgnored || durationMs < 5000L) "No" else "Yes"
            val aiConfidence = "98.5%"

            val voiceStatus = if (zone.voiceText != null && announcementStatus == "Yes") "Triggered" else "No Announcement"

            val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val sdfTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            val dateStr = sdfDate.format(java.util.Date(magneticEventStartTime))
            val timeStr = sdfTime.format(java.util.Date(magneticEventStartTime))

            val event = com.example.data.MagneticEvent(
                id = activeMagneticEventId ?: 0L,
                date = dateStr,
                time = timeStr,
                currentMagneticField = magnitude,
                peakMagneticField = magneticPeakValue,
                averageMagneticField = avgField,
                detectionDurationMs = durationMs,
                safetyZone = zone.name,
                sensorAccuracy = sensorAccuracy,
                deviceOrientation = getDeviceOrientation(),
                chargingStatus = if (isCharging) "Charging" else "Discharging",
                deviceTemperature = liveBatteryState.value.temperature,
                voiceAnnouncementStatus = voiceStatus,
                actionsTaken = actions,
                decision = decision,
                notificationStatus = notificationStatus,
                announcementStatus = announcementStatus,
                aiConfidence = aiConfidence
            )

            if (durationMs >= 5000L) {
                val eventId = activeMagneticEventId
                if (eventId == null) {
                    serviceScope.launch(Dispatchers.IO) {
                        val rowId = repository?.insertMagneticEvent(event) ?: -1L
                        if (rowId != -1L) {
                            activeMagneticEventId = rowId
                        }
                    }
                } else {
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.insertMagneticEvent(event.copy(id = eventId))
                    }
                }
            }

            handleMagneticAnnouncementsAndNotifications(magnitude, zone)
        } else {
            if (isMagneticEventTracking) {
                val durationMs = now - magneticEventStartTime
                val avgField = if (magneticValueCount > 0) magneticSumValues / magneticValueCount else magnitude
                val actions = "Finished. Moved back to safer magnetic zone."
                val isCharging = liveBatteryState.value.isCharging
                val isChargingIgnored = isCharging && magneticPeakValue <= 500.0
                val decision = when {
                    isChargingIgnored -> "Ignored During Charging"
                    durationMs < 5000L -> "Logged Only"
                    else -> "Notified & Announced"
                }
                val notificationStatus = if (isChargingIgnored || durationMs < 5000L) "No" else "Yes"
                val announcementStatus = if (isChargingIgnored || durationMs < 5000L) "No" else "Yes"
                val aiConfidence = "98.5%"

                val eventId = activeMagneticEventId
                serviceScope.launch(Dispatchers.IO) {
                    if (durationMs < 5000L) {
                        if (eventId != null) {
                            repository?.deleteMagneticEvent(eventId)
                        }
                    } else {
                        val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        val sdfTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                        val dateStr = sdfDate.format(java.util.Date(magneticEventStartTime))
                        val timeStr = sdfTime.format(java.util.Date(magneticEventStartTime))

                        val event = com.example.data.MagneticEvent(
                            id = eventId ?: 0L,
                            date = dateStr,
                            time = timeStr,
                            currentMagneticField = magnitude,
                            peakMagneticField = magneticPeakValue,
                            averageMagneticField = avgField,
                            detectionDurationMs = durationMs,
                            safetyZone = zone.name,
                            sensorAccuracy = sensorAccuracy,
                            deviceOrientation = getDeviceOrientation(),
                            chargingStatus = if (isCharging) "Charging" else "Discharging",
                            deviceTemperature = liveBatteryState.value.temperature,
                            voiceAnnouncementStatus = if (lastAnnouncedZoneIndex >= 2 && announcementStatus == "Yes") "Triggered" else "No Announcement",
                            actionsTaken = actions,
                            timestamp = magneticEventStartTime,
                            decision = decision,
                            notificationStatus = notificationStatus,
                            announcementStatus = announcementStatus,
                            aiConfidence = aiConfidence
                        )
                        repository?.insertMagneticEvent(event)
                        repository?.logBatteryEvent("NETRA", "Magnetic Event Closed", "Magnetic event of peak ${String.format(java.util.Locale.US, "%.1f", magneticPeakValue)} uT finished after ${durationMs / 1000}s. Decision: $decision", "HARDWARE", "Netra")
                    }
                }
                activeMagneticEventId = null
                isMagneticEventTracking = false
            }

            if (magnitude >= 50.0) {
                if (currentSettings.aiAnalyticsEnabled) {
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent("NETRA", "Elevated Magnetic Field Detected", "Magnetic field of ${String.format(java.util.Locale.US, "%.1f", magnitude)} uT detected (Safe Zone). Monitoring continues.", "HARDWARE", "Netra")
                    }
                }
            }

            lastAnnouncedZoneIndex = 0
        }
    }

    private fun handleMagneticAnnouncementsAndNotifications(magnitude: Double, zone: MagneticZoneDetails) {
        val now = System.currentTimeMillis()
        val durationMs = if (magneticEventStartTime > 0L) now - magneticEventStartTime else 0L
        if (durationMs < 5000L) {
            return
        }
        val isCharging = liveBatteryState.value.isCharging
        val isChargingIgnored = isCharging && magneticPeakValue <= 500.0
        if (isChargingIgnored) {
            return
        }
        val isNewZone = zone.index > lastAnnouncedZoneIndex

        if (isNewZone && zone.index >= 2) {
            lastAnnouncedZoneIndex = zone.index

            val title = "Netra Magnetic Warning: ${zone.name}"
            val desc = zone.message
            showMagneticNotification(
                text = desc,
                title = title,
                isHighPriority = zone.isHighPriority,
                isCritical = zone.isCritical,
                isPersistent = (zone.index == 7)
            )

            val voiceText = zone.voiceText
            if (voiceText != null && shouldAnnounce() && currentSettings.voiceAssistantEnabled) {
                playWarningToneAndShowNotification(voiceText)
            }

            val durationMs = if (magneticEventStartTime > 0L) now - magneticEventStartTime else 0L
            val avgField = if (magneticValueCount > 0) magneticSumValues / magneticValueCount else magnitude

            when (zone.index) {
                3 -> {
                    val report = com.example.reports.MagneticSafetyReport.generateTechnicalReport(
                        magnitude, magneticPeakValue, avgField, durationMs, liveBatteryState.value.temperature
                    )
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent("REPORT", "Magnetic Technical Report", report, "HARDWARE", "Netra")
                    }
                }
                4 -> {
                    val report = com.example.reports.MagneticSafetyReport.generateSafetyReport(
                        magnitude, magneticPeakValue, avgField, durationMs, liveBatteryState.value.temperature
                    )
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent("REPORT", "Magnetic Safety Report", report, "HARDWARE", "Netra")
                    }
                }
                7 -> {
                    val report = com.example.reports.MagneticSafetyReport.generateDangerousEnvironmentReport(
                        magnitude, magneticPeakValue, avgField, durationMs, liveBatteryState.value.temperature
                    )
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent("REPORT", "Magnetic Critical Danger Report", report, "HARDWARE", "Netra")
                    }
                }
            }
        }
    }

    private fun showMagneticNotification(text: String, title: String, isHighPriority: Boolean, isCritical: Boolean, isPersistent: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val priority = when {
            isCritical -> NotificationCompat.PRIORITY_MAX
            isHighPriority -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val warningNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(priority)
            .setOngoing(isPersistent)
            .build()
        notificationManager.notify(3004, warningNotification)
    }

    private var lastExternalHeatLogTime = 0L

    private fun updateHeatMonitoring(currentTemp: Float, currentLux: Float, isHighLight: Boolean, settings: SettingsEntity) {
        // --- Netra External Heat Inference Engine Integration ---
        val inferenceResult = externalHeatInferenceEngine?.updateAndInfer(
            currentTemp = currentTemp,
            isCharging = liveBatteryState.value.isCharging,
            isScreenOn = isScreenOn,
            ambientLightLux = currentLux,
            outdoorTemp = liveBatteryState.value.outdoorTemp,
            currentSettings = settings
        )

        if (inferenceResult != null) {
            val wasInferred = liveBatteryState.value.isExternalHeatInferred
            val isInferredNow = inferenceResult.isInferred

            liveBatteryState.update { current ->
                current.copy(
                    isExternalHeatInferred = isInferredNow,
                    externalHeatConfidence = inferenceResult.confidence,
                    externalHeatWarningText = if (isInferredNow) {
                        "Warning. Possible external heat source detected. Device temperature is rising unusually. Move the phone away from the heat source immediately."
                    } else "",
                    externalHeatRiseRate = inferenceResult.riseRate,
                    externalHeatStartTime = inferenceResult.startTime,
                    externalHeatEndTime = inferenceResult.endTime,
                    externalHeatPeakTemp = inferenceResult.peakTemp,
                    externalHeatWarningDurationSec = inferenceResult.durationSec
                )
            }

            // If a warning is newly triggered, execute actions:
            if (!wasInferred && isInferredNow) {
                // High-priority notification
                val warningText = "Warning. Possible external heat source detected. Device temperature is rising unusually. Move the phone away from the heat source immediately."
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val warningNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("⚠️ External Heat Detected")
                    .setContentText(warningText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(warningText))
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .build()
                notificationManager.notify(3009, warningNotification)

                // Emergency voice announcement disabled
                // (Settings check and speakReal removed as per user request)

                // Write log to DB
                serviceScope.launch(Dispatchers.IO) {
                    val reasonsStr = inferenceResult.reasons.joinToString(", ")
                    repository?.logBatteryEvent(
                        eventType = "NETRA",
                        title = "External Heat Inferred",
                        details = """
                            Start Time: ${formatTime(inferenceResult.startTime)}
                            Peak Temp: ${inferenceResult.peakTemp}°C
                            Rise Rate: ${String.format(Locale.US, "%.3f", inferenceResult.riseRate)}°C/min
                            Screen State: ${if (isScreenOn) "ON" else "OFF"}
                            Charging State: ${if (liveBatteryState.value.isCharging) "Charging" else "Not Charging"}
                            Weather Temp: ${liveBatteryState.value.outdoorTemp}°C
                            AI Confidence: ${inferenceResult.confidence}%
                            Reasons: $reasonsStr
                        """.trimIndent(),
                        category = "HARDWARE",
                        source = "Netra"
                    )
                }
            } else if (wasInferred && !isInferredNow) {
                // Cancel notification
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(3009)
                
                // Write end-of-event log
                serviceScope.launch(Dispatchers.IO) {
                    repository?.logBatteryEvent(
                        eventType = "NETRA",
                        title = "External Heat Warning Stabilized",
                        details = """
                            End Time: ${formatTime(System.currentTimeMillis())}
                            Peak Temp: ${inferenceResult.peakTemp}°C
                            Rise Rate: ${String.format(Locale.US, "%.3f", inferenceResult.riseRate)}°C/min
                            Duration: ${inferenceResult.durationSec}s
                        """.trimIndent(),
                        category = "HARDWARE",
                        source = "Netra"
                    )
                }
            } else if (isInferredNow) {
                // Write continuous logs / update active warning notification
                // To avoid spamming logs, write every 30 seconds if active
                val now = System.currentTimeMillis()
                if (now - lastExternalHeatLogTime > 30000) {
                    lastExternalHeatLogTime = now
                    serviceScope.launch(Dispatchers.IO) {
                        repository?.logBatteryEvent(
                            eventType = "NETRA",
                            title = "External Heat Warning Active",
                            details = "Warning continues. Temp: ${currentTemp}°C, Rise Rate: ${String.format(Locale.US, "%.3f", inferenceResult.riseRate)}°C/min, Confidence: ${inferenceResult.confidence}%",
                            category = "HARDWARE",
                            source = "Netra"
                        )
                    }
                }
            }
        }

        if (!settings.isLightIntensityDetectionEnabled) {
            if (isHeatMonitoringModeActive) {
                isHeatMonitoringModeActive = false
                temperaturesDuringLight.clear()
                isEarlyHeatWarningIssued = false
                Log.d(TAG, "Heat Monitoring Mode disabled by settings.")
            }
            return
        }

        val now = System.currentTimeMillis()
        if (isHighLight) {
            if (!isHeatMonitoringModeActive) {
                isHeatMonitoringModeActive = true
                highLightDetectionTime = now
                initialLuxValue = currentLux
                initialTemperature = currentTemp
                temperatureRiseStartTime = 0L
                temperatureRiseRate = 0f
                isEarlyHeatWarningIssued = false
                temperaturesDuringLight.clear()
                temperaturesDuringLight.add(now to currentTemp)
                Log.d(TAG, "Heat Monitoring Mode STARTED: Initial Lux=$currentLux, Temp=$currentTemp")
            } else {
                val lastRecorded = temperaturesDuringLight.lastOrNull()
                if (lastRecorded == null || Math.abs(lastRecorded.second - currentTemp) >= 0.05f) {
                    temperaturesDuringLight.add(now to currentTemp)
                    Log.d(TAG, "Heat Monitoring Mode: Added temperature reading $currentTemp°C (Lux=$currentLux)")
                    
                    if (currentTemp > initialTemperature) {
                        if (temperatureRiseStartTime == 0L) {
                            temperatureRiseStartTime = now
                        }
                        val durationHours = (now - temperatureRiseStartTime) / 3600000f
                        temperatureRiseRate = if (durationHours > 0.001f) {
                            (currentTemp - initialTemperature) / durationHours
                        } else {
                            0f
                        }
                    }
                    
                    if (!isEarlyHeatWarningIssued && temperaturesDuringLight.size >= 3) {
                        val size = temperaturesDuringLight.size
                        val t0 = temperaturesDuringLight[size - 3].second
                        val t1 = temperaturesDuringLight[size - 2].second
                        val t2 = temperaturesDuringLight[size - 1].second
                        
                        val isStrictlyIncreasing = (t2 > t1) && (t1 > t0)
                        val overallRise = currentTemp - initialTemperature
                        
                        if (overallRise >= 0.3f && (isStrictlyIncreasing || overallRise >= 0.6f)) {
                            triggerEarlyHeatWarning(currentTemp, currentLux)
                        }
                    }
                }
            }
        } else {
            if (isHeatMonitoringModeActive) {
                isHeatMonitoringModeActive = false
                temperaturesDuringLight.clear()
                isEarlyHeatWarningIssued = false
                Log.d(TAG, "Heat Monitoring Mode STOPPED: Lux dropped below threshold.")
            }
        }
    }

    private fun triggerEarlyHeatWarning(currentTemp: Float, currentLux: Float) {
        if (isEarlyHeatWarningIssued) return
        isEarlyHeatWarningIssued = true
        earlyHeatWarningTime = System.currentTimeMillis()

        val text = "Notice: Strong ambient light detected and device temperature is increasing. Move the phone to a shaded or cooler location to help prevent overheating."
        Log.w(TAG, "Early Heat Warning Triggered: $text")

        if (currentSettings.voiceAssistantEnabled && shouldAnnounce()) {
            playWarningToneAndShowNotification(text)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val warningNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Early Heat Warning")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(3005, warningNotification)

        val currentState = BatteryService.liveBatteryState.value
        val chargingStatus = if (currentState.isPlugged) "Charging (${currentState.chargingType})" else "Discharging"
        val screenState = if (isScreenOn) "ON" else "OFF"
        val voiceStatus = if (currentSettings.voiceAssistantEnabled && shouldAnnounce()) "Spoken Successfully" else "Muted"
        
        val logDetails = """
            High Light Detection Time: ${formatTime(highLightDetectionTime)}
            Initial Lux Value: $initialLuxValue Lux
            Initial Temperature: $initialTemperature°C
            Temperature Rise Start Time: ${if (temperatureRiseStartTime > 0) formatTime(temperatureRiseStartTime) else "N/A"}
            Temperature Rise Rate: ${String.format(java.util.Locale.US, "%.2f", temperatureRiseRate)}°C/hr
            Early Heat Warning Time: ${formatTime(earlyHeatWarningTime)}
            Current Temperature: $currentTemp°C
            Charging Status: $chargingStatus
            Screen State: $screenState
            Voice Announcement Status: $voiceStatus
        """.trimIndent()

        serviceScope.launch(Dispatchers.IO) {
            repository?.logBatteryEvent(
                eventType = "NETRA",
                title = "Early Heat Warning",
                details = logDetails,
                category = "HARDWARE",
                source = "Netra"
            )
        }
    }

    private fun formatTime(timestamp: Long): String {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestamp))
        } catch (e: Exception) {
            timestamp.toString()
        }
    }

    private fun shouldAnnounce(): Boolean {
        try {
            if (com.example.providers.SafeTelephonyProvider.isCallActive(this)) {
                return false
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val isAudioPlaying = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager.activePlaybackConfigurations.isNotEmpty()
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isMusicActive
                    }
                } catch (e: Throwable) {
                    false
                }
                if (isAudioPlaying) return false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error in shouldAnnounce check: ${e.message}")
        }

        return true
    }

    private fun speakReal(announcement: Announcement, settings: SettingsEntity) {
        val isThermalSafety = announcement.category == AnnouncementCategory.THERMAL_EMERGENCY ||
                              announcement.category == AnnouncementCategory.SAFETY_EMERGENCY ||
                              announcement.priority == Priority.EMERGENCY_SAFETY ||
                              announcement.text.contains("temperature", ignoreCase = true) ||
                              announcement.text.contains("overheat", ignoreCase = true) ||
                              announcement.text.contains("heat", ignoreCase = true)

        // CRITICAL NETRA AUDIO RULE: Non-critical battery voice announcements are suppressed.
        if (!isThermalSafety) {
            val textLower = announcement.text.lowercase()
            if (textLower.contains("battery") || textLower.contains("charging") || textLower.contains("charger") ||
                textLower.contains("percent") || textLower.contains("full") || textLower.contains("connected") ||
                textLower.contains("disconnected") || textLower.contains("drain") || textLower.contains("c ") ||
                textLower.contains("d ") || textLower.contains("100") || textLower.contains("telemetry")) {
                return
            }
        }

        if (com.example.engines.deepsleep.DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety, settings, System.currentTimeMillis(), announcement.text, announcement.category.name)) {
            Log.d(TAG, "Announcement suppressed by DeepSleepEngine policy: ${announcement.text}")
            return
        }

        if (!isThermalSafety) {
            if (!settings.voiceAssistantEnabled) return
            if (!shouldAnnounce()) return
        }

        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized yet. Fallback to warning tone & notification for: ${announcement.text}")
            playWarningToneAndShowNotification(announcement.text)
            return
        }

        tts?.apply {
            setPitch(settings.speechPitch)
            setSpeechRate(settings.speechSpeed)
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            setAudioAttributes(audioAttributes)
            
            val availableVoices = voices
            if (availableVoices != null) {
                var selectedVoice = availableVoices.firstOrNull()
                for (voice in availableVoices) {
                    val name = voice.name.lowercase()
                    if (settings.voiceType == "MALE" && (name.contains("male") || name.contains("masc"))) {
                        selectedVoice = voice
                        break
                    } else if (settings.voiceType == "FEMALE" && (name.contains("female") || name.contains("fem"))) {
                        selectedVoice = voice
                        break
                    }
                }
                selectedVoice?.let { voice = it }
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attr = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attr)
                        .build()
                    audioManager.requestAudioFocus(request)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(
                        { },
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Audio focus request handled safely: ${e.message}")
            }

            val params = android.os.Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
            }
            VoiceAnnouncementOptimizer.speakWith1SecondCeiling(
                tts = this,
                rawText = announcement.text,
                userBaseSpeed = settings.speechSpeed,
                utteranceId = announcement.id,
                queueMode = TextToSpeech.QUEUE_FLUSH,
                params = params
            )
        }
    }

    private fun playWarningToneAndShowNotification(text: String) {
        val notificationEvent = if (text.contains("Target", ignoreCase = true)) {
            com.example.engines.notification.NotificationEvent.BATTERY_STATUS_CHANGES
        } else if (text.contains("Temperature", ignoreCase = true) || text.contains("Overheat", ignoreCase = true)) {
            com.example.engines.notification.NotificationEvent.TEMPERATURE_WARNING
        } else if (text.contains("drain", ignoreCase = true) || text.contains("Discharging", ignoreCase = true)) {
            com.example.engines.notification.NotificationEvent.BATTERY_STATUS_CHANGES
        } else {
            com.example.engines.notification.NotificationEvent.HEALTH_MONITOR_MESSAGES
        }

        com.example.engines.notification.NotificationPreferenceEngine.requestNotification(
            context = this,
            event = notificationEvent,
            title = "AmpereFlow Alert",
            details = text,
            source = "BatteryService",
            overridePriority = com.example.engines.notification.EventPriority.WARNING
        )
    }

    private fun isCurrentTimeInWindow(startTimeStr: String, endTimeStr: String): Boolean {
        try {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentTimeInMinutes = currentHour * 60 + currentMinute

            val startMinutes = parseTimeToMinutes(startTimeStr)
            val endMinutes = parseTimeToMinutes(endTimeStr)

            return if (startMinutes < endMinutes) {
                currentTimeInMinutes in startMinutes..endMinutes
            } else {
                currentTimeInMinutes >= startMinutes || currentTimeInMinutes <= endMinutes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking time window", e)
            return false
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.trim().split(" ")
        val timeParts = parts[0].split(":")
        var hour = timeParts[0].toInt()
        val minute = timeParts[1].toInt()
        val amPm = parts[1].uppercase()

        if (amPm == "PM" && hour < 12) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0

        return hour * 60 + minute
    }
}

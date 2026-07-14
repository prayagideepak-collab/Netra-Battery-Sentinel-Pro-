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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.BatteryApplication
import com.example.MainActivity
import com.example.R
import com.example.data.ChargingSession
import com.example.data.SettingsEntity
import com.example.widget.AmpereFlowWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.*

class BatteryService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "BatteryService"
        private const val CHANNEL_ID = "battery_monitor_channel"
        private const val NOTIFICATION_ID = 2002

        val liveBatteryState = MutableStateFlow(BatteryState())
        val isServiceRunning = MutableStateFlow(false)
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var ttsQueue = LinkedList<String>()

    private var lastAnnouncedPercentage = -1
    private var lastPluggedState = false
    private var lastChargingType = "None"
    
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
        val timestamp: Long = System.currentTimeMillis()
    )

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

    // Alerts tracking to prevent repeat vocal alerts within a short duration
    private var lastAlertTimeTemp = 0L
    private var lastAlertTimeSpeed = 0L
    private var lastAlertTimeDrain = 0L

    private val repository by lazy {
        (application as BatteryApplication).repository
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    processBatteryUpdate(intent)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    processPowerConnected()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    processPowerDisconnected()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BatteryService onCreate")
        isServiceRunning.value = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(liveBatteryState.value))

        // Register battery status receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)

        // Initialize Text to Speech
        tts = TextToSpeech(this, this)
    }

    private fun getBatteryIntent(): Intent? {
        return registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun processPowerConnected() {
        Log.d(TAG, "ACTION_POWER_CONNECTED received")
        val batteryIntent = getBatteryIntent()
        if (batteryIntent != null) {
            processBatteryUpdate(batteryIntent, forcePowerConnected = true)
        } else {
            serviceScope.launch {
                val settings = repository.getSettingsOrInit()
                val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                announceChargerConnected("AC", level, settings)
            }
        }
    }

    private fun processPowerDisconnected() {
        Log.d(TAG, "ACTION_POWER_DISCONNECTED received")
        val batteryIntent = getBatteryIntent()
        if (batteryIntent != null) {
            processBatteryUpdate(batteryIntent, forcePowerDisconnected = true)
        } else {
            serviceScope.launch {
                val settings = repository.getSettingsOrInit()
                val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                announceChargerDisconnected(level, settings)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BatteryService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "BatteryService onDestroy")
        isServiceRunning.value = false
        unregisterReceiver(batteryReceiver)
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
            
            serviceScope.launch {
                val settings = repository.getSettingsOrInit()
                queueAnnouncement("Ampere Flow battery monitoring is active.", Priority.INFORMATION, settings)
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

        val contentTitle = if (state.isCharging) {
            "Charging (${state.chargingType}): ${state.percentage}%"
        } else {
            "Battery Status: ${state.percentage}%"
        }

        val contentText = if (state.isCharging) {
            val etaStr = if (state.timeTo100Min > 0) "${state.timeTo100Min}m remaining" else "Calculating..."
            "Temp: ${state.temperature}°C | Speed: ${String.format(Locale.US, "%.1f", state.speed)}%/h | $etaStr"
        } else {
            "Temp: ${state.temperature}°C | Volt: ${String.format(Locale.US, "%.2f", state.voltage / 1000f)}V | Health: ${state.health}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(state: BatteryState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun processBatteryUpdate(
        intent: Intent,
        forcePowerConnected: Boolean = false,
        forcePowerDisconnected: Boolean = false
    ) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        var status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        var plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)

        if (level == -1 || scale == -1) return

        val percentage = (level * 100 / scale.toFloat()).toInt()
        val temperature = rawTemp / 10f

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

        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

        val healthStr = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
            else -> "Unknown"
        }

        // Query actual battery manager for electric current (mA)
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        var rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        
        // Handle positive/negative current convention. Usually, discharging is negative and charging is positive.
        // Some manufacturers return microamperes. Let's convert to milliamperes (mA).
        var currentNowVal = rawCurrent / 1000
        if (Math.abs(currentNowVal) > 15000) {
            // fallback if value is nanoamperes or scale is different
            currentNowVal /= 1000
        }

        // If plugged, force it positive. If unplugged, force it negative for discharging analytics.
        if (isPlugged && currentNowVal < 0) {
            currentNowVal = -currentNowVal
        } else if (!isPlugged && currentNowVal > 0) {
            currentNowVal = -currentNowVal
        }

        val rawAvgCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        var currentAvgVal = rawAvgCurrent / 1000
        if (Math.abs(currentAvgVal) > 15000) currentAvgVal /= 1000

        // Calculate power in Watts (Volt * Ampere)
        val voltVal = voltage / 1000f
        val ampVal = Math.abs(currentNowVal) / 1000f
        val powerWattVal = voltVal * ampVal

        // Keep running statistics
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

        val finalPeakCurrent = peakCurrent
        val finalPeakWatt = peakWatt
        val finalAvgCurrent = if (readingsCount > 0) (sumCurrent / readingsCount).toInt() else 0
        val finalAvgWatt = if (readingsCount > 0) sumWatt / readingsCount else 0f

        val finalHighestTemp = highestTemp
        val finalLowestTemp = lowestTemp
        val finalAverageTemp = if (tempReadingsCount > 0) sumTemp / tempReadingsCount else temperature

        // API 34+ Cycle Count
        val rawCycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        } else {
            -1
        }

        serviceScope.launch {
            val settings = repository.getSettingsOrInit()

            // Analyze charging / discharging session & learn speed
            var speed = 0f
            var timeTo50 = 0
            var timeTo80 = 0
            var timeTo100 = 0

            val now = System.currentTimeMillis()

            if (isPlugged) {
                if (!lastPluggedState) {
                    // Charger connected
                    sessionStartTime = now
                    sessionStartPercentage = percentage
                    repository.startSession(sessionStartTime, percentage, chargingType, temperature)
                    announceChargerConnected(chargingType, percentage, settings)
                } else {
                    repository.updateActiveSessionTemperature(temperature)
                }

                // Calculate charging speed and ETAs
                val sessionDurationHr = (now - sessionStartTime) / 3600000f
                val sessionGainedPct = percentage - sessionStartPercentage

                val realTimeSpeed = if (sessionDurationHr > 0.02f) sessionGainedPct / sessionDurationHr else 0f
                val historicalSpeed = getHistoricalAverageSpeed(chargingType)

                // Blended speed
                speed = if (sessionDurationHr > 0.05f) {
                    val weight = (sessionDurationHr * 2f).coerceAtMost(1f) // full weight after 30 mins
                    realTimeSpeed * weight + historicalSpeed * (1f - weight)
                } else {
                    historicalSpeed
                }

                val safeSpeed = speed.coerceAtLeast(5f)

                // Predictions
                timeTo50 = if (percentage < 50) (((50 - percentage) / safeSpeed) * 60).toInt() else 0
                timeTo80 = if (percentage < 80) (((80 - percentage) / safeSpeed) * 60).toInt() else 0
                timeTo100 = if (percentage < 100) (((100 - percentage) / safeSpeed) * 60).toInt() else 0

                // Check for temperature & speed alerts while charging
                checkChargingAlerts(temperature, sessionDurationHr, realTimeSpeed, settings)
            } else {
                if (lastPluggedState) {
                    // Charger disconnected
                    val sessionEndTime = now
                    repository.endActiveSession(sessionEndTime, percentage)
                    announceChargerDisconnected(percentage, settings)
                    
                    // Reset session tracking for discharging
                    sessionStartTime = now
                    sessionStartPercentage = percentage
                } else {
                    // Track discharging stats
                    val dischargeDurationHr = (now - sessionStartTime) / 3600000f
                    val dischargeGainedPct = sessionStartPercentage - percentage
                    val dischargeRate = if (dischargeDurationHr > 0.02f) dischargeGainedPct / dischargeDurationHr else 0f
                    speed = -dischargeRate // Negative speed indicates discharge

                    // Discharging alarm alerts
                    checkDischargingAlerts(percentage, temperature, dischargeRate, settings)
                }
            }

            // Periodic interval vocal announcements
            checkIntervalAnnouncements(percentage, isCharging, settings)

            // Low battery spoken alert
            if (!isCharging && percentage <= settings.lowBatteryThreshold && lastAnnouncedPercentage > settings.lowBatteryThreshold) {
                if (settings.lowBatteryEnabled) {
                    queueAnnouncement("Attention. Battery level is low. Currently at $percentage percent. Please connect the charger.", Priority.BATTERY_SAFETY, settings)
                }
            }

            // Battery complete announcements
            if (percentage >= settings.fullBatteryThreshold && lastAnnouncedPercentage < settings.fullBatteryThreshold && isCharging) {
                announceChargingComplete(settings)
            }

            // Save state variables
            lastAnnouncedPercentage = if (percentage == 100 || (settings.announcementInterval > 0 && percentage % settings.announcementInterval == 0) || percentage == settings.customPercentage) percentage else lastAnnouncedPercentage
            lastPluggedState = isPlugged
            lastChargingType = chargingType

            // Estimate Health Percentage dynamically based on typical metrics
            val simulatedCycles = if (rawCycleCount > 0) rawCycleCount else {
                // Approximate cycles based on average historical charge sessions
                val allFinished = repository.allSessions.first().filter { it.endTime != null && it.endPercentage != null }
                val totalGained = allFinished.sumOf { (it.endPercentage ?: 0) - it.startPercentage }
                (totalGained / 100).coerceAtLeast(35) // baseline starting estimate
            }
            // Health percentage calculation (loss of 1% per 100 cycles typical)
            val computedHealthPct = (100 - (simulatedCycles / 100)).coerceIn(80, 100)

            val updatedState = BatteryState(
                percentage = percentage,
                isCharging = isCharging,
                chargingType = chargingType,
                temperature = temperature,
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
                highestTemp = finalHighestTemp,
                lowestTemp = finalLowestTemp,
                averageTemp = finalAverageTemp,
                designCapacity = 4500,
                estimatedCapacity = (4500 * computedHealthPct) / 100,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL
            )

            liveBatteryState.value = updatedState
            updateNotification(updatedState)

            // Sync with App Widgets instantly
            AmpereFlowWidget.updateAllWidgets(applicationContext, updatedState)
        }
    }

    private suspend fun getHistoricalAverageSpeed(type: String): Float {
        val sessions = repository.allSessions.first()
        val filtered = sessions.filter { it.chargingType == type && it.endTime != null && it.endPercentage != null }
        if (filtered.isEmpty()) {
            return when (type) {
                "AC" -> 35f
                "USB" -> 12f
                "Wireless" -> 18f
                else -> 20f
            }
        }
        var totalSpeed = 0f
        var count = 0
        for (s in filtered) {
            val durationHr = (s.endTime!! - s.startTime) / 3600000f
            val gainedPct = s.endPercentage!! - s.startPercentage
            if (durationHr > 0.05f && gainedPct > 0) {
                totalSpeed += gainedPct / durationHr
                count++
            }
        }
        return if (count > 0) totalSpeed / count else 25f
    }

    private fun announceChargerConnected(type: String, level: Int, settings: SettingsEntity) {
        if (settings.chargerConnectedEnabled) {
            queueAnnouncement("Charger connected. $type charging started at $level percent.", Priority.CHARGING_EVENTS, settings)
        }
    }

    private fun announceChargerDisconnected(level: Int, settings: SettingsEntity) {
        if (settings.chargerDisconnectedEnabled) {
            queueAnnouncement("Charger disconnected. Battery at $level percent.", Priority.CHARGING_EVENTS, settings)
        }
    }

    private fun announceChargingComplete(settings: SettingsEntity) {
        if (settings.batteryFullEnabled) {
            val level = settings.fullBatteryThreshold
            val msg = if (level >= 100) {
                "Battery fully charged. Please disconnect the charger to save energy."
            } else {
                "Battery charged to target limit of $level percent. Please unplug the charger."
            }
            queueAnnouncement(msg, Priority.BATTERY_SAFETY, settings)
        }
    }

    private fun checkIntervalAnnouncements(percentage: Int, isCharging: Boolean, settings: SettingsEntity) {
        if (!isCharging) return
        
        val interval = settings.announcementInterval
        if (interval <= 0) return
        
        val isAtInterval = percentage % interval == 0
        val isAtCustom = percentage == settings.customPercentage

        if ((isAtInterval || isAtCustom) && percentage != lastAnnouncedPercentage) {
            if (settings.batteryPercentageEnabled) {
                queueAnnouncement("Battery charging. Current level is $percentage percent.", Priority.INFORMATION, settings)
            }
        }
    }

    private fun checkChargingAlerts(temperature: Float, sessionDurationHr: Float, currentSpeed: Float, settings: SettingsEntity) {
        val now = System.currentTimeMillis()

        // Critical Temperature Overheat Alert (>= 45°C) - Priority 1 (Emergency Safety)
        if (temperature >= 45f && (now - lastAlertTimeTemp > 300000)) {
            lastAlertTimeTemp = now
            if (settings.criticalTempEnabled) {
                queueAnnouncement("Warning. Emergency. Critical Battery temperature detected at ${temperature.toInt()} degrees Celsius. Please unplug immediately.", Priority.EMERGENCY_SAFETY, settings)
            }
        }
        // High Temp Overheat Alert (>= threshold and < 45°C) - Priority 3 (Battery Safety)
        else if (temperature >= settings.tempAlertThreshold && (now - lastAlertTimeTemp > 300000)) {
            lastAlertTimeTemp = now
            if (settings.tempWarningEnabled) {
                queueAnnouncement("Warning. Battery temperature is high. It has reached ${temperature.toInt()} degrees Celsius.", Priority.BATTERY_SAFETY, settings)
            }
        }

        // Abnormal Slow Charging Alert (plugged for > 15 mins but charging extremely slow)
        if (sessionDurationHr > 0.25f && currentSpeed in 0.1f..4f && (now - lastAlertTimeSpeed > 600000)) {
            lastAlertTimeSpeed = now
            if (settings.smartBatteryAlertsEnabled) {
                queueAnnouncement("Notice. Charging is extremely slow. Please check if your cable or charger adapter is fully plugged.", Priority.INFORMATION, settings)
            }
        }
    }

    private fun checkDischargingAlerts(percentage: Int, temperature: Float, dischargeRate: Float, settings: SettingsEntity) {
        val now = System.currentTimeMillis()

        // Critical Temperature Overheat Alert (>= 45°C) - Priority 1 (Emergency Safety)
        if (temperature >= 45f && (now - lastAlertTimeTemp > 300000)) {
            lastAlertTimeTemp = now
            if (settings.criticalTempEnabled) {
                queueAnnouncement("Warning. Emergency. Critical Battery temperature detected at ${temperature.toInt()} degrees Celsius.", Priority.EMERGENCY_SAFETY, settings)
            }
        }
        // High temperature alert while discharging (>= threshold and < 45°C) - Priority 3 (Battery Safety)
        else if (temperature >= settings.tempAlertThreshold && (now - lastAlertTimeTemp > 300000)) {
            lastAlertTimeTemp = now
            if (settings.tempWarningEnabled) {
                queueAnnouncement("Warning. Battery temperature is very warm. It is ${temperature.toInt()} degrees Celsius.", Priority.BATTERY_SAFETY, settings)
            }
        }

        // Fast Discharge Drain Alert (discharging faster than 18%/hr under heavy load)
        if (dischargeRate >= 18f && (now - lastAlertTimeDrain > 450000)) {
            lastAlertTimeDrain = now
            if (settings.smartBatteryAlertsEnabled) {
                queueAnnouncement("Warning. Heavy battery drain detected. Discharging speed is high at ${dischargeRate.toInt()} percent per hour.", Priority.BATTERY_SAFETY, settings)
            }
        }
    }

    @Synchronized
    private fun queueAnnouncement(text: String, priority: Int, settings: SettingsEntity) {
        if (!settings.voiceAssistantEnabled) {
            Log.d(TAG, "Speech ignored: Voice assistant is disabled in settings.")
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive
        if (isScreenOn && !settings.screenOnVoiceEnabled) {
            Log.d(TAG, "Speech ignored: Screen is ON (vocal announcement suppressed).")
            return
        }

        if (settings.quietHoursEnabled) {
            val isInQuiet = isCurrentTimeInQuietHours(settings.quietHoursStart, settings.quietHoursEnd)
            if (isInQuiet) {
                Log.d(TAG, "Speech ignored: Quiet Hours are active ($text)")
                return
            }
        }

        val newAnnouncement = Announcement(text = text, priority = priority)
        Log.d(TAG, "Queuing announcement (Priority $priority): $text")

        val current = currentSpeakingAnnouncement
        if (current != null && newAnnouncement.priority < current.priority) {
            Log.d(TAG, "Interrupting current announcement '${current.text}' (Priority ${current.priority}) for higher priority '${newAnnouncement.text}' (Priority ${newAnnouncement.priority})")
            
            // Interrupt current TTS playback
            tts?.stop()
            
            // Re-enqueue the interrupted announcement at the head
            pendingAnnouncements.add(0, current)
            
            currentSpeakingAnnouncement = newAnnouncement
            speakReal(newAnnouncement, settings)
        } else if (current != null) {
            pendingAnnouncements.add(newAnnouncement)
            pendingAnnouncements.sortWith(compareBy<Announcement> { it.priority }.thenBy { it.timestamp })
            Log.d(TAG, "Added to pending queue. Queue size: ${pendingAnnouncements.size}")
        } else {
            currentSpeakingAnnouncement = newAnnouncement
            speakReal(newAnnouncement, settings)
        }
    }

    @Synchronized
    private fun playNextAnnouncement() {
        currentSpeakingAnnouncement = null
        if (pendingAnnouncements.isNotEmpty()) {
            val next = pendingAnnouncements.removeAt(0)
            currentSpeakingAnnouncement = next
            serviceScope.launch {
                val settings = repository.getSettingsOrInit()
                speakReal(next, settings)
            }
        }
    }

    private fun speakReal(announcement: Announcement, settings: SettingsEntity) {
        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized yet. Fallback to warning tone & notification for: ${announcement.text}")
            playWarningToneAndShowNotification(announcement.text)
            return
        }

        tts?.apply {
            setPitch(settings.speechPitch)
            setSpeechRate(settings.speechSpeed)
            
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
            val focusRequestResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attr = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
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
                    android.media.AudioManager.STREAM_NOTIFICATION,
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }

            if (focusRequestResult == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio focus granted")
            } else {
                Log.w(TAG, "Audio focus request failed")
            }

            val params = android.os.Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
            }
            speak(announcement.text, TextToSpeech.QUEUE_FLUSH, params, announcement.id)
        }
    }

    private fun playWarningToneAndShowNotification(text: String) {
        try {
            val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val r = android.media.RingtoneManager.getRingtone(applicationContext, notificationUri)
            r?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing warning tone", e)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val warningNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AmpereFlow Announcement")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(3003, warningNotification)
    }

    private fun isCurrentTimeInQuietHours(startTimeStr: String, endTimeStr: String): Boolean {
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
            Log.e(TAG, "Error checking quiet hours", e)
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

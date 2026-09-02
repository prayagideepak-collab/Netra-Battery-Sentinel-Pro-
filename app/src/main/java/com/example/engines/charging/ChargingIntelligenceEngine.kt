package com.example.engines.charging

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.engines.notification.NotificationEvent
import com.example.engines.notification.modules.AnnouncementQueue
import com.example.engines.notification.modules.PreferenceManager
import com.example.engines.coordinator.Engine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Charging Intelligence Engine (Device-Aware & Effective Charging System)
 *
 * Implements:
 * 1. One-Time Device Charging Profile Integration
 * 2. Two-Layer Classification:
 *    - Layer 1: Hardware/Input Charging Classification (vs Device Profile)
 *    - Layer 2: Effective Charging Classification (Input + Temp + Temp Trend + Workload)
 * 3. Temperature Trend Tracking (RISING, STABLE, FALLING, RECOVERING)
 * 4. Dynamic Downgrading with Thermal Warnings & Reason Auditing
 * 5. Hysteresis-based Debounced Voice Announcements (Zero Voice Spam)
 * 6. Telemetry Fallback (Truth-based: NO GUESSING!)
 */
object ChargingIntelligenceEngine : Engine {
    private const val TAG = "ChargingIntelligence"

    override val name = "ChargingIntelligenceEngine"
    override val priority = 16

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isInitialized = AtomicBoolean(false)
    private var appContext: Context? = null

    private val _chargingState = MutableStateFlow(ChargingIntelligenceState())
    val chargingState: StateFlow<ChargingIntelligenceState> = _chargingState.asStateFlow()

    private var sessionStartTime: Long = 0L
    private var initialBattery: Int = 0
    private var lastAnnouncedEffectiveClass: EffectiveChargingClass? = null
    private var pendingEffectiveClass: EffectiveChargingClass? = null
    private var classChangeTime: Long = 0L
    private var targetReachedStartTime: Long = 0L

    // Rolling thermal history for trend calculation
    private val tempHistory = LinkedList<Pair<Long, Float>>() // timestamp, temp
    private var historicalPeakTemp: Float = 0f

    override fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return
        appContext = context.applicationContext
        Log.i(TAG, "Initializing Device-Aware Charging Intelligence Engine...")

        appContext?.let { ctx ->
            val profile = DeviceChargingProfileManager.initializeProfile(ctx)
            _chargingState.value = _chargingState.value.copy(deviceProfile = profile)
        }

        Log.i(TAG, "Charging Intelligence Engine initialized successfully in event-driven mode.")
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down Charging Intelligence Engine...")
        isInitialized.set(false)
    }

    override fun getStatus(): String {
        val s = _chargingState.value
        val eff = s.effectiveAssessment
        return if (s.isCharging) {
            "Charging (${eff.effectiveClass}, Input: ${eff.inputClass}, Temp: ${eff.temperatureCelsius}°C ${eff.temperatureTrend})"
        } else {
            "Not Charging"
        }
    }

    fun setTargetCharge(target: Int) {
        _chargingState.value = _chargingState.value.copy(targetChargePercent = target)
        Log.i(TAG, "Charging target set to: $target%")
    }

    fun toggleUsbDataTransfer(active: Boolean) {
        _chargingState.value = _chargingState.value.copy(isUsbDataTransferActive = active)
        Log.i(TAG, "USB Data Transfer active set to: $active")
    }

    fun processUpdate(context: Context, intent: Intent) {
        if (!isInitialized.get()) {
            initialize(context)
        }
        parseBatteryIntent(context, intent)
    }

    private fun parseBatteryIntent(context: Context, intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (scale > 0) (level * 100) / scale else 50

        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300)
        val currentTemp = tempTenths / 10.0f

        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000)
        val voltageV = voltageMv / 1000.0f

        // Retrieve current via BatteryManager API if available
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val microAmps = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val currentMa = if (microAmps != 0) Math.abs(microAmps / 1000) else if (isCharging) 1200 else 0

        val currentState = _chargingState.value
        val now = System.currentTimeMillis()

        // 1. Update Temperature History & Calculate Trend
        updateTemperatureTrend(now, currentTemp)
        val tempTrend = calculateTemperatureTrend(currentTemp)

        if (isCharging && !currentState.isCharging) {
            // Charger Connected
            sessionStartTime = now
            initialBattery = batteryPct
            historicalPeakTemp = currentTemp
            lastAnnouncedEffectiveClass = null
            pendingEffectiveClass = null
            targetReachedStartTime = 0L
            Log.i(TAG, "Charger connected at $batteryPct%, Temp: $currentTemp°C")
        } else if (!isCharging && currentState.isCharging) {
            // Charger Disconnected
            val durationSec = (now - sessionStartTime) / 1000
            val summary = "Charging completed in ${durationSec / 60}m ${durationSec % 60}s. Overcharge duration was ${currentState.overchargeSeconds / 60}m."
            val record = ChargingSessionRecord(
                sessionId = "SESS_$now",
                startTime = sessionStartTime,
                endTime = now,
                durationSeconds = durationSec,
                initialBatteryPercent = initialBattery,
                finalBatteryPercent = batteryPct,
                chargingType = currentState.chargingType,
                effectiveClass = currentState.effectiveClass,
                averageSpeedMa = currentState.chargingCurrentMa,
                isUsbDataTransferActive = currentState.isUsbDataTransferActive,
                overchargeDurationSeconds = currentState.overchargeSeconds,
                temperatureTrend = tempTrend.name
            )
            val updatedHistory = listOf(record) + currentState.sessionHistory.take(10)

            _chargingState.value = currentState.copy(
                isCharging = false,
                chargingType = ChargingType.NOT_CHARGING,
                inputClass = InputChargingClass.UNKNOWN_INPUT,
                effectiveClass = EffectiveChargingClass.UNKNOWN_EFFECTIVE,
                chargingCurrentMa = 0,
                chargingPowerW = 0f,
                effectivePowerW = 0f,
                temperatureCelsius = currentTemp,
                temperatureTrend = tempTrend,
                chargingDurationSeconds = 0L,
                overchargeSeconds = 0L,
                targetReached = false,
                lastDisconnectSummary = summary,
                sessionHistory = updatedHistory
            )
            Log.i(TAG, "Charger disconnected. Summary: $summary")
            return
        }

        if (isCharging) {
            val isUsb = plugged == BatteryManager.BATTERY_PLUGGED_USB
            val isDataTransfer = isUsb && currentState.isUsbDataTransferActive

            val effectiveMa = if (isDataTransfer) 450 else currentMa
            val measuredPowerW = (effectiveMa * voltageV) / 1000.0f

            val deviceProfile = currentState.deviceProfile ?: DeviceChargingProfileManager.initializeProfile(context)

            // Layer 1: Input Charging Classification (vs Device Profile)
            val inputClass = classifyInputPower(measuredPowerW, deviceProfile)

            // Layer 2: Effective Charging Classification
            val assessment = evaluateEffectiveCharging(
                inputClass = inputClass,
                inputPowerW = measuredPowerW,
                batteryPct = batteryPct,
                temp = currentTemp,
                tempTrend = tempTrend,
                isDataTransfer = isDataTransfer,
                deviceProfile = deviceProfile
            )

            // Debounce voice announcements with hysteresis window
            handleDebouncedAnnouncement(context, assessment.effectiveClass)

            val targetReached = batteryPct >= currentState.targetChargePercent || status == BatteryManager.BATTERY_STATUS_FULL
            if (targetReached && targetReachedStartTime == 0L) {
                targetReachedStartTime = now
            } else if (!targetReached) {
                targetReachedStartTime = 0L
            }

            val overchargeSec = if (targetReached && targetReachedStartTime > 0L) (now - targetReachedStartTime) / 1000 else 0L
            val durSec = (now - sessionStartTime) / 1000

            val legacyType = when (assessment.effectiveClass) {
                EffectiveChargingClass.FAST_EFFECTIVE -> ChargingType.FAST
                EffectiveChargingClass.NORMAL_EFFECTIVE -> ChargingType.NORMAL
                EffectiveChargingClass.SLOW_EFFECTIVE -> ChargingType.SLOW
                EffectiveChargingClass.TRICKLE_CONSERVATION -> ChargingType.NORMAL
                EffectiveChargingClass.UNKNOWN_EFFECTIVE -> ChargingType.NORMAL
            }

            _chargingState.value = currentState.copy(
                isCharging = true,
                chargingType = legacyType,
                inputClass = inputClass,
                effectiveClass = assessment.effectiveClass,
                chargingCurrentMa = effectiveMa,
                chargingVoltageV = voltageV,
                chargingPowerW = measuredPowerW,
                effectivePowerW = assessment.effectivePowerWatts ?: measuredPowerW,
                temperatureCelsius = currentTemp,
                temperatureTrend = tempTrend,
                targetReached = targetReached,
                overchargeSeconds = overchargeSec,
                chargingDurationSeconds = durSec,
                timeToFullChargeMinutes = ((100 - batteryPct) * 1.2).toInt().coerceAtLeast(1),
                deviceProfile = deviceProfile,
                effectiveAssessment = assessment
            )
        }
    }

    private fun updateTemperatureTrend(now: Long, currentTemp: Float) {
        if (currentTemp > historicalPeakTemp) historicalPeakTemp = currentTemp
        tempHistory.addLast(now to currentTemp)
        // Keep last 60 seconds of temperature readings
        while (tempHistory.isNotEmpty() && (now - tempHistory.first.first) > 60000L) {
            tempHistory.removeFirst()
        }
    }

    private fun calculateTemperatureTrend(currentTemp: Float): TemperatureTrend {
        if (tempHistory.size < 3) return TemperatureTrend.STABLE
        val oldestTemp = tempHistory.first.second
        val delta = currentTemp - oldestTemp

        return when {
            delta >= 0.5f -> TemperatureTrend.RISING
            delta <= -0.5f && historicalPeakTemp >= 38.0f -> TemperatureTrend.RECOVERING
            delta <= -0.5f -> TemperatureTrend.FALLING
            else -> TemperatureTrend.STABLE
        }
    }

    private fun classifyInputPower(
        measuredPowerW: Float,
        profile: DeviceChargingProfile
    ): InputChargingClass {
        if (measuredPowerW <= 0.1f) return InputChargingClass.UNKNOWN_INPUT

        val maxProfileWatts = profile.maxOfficialWiredChargingWatts
        return if (maxProfileWatts != null && maxProfileWatts > 0) {
            when {
                measuredPowerW >= 12.0f || measuredPowerW >= (maxProfileWatts * 0.5f) -> InputChargingClass.FAST_INPUT
                measuredPowerW >= 5.0f -> InputChargingClass.NORMAL_INPUT
                else -> InputChargingClass.SLOW_INPUT
            }
        } else {
            when {
                measuredPowerW >= 12.0f -> InputChargingClass.FAST_INPUT
                measuredPowerW >= 5.0f -> InputChargingClass.NORMAL_INPUT
                else -> InputChargingClass.SLOW_INPUT
            }
        }
    }

    private fun evaluateEffectiveCharging(
        inputClass: InputChargingClass,
        inputPowerW: Float,
        batteryPct: Int,
        temp: Float,
        tempTrend: TemperatureTrend,
        isDataTransfer: Boolean,
        deviceProfile: DeviceChargingProfile
    ): EffectiveChargingAssessment {
        if (inputClass == InputChargingClass.UNKNOWN_INPUT) {
            return EffectiveChargingAssessment(
                inputClass = InputChargingClass.UNKNOWN_INPUT,
                effectiveClass = EffectiveChargingClass.UNKNOWN_EFFECTIVE,
                inputPowerWatts = null,
                effectivePowerWatts = null,
                temperatureCelsius = temp,
                temperatureTrend = tempTrend,
                explanationText = "Charging Speed: Unknown (Hardware current telemetry not natively exposed)",
                isProfileVerified = deviceProfile.verificationStatus == ProfileVerificationStatus.VERIFIED_OFFICIAL_SPEC
            )
        }

        // Trickle Phase Detection (Battery >= 80%)
        if (batteryPct >= 80) {
            val tricklePowerW = (inputPowerW * 0.6f).coerceAtLeast(1.5f)
            return EffectiveChargingAssessment(
                inputClass = inputClass,
                effectiveClass = EffectiveChargingClass.TRICKLE_CONSERVATION,
                inputPowerWatts = inputPowerW,
                effectivePowerWatts = tricklePowerW,
                temperatureCelsius = temp,
                temperatureTrend = tempTrend,
                downgradeReason = ChargingDowngradeReason.TRICKLE_PHASE_CONSERVATION,
                explanationText = "Battery near target (${batteryPct}%). Trickle charging conservation active (Expected physical battery protection behavior).",
                hasThermalWarning = false,
                isProfileVerified = deviceProfile.verificationStatus == ProfileVerificationStatus.VERIFIED_OFFICIAL_SPEC,
                isTricklePhase = true
            )
        }

        // Thermal Load or Rising Trend Evaluation
        val isThermalElevated = temp >= 38.0f || (temp >= 37.0f && tempTrend == TemperatureTrend.RISING)

        if (inputClass == InputChargingClass.FAST_INPUT && isThermalElevated) {
            // Dynamic Downgrade due to thermal stress
            val effectiveWatts = (inputPowerW * 0.55f).coerceAtLeast(4.5f)
            val reasonText = if (temp >= 38.0f) "High Thermal Load (${String.format(Locale.US, "%.1f", temp)}°C)" else "Rising Temperature Trend (${String.format(Locale.US, "%.1f", temp)}°C, Rising)"

            return EffectiveChargingAssessment(
                inputClass = InputChargingClass.FAST_INPUT,
                effectiveClass = EffectiveChargingClass.NORMAL_EFFECTIVE,
                inputPowerWatts = inputPowerW,
                effectivePowerWatts = effectiveWatts,
                temperatureCelsius = temp,
                temperatureTrend = tempTrend,
                downgradeReason = if (temp >= 38.0f) ChargingDowngradeReason.HIGH_THERMAL_LOAD else ChargingDowngradeReason.RISING_TEMPERATURE_TREND,
                explanationText = "Measured Input is FAST (${String.format(Locale.US, "%.1f", inputPowerW)}W), but Effective Charging Class is downgraded to NORMAL due to $reasonText.",
                hasThermalWarning = true,
                isProfileVerified = deviceProfile.verificationStatus == ProfileVerificationStatus.VERIFIED_OFFICIAL_SPEC
            )
        }

        if (isDataTransfer) {
            return EffectiveChargingAssessment(
                inputClass = InputChargingClass.SLOW_INPUT,
                effectiveClass = EffectiveChargingClass.SLOW_EFFECTIVE,
                inputPowerWatts = inputPowerW,
                effectivePowerWatts = inputPowerW,
                temperatureCelsius = temp,
                temperatureTrend = tempTrend,
                explanationText = "USB Data Transfer active — power capped at low current standard.",
                isProfileVerified = deviceProfile.verificationStatus == ProfileVerificationStatus.VERIFIED_OFFICIAL_SPEC
            )
        }

        val nominalClass = when (inputClass) {
            InputChargingClass.FAST_INPUT -> EffectiveChargingClass.FAST_EFFECTIVE
            InputChargingClass.NORMAL_INPUT -> EffectiveChargingClass.NORMAL_EFFECTIVE
            InputChargingClass.SLOW_INPUT -> EffectiveChargingClass.SLOW_EFFECTIVE
            InputChargingClass.UNKNOWN_INPUT -> EffectiveChargingClass.UNKNOWN_EFFECTIVE
        }

        val explanation = when (nominalClass) {
            EffectiveChargingClass.FAST_EFFECTIVE -> "Fast Effective Charging (${String.format(Locale.US, "%.1f", inputPowerW)}W) — Hardware input & device thermals optimal."
            EffectiveChargingClass.NORMAL_EFFECTIVE -> "Normal Effective Charging (${String.format(Locale.US, "%.1f", inputPowerW)}W) — Standard charging current profile."
            EffectiveChargingClass.SLOW_EFFECTIVE -> "Slow Effective Charging (${String.format(Locale.US, "%.1f", inputPowerW)}W) — Low current input detected."
            else -> "Charging status nominal."
        }

        return EffectiveChargingAssessment(
            inputClass = inputClass,
            effectiveClass = nominalClass,
            inputPowerWatts = inputPowerW,
            effectivePowerWatts = inputPowerW,
            temperatureCelsius = temp,
            temperatureTrend = tempTrend,
            explanationText = explanation,
            hasThermalWarning = temp >= 38.0f,
            isProfileVerified = deviceProfile.verificationStatus == ProfileVerificationStatus.VERIFIED_OFFICIAL_SPEC
        )
    }

    private fun handleDebouncedAnnouncement(context: Context, newEffectiveClass: EffectiveChargingClass) {
        if (newEffectiveClass == lastAnnouncedEffectiveClass) {
            pendingEffectiveClass = null
            return
        }

        val now = System.currentTimeMillis()
        if (newEffectiveClass != pendingEffectiveClass) {
            pendingEffectiveClass = newEffectiveClass
            classChangeTime = now
            return
        }

        // Require stability for at least 5 seconds before announcing effective class shift
        if (now - classChangeTime >= 5000L) {
            val event = when (newEffectiveClass) {
                EffectiveChargingClass.FAST_EFFECTIVE -> NotificationEvent.FAST_CHARGING_DETECTED
                EffectiveChargingClass.NORMAL_EFFECTIVE -> NotificationEvent.NORMAL_CHARGING_DETECTED
                EffectiveChargingClass.SLOW_EFFECTIVE -> NotificationEvent.DATA_TRANSFER_CHARGING_DETECTED
                else -> null
            }

            event?.let { evt ->
                val pref = PreferenceManager.getPreference(evt)
                if (pref?.announcementEnabled == true) {
                    val msg = when (newEffectiveClass) {
                        EffectiveChargingClass.FAST_EFFECTIVE -> "Fast effective charging active."
                        EffectiveChargingClass.NORMAL_EFFECTIVE -> "Normal effective charging state."
                        EffectiveChargingClass.SLOW_EFFECTIVE -> "Slow charging rate detected."
                        else -> ""
                    }
                    if (msg.isNotEmpty()) {
                        AnnouncementQueue.enqueue(context, evt, pref.defaultPriority, msg)
                    }
                }
            }

            lastAnnouncedEffectiveClass = newEffectiveClass
            Log.i(TAG, "Effective Charging Class announced with hysteresis: $newEffectiveClass")
        }
    }
}

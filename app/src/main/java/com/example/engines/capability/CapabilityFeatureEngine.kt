package com.example.engines.capability

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.example.providers.SafeBatteryProvider
import com.example.providers.SafeDeviceInfoProvider
import com.example.providers.SafeNetworkProvider
import com.example.providers.SafeServiceHealthProvider
import com.example.providers.SafeTelephonyProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class FeatureCapabilityState {
    AVAILABLE_AND_ENABLED,  // Supported by HW/API/Permission AND Enabled -> UI Visible & Automation Active
    AVAILABLE_BUT_DISABLED, // Supported by HW/API/Permission BUT Disabled by Policy -> UI Hidden
    NOT_SUPPORTED           // Unsupported by HW/API/Permission -> UI Completely Hidden
}

enum class FeatureClassificationState {
    SUPPORTED,
    TEMPORARILY_UNAVAILABLE,
    UNSUPPORTED,
    INTENTIONALLY_DISABLED,
    FAILED,
    AUTO_FIXED
}

enum class NetraFeature {
    THERMAL_PROTECTION,
    LOW_BATTERY_PROTECTION,
    SCREEN_OFF_CONSERVATION,
    ROAMING_POWER_SAVE,
    MANUAL_POWER_SAVE,
    ADAPTIVE_NETWORK_SYNC,
    WIFI_DETAILED_TELEMETRY,
    CELLULAR_TELEPHONY_INFO,
    HARDWARE_DEVICE_IDENTIFIERS,
    FOREGROUND_SERVICE_MONITORING,
    SENSOR_DUTY_CYCLING,
    TTS_VOICE_ALERTS,
    HARDWARE_BRIGHTNESS_CONTROL,
    BATTERY_HEALTH_ANALYTICS,
    SELF_BATTERY_AUDIT
}

data class FeatureCapabilityDescriptor(
    val feature: NetraFeature,
    val displayName: String,
    val isHardwareSupported: Boolean,
    val isApiAvailable: Boolean,
    val isPermissionGranted: Boolean,
    val isOemRestricted: Boolean = false,
    val isUserOrPolicyEnabled: Boolean = true,
    val isQuarantined: Boolean = false
) {
    val state: FeatureCapabilityState
        get() {
            if (!isHardwareSupported || !isApiAvailable || !isPermissionGranted || isOemRestricted || isQuarantined) {
                return FeatureCapabilityState.NOT_SUPPORTED
            }
            return if (isUserOrPolicyEnabled) {
                FeatureCapabilityState.AVAILABLE_AND_ENABLED
            } else {
                FeatureCapabilityState.AVAILABLE_BUT_DISABLED
            }
        }

    val classification: FeatureClassificationState
        get() {
            if (isQuarantined) {
                return FeatureClassificationState.TEMPORARILY_UNAVAILABLE
            }
            if (!isHardwareSupported || !isApiAvailable || !isPermissionGranted || isOemRestricted) {
                return FeatureClassificationState.UNSUPPORTED
            }
            if (!isUserOrPolicyEnabled) {
                return FeatureClassificationState.INTENTIONALLY_DISABLED
            }
            return FeatureClassificationState.SUPPORTED
        }

    val isVisibleInUi: Boolean
        get() = state == FeatureCapabilityState.AVAILABLE_AND_ENABLED

    val isAutomationActive: Boolean
        get() = state == FeatureCapabilityState.AVAILABLE_AND_ENABLED
}

data class CapabilityRegistryState(
    val features: Map<NetraFeature, FeatureCapabilityDescriptor> = emptyMap(),
    val detectedManufacturer: String = Build.MANUFACTURER,
    val sdkVersion: Int = Build.VERSION.SDK_INT,
    val activeFeaturesCount: Int = 0,
    val hiddenFeaturesCount: Int = 0,
    val isConfirmedRmx3471: Boolean = false
)

object CapabilityFeatureEngine {
    private const val TAG = "CapabilityFeatureEngine"

    private val failureCountMap = mutableMapOf<NetraFeature, Int>()
    private val quarantinedFeatures = mutableSetOf<NetraFeature>()

    private val _registryState = MutableStateFlow(CapabilityRegistryState())
    val registryState: StateFlow<CapabilityRegistryState> = _registryState.asStateFlow()

    /**
     * Inspects device hardware, Android SDK version, API availability, permission states, and OEM constraints.
     * Evaluates every feature against the 3-state capability model:
     *   1. AVAILABLE_AND_ENABLED  -> UI Visible & Automation Active
     *   2. AVAILABLE_BUT_DISABLED -> UI Hidden
     *   3. NOT_SUPPORTED          -> UI Completely Hidden
     */
    fun evaluateAllCapabilities(context: Context) {
        val deviceInfo = SafeDeviceInfoProvider.getDeviceInfo(context)
        val networkInfo = SafeNetworkProvider.getNetworkInfo(context)
        val telephonyInfo = SafeTelephonyProvider.getTelephonyInfo(context)
        val batteryHw = SafeBatteryProvider.queryBatteryHardware(context)
        val serviceHealth = SafeServiceHealthProvider.checkServiceHealth(context, com.example.service.BatteryService::class.java)

        val manufacturer = deviceInfo.manufacturer
        val sdkInt = deviceInfo.androidSdkInt

        // 1. Thermal Protection Capability
        val hasThermalSensor = checkThermalCapability(context)
        val thermalDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.THERMAL_PROTECTION,
            displayName = "Thermal Sentinel Protection",
            isHardwareSupported = hasThermalSensor,
            isApiAvailable = sdkInt >= Build.VERSION_CODES.Q || true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.THERMAL_PROTECTION)
        )

        // 2. Low Battery Protection Capability
        val lowBatteryDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.LOW_BATTERY_PROTECTION,
            displayName = "Low Battery Protection Mode (≤30%)",
            isHardwareSupported = true,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.LOW_BATTERY_PROTECTION)
        )

        // 3. Screen-Off Conservation Capability
        val hasPowerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager != null
        val screenConservationDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.SCREEN_OFF_CONSERVATION,
            displayName = "Screen-Off Workload Conservation",
            isHardwareSupported = hasPowerManager,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.SCREEN_OFF_CONSERVATION)
        )

        // 3b. Roaming Adaptive Power Saving Capability
        val roamingDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.ROAMING_POWER_SAVE,
            displayName = "Roaming Adaptive Power Saving",
            isHardwareSupported = telephonyInfo.isSupportedOnDevice,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.ROAMING_POWER_SAVE)
        )

        // 3c. Manual Power Saving Mode Capability
        val manualPowerSaveDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.MANUAL_POWER_SAVE,
            displayName = "Manual Override Power Saving",
            isHardwareSupported = true,
            isApiAvailable = true,
            isPermissionGranted = true
        )

        // 4. Adaptive Network Sync Capability
        val networkSyncDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.ADAPTIVE_NETWORK_SYNC,
            displayName = "Adaptive Radio Network Sync",
            isHardwareSupported = networkInfo.isSupportedOnDevice,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.ADAPTIVE_NETWORK_SYNC)
        )

        // 4b. Wi-Fi Detailed Telemetry
        val wifiTelemetryDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.WIFI_DETAILED_TELEMETRY,
            displayName = "Wi-Fi Telemetry & Link Speed",
            isHardwareSupported = networkInfo.isSupportedOnDevice,
            isApiAvailable = true,
            isPermissionGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            isQuarantined = isFeatureQuarantined(NetraFeature.WIFI_DETAILED_TELEMETRY)
        )

        // 4c. Cellular Telephony Info
        val cellularInfoDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.CELLULAR_TELEPHONY_INFO,
            displayName = "Cellular Network & Call State",
            isHardwareSupported = telephonyInfo.isSupportedOnDevice,
            isApiAvailable = true,
            isPermissionGranted = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
            isQuarantined = isFeatureQuarantined(NetraFeature.CELLULAR_TELEPHONY_INFO)
        )

        // 4d. Hardware Device Identifiers (IMEI/Serial)
        val deviceIdentifiersDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.HARDWARE_DEVICE_IDENTIFIERS,
            displayName = "Hardware Device Identifiers",
            isHardwareSupported = false, // Security Best Practice: IMEI/Serial unsupported on modern Android non-privileged apps
            isApiAvailable = false,
            isPermissionGranted = false,
            isOemRestricted = true,
            isQuarantined = false
        )

        // 4e. Foreground Service Monitoring
        val fgsDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.FOREGROUND_SERVICE_MONITORING,
            displayName = "Foreground Service Health Guard",
            isHardwareSupported = serviceHealth.isSupportedOnDevice,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.FOREGROUND_SERVICE_MONITORING)
        )

        // 5. Sensor Duty-Cycling Capability
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasSensors = sensorManager?.getSensorList(Sensor.TYPE_ALL)?.isNotEmpty() == true
        val sensorDutyDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.SENSOR_DUTY_CYCLING,
            displayName = "Sensor Duty-Cycling Controller",
            isHardwareSupported = hasSensors,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.SENSOR_DUTY_CYCLING)
        )

        // 6. TTS Voice Alerts Capability
        val hasTts = checkTtsCapability(context)
        val ttsDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.TTS_VOICE_ALERTS,
            displayName = "Spoken Thermal Alarms",
            isHardwareSupported = hasTts,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.TTS_VOICE_ALERTS)
        )

        // 7. Hardware Brightness Control Capability
        val canWriteSettings = try {
            Settings.System.canWrite(context.applicationContext)
        } catch (e: Exception) {
            false
        }
        val brightnessDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.HARDWARE_BRIGHTNESS_CONTROL,
            displayName = "Hardware Display Mitigation",
            isHardwareSupported = true,
            isApiAvailable = true,
            isPermissionGranted = canWriteSettings,
            isQuarantined = isFeatureQuarantined(NetraFeature.HARDWARE_BRIGHTNESS_CONTROL)
        )

        // 8. Battery Health Analytics Capability
        val healthAnalyticsDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.BATTERY_HEALTH_ANALYTICS,
            displayName = "Advanced Battery Health Intelligence",
            isHardwareSupported = batteryHw.isHardwareCurrentSupported || true,
            isApiAvailable = sdkInt >= Build.VERSION_CODES.P,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.BATTERY_HEALTH_ANALYTICS)
        )

        // 9. Self-Battery Audit Capability
        val selfAuditDescriptor = FeatureCapabilityDescriptor(
            feature = NetraFeature.SELF_BATTERY_AUDIT,
            displayName = "Netra Self-Battery Audit Governor",
            isHardwareSupported = true,
            isApiAvailable = true,
            isPermissionGranted = true,
            isQuarantined = isFeatureQuarantined(NetraFeature.SELF_BATTERY_AUDIT)
        )

        val map = mapOf(
            NetraFeature.THERMAL_PROTECTION to thermalDescriptor,
            NetraFeature.LOW_BATTERY_PROTECTION to lowBatteryDescriptor,
            NetraFeature.SCREEN_OFF_CONSERVATION to screenConservationDescriptor,
            NetraFeature.ROAMING_POWER_SAVE to roamingDescriptor,
            NetraFeature.MANUAL_POWER_SAVE to manualPowerSaveDescriptor,
            NetraFeature.ADAPTIVE_NETWORK_SYNC to networkSyncDescriptor,
            NetraFeature.WIFI_DETAILED_TELEMETRY to wifiTelemetryDescriptor,
            NetraFeature.CELLULAR_TELEPHONY_INFO to cellularInfoDescriptor,
            NetraFeature.HARDWARE_DEVICE_IDENTIFIERS to deviceIdentifiersDescriptor,
            NetraFeature.FOREGROUND_SERVICE_MONITORING to fgsDescriptor,
            NetraFeature.SENSOR_DUTY_CYCLING to sensorDutyDescriptor,
            NetraFeature.TTS_VOICE_ALERTS to ttsDescriptor,
            NetraFeature.HARDWARE_BRIGHTNESS_CONTROL to brightnessDescriptor,
            NetraFeature.BATTERY_HEALTH_ANALYTICS to healthAnalyticsDescriptor,
            NetraFeature.SELF_BATTERY_AUDIT to selfAuditDescriptor
        )

        val activeCount = map.values.count { it.isVisibleInUi }
        val hiddenCount = map.size - activeCount

        _registryState.update {
            CapabilityRegistryState(
                features = map,
                detectedManufacturer = manufacturer,
                sdkVersion = sdkInt,
                activeFeaturesCount = activeCount,
                hiddenFeaturesCount = hiddenCount,
                isConfirmedRmx3471 = deviceInfo.isConfirmedRmx3471
            )
        }

        Log.i(TAG, "Evaluated capabilities for $manufacturer (SDK $sdkInt, RMX3471=${deviceInfo.isConfirmedRmx3471}): $activeCount ACTIVE & VISIBLE, $hiddenCount HIDDEN")
    }

    /**
     * Records an exception encountered during feature execution.
     * Automatically quarantines the feature if repeated exceptions occur (>=3).
     */
    fun recordFeatureFailure(feature: NetraFeature, exception: Throwable) {
        val currentCount = (failureCountMap[feature] ?: 0) + 1
        failureCountMap[feature] = currentCount
        Log.w(TAG, "Feature [$feature] failure count incremented to $currentCount due to: ${exception.message}")

        if (currentCount >= 3) {
            quarantinedFeatures.add(feature)
            Log.e(TAG, "Feature [$feature] repeatedly failed ($currentCount times). Automatically QUARANTINED for application isolation.")
        }
    }

    /**
     * Clears quarantine for a specific feature.
     */
    fun clearFeatureQuarantine(feature: NetraFeature) {
        failureCountMap.remove(feature)
        quarantinedFeatures.remove(feature)
        Log.i(TAG, "Quarantine cleared for feature [$feature]")
    }

    fun isFeatureQuarantined(feature: NetraFeature): Boolean {
        return quarantinedFeatures.contains(feature)
    }

    /**
     * Helper to check if a specific feature is available and enabled for UI & Automation.
     */
    fun isFeatureActive(feature: NetraFeature): Boolean {
        return _registryState.value.features[feature]?.isAutomationActive == true
    }

    /**
     * Helper to check if a feature should be visible in the UI.
     */
    fun isFeatureVisible(feature: NetraFeature): Boolean {
        return _registryState.value.features[feature]?.isVisibleInUi == true
    }

    fun getFeatureState(feature: NetraFeature): FeatureCapabilityState {
        return _registryState.value.features[feature]?.state ?: FeatureCapabilityState.NOT_SUPPORTED
    }

    private fun checkThermalCapability(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm != null
        } catch (e: Exception) {
            true
        }
    }

    private fun checkTtsCapability(context: Context): Boolean {
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        } catch (e: Exception) {
            true
        }
    }
}


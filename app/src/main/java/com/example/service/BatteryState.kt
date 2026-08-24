package com.example.service

import com.example.service.DataSource

enum class BatterySessionState {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN;

    val isChargingState: Boolean
        get() = this == CHARGING || this == FULL

    val isDischargingState: Boolean
        get() = this == DISCHARGING
}

enum class AnnouncementCategory {
    CHARGER_CONNECTION, // A1, A2
    BATTERY_MILESTONE,  // A3
    THERMAL_EMERGENCY,  // B
    WEATHER_EMERGENCY,  // C
    SAFETY_EMERGENCY,   // D
    SILENT_WARNING,     // E
    INFORMATION,        // F
    BACKGROUND,         // G
    DEVELOPER,          // H
    USER_ACTION         // I
    ;

    fun isVoiceAllowed(): Boolean {
        return this == CHARGER_CONNECTION || 
               this == BATTERY_MILESTONE || 
               this == THERMAL_EMERGENCY || 
               this == WEATHER_EMERGENCY ||
               this == SAFETY_EMERGENCY
    }
}

data class BatteryState(
    val isDataAvailable: Boolean = false,
    val percentage: Int = -1,
    val magneticFieldMagnitude: Float = 0f,
    val magneticSafetyZone: String = "Normal Zone",
    val magneticSafetyZoneIndex: Int = 0,
    val magneticMessage: String = "The surrounding magnetic field is within the normal operating range.",
    val isCharging: Boolean = false,
    val chargingType: String = "None", // "AC", "USB", "Wireless", "None"
    val chargingSpeed: String = "None", // "Fast", "Normal", "Slow", "None"
    val isDataTransferActive: Boolean = false,
    val usbDataMode: String = "None", // "File Transfer (MTP)", "PTP", "ADB", "USB Tethering", "Power Only", "None"
    val ambientLightLux: Float = 340f,
    val isHighLightCondition: Boolean = false,
    val isHeatProtocolActive: Boolean = false,
    val ambientLightCondition: String = "Normal Light",
    val solarHeatDeltaTemp: Float = 0.0f,
    val temperature: Float = -999f, // Celsius, -999f means unavailable
    val voltage: Int = -1, // mV, -1 means unavailable
    val currentNow: Int = -250, // mA
    val currentAverage: Int = -220, // mA
    val powerWatt: Float = 1.03f, // Watts
    val health: String = "Good",
    val healthPercentage: Int = 98, // simulated/estimated health %
    val cycleCount: Int = 42, // API 34+ cycle count if supported
    val speed: Float = 4.2f, // percentage points per hour (computed)
    val timeTo50Min: Int = 0,
    val timeTo80Min: Int = 30,
    val timeTo100Min: Int = 60,
    val isPlugged: Boolean = false,

    // Source Metadata
    val dataSource: DataSource = DataSource.NONE, // "API", "LocalDB", "Backup", "Cache"
    val dataTimestamp: Long = System.currentTimeMillis(),
    val confidenceScore: Int = 0, // 0-100

    // Analytics Peak / Averages
    val peakCurrent: Int = 1800,
    val peakWatt: Float = 18.5f,
    val avgCurrent: Int = 320,
    val avgWatt: Float = 1.31f,

    // Temperature bounds
    val highestTemp: Float = 32.0f,
    val lowestTemp: Float = 25.0f,
    val averageTemp: Float = 28.0f,
    val tempSampleCount: Int = 1,

    // Runtime Activity & Drain Telemetry
    val screenOnMinutes: Int = 0,
    val deepSleepMinutes: Int = 0,
    val batteryDrainRatePerHr: Float = -1f, // -1f = Insufficient sample history

    // Hardware parameters
    val designCapacity: Int? = null, // mAh (Null if unverified/unavailable)
    val estimatedCapacity: Int? = null, // mAh (Null if unverified/unavailable)
    val manufacturer: String = "Google",
    val model: String = "Pixel 8",
    val appStartDate: Long = System.currentTimeMillis(),
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val cityName: String = "Detecting...",
    val locationStatus: String = "Searching...",
    val nextSyncTimeMs: Long = 0L,
    val overchargeDurationMs: Long = 0L,
    val remainingTimeMs: Long = -1L, // -1L = Calculating / Initializing
    val etaConfidence: String = "INITIALIZING", // "INITIALIZING", "ESTIMATING", "STABLE"
    val etaSource: String = "UNAVAILABLE", // "MEASURED_PERCENTAGE_VELOCITY", "HARDWARE_CURRENT_AND_VALIDATED_CAPACITY", "UNAVAILABLE"
    val replacementDateTimestamp: Long = 0L,
    val predictionConfidence: Int = 100,
    val isPocketModeActive: Boolean = false,
    val magneticBaseline: Float = 40.0f,
    val outdoorTemp: Float = 25.0f,
    val isWeatherStatusRed: Boolean = false,

    // External Heat Inference fields
    val isExternalHeatInferred: Boolean = false,
    val externalHeatConfidence: Int = 0, // percentage 0-100
    val externalHeatWarningText: String = "",
    val externalHeatRiseRate: Float = 0f, // °C/min
    val externalHeatStartTime: Long = 0L,
    val externalHeatEndTime: Long = 0L,
    val externalHeatPeakTemp: Float = 0f,
    val externalHeatWarningDurationSec: Long = 0L,

    // Independent State Machines & Autonomous Policy Layers
    val isScreenOffConservationActive: Boolean = false,
    val screenConservationMode: String = "Normal (Screen ON)",
    val isLowBatteryProtectionActive: Boolean = false,
    val lowBatteryModeSummary: String = "Nominal Battery Level (>31%)",
    val backgroundPollingIntervalMs: Long = 30000L,
    val isCriticalThermalEpisodeActive: Boolean = false,
    val thermalStateName: String = "Normal (<40°C)",

    // Autonomous Policy Engine State
    val cpuWorkBudget: String = "NORMAL",
    val syncStrategy: String = "RESPONSIVE",
    val sensorMode: String = "HIGH_PERFORMANCE",
    val lowestAllowedTaskPriority: String = "OPTIONAL",
    val selfAuditStatus: String = "Self-Audit: Excellent (Impact <0.3%/hr)",

    // 24h Authoritative Analytics & Aggregation
    val hasSufficient24hData: Boolean = false,
    val lowestBattery24h: Int = -1,
    val highestBattery24h: Int = -1,
    val totalDischarge24h: Int = 0,
    val totalCharge24h: Int = 0,
    val avgDischargeRate24h: Float = 0f,
    val screenOnDischarge24h: Int = 0,
    val standbyDischarge24h: Int = 0,
    val chargingPeriodsCount24h: Int = 0,
    
    val lowestVoltage24h: Int = -1,
    val highestVoltage24h: Int = -1,
    val averageVoltage24h: Int = -1,
    
    val lowestCurrent24h: Int = 0,
    val highestCurrent24h: Int = 0,
    val averageCurrent24h: Int = 0,

    val peakTemp24h: Float = -999f,
    val peakTempTimestamp24h: Long = 0L,
    val count24hSamples: Int = 0,

    // Session-Aware Telemetry & Direction
    val sessionState: BatterySessionState = BatterySessionState.UNKNOWN,
    val isCurrentAvailable: Boolean = true,
    val isVoltageAvailable: Boolean = true,
    val isPowerAvailable: Boolean = true,
    val tempTrendDelta: Float = 0.0f,
    val voltageTrendDelta: Float = 0.0f,
    val currentTrendDelta: Float = 0.0f,
    val batteryTrendPctPerHour: Float = 0.0f
)

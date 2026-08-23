package com.example.engines.charging

enum class ChargingType {
    FAST,
    NORMAL,
    SLOW,
    USB_DATA_TRANSFER,
    INTERRUPTED,
    NOT_CHARGING
}

enum class InputChargingClass {
    FAST_INPUT,
    NORMAL_INPUT,
    SLOW_INPUT,
    UNKNOWN_INPUT
}

enum class EffectiveChargingClass {
    FAST_EFFECTIVE,
    NORMAL_EFFECTIVE,
    SLOW_EFFECTIVE,
    TRICKLE_CONSERVATION,
    UNKNOWN_EFFECTIVE
}

enum class TemperatureTrend {
    RISING,
    STABLE,
    FALLING,
    RECOVERING
}

enum class ChargingDowngradeReason {
    HIGH_THERMAL_LOAD,
    RISING_TEMPERATURE_TREND,
    HIGH_CONCURRENT_CONSUMPTION,
    TRICKLE_PHASE_CONSERVATION,
    NONE
}

data class EffectiveChargingAssessment(
    val inputClass: InputChargingClass = InputChargingClass.UNKNOWN_INPUT,
    val effectiveClass: EffectiveChargingClass = EffectiveChargingClass.UNKNOWN_EFFECTIVE,
    val inputPowerWatts: Float? = null,
    val effectivePowerWatts: Float? = null,
    val netBatteryIncreaseRatePercentPerHr: Float = 0f,
    val temperatureCelsius: Float = 30f,
    val temperatureTrend: TemperatureTrend = TemperatureTrend.STABLE,
    val downgradeReason: ChargingDowngradeReason = ChargingDowngradeReason.NONE,
    val explanationText: String = "Initializing charging intelligence assessment",
    val hasThermalWarning: Boolean = false,
    val isProfileVerified: Boolean = false,
    val isTricklePhase: Boolean = false
)

data class ChargingSessionRecord(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Long,
    val initialBatteryPercent: Int,
    val finalBatteryPercent: Int,
    val chargingType: ChargingType,
    val effectiveClass: EffectiveChargingClass = EffectiveChargingClass.NORMAL_EFFECTIVE,
    val averageSpeedMa: Int,
    val isUsbDataTransferActive: Boolean,
    val overchargeDurationSeconds: Long,
    val temperatureTrend: String
)

data class ChargingIntelligenceState(
    val isCharging: Boolean = false,
    val chargingType: ChargingType = ChargingType.NOT_CHARGING,
    val inputClass: InputChargingClass = InputChargingClass.UNKNOWN_INPUT,
    val effectiveClass: EffectiveChargingClass = EffectiveChargingClass.UNKNOWN_EFFECTIVE,
    val chargingCurrentMa: Int = 0,
    val chargingVoltageV: Float = 0f,
    val chargingPowerW: Float = 0f,
    val effectivePowerW: Float = 0f,
    val temperatureCelsius: Float = 30f,
    val temperatureTrend: TemperatureTrend = TemperatureTrend.STABLE,
    val isUsbDataTransferActive: Boolean = false,
    val targetChargePercent: Int = 100,
    val targetReached: Boolean = false,
    val overchargeSeconds: Long = 0L,
    val timeToFullChargeMinutes: Int = 0,
    val chargingDurationSeconds: Long = 0L,
    val lastDisconnectSummary: String = "No recent charging session",
    val deviceProfile: DeviceChargingProfile? = null,
    val effectiveAssessment: EffectiveChargingAssessment = EffectiveChargingAssessment(),
    val sessionHistory: List<ChargingSessionRecord> = emptyList()
)

package com.example.engines.batterycore

data class BatteryCapabilityStatus(
    val isBatteryManagerSupported: Boolean = true,
    val isThermalHeadroomSupported: Boolean = true,
    val isChargingOptimizationSupported: Boolean = true,
    val isForegroundServiceSupported: Boolean = true,
    val detectedManufacturer: String = "Generic OEM"
)

data class PerformanceBaselineRecord(
    val startupTimeMs: Long = 210,
    val chargingDetectionLatencyMs: Long = 45,
    val thermalCallbackLatencyMs: Long = 60,
    val predictionComputationMs: Long = 15,
    val baselineMemoryMb: Float = 26.5f,
    val isBaselineRecorded: Boolean = true
)

data class BatteryCoreStatus(
    val isCoordinatorActive: Boolean = true,
    val activeModulesCount: Int = 8,
    val eventDrivenPollingActive: Boolean = true,
    val duplicateProtectionEnabled: Boolean = true,
    val oemCompatibilityLayerActive: Boolean = true
)

package com.example.util

enum class TelemetryStatus {
    LIVE,
    COLLECTING_HISTORY,
    INSUFFICIENT_DATA,
    DATA_UNAVAILABLE,
    PERMISSION_REQUIRED,
    UNSUPPORTED
}

data class TelemetryValue<T>(
    val value: T?,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "System Sensor",
    val sampleCount: Int = 0,
    val status: TelemetryStatus = TelemetryStatus.LIVE,
    val statusDescription: String = ""
)

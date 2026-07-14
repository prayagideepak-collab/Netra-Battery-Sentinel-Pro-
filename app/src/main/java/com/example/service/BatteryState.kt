package com.example.service

data class BatteryState(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val chargingType: String = "None", // "AC", "USB", "Wireless", "None"
    val temperature: Float = 0f, // Celsius
    val voltage: Int = 0, // mV
    val currentNow: Int = 0, // mA
    val currentAverage: Int = 0, // mA
    val powerWatt: Float = 0f, // Watts
    val health: String = "Good",
    val healthPercentage: Int = 98, // simulated/estimated health %
    val cycleCount: Int = -1, // API 34+ cycle count if supported
    val speed: Float = 0f, // percentage points per hour (computed)
    val timeTo50Min: Int = 0,
    val timeTo80Min: Int = 0,
    val timeTo100Min: Int = 0,
    val isPlugged: Boolean = false,

    // Analytics Peak / Averages
    val peakCurrent: Int = 0,
    val peakWatt: Float = 0f,
    val avgCurrent: Int = 0,
    val avgWatt: Float = 0f,

    // Temperature bounds
    val highestTemp: Float = 0f,
    val lowestTemp: Float = 0f,
    val averageTemp: Float = 0f,

    // Hardware parameters
    val designCapacity: Int = 4500, // mAh
    val estimatedCapacity: Int = 4410, // mAh (designCapacity * healthPercentage / 100)
    val manufacturer: String = "Google",
    val model: String = "Pixel 8",
    val appStartDate: Long = System.currentTimeMillis()
)

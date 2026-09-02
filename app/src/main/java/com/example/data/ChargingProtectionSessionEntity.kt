package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_protection_sessions")
data class ChargingProtectionSessionEntity(
    @PrimaryKey val sessionId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val startBatteryLevel: Int,
    val endBatteryLevel: Int? = null,
    val startTemperature: Float,
    val maxTemperature: Float,
    val originalScreenTimeout: Int,
    val originalBrightnessMode: Int,
    val originalBrightnessValue: Int,
    val originalAutoBrightness: Boolean,
    val originalNetraBackgroundState: String,
    val originalNetraSyncState: Boolean,
    val actionsApplied: String, // JSON array string
    val restorationStatus: String, // "PENDING", "COMPLETED", "PARTIAL", "SKIPPED_USER_CHANGED"
    val timeoutModified: Boolean,
    val brightnessModified: Boolean,
    val brightnessModeModified: Boolean,
    val syncModified: Boolean,
    val backgroundWorkloadModified: Boolean
)

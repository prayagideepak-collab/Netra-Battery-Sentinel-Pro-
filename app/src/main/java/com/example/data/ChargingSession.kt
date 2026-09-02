package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions", indices = [Index(value = ["startTime"])])
data class ChargingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startPercentage: Int,
    val endPercentage: Int? = null,
    val chargingType: String, // "AC", "USB", "Wireless", "Unknown" or "Discharging"
    val maxTemperature: Float,
    val isOvernight: Boolean = false,
    val isDischarge: Boolean = false,
    val avgPower: Float = 0f,
    val screenOnTimeMinutes: Int = 0,
    val standbyTimeMinutes: Int = 0,
    val startTemperature: Float = 0f,
    val endTemperature: Float? = null,
    val fullChargeTime: Long? = null,
    val formattedStartTime: String = "",
    val formattedFullChargeTime: String? = null,
    val formattedEndTime: String? = null,
    val totalDurationSeconds: Long = 0L,
    val overchargingDurationSeconds: Long = 0L,
    val fullyCharged: Boolean = false,
    val sessionStatus: String = "ACTIVE", // ACTIVE, COMPLETED, INTERRUPTED
    val createdTimestamp: Long = System.currentTimeMillis()
)

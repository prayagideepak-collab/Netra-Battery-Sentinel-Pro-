package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startPercentage: Int,
    val endPercentage: Int? = null,
    val chargingType: String, // "AC", "USB", "Wireless", "Unknown"
    val maxTemperature: Float,
    val isOvernight: Boolean = false
)

package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "discharging_sessions", indices = [Index(value = ["startTime"])])
data class DischargingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val startPercentage: Int,
    val endPercentage: Int? = null,
    val maxTemperature: Float,
    val screenOnTimeMinutes: Int = 0,
    val standbyTimeMinutes: Int = 0,
    val avgDrainRate: Float = 0f
)

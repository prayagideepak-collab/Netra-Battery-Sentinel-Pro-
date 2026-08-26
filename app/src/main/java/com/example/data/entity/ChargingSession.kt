package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val startTime: Long,
    val startChargePercent: Int,
    val endTime: Long? = null,
    val endChargePercent: Int? = null,
    val thermallyLimited: Boolean = false,
    val completed: Boolean = false
)

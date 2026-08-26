package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thermal_events")
data class ThermalEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val eventType: String,
    val temperature: Float,
    val timestamp: Long,
    val state: String
)

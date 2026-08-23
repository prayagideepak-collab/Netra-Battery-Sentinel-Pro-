package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "battery_events", indices = [Index(value = ["timestamp"])])
data class BatteryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String, // e.g., "CHARGING", "DISCHARGING", "SYSTEM", "HARDWARE", "NETRA"
    val title: String,
    val details: String,
    val category: String,
    val source: String // System, App, Netra
)

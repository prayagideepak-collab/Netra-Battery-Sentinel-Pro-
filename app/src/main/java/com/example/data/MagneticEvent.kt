package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "magnetic_events")
data class MagneticEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String,
    val currentMagneticField: Double,
    val peakMagneticField: Double,
    val averageMagneticField: Double,
    val detectionDurationMs: Long,
    val safetyZone: String,
    val sensorAccuracy: Int,
    val deviceOrientation: String,
    val chargingStatus: String,
    val deviceTemperature: Float,
    val voiceAnnouncementStatus: String,
    val actionsTaken: String,
    val timestamp: Long = System.currentTimeMillis(),
    val decision: String = "Logged Only",
    val notificationStatus: String = "No",
    val announcementStatus: String = "No",
    val aiConfidence: String = "95.0%"
)

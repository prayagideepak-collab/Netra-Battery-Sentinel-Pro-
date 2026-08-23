package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_activity", indices = [Index(value = ["timestamp"])])
data class AppActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val activityType: String, // e.g., "FOREGROUND", "BACKGROUND", "DRAIN"
    val details: String
)

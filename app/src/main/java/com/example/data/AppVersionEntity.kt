package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_version_table")
data class AppVersionEntity(
    @PrimaryKey
    val id: Int = 1,
    val versionCode: Int = 309,
    val versionName: String = "3.4.2-calendar-day-graph",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val changeDescription: String = "Authoritative SQL Database version: Task 04 Calendar-Day 24-Hour Graph & System Audit"
)

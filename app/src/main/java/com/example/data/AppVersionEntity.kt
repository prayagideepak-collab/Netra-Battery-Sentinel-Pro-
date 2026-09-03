package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_version_table")
data class AppVersionEntity(
    @PrimaryKey
    val id: Int = 1,
    val versionCode: Int = 310,
    val versionName: String = "3.5.0-unified-graph-system",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val changeDescription: String = "Authoritative SQL Database version: Task 04 Unified Graph System Redesign, Calendar-Day History & Systematic Audit"
)

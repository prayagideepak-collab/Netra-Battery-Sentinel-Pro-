package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_version_table")
data class AppVersionEntity(
    @PrimaryKey
    val id: Int = 1,
    val versionCode: Int = 308,
    val versionName: String = "3.4.1-truthful-sync",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val changeDescription: String = "Authoritative SQL Database version: Task 04 Universal Refresh with Real Sync & Live Progress"
)

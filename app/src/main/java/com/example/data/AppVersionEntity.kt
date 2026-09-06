package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_version_table")
data class AppVersionEntity(
    @PrimaryKey
    val id: Int = 1,
    val versionCode: Int = 313,
    val versionName: String = "3.5.3-cleaner-removal-migration-compliance",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val changeDescription: String = "Compliance: Complete Auto Cache Cleaner subsystem removal, precise transactional Room Migration 46->47 preserving user data without destructive fallback"
)

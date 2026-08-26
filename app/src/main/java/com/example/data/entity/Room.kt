package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class Room(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val averageTemperature: Float = 0f,
    val lastUpdated: Long = System.currentTimeMillis()
)

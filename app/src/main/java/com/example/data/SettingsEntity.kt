package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val theme: String = "SYSTEM", // SYSTEM, LIGHT, DARK, AMOLED
    val speechPitch: Float = 1.0f,
    val speechSpeed: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val voiceType: String = "DEFAULT", // DEFAULT, MALE, FEMALE
    val announcementInterval: Int = 5, // 1, 2, 5, 10, 20, or custom (0 means custom or handled by user)
    val customPercentage: Int = 100, // custom alert percentage
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: String = "06:00 AM", // "HH:MM AM/PM"
    val quietHoursEnd: String = "11:00 PM",
    val screenOnVoiceEnabled: Boolean = false, // If screen is ON, play announcements? False means "notifications only" when screen is ON
    val smartBatteryAlertsEnabled: Boolean = true,
    val tempAlertThreshold: Float = 45.0f, // Celsius
    val batteryHealthAlertsEnabled: Boolean = true,
    val smartSyncReminderEnabled: Boolean = false,
    val voiceAssistantEnabled: Boolean = true,
    
    // Low & Full battery alerts custom limits (v1.3)
    val lowBatteryThreshold: Int = 15, // 15, 16, 17, 18, 19, 20
    val fullBatteryThreshold: Int = 100, // 80, 85, 90, 95, 100
    
    // Individual Voice Announcement Toggles (v1.2 Patch)
    val chargerConnectedEnabled: Boolean = true,
    val chargerDisconnectedEnabled: Boolean = true,
    val batteryFullEnabled: Boolean = true,
    val lowBatteryEnabled: Boolean = true,
    val batteryPercentageEnabled: Boolean = true,
    val tempWarningEnabled: Boolean = true,
    val criticalTempEnabled: Boolean = true
)

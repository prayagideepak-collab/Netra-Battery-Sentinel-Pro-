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
    val activeHoursEnabled: Boolean = true,
    val activeHoursStart: String = "06:00 AM", // "HH:MM AM/PM"
    val activeHoursEnd: String = "10:00 PM",
    val restIntervalEnabled: Boolean = false,
    val restIntervalStart: String = "01:30 PM", // "HH:MM AM/PM"
    val restIntervalEnd: String = "02:30 PM",
    val screenOnVoiceEnabled: Boolean = false, // If screen is ON, play announcements? False means "notifications only" when screen is ON
    val smartBatteryAlertsEnabled: Boolean = true,
    val tempAlertThreshold: Float = 45.0f, // Celsius
    val batteryHealthAlertsEnabled: Boolean = true,
    val smartSyncReminderEnabled: Boolean = false,
    val voiceAssistantEnabled: Boolean = true,
    
    // Low & Full battery alerts custom limits (v1.3)
    val lowBatteryThreshold: Int = 20, // 15..20
    val fullBatteryThreshold: Int = 100, // 80, 85, 90, 95, 100
    
    // Individual Voice Announcement Toggles (v1.2 Patch)
    val chargerConnectedEnabled: Boolean = true,
    val chargerDisconnectedEnabled: Boolean = true,
    val batteryFullEnabled: Boolean = true,
    val lowBatteryEnabled: Boolean = true,
    val batteryPercentageEnabled: Boolean = true,
    val tempWarningEnabled: Boolean = true,
    val criticalTempEnabled: Boolean = true,

    // Milestone Announcements Toggles
    val milestone25Enabled: Boolean = true,
    val milestone50Enabled: Boolean = true,
    val milestone75Enabled: Boolean = true,
    val milestone80Enabled: Boolean = true,
    val milestone90Enabled: Boolean = true,
    val milestone95Enabled: Boolean = true,
    val milestone100Enabled: Boolean = true,

    // Battery Health Alerts
    val healthDecliningAlertEnabled: Boolean = true,
    val speedReducedAlertEnabled: Boolean = true,
    val calibrationAlertEnabled: Boolean = true,
    val cloudBackupEnabled: Boolean = false,
    val aiSharingEnabled: Boolean = false,
    val firstLaunchWizardCompleted: Boolean = true,
    val deviceAdminEnabled: Boolean = false,
    val autoStartConfigured: Boolean = false,
    val deepSleepModeEnabled: Boolean = true,
    val deepSleepStartTime: String = "09:00 PM",
    val deepSleepEndTime: String = "06:00 AM",
    
    // --- NETRA MONETIZATION ENGINE V1.0 FIELDS ---
    val isPremium: Boolean = true,
    val credits: Int = 500, // Starts with 500 credits by default
    val onboardingTimestamp: Long = 0L,
    val lastCheckInTimestamp: Long = 0L,
    val completedAchievementsJson: String = "",
    val tokenSpentCount: Int = 0,
    val trialSelected: String = "PREMIUM_7_DAYS", // PREMIUM_7_DAYS, BONUS_100_CREDITS
    
    // New Features: Run At Startup & Full Battery Alarm
    val runAtStartup: Boolean = true,
    val fullBatteryAlarmEnabled: Boolean = true,
    val fullBatteryAlarmOption: String = "ALARM_RING",
    
    // Low battery alerts for connected devices (v1.7)
    val connectedDevicesLowBatteryThreshold: Int = 15,
    
    // New AI Toggles
    val aiThrottlingEnabled: Boolean = true,
    val aiAnalyticsEnabled: Boolean = true,
    
    // Safety Features (v1.9)
    val isMagneticFieldDetectionEnabled: Boolean = true,
    val magneticFieldThreshold: Float = 100.0f,
    val isLightIntensityDetectionEnabled: Boolean = true,
    val lightIntensityThreshold: Float = 10000.0f,
    val highDrainAppUsageEnabled: Boolean = true,
    val lowBatteryRedThemeEnabled: Boolean = true,
    val dynamicBatteryColorEngineEnabled: Boolean = false,
    val showSpeedIndicatorInNotification: Boolean = false
)

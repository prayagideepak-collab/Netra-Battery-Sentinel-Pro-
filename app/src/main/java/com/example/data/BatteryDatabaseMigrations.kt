package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object BatteryDatabaseMigrations {

    private fun executeFullUpgrade(database: SupportSQLiteDatabase) {
        // Ensure app_settings exists and has all columns
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_settings` (`id` INTEGER NOT NULL, `theme` TEXT NOT NULL, `speechPitch` REAL NOT NULL, `speechSpeed` REAL NOT NULL, `speechVolume` REAL NOT NULL, `voiceType` TEXT NOT NULL, `announcementInterval` INTEGER NOT NULL, `customPercentage` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )

        val columnsToAdd = listOf(
            "activeHoursEnabled INTEGER NOT NULL DEFAULT 1",
            "activeHoursStart TEXT NOT NULL DEFAULT '06:00 AM'",
            "activeHoursEnd TEXT NOT NULL DEFAULT '10:00 PM'",
            "restIntervalEnabled INTEGER NOT NULL DEFAULT 0",
            "restIntervalStart TEXT NOT NULL DEFAULT '01:30 PM'",
            "restIntervalEnd TEXT NOT NULL DEFAULT '02:30 PM'",
            "screenOnVoiceEnabled INTEGER NOT NULL DEFAULT 0",
            "smartBatteryAlertsEnabled INTEGER NOT NULL DEFAULT 1",
            "tempAlertThreshold REAL NOT NULL DEFAULT 45.0",
            "batteryHealthAlertsEnabled INTEGER NOT NULL DEFAULT 1",
            "smartSyncReminderEnabled INTEGER NOT NULL DEFAULT 0",
            "voiceAssistantEnabled INTEGER NOT NULL DEFAULT 1",
            "lowBatteryThreshold INTEGER NOT NULL DEFAULT 20",
            "fullBatteryThreshold INTEGER NOT NULL DEFAULT 100",
            "chargerConnectedEnabled INTEGER NOT NULL DEFAULT 1",
            "chargerDisconnectedEnabled INTEGER NOT NULL DEFAULT 1",
            "lowBatteryEnabled INTEGER NOT NULL DEFAULT 1",
            "batteryPercentageEnabled INTEGER NOT NULL DEFAULT 1",
            "tempWarningEnabled INTEGER NOT NULL DEFAULT 1",
            "criticalTempEnabled INTEGER NOT NULL DEFAULT 1",
            "milestone25Enabled INTEGER NOT NULL DEFAULT 1",
            "milestone50Enabled INTEGER NOT NULL DEFAULT 1",
            "milestone75Enabled INTEGER NOT NULL DEFAULT 1",
            "milestone80Enabled INTEGER NOT NULL DEFAULT 1",
            "milestone90Enabled INTEGER NOT NULL DEFAULT 1",
            "milestone95Enabled INTEGER NOT NULL DEFAULT 1",
            "healthDecliningAlertEnabled INTEGER NOT NULL DEFAULT 1",
            "speedReducedAlertEnabled INTEGER NOT NULL DEFAULT 1",
            "calibrationAlertEnabled INTEGER NOT NULL DEFAULT 1",
            "cloudBackupEnabled INTEGER NOT NULL DEFAULT 0",
            "aiSharingEnabled INTEGER NOT NULL DEFAULT 0",
            "firstLaunchWizardCompleted INTEGER NOT NULL DEFAULT 1",
            "deviceAdminEnabled INTEGER NOT NULL DEFAULT 0",
            "autoStartConfigured INTEGER NOT NULL DEFAULT 0",
            "deepSleepModeEnabled INTEGER NOT NULL DEFAULT 1",
            "deepSleepStartTime TEXT NOT NULL DEFAULT '09:00 PM'",
            "deepSleepEndTime TEXT NOT NULL DEFAULT '06:00 AM'",
            "deepSleepStandardVoiceEnabled INTEGER NOT NULL DEFAULT 0",
            "deepSleepChargerVoiceEnabled INTEGER NOT NULL DEFAULT 0",
            "deepSleepMilestonesEnabled INTEGER NOT NULL DEFAULT 0",
            "deepSleepBackgroundTelemetryEnabled INTEGER NOT NULL DEFAULT 1",
            "isPremium INTEGER NOT NULL DEFAULT 1",
            "credits INTEGER NOT NULL DEFAULT 500",
            "onboardingTimestamp INTEGER NOT NULL DEFAULT 0",
            "lastCheckInTimestamp INTEGER NOT NULL DEFAULT 0",
            "completedAchievementsJson TEXT NOT NULL DEFAULT ''",
            "tokenSpentCount INTEGER NOT NULL DEFAULT 0",
            "trialSelected TEXT NOT NULL DEFAULT 'PREMIUM_7_DAYS'",
            "runAtStartup INTEGER NOT NULL DEFAULT 1",
            "connectedDevicesLowBatteryThreshold INTEGER NOT NULL DEFAULT 15",
            "aiThrottlingEnabled INTEGER NOT NULL DEFAULT 1",
            "aiAnalyticsEnabled INTEGER NOT NULL DEFAULT 1",
            "isMagneticFieldDetectionEnabled INTEGER NOT NULL DEFAULT 1",
            "magneticFieldThreshold REAL NOT NULL DEFAULT 100.0",
            "isLightIntensityDetectionEnabled INTEGER NOT NULL DEFAULT 1",
            "lightIntensityThreshold REAL NOT NULL DEFAULT 10000.0",
            "highDrainAppUsageEnabled INTEGER NOT NULL DEFAULT 1",
            "lowBatteryRedThemeEnabled INTEGER NOT NULL DEFAULT 1",
            "dynamicBatteryColorEngineEnabled INTEGER NOT NULL DEFAULT 0",
            "showSpeedIndicatorInNotification INTEGER NOT NULL DEFAULT 0"
        )
        for (col in columnsToAdd) {
            try {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN $col")
            } catch (e: Exception) {
                // Column already exists, safe to ignore
            }
        }

        // Ensure app_consumption has all network and telemetry columns
        val appConsumptionCols = listOf(
            "uid INTEGER NOT NULL DEFAULT 0",
            "mobileRxBytes INTEGER NOT NULL DEFAULT 0",
            "mobileTxBytes INTEGER NOT NULL DEFAULT 0",
            "wifiRxBytes INTEGER NOT NULL DEFAULT 0",
            "wifiTxBytes INTEGER NOT NULL DEFAULT 0",
            "totalRxBytes INTEGER NOT NULL DEFAULT 0",
            "totalTxBytes INTEGER NOT NULL DEFAULT 0",
            "totalNetworkBytes INTEGER NOT NULL DEFAULT 0",
            "networkStatsAvailable INTEGER NOT NULL DEFAULT 0",
            "batteryAttributionAvailable INTEGER NOT NULL DEFAULT 0",
            "activityState TEXT NOT NULL DEFAULT 'Inactive'"
        )
        for (col in appConsumptionCols) {
            try {
                database.execSQL("ALTER TABLE app_consumption ADD COLUMN $col")
            } catch (e: Exception) {
                // Column already exists, safe to ignore
            }
        }

        // Ensure app_version_table exists
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_version_table` (`id` INTEGER NOT NULL, `versionCode` INTEGER NOT NULL, `versionName` TEXT NOT NULL, `lastUpdatedTimestamp` INTEGER NOT NULL, `changeDescription` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )

        // Ensure indices exist
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_events_timestamp` ON `battery_events` (`timestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_charging_sessions_startTime` ON `charging_sessions` (`startTime`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_discharging_sessions_startTime` ON `discharging_sessions` (`startTime`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_app_activity_timestamp` ON `app_activity` (`timestamp`)")

        // Ensure sync_tasks and charging_protection_sessions tables exist
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_tasks` (`taskId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `category` TEXT NOT NULL, `state` TEXT NOT NULL, `startTimestamp` INTEGER NOT NULL, `completionTimestamp` INTEGER NOT NULL, `errorReason` TEXT, `progress` INTEGER NOT NULL, `isApplicable` INTEGER NOT NULL, `lastSuccessfulTimestamp` INTEGER NOT NULL, PRIMARY KEY(`taskId`))"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `charging_protection_sessions` (`sessionId` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `startBatteryLevel` INTEGER NOT NULL, `endBatteryLevel` INTEGER, `startTemperature` REAL NOT NULL, `maxTemperature` REAL NOT NULL, `originalScreenTimeout` INTEGER NOT NULL, `originalBrightnessMode` INTEGER NOT NULL, `originalBrightnessValue` INTEGER NOT NULL, `originalAutoBrightness` INTEGER NOT NULL, `originalNetraBackgroundState` TEXT NOT NULL, `originalNetraSyncState` INTEGER NOT NULL, `actionsApplied` TEXT NOT NULL, `restorationStatus` TEXT NOT NULL, `timeoutModified` INTEGER NOT NULL, `brightnessModified` INTEGER NOT NULL, `brightnessModeModified` INTEGER NOT NULL, `syncModified` INTEGER NOT NULL, `backgroundWorkloadModified` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))"
        )
    }

    val MIGRATION_1_37 = object : Migration(1, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_17_37 = object : Migration(17, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_18_37 = object : Migration(18, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_28_37 = object : Migration(28, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_29_37 = object : Migration(29, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_35_37 = object : Migration(35, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_41_42 = object : Migration(41, 42) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_42_43 = object : Migration(42, 43) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_tasks` (`taskId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `category` TEXT NOT NULL, `state` TEXT NOT NULL, `startTimestamp` INTEGER NOT NULL, `completionTimestamp` INTEGER NOT NULL, `errorReason` TEXT, `progress` INTEGER NOT NULL, `isApplicable` INTEGER NOT NULL, `lastSuccessfulTimestamp` INTEGER NOT NULL, PRIMARY KEY(`taskId`))"
            )
        }
    }

    val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `charging_protection_sessions` (`sessionId` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `startBatteryLevel` INTEGER NOT NULL, `endBatteryLevel` INTEGER, `startTemperature` REAL NOT NULL, `maxTemperature` REAL NOT NULL, `originalScreenTimeout` INTEGER NOT NULL, `originalBrightnessMode` INTEGER NOT NULL, `originalBrightnessValue` INTEGER NOT NULL, `originalAutoBrightness` INTEGER NOT NULL, `originalNetraBackgroundState` TEXT NOT NULL, `originalNetraSyncState` INTEGER NOT NULL, `actionsApplied` TEXT NOT NULL, `restorationStatus` TEXT NOT NULL, `timeoutModified` INTEGER NOT NULL, `brightnessModified` INTEGER NOT NULL, `brightnessModeModified` INTEGER NOT NULL, `syncModified` INTEGER NOT NULL, `backgroundWorkloadModified` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))"
            )
        }
    }
}


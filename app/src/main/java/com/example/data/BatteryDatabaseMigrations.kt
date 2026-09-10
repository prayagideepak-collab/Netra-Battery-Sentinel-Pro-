package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object BatteryDatabaseMigrations {

    private fun executeFullUpgrade(database: SupportSQLiteDatabase) {
        // Ensure app_settings exists and has all columns
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_settings` (`id` INTEGER NOT NULL, `theme` TEXT NOT NULL, `speechPitch` REAL NOT NULL, `speechSpeed` REAL NOT NULL, `speechVolume` REAL NOT NULL, `voiceType` TEXT NOT NULL, `announcementInterval` INTEGER NOT NULL, `customPercentage` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )

        // Ensure all core entity tables exist
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `battery_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `eventType` TEXT NOT NULL, `title` TEXT NOT NULL, `details` TEXT NOT NULL, `category` TEXT NOT NULL, `source` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `charging_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `startPercentage` INTEGER NOT NULL, `endPercentage` INTEGER, `chargingType` TEXT NOT NULL, `maxTemperature` REAL NOT NULL, `isOvernight` INTEGER NOT NULL, `isDischarge` INTEGER NOT NULL, `avgPower` REAL NOT NULL, `screenOnTimeMinutes` INTEGER NOT NULL, `standbyTimeMinutes` INTEGER NOT NULL, `startTemperature` REAL NOT NULL, `endTemperature` REAL, `fullChargeTime` INTEGER, `formattedStartTime` TEXT NOT NULL, `formattedFullChargeTime` TEXT, `formattedEndTime` TEXT, `totalDurationSeconds` INTEGER NOT NULL, `overchargingDurationSeconds` INTEGER NOT NULL, `fullyCharged` INTEGER NOT NULL, `sessionStatus` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `discharging_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `startPercentage` INTEGER NOT NULL, `endPercentage` INTEGER, `maxTemperature` REAL NOT NULL, `screenOnTimeMinutes` INTEGER NOT NULL, `standbyTimeMinutes` INTEGER NOT NULL, `avgDrainRate` REAL NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_activity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `activityType` TEXT NOT NULL, `details` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_consumption` (`packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `uid` INTEGER NOT NULL, `foregroundTimeMs` INTEGER NOT NULL, `backgroundTimeMs` INTEGER NOT NULL, `consumedMah` REAL NOT NULL, `estimatedDrainRate` REAL NOT NULL, `drainRating` TEXT NOT NULL, `isRunning` INTEGER NOT NULL, `lastActiveTime` INTEGER NOT NULL, `mobileRxBytes` INTEGER NOT NULL, `mobileTxBytes` INTEGER NOT NULL, `wifiRxBytes` INTEGER NOT NULL, `wifiTxBytes` INTEGER NOT NULL, `totalRxBytes` INTEGER NOT NULL, `totalTxBytes` INTEGER NOT NULL, `totalNetworkBytes` INTEGER NOT NULL, `networkStatsAvailable` INTEGER NOT NULL, `batteryAttributionAvailable` INTEGER NOT NULL, `activityState` TEXT NOT NULL, PRIMARY KEY(`packageName`))"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `battery_trend_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `dischargeRate` REAL NOT NULL, `chargeCycleDuration` INTEGER NOT NULL, `batteryLevel` INTEGER NOT NULL, `temperature` REAL NOT NULL, `voltage` INTEGER NOT NULL, `currentNow` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `devices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `macAddress` TEXT NOT NULL, `ipAddress` TEXT, `vendor` TEXT, `isConnected` INTEGER NOT NULL, `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL, `connectedTime` INTEGER NOT NULL, `disconnectTime` INTEGER NOT NULL, `totalConnectionCount` INTEGER NOT NULL, `totalConnectedDuration` INTEGER NOT NULL, `batteryLevel` INTEGER, `isCharging` INTEGER NOT NULL, `rssi` INTEGER)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `magnetic_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `currentMagneticField` REAL NOT NULL, `peakMagneticField` REAL NOT NULL, `averageMagneticField` REAL NOT NULL, `detectionDurationMs` INTEGER NOT NULL, `safetyZone` TEXT NOT NULL, `sensorAccuracy` INTEGER NOT NULL, `deviceOrientation` TEXT NOT NULL, `chargingStatus` TEXT NOT NULL, `deviceTemperature` REAL NOT NULL, `voiceAnnouncementStatus` TEXT NOT NULL, `actionsTaken` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `decision` TEXT NOT NULL, `notificationStatus` TEXT NOT NULL, `announcementStatus` TEXT NOT NULL, `aiConfidence` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `system_audit_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `totalServicesChecked` INTEGER NOT NULL, `healthyServices` INTEGER NOT NULL, `restartedServices` INTEGER NOT NULL, `failedServices` INTEGER NOT NULL, `unsupportedComponents` INTEGER NOT NULL, `recoveryActions` TEXT NOT NULL, `healthScore` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `battery_alerts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `batteryLevel` INTEGER NOT NULL, `isBelow` INTEGER NOT NULL, `voicePrompt` TEXT NOT NULL, `enabled` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_status` (`moduleName` TEXT NOT NULL, `status` TEXT NOT NULL, `memoryUsageMb` INTEGER NOT NULL, `threadHealth` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`moduleName`))"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `diagnostic_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventType` TEXT NOT NULL, `moduleName` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `root_cause_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `moduleName` TEXT NOT NULL, `failureType` TEXT NOT NULL, `rootCause` TEXT NOT NULL, `threadDump` TEXT NOT NULL, `exception` TEXT NOT NULL, `memorySnapshot` TEXT NOT NULL, `cpuSnapshot` TEXT NOT NULL, `recommendedRecovery` TEXT NOT NULL, `recoveryExecuted` TEXT NOT NULL, `recoveryResult` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `resource_optimizations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `cpuLoad` REAL NOT NULL, `ramUsage` INTEGER NOT NULL, `optimizationApplied` TEXT NOT NULL, `beforeState` TEXT NOT NULL, `afterState` TEXT NOT NULL, `result` TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `battery_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `batteryLevel` INTEGER NOT NULL, `isCharging` INTEGER NOT NULL, `chargingType` TEXT NOT NULL, `temperature` REAL NOT NULL, `voltageMv` INTEGER NOT NULL, `currentNowMa` INTEGER NOT NULL, `batteryHealth` TEXT NOT NULL, `batteryStatus` TEXT NOT NULL, `hourOfDay` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_version_table` (`id` INTEGER NOT NULL, `versionCode` INTEGER NOT NULL, `versionName` TEXT NOT NULL, `lastUpdatedTimestamp` INTEGER NOT NULL, `changeDescription` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_tasks` (`taskId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `category` TEXT NOT NULL, `state` TEXT NOT NULL, `startTimestamp` INTEGER NOT NULL, `completionTimestamp` INTEGER NOT NULL, `errorReason` TEXT, `progress` INTEGER NOT NULL, `isApplicable` INTEGER NOT NULL, `lastSuccessfulTimestamp` INTEGER NOT NULL, PRIMARY KEY(`taskId`))"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `charging_protection_sessions` (`sessionId` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `startBatteryLevel` INTEGER NOT NULL, `endBatteryLevel` INTEGER, `startTemperature` REAL NOT NULL, `maxTemperature` REAL NOT NULL, `originalScreenTimeout` INTEGER NOT NULL, `originalBrightnessMode` INTEGER NOT NULL, `originalBrightnessValue` INTEGER NOT NULL, `originalAutoBrightness` INTEGER NOT NULL, `originalNetraBackgroundState` TEXT NOT NULL, `originalNetraSyncState` INTEGER NOT NULL, `actionsApplied` TEXT NOT NULL, `restorationStatus` TEXT NOT NULL, `timeoutModified` INTEGER NOT NULL, `brightnessModified` INTEGER NOT NULL, `brightnessModeModified` INTEGER NOT NULL, `syncModified` INTEGER NOT NULL, `backgroundWorkloadModified` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))"
        )

        // Ensure indices exist
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_events_timestamp` ON `battery_events` (`timestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_charging_sessions_startTime` ON `charging_sessions` (`startTime`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_discharging_sessions_startTime` ON `discharging_sessions` (`startTime`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_app_activity_timestamp` ON `app_activity` (`timestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_history_timestamp` ON `battery_history` (`timestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_history_isCharging` ON `battery_history` (`isCharging`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_history_hourOfDay` ON `battery_history` (`hourOfDay`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_battery_history_dayOfWeek` ON `battery_history` (`dayOfWeek`)")

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

        val chargingSessionCols = listOf(
            "startTemperature REAL NOT NULL DEFAULT 0.0",
            "endTemperature REAL",
            "fullChargeTime INTEGER",
            "formattedStartTime TEXT NOT NULL DEFAULT ''",
            "formattedFullChargeTime TEXT",
            "formattedEndTime TEXT",
            "totalDurationSeconds INTEGER NOT NULL DEFAULT 0",
            "overchargingDurationSeconds INTEGER NOT NULL DEFAULT 0",
            "fullyCharged INTEGER NOT NULL DEFAULT 0",
            "sessionStatus TEXT NOT NULL DEFAULT 'ACTIVE'",
            "createdTimestamp INTEGER NOT NULL DEFAULT 0"
        )
        for (col in chargingSessionCols) {
            try {
                database.execSQL("ALTER TABLE charging_sessions ADD COLUMN $col")
            } catch (e: Exception) {
                // Column already exists, safe to ignore
            }
        }
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `charging_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `startPercentage` INTEGER NOT NULL, `endPercentage` INTEGER, `chargingType` TEXT NOT NULL, `maxTemperature` REAL NOT NULL, `isOvernight` INTEGER NOT NULL, `isDischarge` INTEGER NOT NULL, `avgPower` REAL NOT NULL, `screenOnTimeMinutes` INTEGER NOT NULL, `standbyTimeMinutes` INTEGER NOT NULL, `startTemperature` REAL NOT NULL DEFAULT 0.0, `endTemperature` REAL, `fullChargeTime` INTEGER, `formattedStartTime` TEXT NOT NULL DEFAULT '', `formattedFullChargeTime` TEXT, `formattedEndTime` TEXT, `totalDurationSeconds` INTEGER NOT NULL DEFAULT 0, `overchargingDurationSeconds` INTEGER NOT NULL DEFAULT 0, `fullyCharged` INTEGER NOT NULL DEFAULT 0, `sessionStatus` TEXT NOT NULL DEFAULT 'ACTIVE', `createdTimestamp` INTEGER NOT NULL DEFAULT 0)"
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_charging_sessions_startTime` ON `charging_sessions` (`startTime`)")
            
            val cols = listOf(
                "startTemperature REAL NOT NULL DEFAULT 0.0",
                "endTemperature REAL",
                "fullChargeTime INTEGER",
                "formattedStartTime TEXT NOT NULL DEFAULT ''",
                "formattedFullChargeTime TEXT",
                "formattedEndTime TEXT",
                "totalDurationSeconds INTEGER NOT NULL DEFAULT 0",
                "overchargingDurationSeconds INTEGER NOT NULL DEFAULT 0",
                "fullyCharged INTEGER NOT NULL DEFAULT 0",
                "sessionStatus TEXT NOT NULL DEFAULT 'ACTIVE'",
                "createdTimestamp INTEGER NOT NULL DEFAULT 0"
            )
            for (col in cols) {
                try {
                    database.execSQL("ALTER TABLE charging_sessions ADD COLUMN $col")
                } catch (e: Exception) {
                    // Ignored if column exists
                }
            }
        }
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

    val MIGRATION_44_45 = object : Migration(44, 45) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_45_46 = object : Migration(45, 46) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }

    val MIGRATION_46_47 = object : Migration(46, 47) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.beginTransaction()
            try {
                // 1. Create new table matching exact Room Version 47 schema for app_settings without autoCacheCleanerEnabled
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_settings_new` (
                        `id` INTEGER NOT NULL,
                        `theme` TEXT NOT NULL,
                        `speechPitch` REAL NOT NULL,
                        `speechSpeed` REAL NOT NULL,
                        `speechVolume` REAL NOT NULL,
                        `voiceType` TEXT NOT NULL,
                        `announcementInterval` INTEGER NOT NULL,
                        `customPercentage` INTEGER NOT NULL,
                        `activeHoursEnabled` INTEGER NOT NULL,
                        `activeHoursStart` TEXT NOT NULL,
                        `activeHoursEnd` TEXT NOT NULL,
                        `restIntervalEnabled` INTEGER NOT NULL,
                        `restIntervalStart` TEXT NOT NULL,
                        `restIntervalEnd` TEXT NOT NULL,
                        `screenOnVoiceEnabled` INTEGER NOT NULL,
                        `smartBatteryAlertsEnabled` INTEGER NOT NULL,
                        `tempAlertThreshold` REAL NOT NULL,
                        `batteryHealthAlertsEnabled` INTEGER NOT NULL,
                        `smartSyncReminderEnabled` INTEGER NOT NULL,
                        `voiceAssistantEnabled` INTEGER NOT NULL,
                        `lowBatteryThreshold` INTEGER NOT NULL,
                        `fullBatteryThreshold` INTEGER NOT NULL,
                        `chargerConnectedEnabled` INTEGER NOT NULL,
                        `chargerDisconnectedEnabled` INTEGER NOT NULL,
                        `lowBatteryEnabled` INTEGER NOT NULL,
                        `batteryPercentageEnabled` INTEGER NOT NULL,
                        `tempWarningEnabled` INTEGER NOT NULL,
                        `criticalTempEnabled` INTEGER NOT NULL,
                        `milestone25Enabled` INTEGER NOT NULL,
                        `milestone50Enabled` INTEGER NOT NULL,
                        `milestone75Enabled` INTEGER NOT NULL,
                        `milestone80Enabled` INTEGER NOT NULL,
                        `milestone90Enabled` INTEGER NOT NULL,
                        `milestone95Enabled` INTEGER NOT NULL,
                        `healthDecliningAlertEnabled` INTEGER NOT NULL,
                        `speedReducedAlertEnabled` INTEGER NOT NULL,
                        `calibrationAlertEnabled` INTEGER NOT NULL,
                        `cloudBackupEnabled` INTEGER NOT NULL,
                        `aiSharingEnabled` INTEGER NOT NULL,
                        `firstLaunchWizardCompleted` INTEGER NOT NULL,
                        `deviceAdminEnabled` INTEGER NOT NULL,
                        `autoStartConfigured` INTEGER NOT NULL,
                        `deepSleepModeEnabled` INTEGER NOT NULL,
                        `deepSleepStartTime` TEXT NOT NULL,
                        `deepSleepEndTime` TEXT NOT NULL,
                        `deepSleepStandardVoiceEnabled` INTEGER NOT NULL,
                        `deepSleepChargerVoiceEnabled` INTEGER NOT NULL,
                        `deepSleepMilestonesEnabled` INTEGER NOT NULL,
                        `deepSleepBackgroundTelemetryEnabled` INTEGER NOT NULL,
                        `isPremium` INTEGER NOT NULL,
                        `credits` INTEGER NOT NULL,
                        `onboardingTimestamp` INTEGER NOT NULL,
                        `lastCheckInTimestamp` INTEGER NOT NULL,
                        `completedAchievementsJson` TEXT NOT NULL,
                        `tokenSpentCount` INTEGER NOT NULL,
                        `trialSelected` TEXT NOT NULL,
                        `runAtStartup` INTEGER NOT NULL,
                        `connectedDevicesLowBatteryThreshold` INTEGER NOT NULL,
                        `aiThrottlingEnabled` INTEGER NOT NULL,
                        `aiAnalyticsEnabled` INTEGER NOT NULL,
                        `isMagneticFieldDetectionEnabled` INTEGER NOT NULL,
                        `magneticFieldThreshold` REAL NOT NULL,
                        `isLightIntensityDetectionEnabled` INTEGER NOT NULL,
                        `lightIntensityThreshold` REAL NOT NULL,
                        `highDrainAppUsageEnabled` INTEGER NOT NULL,
                        `lowBatteryRedThemeEnabled` INTEGER NOT NULL,
                        `dynamicBatteryColorEngineEnabled` INTEGER NOT NULL,
                        `showSpeedIndicatorInNotification` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                // 2. Safely copy all existing configuration data
                database.execSQL(
                    """
                    INSERT INTO `app_settings_new` (
                        `id`, `theme`, `speechPitch`, `speechSpeed`, `speechVolume`, `voiceType`,
                        `announcementInterval`, `customPercentage`, `activeHoursEnabled`, `activeHoursStart`,
                        `activeHoursEnd`, `restIntervalEnabled`, `restIntervalStart`, `restIntervalEnd`,
                        `screenOnVoiceEnabled`, `smartBatteryAlertsEnabled`, `tempAlertThreshold`,
                        `batteryHealthAlertsEnabled`, `smartSyncReminderEnabled`, `voiceAssistantEnabled`,
                        `lowBatteryThreshold`, `fullBatteryThreshold`, `chargerConnectedEnabled`,
                        `chargerDisconnectedEnabled`, `lowBatteryEnabled`, `batteryPercentageEnabled`,
                        `tempWarningEnabled`, `criticalTempEnabled`, `milestone25Enabled`, `milestone50Enabled`,
                        `milestone75Enabled`, `milestone80Enabled`, `milestone90Enabled`, `milestone95Enabled`,
                        `healthDecliningAlertEnabled`, `speedReducedAlertEnabled`, `calibrationAlertEnabled`,
                        `cloudBackupEnabled`, `aiSharingEnabled`, `firstLaunchWizardCompleted`,
                        `deviceAdminEnabled`, `autoStartConfigured`, `deepSleepModeEnabled`,
                        `deepSleepStartTime`, `deepSleepEndTime`, `deepSleepStandardVoiceEnabled`,
                        `deepSleepChargerVoiceEnabled`, `deepSleepMilestonesEnabled`,
                        `deepSleepBackgroundTelemetryEnabled`, `isPremium`, `credits`,
                        `onboardingTimestamp`, `lastCheckInTimestamp`, `completedAchievementsJson`,
                        `tokenSpentCount`, `trialSelected`, `runAtStartup`,
                        `connectedDevicesLowBatteryThreshold`, `aiThrottlingEnabled`, `aiAnalyticsEnabled`,
                        `isMagneticFieldDetectionEnabled`, `magneticFieldThreshold`,
                        `isLightIntensityDetectionEnabled`, `lightIntensityThreshold`,
                        `highDrainAppUsageEnabled`, `lowBatteryRedThemeEnabled`,
                        `dynamicBatteryColorEngineEnabled`, `showSpeedIndicatorInNotification`
                    )
                    SELECT
                        `id`, `theme`, `speechPitch`, `speechSpeed`, `speechVolume`, `voiceType`,
                        `announcementInterval`, `customPercentage`, `activeHoursEnabled`, `activeHoursStart`,
                        `activeHoursEnd`, `restIntervalEnabled`, `restIntervalStart`, `restIntervalEnd`,
                        `screenOnVoiceEnabled`, `smartBatteryAlertsEnabled`, `tempAlertThreshold`,
                        `batteryHealthAlertsEnabled`, `smartSyncReminderEnabled`, `voiceAssistantEnabled`,
                        `lowBatteryThreshold`, `fullBatteryThreshold`, `chargerConnectedEnabled`,
                        `chargerDisconnectedEnabled`, `lowBatteryEnabled`, `batteryPercentageEnabled`,
                        `tempWarningEnabled`, `criticalTempEnabled`, `milestone25Enabled`, `milestone50Enabled`,
                        `milestone75Enabled`, `milestone80Enabled`, `milestone90Enabled`, `milestone95Enabled`,
                        `healthDecliningAlertEnabled`, `speedReducedAlertEnabled`, `calibrationAlertEnabled`,
                        `cloudBackupEnabled`, `aiSharingEnabled`, `firstLaunchWizardCompleted`,
                        `deviceAdminEnabled`, `autoStartConfigured`, `deepSleepModeEnabled`,
                        `deepSleepStartTime`, `deepSleepEndTime`, `deepSleepStandardVoiceEnabled`,
                        `deepSleepChargerVoiceEnabled`, `deepSleepMilestonesEnabled`,
                        `deepSleepBackgroundTelemetryEnabled`, `isPremium`, `credits`,
                        `onboardingTimestamp`, `lastCheckInTimestamp`, `completedAchievementsJson`,
                        `tokenSpentCount`, `trialSelected`, `runAtStartup`,
                        `connectedDevicesLowBatteryThreshold`, `aiThrottlingEnabled`, `aiAnalyticsEnabled`,
                        `isMagneticFieldDetectionEnabled`, `magneticFieldThreshold`,
                        `isLightIntensityDetectionEnabled`, `lightIntensityThreshold`,
                        `highDrainAppUsageEnabled`, `lowBatteryRedThemeEnabled`,
                        `dynamicBatteryColorEngineEnabled`, `showSpeedIndicatorInNotification`
                    FROM `app_settings`
                    """.trimIndent()
                )

                // 3. Drop old table and rename new table
                database.execSQL("DROP TABLE `app_settings`")
                database.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")

                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    val MIGRATION_47_48 = object : Migration(47, 48) {
        override fun migrate(database: SupportSQLiteDatabase) {
            executeFullUpgrade(database)
        }
    }
}


package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SettingsEntity::class,
        ChargingSession::class,
        DischargingSession::class,
        AppConsumptionEntity::class,
        BatteryTrendLog::class,
        BatteryEvent::class,
        AppActivity::class,
        com.example.devices.Device::class,
        MagneticEvent::class,
        SystemAuditRecord::class,
        BatteryAlert::class,
        HealthStatusEntity::class,
        DiagnosticLogEntity::class,
        RootCauseEntity::class,
        ResourceOptimizerEntity::class,
        BatteryHistoryEntity::class,
        AppVersionEntity::class,
        SyncTaskEntity::class,
        ChargingProtectionSessionEntity::class
    ],
    version = 48,
    exportSchema = false
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    abstract fun deviceDao(): com.example.devices.DeviceDao
    abstract fun batteryHistoryDao(): BatteryHistoryDao
    abstract fun syncTaskDao(): SyncTaskDao

    companion object {
        @Volatile
        private var INSTANCE: BatteryDatabase? = null

        private val roomCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                android.util.Log.i("BatteryDatabase", "Database created successfully.")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    val cursor = db.query("PRAGMA table_info(charging_sessions)")
                    val columns = mutableSetOf<String>()
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex >= 0) {
                        while (cursor.moveToNext()) {
                            columns.add(cursor.getString(nameIndex))
                        }
                    }
                    cursor.close()

                    val requiredCols = listOf(
                        "id", "startTime", "endTime", "startPercentage", "endPercentage",
                        "chargingType", "maxTemperature", "isOvernight", "isDischarge",
                        "avgPower", "screenOnTimeMinutes", "standbyTimeMinutes",
                        "startTemperature", "endTemperature", "fullChargeTime",
                        "formattedStartTime", "formattedFullChargeTime", "formattedEndTime",
                        "totalDurationSeconds", "overchargingDurationSeconds",
                        "fullyCharged", "sessionStatus", "createdTimestamp"
                    )

                    val missing = requiredCols.filter { !columns.contains(it) }
                    if (missing.isNotEmpty()) {
                        android.util.Log.e("BatteryDatabase", "SCHEMA CONSISTENCY WARNING: charging_sessions is missing columns: $missing")
                        for (colName in missing) {
                            try {
                                val def = when (colName) {
                                    "startTemperature", "maxTemperature" -> "REAL NOT NULL DEFAULT 0.0"
                                    "endTemperature", "fullChargeTime", "endPercentage", "endTime", "formattedFullChargeTime", "formattedEndTime" -> "REAL"
                                    "formattedStartTime" -> "TEXT NOT NULL DEFAULT ''"
                                    "sessionStatus" -> "TEXT NOT NULL DEFAULT 'ACTIVE'"
                                    "totalDurationSeconds", "overchargingDurationSeconds", "createdTimestamp" -> "INTEGER NOT NULL DEFAULT 0"
                                    "fullyCharged", "isOvernight", "isDischarge" -> "INTEGER NOT NULL DEFAULT 0"
                                    else -> "TEXT"
                                }
                                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN $colName $def")
                                android.util.Log.i("BatteryDatabase", "Successfully auto-patched missing column in charging_sessions: $colName")
                            } catch (e: Exception) {
                                android.util.Log.e("BatteryDatabase", "Failed to auto-patch column $colName: ${e.message}")
                            }
                        }
                    } else {
                        android.util.Log.i("BatteryDatabase", "charging_sessions schema validation passed successfully.")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BatteryDatabase", "Error validating charging_sessions schema: ${e.message}", e)
                }
            }
        }

        fun getDatabase(context: Context): BatteryDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    BatteryDatabase::class.java,
                    "battery_voice_assistant_db"
                )
                .addMigrations(
                    BatteryDatabaseMigrations.MIGRATION_1_2,
                    BatteryDatabaseMigrations.MIGRATION_1_37,
                    BatteryDatabaseMigrations.MIGRATION_17_37,
                    BatteryDatabaseMigrations.MIGRATION_18_37,
                    BatteryDatabaseMigrations.MIGRATION_28_37,
                    BatteryDatabaseMigrations.MIGRATION_29_37,
                    BatteryDatabaseMigrations.MIGRATION_35_37,
                    BatteryDatabaseMigrations.MIGRATION_36_37,
                    BatteryDatabaseMigrations.MIGRATION_37_38,
                    BatteryDatabaseMigrations.MIGRATION_38_39,
                    BatteryDatabaseMigrations.MIGRATION_39_40,
                    BatteryDatabaseMigrations.MIGRATION_40_41,
                    BatteryDatabaseMigrations.MIGRATION_41_42,
                    BatteryDatabaseMigrations.MIGRATION_42_43,
                    BatteryDatabaseMigrations.MIGRATION_43_44,
                    BatteryDatabaseMigrations.MIGRATION_44_45,
                    BatteryDatabaseMigrations.MIGRATION_45_46,
                    BatteryDatabaseMigrations.MIGRATION_46_47,
                    BatteryDatabaseMigrations.MIGRATION_47_48
                )
                .addCallback(roomCallback)

                if (com.example.BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration()
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}

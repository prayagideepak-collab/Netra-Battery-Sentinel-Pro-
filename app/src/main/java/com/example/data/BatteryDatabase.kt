package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        AppVersionEntity::class
    ],
    version = 42,
    exportSchema = false
)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao
    abstract fun deviceDao(): com.example.devices.DeviceDao
    abstract fun batteryHistoryDao(): BatteryHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: BatteryDatabase? = null

        fun getDatabase(context: Context): BatteryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BatteryDatabase::class.java,
                    "battery_voice_assistant_db"
                )
                .addMigrations(
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
                    BatteryDatabaseMigrations.MIGRATION_41_42
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

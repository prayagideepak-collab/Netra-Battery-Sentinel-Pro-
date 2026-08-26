package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ChargingSessionDao
import com.example.data.dao.RoomDao
import com.example.data.dao.ThermalEventDao
import com.example.data.entity.ChargingSession
import com.example.data.entity.Room
import com.example.data.entity.ThermalEvent

@Database(
    entities = [Room::class, ThermalEvent::class, ChargingSession::class],
    version = 306,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun thermalEventDao(): ThermalEventDao
    abstract fun chargingSessionDao(): ChargingSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netra_database"
                )
                    .addMigrations(
                        MIGRATION_305_306
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_305_306 = object : Migration(305, 306) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new tables for thermal protection and charging optimization
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS rooms (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        averageTemperature REAL NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS thermal_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        eventType TEXT NOT NULL,
                        temperature REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        state TEXT NOT NULL
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS charging_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        startTime INTEGER NOT NULL,
                        startChargePercent INTEGER NOT NULL,
                        endTime INTEGER,
                        endChargePercent INTEGER,
                        thermallyLimited INTEGER NOT NULL DEFAULT 0,
                        completed INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                // Create indexes for performance
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_thermal_events_timestamp ON thermal_events(timestamp)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_charging_sessions_startTime ON charging_sessions(startTime)"
                )
            }
        }
    }
}

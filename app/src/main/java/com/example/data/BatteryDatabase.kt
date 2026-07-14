package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SettingsEntity::class, ChargingSession::class], version = 3, exportSchema = false)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batteryDao(): BatteryDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

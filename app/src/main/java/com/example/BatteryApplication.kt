package com.example

import android.app.Application
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository

class BatteryApplication : Application() {
    val database by lazy { BatteryDatabase.getDatabase(this) }
    val repository by lazy { BatteryRepository(database.batteryDao()) }

    override fun onCreate() {
        super.onCreate()
    }
}

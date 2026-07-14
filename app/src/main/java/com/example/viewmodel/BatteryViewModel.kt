package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BatteryApplication
import com.example.data.ChargingSession
import com.example.data.SettingsEntity
import com.example.service.BatteryService
import com.example.service.BatteryState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as BatteryApplication).repository

    val batteryState: StateFlow<BatteryState> = BatteryService.liveBatteryState
    val isServiceRunning: StateFlow<Boolean> = BatteryService.isServiceRunning

    val settings: StateFlow<SettingsEntity> = repository.settings
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsEntity()
        )

    val sessions: StateFlow<List<ChargingSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _connectedBluetoothDevices = MutableStateFlow<List<com.example.service.ConnectedBluetoothDevice>>(emptyList())
    val connectedBluetoothDevices: StateFlow<List<com.example.service.ConnectedBluetoothDevice>> = _connectedBluetoothDevices.asStateFlow()

    fun refreshBluetoothDevices(context: Context) {
        if (com.example.service.BluetoothDeviceMonitor.hasBluetoothPermission(context)) {
            _connectedBluetoothDevices.value = com.example.service.BluetoothDeviceMonitor.getConnectedBluetoothDevices(context)
        } else {
            _connectedBluetoothDevices.value = emptyList()
        }
    }

    init {
        // Fetch settings once to initialize if empty
        viewModelScope.launch {
            repository.getSettingsOrInit()
        }
    }

    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun startMonitorService(context: Context) {
        val intent = Intent(context, BatteryService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopMonitorService(context: Context) {
        val intent = Intent(context, BatteryService::class.java)
        context.stopService(intent)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to settings
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    // Helper to request Sync Settings or system shortcut
    fun openSyncSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Fail-safe
            }
        }
    }
}

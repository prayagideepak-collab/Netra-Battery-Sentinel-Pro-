package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothBatteryState(
    val name: String,
    val address: String,
    val batteryLevel: Int, // 0 to 100, or -1 if unavailable
    val isConnected: Boolean,
    val deviceType: String,
    val isAudioDevice: Boolean,
    val lastUpdated: Long = System.currentTimeMillis()
)

object BluetoothBatteryManager {
    private const val TAG = "BluetoothBatteryManager"

    private val _bluetoothBatteryStates = MutableStateFlow<Map<String, BluetoothBatteryState>>(emptyMap())
    val bluetoothBatteryStates: StateFlow<Map<String, BluetoothBatteryState>> = _bluetoothBatteryStates.asStateFlow()

    private var isRegistered = false
    private var broadcastReceiver: BroadcastReceiver? = null

    @SuppressLint("MissingPermission")
    fun register(context: Context) {
        if (isRegistered) return
        val appContext = context.applicationContext
        
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val action = intent.action
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null) {
                    val address = device.address ?: return
                    val name = try { device.name ?: "Bluetooth Device" } catch (e: Exception) { "Bluetooth Device" }

                    when (action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            val batteryLevel = fetchBatteryLevel(device)
                            val isAudio = isAudioDevice(device)
                            val type = getDeviceType(device)
                            updateState(address, BluetoothBatteryState(name, address, batteryLevel, true, type, isAudio))
                            Log.i(TAG, "Device connected: $name ($address), battery: $batteryLevel")
                        }
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            updateState(address, BluetoothBatteryState(name, address, -1, false, getDeviceType(device), isAudioDevice(device)))
                            Log.i(TAG, "Device disconnected: $name ($address)")
                        }
                        "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED",
                        "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED" -> {
                            val batteryLevel = fetchBatteryLevel(device)
                            val existing = _bluetoothBatteryStates.value[address]
                            val isAudio = isAudioDevice(device)
                            val type = existing?.deviceType ?: getDeviceType(device)
                            updateState(address, BluetoothBatteryState(
                                name = existing?.name ?: name,
                                address = address,
                                batteryLevel = batteryLevel,
                                isConnected = true,
                                deviceType = type,
                                isAudioDevice = isAudio
                            ))
                            Log.i(TAG, "Battery broadcast received for $name ($address): $batteryLevel%")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
        }

        try {
            appContext.registerReceiver(broadcastReceiver, filter)
            isRegistered = true
            refreshConnectedDevices(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register BluetoothBatteryManager receiver", e)
        }
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        try {
            broadcastReceiver?.let { context.applicationContext.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering BluetoothBatteryManager", e)
        }
        isRegistered = false
        broadcastReceiver = null
    }

    @SuppressLint("MissingPermission")
    fun refreshConnectedDevices(context: Context) {
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: return
            if (!adapter.isEnabled) return

            val connectedDevices = mutableSetOf<BluetoothDevice>()
            try {
                btManager.getConnectedDevices(android.bluetooth.BluetoothProfile.A2DP)?.let { connectedDevices.addAll(it) }
                btManager.getConnectedDevices(android.bluetooth.BluetoothProfile.HEADSET)?.let { connectedDevices.addAll(it) }
                btManager.getConnectedDevices(android.bluetooth.BluetoothProfile.HEALTH)?.let { connectedDevices.addAll(it) }
            } catch (e: Exception) {}

            for (device in adapter.bondedDevices ?: emptySet()) {
                try {
                    val isConnectedMethod = device.javaClass.getMethod("isConnected")
                    if (isConnectedMethod.invoke(device) as? Boolean == true) {
                        connectedDevices.add(device)
                    }
                } catch (e: Exception) {}
            }

            val currentMap = _bluetoothBatteryStates.value.toMutableMap()
            for (device in connectedDevices) {
                val address = device.address ?: continue
                val name = try { device.name ?: "Bluetooth Device" } catch (e: Exception) { "Bluetooth Device" }
                val batteryLevel = fetchBatteryLevel(device)
                val isAudio = isAudioDevice(device)
                val type = getDeviceType(device)
                currentMap[address] = BluetoothBatteryState(name, address, batteryLevel, true, type, isAudio)
            }
            _bluetoothBatteryStates.value = currentMap
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing connected devices", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchBatteryLevel(device: BluetoothDevice): Int {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            if (level in 0..100) level else -1
        } catch (e: Exception) {
            -1
        }
    }

    @SuppressLint("MissingPermission")
    private fun isAudioDevice(device: BluetoothDevice): Boolean {
        return try {
            val majorClass = device.bluetoothClass?.majorDeviceClass ?: 0
            val deviceClass = device.bluetoothClass?.deviceClass ?: 0
            majorClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO ||
            deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
            deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
            deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceType(device: BluetoothDevice): String {
        return try {
            val major = device.bluetoothClass?.majorDeviceClass ?: 0
            val dc = device.bluetoothClass?.deviceClass ?: 0
            when (major) {
                android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    when (dc) {
                        android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
                        android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Earbuds"
                        android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Speaker"
                        else -> "Audio Device"
                    }
                }
                android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> "Smartwatch"
                android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
                else -> "Bluetooth Device"
            }
        } catch (e: Exception) {
            "Bluetooth Device"
        }
    }

    private fun updateState(address: String, state: BluetoothBatteryState) {
        val map = _bluetoothBatteryStates.value.toMutableMap()
        if (state.isConnected) {
            map[address] = state
        } else {
            map.remove(address)
        }
        _bluetoothBatteryStates.value = map
    }
}

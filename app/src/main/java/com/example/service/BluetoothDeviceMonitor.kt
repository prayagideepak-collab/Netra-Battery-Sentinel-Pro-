package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Log

data class ConnectedBluetoothDevice(
    val name: String,
    val address: String,
    val batteryLevel: Int, // 0 to 100, or -1 if unknown
    val deviceType: String, // "Watch", "Earbuds", "Headphones", "Speaker", "Stylus", "Other"
    val isCharging: Boolean = false
)

object BluetoothDeviceMonitor {
    private const val TAG = "BluetoothDeviceMonitor"

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun getConnectedBluetoothDevices(context: Context): List<ConnectedBluetoothDevice> {
        val result = mutableListOf<ConnectedBluetoothDevice>()
        if (!hasBluetoothPermission(context)) {
            Log.w(TAG, "Missing Bluetooth permissions")
            return emptyList()
        }

        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: return emptyList()

            if (!adapter.isEnabled) {
                return emptyList()
            }

            val bondedDevices = adapter.bondedDevices ?: return emptyList()

            for (device in bondedDevices) {
                try {
                    val isConnectedMethod = device.javaClass.getMethod("isConnected")
                    val isConnected = isConnectedMethod.invoke(device) as? Boolean ?: false

                    if (isConnected) {
                        val batteryLevelMethod = device.javaClass.getMethod("getBatteryLevel")
                        val batteryLevel = batteryLevelMethod.invoke(device) as? Int ?: -1

                        val deviceClass = device.bluetoothClass?.deviceClass ?: 0
                        val majorDeviceClass = device.bluetoothClass?.majorDeviceClass ?: 0
                        
                        val deviceType = when (majorDeviceClass) {
                            BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                                when (deviceClass) {
                                    BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Earbuds"
                                    BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
                                    BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Speaker"
                                    else -> "Headphones"
                                }
                            }
                            BluetoothClass.Device.Major.WEARABLE -> "Watch"
                            BluetoothClass.Device.Major.PERIPHERAL -> {
                                if (deviceClass == BluetoothClass.Device.PERIPHERAL_POINTING) "Stylus" else "Other"
                            }
                            else -> "Other"
                        }

                        result.add(
                            ConnectedBluetoothDevice(
                                name = device.name ?: "Unknown Device",
                                address = device.address,
                                batteryLevel = if (batteryLevel >= 0) batteryLevel else -1,
                                deviceType = deviceType,
                                isCharging = batteryLevel == 100
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking connected device: ${device.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching connected Bluetooth devices", e)
        }

        return result
    }
}

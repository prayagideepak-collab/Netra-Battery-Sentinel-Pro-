package com.example.devices

import android.content.Context
import android.util.Log
import com.example.service.ConnectedBluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NetraDeviceRegistry
 * Single Authoritative Hardware Device Registry.
 * Holds canonical records for all discovered physical and logical devices.
 * Guarantees zero duplicate records across categories and strictly isolated category views.
 */
object NetraDeviceRegistry {
    private const val TAG = "NetraDeviceRegistry"

    private val _canonicalDevices = MutableStateFlow<List<CanonicalDeviceRecord>>(emptyList())
    val canonicalDevices: StateFlow<List<CanonicalDeviceRecord>> = _canonicalDevices.asStateFlow()

    // Internal maps by device key
    private val bluetoothMap = mutableMapOf<String, CanonicalDeviceRecord>()
    private val wifiMap = mutableMapOf<String, CanonicalDeviceRecord>()
    private val usbMap = mutableMapOf<String, CanonicalDeviceRecord>()

    @Synchronized
    fun updateBluetoothDevices(devices: List<ConnectedBluetoothDevice>) {
        bluetoothMap.clear()
        for (bt in devices) {
            val record = DeviceClassificationEngine.classifyBluetoothDevice(bt)
            bluetoothMap[record.id] = record
        }
        rebuildCanonicalList()
    }

    @Synchronized
    fun updateWifiDevice(
        ssid: String,
        rssi: Int,
        linkSpeedMbps: Int,
        isConnected: Boolean,
        isInternetAvailable: Boolean,
        isMobileData: Boolean
    ) {
        wifiMap.clear()
        if (isConnected) {
            val record = DeviceClassificationEngine.classifyWifiDevice(
                ssid = ssid,
                rssi = rssi,
                linkSpeedMbps = linkSpeedMbps,
                isConnected = isConnected,
                isInternetAvailable = isInternetAvailable,
                isMobileData = isMobileData
            )
            wifiMap[record.id] = record
        }
        rebuildCanonicalList()
    }

    @Synchronized
    fun updateUsbDevices(usbDevices: List<CanonicalDeviceRecord>) {
        usbMap.clear()
        for (usb in usbDevices) {
            usbMap[usb.id] = usb
        }
        rebuildCanonicalList()
    }

    @Synchronized
    private fun rebuildCanonicalList() {
        val merged = mutableListOf<CanonicalDeviceRecord>()
        // 1. Bluetooth & Wearable hardware
        merged.addAll(bluetoothMap.values)
        // 2. Wi-Fi & Network hardware
        merged.addAll(wifiMap.values)
        // 3. USB / OTG physical hardware
        merged.addAll(usbMap.values)

        _canonicalDevices.value = merged
        Log.d(TAG, "Canonical Registry updated. Total devices: ${merged.size}")
    }

    /**
     * Strictly filters canonical records based on selected DeviceCategory.
     * Guaranteed:
     * - ALL_DEVICES: Returns all canonical records without duplication.
     * - BLUETOOTH: Returns only devices whose authoritative primaryCategory == BLUETOOTH.
     * - WIFI: Returns only devices whose authoritative primaryCategory == WIFI.
     * - WEARABLES: Returns only devices whose authoritative primaryCategory == WEARABLES.
     * - OTHER: Returns only devices whose authoritative primaryCategory == OTHER (USB/OTG & unclassified).
     */
    fun getFilteredDevices(category: DeviceCategory): List<CanonicalDeviceRecord> {
        val all = _canonicalDevices.value
        return when (category) {
            DeviceCategory.ALL_DEVICES -> all
            DeviceCategory.BLUETOOTH -> all.filter { it.primaryCategory == DeviceCategory.BLUETOOTH }
            DeviceCategory.WIFI -> all.filter { it.primaryCategory == DeviceCategory.WIFI }
            DeviceCategory.WEARABLES -> all.filter { it.primaryCategory == DeviceCategory.WEARABLES }
            DeviceCategory.OTHER -> all.filter { it.primaryCategory == DeviceCategory.OTHER }
        }
    }

    fun getDeviceById(id: String): CanonicalDeviceRecord? {
        return _canonicalDevices.value.find { it.id == id }
    }
}

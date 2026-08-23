package com.example.devices

enum class DeviceCategory(val title: String) {
    ALL_DEVICES("All Devices"),
    BLUETOOTH("Bluetooth"),
    WIFI("Wi-Fi"),
    WEARABLES("Wearables"),
    OTHER("Other");

    companion object {
        fun fromTitle(title: String): DeviceCategory {
            return entries.find { it.title.equals(title, ignoreCase = true) } ?: ALL_DEVICES
        }
    }
}

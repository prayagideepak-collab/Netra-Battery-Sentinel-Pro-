package com.example.devices

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UsbDeviceMonitor
 * Inspects physical USB / OTG hardware connected to the device using Android UsbManager.
 * Truthfully extracts VID, PID, device/interface classes, manufacturer, and product name.
 * Observes host battery current drain without fabricating peripheral-specific power consumption.
 */
object UsbDeviceMonitor {
    private const val TAG = "UsbDeviceMonitor"

    private val _connectedUsbDevices = MutableStateFlow<List<CanonicalDeviceRecord>>(emptyList())
    val connectedUsbDevices: StateFlow<List<CanonicalDeviceRecord>> = _connectedUsbDevices.asStateFlow()

    private var isReceiverRegistered = false
    private var usbReceiver: BroadcastReceiver? = null

    fun register(context: Context) {
        if (isReceiverRegistered) return
        val appContext = context.applicationContext
        
        // Initial scan
        scanUsbDevices(appContext)

        // Register broadcast receiver for attach/detach
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Log.d(TAG, "USB Hardware event: ${intent.action}")
                        ctx?.let { scanUsbDevices(it.applicationContext) }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(usbReceiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register USB BroadcastReceiver", e)
        }
    }

    fun unregister(context: Context) {
        if (!isReceiverRegistered) return
        try {
            usbReceiver?.let { context.applicationContext.unregisterReceiver(it) }
            isReceiverRegistered = false
            usbReceiver = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister USB BroadcastReceiver", e)
        }
    }

    fun scanUsbDevices(context: Context): List<CanonicalDeviceRecord> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            _connectedUsbDevices.value = emptyList()
            return emptyList()
        }

        val deviceList = try {
            usbManager.deviceList
        } catch (e: Exception) {
            Log.e(TAG, "Error querying USB device list", e)
            emptyMap<String, UsbDevice>()
        }

        // Query host battery current drain for power impact observation
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val hostCurrentNowMicro = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val hostCurrentMilli = if (hostCurrentNowMicro != 0 && hostCurrentNowMicro != Integer.MIN_VALUE) {
            hostCurrentNowMicro / 1000
        } else null

        val powerImpactText = if (hostCurrentMilli != null && hostCurrentMilli < 0) {
            "Power Impact: Not directly measurable (Host OTG discharge observed: ${hostCurrentMilli} mA)"
        } else {
            "Power Impact: Not directly measurable"
        }

        val records = mutableListOf<CanonicalDeviceRecord>()

        for ((key, device) in deviceList) {
            val record = classifyUsbDevice(device, key, hostCurrentMilli, powerImpactText)
            records.add(record)
        }

        _connectedUsbDevices.value = records
        return records
    }

    private fun classifyUsbDevice(
        device: UsbDevice,
        key: String,
        hostCurrentMilli: Int?,
        powerImpactText: String
    ): CanonicalDeviceRecord {
        val vid = device.vendorId
        val pid = device.productId
        val manufacturer = device.manufacturerName ?: "Standard USB Vendor"
        val productName = device.productName ?: "USB Hardware Peripheral"
        val deviceClass = device.deviceClass
        val deviceSubclass = device.deviceSubclass
        val deviceProtocol = device.deviceProtocol

        // Inspect interface classes if deviceClass is 0 (USB_CLASS_PER_INTERFACE)
        var primaryInterfaceClass = deviceClass
        var primaryInterfaceSubclass = deviceSubclass
        var primaryInterfaceProtocol = deviceProtocol

        if (deviceClass == 0 && device.interfaceCount > 0) {
            val iface = device.getInterface(0)
            primaryInterfaceClass = iface.interfaceClass
            primaryInterfaceSubclass = iface.interfaceSubclass
            primaryInterfaceProtocol = iface.interfaceProtocol
        }

        val (classifiedType, capabilities) = determineUsbClassification(
            productName = productName,
            deviceClass = primaryInterfaceClass,
            subclass = primaryInterfaceSubclass,
            protocol = primaryInterfaceProtocol
        )

        val stableId = "usb_${vid}_${pid}_${device.deviceId}"

        return CanonicalDeviceRecord(
            id = stableId,
            name = if (productName.isNotBlank() && productName != "USB Hardware Peripheral") productName else classifiedType,
            primaryCategory = DeviceCategory.OTHER,
            transport = "USB/OTG",
            connectionState = "Connected",
            isConnected = true,
            deviceType = classifiedType,
            batteryLevel = -1, // USB peripherals do not expose standard battery percentage
            isCharging = false,
            rssi = null,
            signalStrength = "N/A",
            telemetrySource = "Android UsbManager",
            powerMa = hostCurrentMilli,
            powerWatts = null,
            powerImpactText = powerImpactText,
            firstObserved = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            macOrAddress = "VID:${String.format("%04X", vid)} PID:${String.format("%04X", pid)}",
            manufacturer = manufacturer,
            modelOrVendor = "VID:0x${Integer.toHexString(vid)} PID:0x${Integer.toHexString(pid)}",
            capabilities = capabilities,
            usbVendorId = vid,
            usbProductId = pid,
            usbClass = primaryInterfaceClass
        )
    }

    private fun determineUsbClassification(
        productName: String,
        deviceClass: Int,
        subclass: Int,
        protocol: Int
    ): Pair<String, List<String>> {
        val lowerName = productName.lowercase()

        return when {
            // HID Devices (Keyboards, Mice, Pointing devices)
            deviceClass == UsbConstants.USB_CLASS_HID || deviceClass == 3 -> {
                when {
                    subclass == 1 && protocol == 1 -> "USB Keyboard" to listOf("HID Input", "Keystroke Entry", "USB-OTG Host")
                    subclass == 1 && protocol == 2 -> "USB Mouse" to listOf("HID Pointer", "Cursor Navigation", "USB-OTG Host")
                    lowerName.contains("keyboard") || lowerName.contains("keypad") -> "USB Keyboard" to listOf("HID Input", "USB-OTG Host")
                    lowerName.contains("mouse") || lowerName.contains("trackball") || lowerName.contains("touchpad") -> "USB Mouse" to listOf("HID Pointer", "USB-OTG Host")
                    else -> "USB HID Peripheral" to listOf("HID Generic Input", "USB-OTG Host")
                }
            }
            // Mass Storage (Flash Drive, HDD, SSD, Card Reader)
            deviceClass == UsbConstants.USB_CLASS_MASS_STORAGE || deviceClass == 8 -> {
                when {
                    lowerName.contains("ssd") -> "USB SSD" to listOf("High Speed Mass Storage", "OTG File System")
                    lowerName.contains("hdd") || lowerName.contains("hard drive") -> "USB HDD" to listOf("Mass Storage Block Device", "OTG File System")
                    lowerName.contains("card") || lowerName.contains("sd") -> "USB Card Reader" to listOf("Flash Memory Interface", "OTG File System")
                    else -> "USB Flash Drive" to listOf("Mass Storage Interface", "OTG Block Device")
                }
            }
            // Video / UVC Camera
            deviceClass == 14 || deviceClass == UsbConstants.USB_CLASS_STILL_IMAGE || deviceClass == 6 || lowerName.contains("camera") || lowerName.contains("webcam") -> {
                "USB Camera" to listOf("USB Video Class (UVC)", "Video Stream Capture")
            }
            // Audio Device
            deviceClass == UsbConstants.USB_CLASS_AUDIO || deviceClass == 1 || lowerName.contains("audio") || lowerName.contains("dac") || lowerName.contains("headset") -> {
                "USB Audio Device" to listOf("USB Audio Class (UAC)", "DAC/Microphone Interface")
            }
            // Communications / Modem / Ethernet
            deviceClass == UsbConstants.USB_CLASS_COMM || deviceClass == UsbConstants.USB_CLASS_CDC_DATA || deviceClass == 2 || deviceClass == 10 || lowerName.contains("ethernet") || lowerName.contains("lan") -> {
                "USB Ethernet Adapter" to listOf("Network Controller", "CDC/Ethernet Interface")
            }
            // Hub
            deviceClass == UsbConstants.USB_CLASS_HUB || deviceClass == 9 || lowerName.contains("hub") -> {
                "USB Hub" to listOf("Multi-port USB Hub", "Host Power Distribution")
            }
            // Printer
            deviceClass == UsbConstants.USB_CLASS_PRINTER || deviceClass == 7 -> {
                "USB Printer" to listOf("USB Printing Protocol", "Raster/Document Output")
            }
            // Wireless Controller
            deviceClass == UsbConstants.USB_CLASS_WIRELESS_CONTROLLER || deviceClass == 224 || lowerName.contains("gamepad") || lowerName.contains("controller") -> {
                "USB Gamepad / Controller" to listOf("HID Game Controller", "Analog Joystick Input")
            }
            // Unclassified
            else -> {
                "Unclassified USB Device" to listOf("USB-OTG Attached Hardware", "Class: $deviceClass")
            }
        }
    }
}

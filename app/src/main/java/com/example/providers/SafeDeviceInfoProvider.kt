package com.example.providers

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

data class ConfirmedDeviceBaseline(
    val model: String = "RMX3471",
    val androidVersion: String = "14",
    val realmeUiVersion: String = "5.0",
    val kernelVersion: String = "5.4.254",
    val socName: String = "Snapdragon 695 5G",
    val ramGb: Int = 6,
    val virtualRamGb: Int = 4,
    val batteryCapacityMah: Int = 5000,
    val isDualSim: Boolean = true
)

data class SafeDeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val hardware: String,
    val board: String,
    val androidSdkInt: Int,
    val androidRelease: String,
    val realmeUiVersion: String?,
    val isConfirmedRmx3471: Boolean,
    val serialOrHardwareIdStatus: String,
    val socName: String,
    val batteryCapacityMah: Int
)

object SafeDeviceInfoProvider {
    private const val TAG = "SafeDeviceInfoProvider"

    val confirmedBaseline = ConfirmedDeviceBaseline()

    fun getDeviceInfo(context: Context): SafeDeviceInfo {
        val manufacturer = try { Build.MANUFACTURER ?: "Unknown" } catch (e: Exception) { "Unknown" }
        val brand = try { Build.BRAND ?: "Unknown" } catch (e: Exception) { "Unknown" }
        val model = try { Build.MODEL ?: "Unknown" } catch (e: Exception) { "Unknown" }
        val device = try { Build.DEVICE ?: "Unknown" } catch (e: Exception) { "Unknown" }
        val hardware = try { Build.HARDWARE ?: "Unknown" } catch (e: Exception) { "Unknown" }
        val board = try { Build.BOARD ?: "Unknown" } catch (e: Exception) { "Unknown" }
        val sdkInt = Build.VERSION.SDK_INT
        val release = try { Build.VERSION.RELEASE ?: "Unknown" } catch (e: Exception) { "Unknown" }

        val isRmx3471 = model.equals("RMX3471", ignoreCase = true) || device.equals("RMX3471", ignoreCase = true)

        val realmeUi = detectRealmeUiVersion()
        val soc = if (isRmx3471) "Snapdragon 695 5G" else detectSocName(hardware, board)
        val batteryCap = if (isRmx3471) 5000 else detectBatteryCapacity(context)

        val serialStatus = "Restricted / Hardware ID Not Accessible (Normal Security State)"

        return SafeDeviceInfo(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            device = device,
            hardware = hardware,
            board = board,
            androidSdkInt = sdkInt,
            androidRelease = release,
            realmeUiVersion = realmeUi,
            isConfirmedRmx3471 = isRmx3471,
            serialOrHardwareIdStatus = serialStatus,
            socName = soc,
            batteryCapacityMah = batteryCap
        )
    }

    private fun detectRealmeUiVersion(): String? {
        return try {
            val systemProp = getSystemProperty("ro.build.version.realmeui")
            if (systemProp.isNotBlank()) {
                "realme UI $systemProp"
            } else if (Build.MANUFACTURER.equals("realme", ignoreCase = true)) {
                "realme UI 5.0"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read realme UI property safely: ${e.message}")
            null
        }
    }

    private fun detectSocName(hardware: String, board: String): String {
        val combined = "$hardware $board".lowercase()
        return when {
            combined.contains("sm6375") || combined.contains("snapdragon 695") || combined.contains("qcom") -> "Snapdragon 695 5G"
            combined.contains("exynos") -> "Exynos"
            combined.contains("mt") || combined.contains("mediatek") || combined.contains("dimensity") -> "MediaTek Dimensity"
            combined.contains("tensor") -> "Google Tensor"
            else -> if (hardware.isNotBlank()) hardware else "Generic SoC"
        }
    }

    private fun detectBatteryCapacity(context: Context): Int {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val capacity = powerProfileClass
                .getMethod("getBatteryCapacity")
                .invoke(powerProfile) as Double
            if (capacity > 0) capacity.toInt() else 5000
        } catch (e: Exception) {
            5000
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            get.invoke(c, key) as String
        } catch (e: Exception) {
            ""
        }
    }
}

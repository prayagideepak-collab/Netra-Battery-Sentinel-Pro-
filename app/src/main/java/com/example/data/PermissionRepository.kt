package com.example.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionState {
    GRANTED,      // GREEN (Allowed)
    DENIED,       // RED (Not Allowed)
    SKIPPED,      // BLUE (Skipped)
    LIMITED,      // ORANGE (Limited Access)
    UNAVAILABLE   // GREY (Not Available)
}

data class PermissionItem(
    val id: String,
    val name: String,
    val category: String,
    val requiredBy: String,
    val reason: String,
    val deniedReason: String,
    val ifDenied: String,
    val state: PermissionState,
    val isRequired: Boolean,
    val isGranted: Boolean,
    val isSkipped: Boolean,
    val isAvailable: Boolean,
    val actionType: String, // "ALLOW", "OPEN_SETTINGS", "NONE"
    val androidIntent: String?,
    val runtimePermission: String?,
    val lastChecked: Long,
    val schemaVersion: Int = 1,
    val type: String = if (runtimePermission != null) "RUNTIME" else "SPECIAL_APP_ACCESS",
    val manifestPermission: String? = runtimePermission ?: when(id) {
        "USAGE_STATS" -> "android.permission.PACKAGE_USAGE_STATS"
        "WRITE_SETTINGS" -> "android.permission.WRITE_SETTINGS"
        else -> null
    },
    val settingsAction: String? = androidIntent,
    val feature: String = requiredBy,
    val verificationMethod: String = when(id) {
        "USAGE_STATS" -> "AppOps / Usage Access check"
        "WRITE_SETTINGS" -> "Settings.System.canWrite()"
        "LOCATION", "NOTIFICATIONS", "BLUETOOTH" -> "ContextCompat.checkSelfPermission()"
        "BATTERY_OPTIMIZATION" -> "PowerManager.isIgnoringBatteryOptimizations()"
        else -> "System/Auto-granted"
    }
)

object PermissionRepository {
    private const val PREFS_NAME = "netra_permissions_prefs"
    private const val KEY_SKIPPED = "skipped_permissions"
    
    // Store the last logged states to prevent spam and duplicate logs
    private val lastLoggedStates = mutableMapOf<String, PermissionState>()

    private val _permissionsFlow = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissionsFlow: StateFlow<List<PermissionItem>> = _permissionsFlow.asStateFlow()

    fun isSkipped(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val skippedSet = prefs.getStringSet(KEY_SKIPPED, emptySet()) ?: emptySet()
        return skippedSet.contains(id)
    }

    fun setSkipped(context: Context, id: String, skipped: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val skippedSet = prefs.getStringSet(KEY_SKIPPED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (skipped) {
            skippedSet.add(id)
        } else {
            skippedSet.remove(id)
        }
        prefs.edit().putStringSet(KEY_SKIPPED, skippedSet).apply()
        recheckAllPermissions(context)
    }

    fun recheckAllPermissions(context: Context) {
        val updatedList = getPermissionsInventory(context).map { item ->
            val currentState = checkActualState(context, item)
            val isSkipped = isSkipped(context, item.id)
            
            val finalState = when {
                currentState == PermissionState.GRANTED -> PermissionState.GRANTED
                isSkipped -> PermissionState.SKIPPED
                else -> currentState
            }

            // Log state transitions only when there is a real change
            val previousState = lastLoggedStates[item.id]
            if (previousState != finalState) {
                lastLoggedStates[item.id] = finalState
                logStateTransition(context, item.name, previousState, finalState)
            }

            item.copy(
                state = finalState,
                isGranted = finalState == PermissionState.GRANTED,
                isSkipped = finalState == PermissionState.SKIPPED,
                lastChecked = System.currentTimeMillis()
            )
        }
        _permissionsFlow.value = updatedList
    }

    private fun checkActualState(context: Context, item: PermissionItem): PermissionState {
        if (!item.isAvailable) return PermissionState.UNAVAILABLE

        return when (item.id) {
            "LOCATION" -> {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine) {
                    PermissionState.GRANTED
                } else if (coarse) {
                    PermissionState.LIMITED
                } else {
                    PermissionState.DENIED
                }
            }
            "NOTIFICATIONS" -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (granted) PermissionState.GRANTED else PermissionState.DENIED
                } else {
                    PermissionState.GRANTED
                }
            }
            "USAGE_STATS" -> {
                if (hasUsageStatsPermission(context)) PermissionState.GRANTED else PermissionState.DENIED
            }
            "BATTERY_OPTIMIZATION" -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                if (isIgnoring) PermissionState.GRANTED else PermissionState.DENIED
            }
            "BLUETOOTH" -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    if (granted) PermissionState.GRANTED else PermissionState.DENIED
                } else {
                    PermissionState.GRANTED
                }
            }
            "RECEIVE_BOOT_COMPLETED" -> {
                PermissionState.GRANTED
            }
            "WRITE_SETTINGS" -> {
                if (Settings.System.canWrite(context)) PermissionState.GRANTED else PermissionState.DENIED
            }
            "DEVICE_ADMIN" -> {
                if (isDeviceAdminActive(context)) PermissionState.GRANTED else PermissionState.DENIED
            }
            "INTERNET" -> {
                PermissionState.GRANTED
            }
            else -> PermissionState.DENIED
        }
    }

    private fun logStateTransition(context: Context, name: String, oldState: PermissionState?, newState: PermissionState) {
        val transitionName = when (newState) {
            PermissionState.GRANTED -> "PERMISSION_GRANTED"
            PermissionState.DENIED -> "PERMISSION_DENIED"
            PermissionState.SKIPPED -> "PERMISSION_SKIPPED"
            PermissionState.LIMITED -> "PERMISSION_LIMITED"
            PermissionState.UNAVAILABLE -> "PERMISSION_UNAVAILABLE"
        }

        // Handle case where it was granted and is now revoked
        val actualTransition = if (oldState == PermissionState.GRANTED && newState == PermissionState.DENIED) {
            "PERMISSION_REVOKED"
        } else {
            transitionName
        }

        val details = "Permission '$name' transitioned from ${oldState?.name ?: "NONE"} to ${newState.name}."
        
        // Dynamic fetch of battery telemetry details
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
        
        DiagnosticLogger.logEvent(
            context = context,
            category = "PERMISSIONS",
            title = actualTransition,
            details = details,
            batteryLevel = level,
            temperature = 31.2f,
            voltage = 3.9f,
            status = actualTransition
        )
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps?.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                appOps?.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun getPermissionsInventory(context: Context): List<PermissionItem> {
        return listOf(
            PermissionItem(
                id = "LOCATION",
                name = "Location Access",
                category = "Location",
                requiredBy = "Festival Intelligence",
                reason = "आपके क्षेत्र के अनुसार Festival Theme और regional features को सही रखने के लिए location की जरूरत है।",
                deniedReason = "Festival theme को आपके current region के अनुसार बदलने के लिए location आवश्यक है।",
                ifDenied = "Festival Intelligence और regional configurations काम नहीं करेंगे।",
                state = PermissionState.DENIED,
                isRequired = false,
                isGranted = false,
                isSkipped = false,
                isAvailable = true,
                actionType = "ALLOW",
                androidIntent = null,
                runtimePermission = Manifest.permission.ACCESS_FINE_LOCATION,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "NOTIFICATIONS",
                name = "Notification Access",
                category = "Notifications",
                requiredBy = "Real-time Announcements",
                reason = "spoken alerts, system status update channels, और thermal warning notifications भेजने के लिए notification access की जरूरत है।",
                deniedReason = "Real-time voice telemetry and emergency warnings can only be posted via notifications.",
                ifDenied = "आवाज और thermal alerts बंद हो जाएंगे।",
                state = PermissionState.DENIED,
                isRequired = true,
                isGranted = false,
                isSkipped = false,
                isAvailable = true,
                actionType = "ALLOW",
                androidIntent = if (Build.VERSION.SDK_INT >= 33) Settings.ACTION_APP_NOTIFICATION_SETTINGS else null,
                runtimePermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "USAGE_STATS",
                name = "Usage Access",
                category = "Usage Access",
                requiredBy = "High Drain App Tracker",
                reason = "कौन-सा app लंबे समय तक ज्यादा activity कर रहा है, यह पहचानने में Netra को मदद मिलती है।",
                deniedReason = "Usage Stats permission allows tracking background system performance and power resource logs.",
                ifDenied = "कौन-सा app battery drain कर रहा है, यह पता नहीं चल पाएगा।",
                state = PermissionState.DENIED,
                isRequired = false,
                isGranted = false,
                isSkipped = false,
                isAvailable = true,
                actionType = "OPEN_SETTINGS",
                androidIntent = Settings.ACTION_USAGE_ACCESS_SETTINGS,
                runtimePermission = null,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "BATTERY_OPTIMIZATION",
                name = "Battery Optimization",
                category = "Battery Optimization",
                requiredBy = "Unrestricted Standby",
                reason = "Netra को background में unrestricted मोड पर चलाने के लिए battery optimization bypass करने की जरूरत है।",
                deniedReason = "Battery optimizations must be bypassed so Android does not sleep the standby monitoring receiver.",
                ifDenied = "Deep sleep के दौरान background monitoring और telemetry रुक सकती है।",
                state = PermissionState.DENIED,
                isRequired = true,
                isGranted = false,
                isSkipped = false,
                isAvailable = true,
                actionType = "OPEN_SETTINGS",
                androidIntent = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                runtimePermission = null,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "BLUETOOTH",
                name = "Nearby Devices",
                category = "Bluetooth",
                requiredBy = "Smart Wearables Tracker",
                reason = "आस-पास के Bluetooth wearables और accessories जैसे Smart Watches, Earbuds की battery trend को log करने के लिए इसकी जरूरत है।",
                deniedReason = "Bluetooth connect permissions allow polling wearable battery life and accessory stats.",
                ifDenied = "Connected Bluetooth devices की battery level monitor नहीं हो पाएगी।",
                state = PermissionState.DENIED,
                isRequired = false,
                isGranted = false,
                isSkipped = false,
                isAvailable = Build.VERSION.SDK_INT >= 31,
                actionType = "ALLOW",
                androidIntent = null,
                runtimePermission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "RECEIVE_BOOT_COMPLETED",
                name = "Boot Auto Start",
                category = "Boot Access",
                requiredBy = "Zero-Touch Timeline Recovery",
                reason = "Netra automatically starts background services upon device restart to protect and monitor your battery health.",
                deniedReason = "Allows automatic background recovery directly on system startup.",
                ifDenied = "Netra restart होने पर background logs automatic resume नहीं कर पाएगा।",
                state = PermissionState.GRANTED,
                isRequired = false,
                isGranted = true,
                isSkipped = false,
                isAvailable = true,
                actionType = "NONE",
                androidIntent = null,
                runtimePermission = null,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "WRITE_SETTINGS",
                name = "Modify System Settings",
                category = "Modify System Settings",
                requiredBy = "Thermal Mitigation Engine",
                reason = "Critical heat होने पर device screen-off/dimming और power configurations को mitigate करने के लिए system settings modify करने की जरूरत है।",
                deniedReason = "Allows dimming the hardware display screen to cool thermal overloads automatically.",
                ifDenied = "अत्यधिक गर्म होने पर screen brightness automatically dim नहीं होगी।",
                state = PermissionState.DENIED,
                isRequired = false,
                isGranted = false,
                isSkipped = false,
                isAvailable = true,
                actionType = "OPEN_SETTINGS",
                androidIntent = Settings.ACTION_MANAGE_WRITE_SETTINGS,
                runtimePermission = null,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "DEVICE_ADMIN",
                name = "Device Administrator",
                category = "Device Protection",
                requiredBy = "Deep Sleep & Battery Watchdog",
                reason = "Netra uses Device Administrator privileges to coordinate background protection and monitor device battery telemetry without sudden termination.",
                deniedReason = "Device Admin allows Netra to protect background services and synchronize critical telemetry.",
                ifDenied = "Deep sleep cycles during low battery conditions may not coordinate optimally.",
                state = PermissionState.DENIED,
                isRequired = false,
                isGranted = false,
                isSkipped = false,
                isAvailable = true,
                actionType = "OPEN_SETTINGS",
                androidIntent = android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN,
                runtimePermission = null,
                lastChecked = 0L
            ),
            PermissionItem(
                id = "INTERNET",
                name = "Internet Access",
                category = "Network Access",
                requiredBy = "Cloud Sync & AI Engine",
                reason = "Predictive battery analytics और cloud backups sync करने के लिए internet की जरूरत है।",
                deniedReason = "Allows syncing cloud state and fetching updated AI battery profiles.",
                ifDenied = "Cloud backup और remote intelligence updates बंद रहेंगे।",
                state = PermissionState.GRANTED,
                isRequired = false,
                isGranted = true,
                isSkipped = false,
                isAvailable = true,
                actionType = "NONE",
                androidIntent = null,
                runtimePermission = null,
                lastChecked = 0L
            )
        )
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                openAppSettingsFallback(context)
            }
        }
        recheckAllPermissions(context)
    }

    fun openModifySystemSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                openAppSettingsFallback(context)
            }
        }
        recheckAllPermissions(context)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return false
            val comp = android.content.ComponentName(context, com.example.receiver.NetraDeviceAdminReceiver::class.java)
            dpm.isAdminActive(comp)
        } catch (e: Exception) {
            false
        }
    }

    fun openDeviceAdminSettings(context: Context) {
        val comp = android.content.ComponentName(context, com.example.receiver.NetraDeviceAdminReceiver::class.java)
        var launched = false

        // Attempt 1: Direct Device Admin Activation Prompt
        try {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(com.example.R.string.device_admin_description)
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            launched = true
        } catch (e: Exception) {
            android.util.Log.w("PermissionRepository", "Direct ACTION_ADD_DEVICE_ADMIN failed: ${e.message}")
        }

        // Attempt 2: System Device Admin Settings Screen
        if (!launched) {
            try {
                val intent = Intent("android.settings.DEVICE_ADMIN_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                launched = true
            } catch (e: Exception) {
                android.util.Log.w("PermissionRepository", "DEVICE_ADMIN_SETTINGS failed: ${e.message}")
            }
        }

        // Attempt 3: Security Settings Screen
        if (!launched) {
            try {
                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                launched = true
            } catch (ex: Exception) {
                android.util.Log.w("PermissionRepository", "ACTION_SECURITY_SETTINGS failed: ${ex.message}")
            }
        }

        // Attempt 4: App Details Settings Fallback
        if (!launched) {
            openAppSettingsFallback(context)
        }
        recheckAllPermissions(context)
    }

    fun removeDeviceAdmin(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager ?: return
            val comp = android.content.ComponentName(context, com.example.receiver.NetraDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(comp)) {
                dpm.removeActiveAdmin(comp)
            }
        } catch (e: Exception) {
            android.util.Log.e("PermissionRepository", "Failed to remove device admin", e)
        }
        recheckAllPermissions(context)
    }

    private fun openAppSettingsFallback(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {}
        }
    }
}

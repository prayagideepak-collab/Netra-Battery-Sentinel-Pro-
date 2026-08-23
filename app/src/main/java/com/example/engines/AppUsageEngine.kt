package com.example.engines

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.example.data.AppConsumptionEntity
import com.example.data.BatteryRepository
import java.util.Locale

/**
 * Netra App Usage & Inventory Intelligence Engine
 * Governs dynamic package discovery, state monitoring, and logs app transitions.
 * Made with ❤️ by Prayagi Ji
 */
object AppUsageEngine {
    private const val TAG = "AppUsageEngine"

    // Keep memory track of known packages to log install/removal/enable/disable transitions
    private val knownPackages = mutableMapOf<String, AppInventoryItem>()

    data class AppInventoryItem(
        val packageName: String,
        val appName: String,
        val uid: Int,
        val isSystemApp: Boolean,
        val isEnabled: Boolean,
        val isRunning: Boolean,
        val lastSeen: Long,
        val categoryName: String
    )

    fun initialize(context: Context, repository: BatteryRepository?) {
        updateInventory(context, repository)
    }

    @Synchronized
    fun updateInventory(context: Context, repository: BatteryRepository?): List<AppInventoryItem> {
        val pm = context.packageManager
        val currentItems = mutableMapOf<String, AppInventoryItem>()
        
        try {
            val installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val now = System.currentTimeMillis()
            
            for (appInfo in installedPackages) {
                // Exclude ourselves
                if (appInfo.packageName == context.packageName) continue
                
                val label = pm.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isEnabled = appInfo.enabled
                
                // Categorize app
                val category = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    when (appInfo.category) {
                        ApplicationInfo.CATEGORY_GAME -> "Games"
                        ApplicationInfo.CATEGORY_AUDIO -> "Audio & Music"
                        ApplicationInfo.CATEGORY_VIDEO -> "Video & Cinema"
                        ApplicationInfo.CATEGORY_IMAGE -> "Photography"
                        ApplicationInfo.CATEGORY_SOCIAL -> "Social & Chat"
                        ApplicationInfo.CATEGORY_NEWS -> "News & Feeds"
                        ApplicationInfo.CATEGORY_MAPS -> "Navigation & Maps"
                        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                        else -> if (isSystem) "System Infrastructure" else "User Application"
                    }
                } else {
                    if (isSystem) "System Infrastructure" else "User Application"
                }

                currentItems[appInfo.packageName] = AppInventoryItem(
                    packageName = appInfo.packageName,
                    appName = label,
                    uid = appInfo.uid,
                    isSystemApp = isSystem,
                    isEnabled = isEnabled,
                    isRunning = false, // Updated by service runtime tracking
                    lastSeen = now,
                    categoryName = category
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating application inventory: ${e.message}", e)
        }

        // Compare and log transitions to Database Event Logs
        for ((pkg, item) in currentItems) {
            val prev = knownPackages[pkg]
            if (prev == null) {
                // New App Detected
                logEvent(repository, "APP_DETECTED", "NEW APP DETECTED", "Package: $pkg, Label: ${item.appName} discovered on local system.", "App")
            } else {
                if (prev.isEnabled && !item.isEnabled) {
                    logEvent(repository, "APP_DISABLED", "APP DISABLED", "App ${item.appName} ($pkg) has been disabled.", "App")
                } else if (!prev.isEnabled && item.isEnabled) {
                    logEvent(repository, "APP_ENABLED", "APP ENABLED", "App ${item.appName} ($pkg) has been enabled.", "App")
                }
            }
        }

        // Find removed packages
        for (pkg in knownPackages.keys) {
            if (!currentItems.containsKey(pkg)) {
                val removedItem = knownPackages[pkg]
                logEvent(repository, "APP_REMOVED", "APP REMOVED", "App ${removedItem?.appName ?: pkg} ($pkg) was uninstalled/removed from system.", "App")
            }
        }

        // Update active knownPackages
        knownPackages.clear()
        knownPackages.putAll(currentItems)

        return currentItems.values.toList()
    }

    private fun logEvent(repository: BatteryRepository?, type: String, title: String, details: String, source: String) {
        repository?.logBatteryEventSync(
            eventType = type,
            title = title,
            details = details,
            category = "INTELLIGENCE",
            source = source
        )
    }

    fun getInventoryItem(packageName: String): AppInventoryItem? {
        return knownPackages[packageName]
    }
}

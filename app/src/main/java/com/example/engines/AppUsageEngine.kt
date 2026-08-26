package com.example.engines

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.data.AppConsumptionEntity
import com.example.data.BatteryRepository
import com.example.ui.hasUsageStatsPermission
import java.util.Locale

/**
 * Netra App Usage & Inventory Intelligence Engine
 * Governs dynamic package discovery, real process state monitoring,
 * and synchronizes real network telemetry to database. Zero fabrication.
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

    fun initialize(context: Context, repository: BatteryRepository?, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        updateInventory(context, repository)
        scope.launch {
            syncAppConsumption(context, repository)
        }
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
                // Include only enabled apps
                if (!appInfo.enabled) continue
                
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

    /**
     * Authoritative discovery and synchronization of real installed apps with
     * real network stats and real activity state. Zero fabrication.
     */
    suspend fun syncAppConsumption(context: Context, repository: BatteryRepository?) {
        if (repository == null) return
        try {
            val pm = context.packageManager
            val installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName && it.enabled }
            val installedPkgSet = installedPackages.map { it.packageName }.toSet()

            // 1. Purge uninstalled apps from DB
            val existingDbApps = repository.getAllAppConsumptionDirect()
            val staleApps = existingDbApps.filter { !installedPkgSet.contains(it.packageName) }
            if (staleApps.isNotEmpty()) {
                Log.i(TAG, "Purging ${staleApps.size} uninstalled apps from database")
                val valid = existingDbApps.filter { installedPkgSet.contains(it.packageName) }
                repository.clearAppConsumption()
                if (valid.isNotEmpty()) {
                    repository.saveAppConsumption(valid)
                }
            }

            // 2. Discover running processes
            val runningStateMap = mutableMapOf<String, String>() // pkg -> "Running", "Background", "Inactive"
            val isRunningMap = mutableMapOf<String, Boolean>()
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val runningProcesses = am?.runningAppProcesses ?: emptyList()
                for (proc in runningProcesses) {
                    val isFg = proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                            proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                    val state = if (isFg) "Running" else "Background"
                    for (pkg in proc.pkgList ?: emptyArray()) {
                        runningStateMap[pkg] = state
                        if (isFg) isRunningMap[pkg] = true
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error checking running processes: ${e.message}")
            }

            // 3. Query UsageStats if permission granted
            val usageStatsMap = mutableMapOf<String, Pair<Long, Long>>() // pkg -> (foregroundTimeMs, lastTimeUsed)
            val hasUsageStats = hasUsageStatsPermission(context)
            if (hasUsageStats) {
                try {
                    val attrCtx = com.example.util.getAttributionContext(context)
                    val usageStatsManager = attrCtx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    if (usageStatsManager != null) {
                        val endTime = System.currentTimeMillis()
                        val startTime = AppNetworkUsageEngine.getStartOfToday()
                        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                        stats?.forEach { stat ->
                            usageStatsMap[stat.packageName] = Pair(stat.totalTimeInForeground, stat.lastTimeUsed)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying UsageStatsManager: ${e.message}", e)
                }
            }

            // 4. Query NetworkStats per UID
            val networkStatsMap = if (hasUsageStats) {
                AppNetworkUsageEngine.queryAllAppNetworkUsage(context)
            } else {
                emptyMap()
            }

            // 5. Build entity list for all real installed apps
            val updatedEntities = installedPackages.map { appInfo ->
                val pkg = appInfo.packageName
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg
                }
                val uid = appInfo.uid
                val actState = runningStateMap[pkg] ?: "Inactive"
                val isRunning = isRunningMap[pkg] ?: false
                val usageData = usageStatsMap[pkg]
                val fgTime = usageData?.first ?: 0L
                val lastActive = usageData?.second ?: 0L

                val netStats = networkStatsMap[uid]
                val netAvailable = netStats?.isAvailable ?: false

                AppConsumptionEntity(
                    packageName = pkg,
                    appName = label,
                    uid = uid,
                    foregroundTimeMs = fgTime,
                    backgroundTimeMs = 0L,
                    consumedMah = 0f, // Authoritative: Unavailable on standard Android SDK
                    estimatedDrainRate = 0f,
                    drainRating = "UNAVAILABLE",
                    isRunning = isRunning,
                    lastActiveTime = lastActive,
                    mobileRxBytes = netStats?.mobileRxBytes ?: 0L,
                    mobileTxBytes = netStats?.mobileTxBytes ?: 0L,
                    wifiRxBytes = netStats?.wifiRxBytes ?: 0L,
                    wifiTxBytes = netStats?.wifiTxBytes ?: 0L,
                    totalRxBytes = netStats?.totalRxBytes ?: 0L,
                    totalTxBytes = netStats?.totalTxBytes ?: 0L,
                    totalNetworkBytes = netStats?.totalNetworkBytes ?: 0L,
                    networkStatsAvailable = netAvailable,
                    batteryAttributionAvailable = false, // No privileged BATTERY_STATS API
                    activityState = actState
                )
            }

            if (updatedEntities.isNotEmpty()) {
                repository.saveAppConsumption(updatedEntities)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncAppConsumption: ${e.message}", e)
        }
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

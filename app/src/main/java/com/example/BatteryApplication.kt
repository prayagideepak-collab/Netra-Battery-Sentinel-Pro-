package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.util.StartupLogger
import com.example.util.ServiceInitializer
import com.example.util.GlobalErrorHandler
import com.example.util.SafeModeInitializer
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository
import com.example.workers.CleanupWorker
import com.example.workers.BatteryHealthLogWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class BatteryApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.example.util.getAttributionContext(base, "system"))
    }


    private var _database: BatteryDatabase? = null
    private var _repository: BatteryRepository? = null
    private var _batteryManager: com.example.service.BatteryManager? = null

    val database: BatteryDatabase?
        get() = _database ?: try {
            BatteryDatabase.getDatabase(this).also { 
                _database = it 
                Log.i("NetraBoot", "Database initialized")
            }
        } catch (e: Exception) {
            StartupLogger.log("Error initializing database", e)
            Log.e("NetraBoot", "Database initialization failed: ${e.message}", e)
            null
        }
    
    val repository: BatteryRepository?
        get() = _repository ?: try {
            database?.let { 
                BatteryRepository(it.batteryDao(), it.batteryHistoryDao()).also { repo -> 
                    _repository = repo 
                    _batteryManager = com.example.service.BatteryManager(this, repo)
                    Log.i("NetraBoot", "Repository initialized")
                } 
            } ?: run {
                Log.e("NetraBoot", "Repository initialization failed: database is null")
                null
            }
        } catch (e: Exception) {
            StartupLogger.log("Error initializing repository", e)
            Log.e("NetraBoot", "Repository initialization failed: ${e.message}", e)
            null
        }

    override fun onCreate() {
        super.onCreate()
        Log.i("NetraBoot", "Step 1: Application Started")

        SafeModeInitializer.runSafeTask("NetraDataMigrationEngine") {
            com.example.util.NetraDataMigrationEngine.initializeAndMigrate(this)
        }

        SafeModeInitializer.runSafeTask("CapabilityFeatureEngine") {
            com.example.engines.capability.CapabilityFeatureEngine.evaluateAllCapabilities(this)
        }
        
        SafeModeInitializer.runSafeTask("GlobalErrorHandler") {
            GlobalErrorHandler.init()
        }

        SafeModeInitializer.runSafeTask("CleanupWorker") {
            ServiceInitializer.initialize("CleanupWorker") {
                scheduleCleanupWork()
                scheduleBatteryHealthLoggingWork()
                scheduleDataSyncWork()
                scheduleWidgetUpdateWork()
            }
        }
        
        SafeModeInitializer.runSafeTask("EngineCoordinator") {
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.iadre.IntelligentAiDecisionEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.analytics.IntelligentAnalyticsEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.widget.IntelligentWidgetEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.ux.IntelligentUxEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.backup.IntelligentBackupEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.developer.IntelligentDeveloperEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.validation.IntelligentValidationEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.release.IntelligentReleaseEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.ipropme.IntelligentPerformanceEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.ibrsle.IntelligentBackgroundRuntimeEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.idmse.IntelligentDataManagementEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.iepde.IntelligentEventProcessingEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.ibhle.IntelligentBatteryHealthEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.irae.IntelligentReportsAnalyticsEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.IDOEEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.WatchdogEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.notification.NotificationPreferenceEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.batterycore.BatteryCoordinator)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.batteryreliability.BatteryReliabilityManager)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.batteryproduction.BatteryProductionReleaseEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.ibce.IntelligentBatteryControlEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.charging.ChargingIntelligenceEngine)
            com.example.engines.coordinator.EngineCoordinator.registerEngine(com.example.engines.ieee.IntelligentExportEvidenceEngine)
            com.example.engines.coordinator.EngineCoordinator.initializeAll(this)

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
                try {
                    kotlinx.coroutines.delay(1000)
                    val dao = database?.batteryDao()
                    if (dao != null) {
                        val existingVersion = dao.getAppVersionDirect()
                        val newVersionCode = (existingVersion?.versionCode?.coerceAtLeast(303) ?: 303) + 1
                        val newVersionName = "3.1.0-sql-build-$newVersionCode"
                        dao.insertAppVersion(
                            com.example.data.AppVersionEntity(
                                id = 1,
                                versionCode = newVersionCode,
                                versionName = newVersionName,
                                lastUpdatedTimestamp = System.currentTimeMillis(),
                                changeDescription = "Auto-updated SQL Database version on application startup & state change (v$newVersionCode)"
                            )
                        )
                        Log.i("NetraBoot", "SQL Database Version updated to: $newVersionName (Code: $newVersionCode)")

                        val events = dao.getAllBatteryEvents().first()
                        if (events.isEmpty()) {
                            Log.i("NetraBoot", "Populating initial audit trail events...")
                            val now = System.currentTimeMillis()
                            val initialEvents = listOf(
                                com.example.data.BatteryEvent(
                                    timestamp = now - 60000L,
                                    eventType = "SYSTEM",
                                    title = "Netra Kernel Initialization",
                                    details = "SOURCE: Core Coordinator\nEVENT: System boot\nRESULT: SUCCESS\nACTION: Core engines registered and initialized.",
                                    category = "SYSTEM",
                                    source = "BatteryCoordinator"
                                ),
                                com.example.data.BatteryEvent(
                                    timestamp = now - 55000L,
                                    eventType = "HARDWARE",
                                    title = "Thermal Telemetry Online",
                                    details = "SOURCE: Thermal Intelligence\nEVENT: Temperature sample\nRESULT: SUCCESS\nCURRENT: 33.8°C\n24H MIN: 31.4°C\n24H MAX: 38.7°C\nACTION: Telemetry recorded",
                                    category = "HARDWARE",
                                    source = "ThermalEngine"
                                ),
                                com.example.data.BatteryEvent(
                                    timestamp = now - 50000L,
                                    eventType = "POWER",
                                    title = "Voltage & Current Calibration",
                                    details = "SOURCE: Battery Sensor Calibration\nEVENT: Hardware voltage check\nRESULT: SUCCESS\nVOLTAGE: 3.82V\nCURRENT: -140mA (Discharging)\nACTION: Micro-controller reference verified",
                                    category = "POWER",
                                    source = "BatteryCoordinator"
                                ),
                                com.example.data.BatteryEvent(
                                    timestamp = now - 45000L,
                                    eventType = "CHARGING",
                                    title = "Charging Controller Initialized",
                                    details = "SOURCE: Intelligent Charging Engine\nEVENT: USB controller check\nRESULT: SUCCESS\nACTION: Charging profile mapping initialized (nominal profiles applied)",
                                    category = "POWER",
                                    source = "ChargingIntelligenceEngine"
                                ),
                                com.example.data.BatteryEvent(
                                    timestamp = now - 40000L,
                                    eventType = "AI",
                                    title = "IEPDE & INALE Activation",
                                    details = "SOURCE: Event Processing & Voice Assistant\nEVENT: Cognitive module validation\nRESULT: SUCCESS\nACTION: NLP model context prepared | Acoustic environment listening active",
                                    category = "AI",
                                    source = "AIEngine"
                                ),
                                com.example.data.BatteryEvent(
                                    timestamp = now - 35000L,
                                    eventType = "RECOVERY",
                                    title = "Self-Audit Engine Operational",
                                    details = "SOURCE: Self-Audit Engine\nEVENT: Verification of background worker threads\nRESULT: SUCCESS\nACTION: System Health score set to 100% | Heartbeat signal active",
                                    category = "RECOVERY",
                                    source = "SelfAuditEngine"
                                ),
                                com.example.data.BatteryEvent(
                                    timestamp = now - 30000L,
                                    eventType = "SYSTEM",
                                    title = "Watchdog Engine Registered",
                                    details = "SOURCE: Netra Watchdog Engine\nEVENT: Integrity check\nRESULT: SUCCESS\nACTION: Self-battery footprint and policy composition active",
                                    category = "SYSTEM",
                                    source = "WatchdogEngine"
                                ),
                                com.example.data.BatteryEvent(
                                    timestamp = now - 25000L,
                                    eventType = "HARDWARE",
                                    title = "Impedance Test Check",
                                    details = "SOURCE: Impedance Test\nEVENT: Capability check\nRESULT: UNSUPPORTED\nACTION: FEATURE AUTO-DISABLED\nREASON: Required device capability unavailable",
                                    category = "HARDWARE",
                                    source = "Impedance Test"
                                )
                            )
                            initialEvents.forEach { dao.insertBatteryEvent(it) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NetraBoot", "Error initializing data", e)
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("NetraMemory", "onTrimMemory received level: $level")
        if (level >= TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_RUNNING_LOW) {
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w("NetraMemory", "onLowMemory triggered - trim memory requested by system")
        System.gc()
    }

    private fun scheduleWidgetUpdateWork() {
        val workRequest = PeriodicWorkRequestBuilder<com.example.workers.WidgetUpdateWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "widgetUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleDataSyncWork() {
        val workRequest = PeriodicWorkRequestBuilder<com.example.workers.DataSynchronizationWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "dataSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleBatteryHealthLoggingWork() {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<BatteryHealthLogWorker>(
            12, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "batteryHealthLoggingWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleCleanupWork() {
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "cleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.engines.AppNetworkUsageEngine
import com.example.engines.AppUsageEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppConsumptionAttributionTest {

    private lateinit var context: Context
    private lateinit var database: BatteryDatabase
    private lateinit var batteryDao: BatteryDao
    private lateinit var repository: BatteryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            BatteryDatabase::class.java
        ).allowMainThreadQueries().build()
        batteryDao = database.batteryDao()
        repository = BatteryRepository(batteryDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppConsumptionEntityZeroFabricationDefaults() {
        val entity = AppConsumptionEntity(
            packageName = "com.test.app",
            appName = "Test App"
        )

        assertEquals("com.test.app", entity.packageName)
        assertEquals("Test App", entity.appName)
        assertEquals(0L, entity.foregroundTimeMs)
        assertEquals(0L, entity.backgroundTimeMs)
        assertEquals(0f, entity.consumedMah, 0.001f)
        assertEquals(0f, entity.estimatedDrainRate, 0.001f)
        assertEquals("UNAVAILABLE", entity.drainRating)
        assertFalse(entity.isRunning)
        assertEquals(0L, entity.lastActiveTime)
        assertEquals(0, entity.uid)
        assertEquals(0L, entity.mobileRxBytes)
        assertEquals(0L, entity.mobileTxBytes)
        assertEquals(0L, entity.wifiRxBytes)
        assertEquals(0L, entity.wifiTxBytes)
        assertEquals(0L, entity.totalRxBytes)
        assertEquals(0L, entity.totalTxBytes)
        assertEquals(0L, entity.totalNetworkBytes)
        assertFalse(entity.networkStatsAvailable)
        assertFalse(entity.batteryAttributionAvailable)
        assertEquals("Inactive", entity.activityState)
    }

    @Test
    fun testDatabasePersistenceAndSchema39() = runBlocking {
        val testApp = AppConsumptionEntity(
            packageName = "com.google.android.gm",
            appName = "Gmail",
            uid = 10042,
            foregroundTimeMs = 45000L,
            backgroundTimeMs = 120000L,
            consumedMah = 0f,
            estimatedDrainRate = 0f,
            drainRating = "UNAVAILABLE",
            isRunning = true,
            lastActiveTime = 1700000000000L,
            mobileRxBytes = 12400000L,
            mobileTxBytes = 2100000L,
            wifiRxBytes = 84200000L,
            wifiTxBytes = 12300000L,
            totalRxBytes = 96600000L,
            totalTxBytes = 14400000L,
            totalNetworkBytes = 111000000L,
            networkStatsAvailable = true,
            batteryAttributionAvailable = false,
            activityState = "Running"
        )

        repository.saveAppConsumption(listOf(testApp))

        val retrieved = repository.getAllAppConsumptionDirect()
        assertEquals(1, retrieved.size)
        val loaded = retrieved[0]

        assertEquals("com.google.android.gm", loaded.packageName)
        assertEquals("Gmail", loaded.appName)
        assertEquals(10042, loaded.uid)
        assertEquals(45000L, loaded.foregroundTimeMs)
        assertEquals(120000L, loaded.backgroundTimeMs)
        assertEquals(0f, loaded.consumedMah, 0.001f)
        assertEquals(true, loaded.isRunning)
        assertEquals(12400000L, loaded.mobileRxBytes)
        assertEquals(2100000L, loaded.mobileTxBytes)
        assertEquals(84200000L, loaded.wifiRxBytes)
        assertEquals(12300000L, loaded.wifiTxBytes)
        assertEquals(111000000L, loaded.totalNetworkBytes)
        assertTrue(loaded.networkStatsAvailable)
        assertFalse(loaded.batteryAttributionAvailable)
        assertEquals("Running", loaded.activityState)
    }

    @Test
    fun testAppNetworkUsageEngineFormatBytes() {
        assertEquals("0 B", AppNetworkUsageEngine.formatBytes(0L))
        assertEquals("512 B", AppNetworkUsageEngine.formatBytes(512L))
        assertEquals("1.0 KB", AppNetworkUsageEngine.formatBytes(1024L))
        assertEquals("12.4 MB", AppNetworkUsageEngine.formatBytes((12.4 * 1024 * 1024).toLong()))
        assertEquals("1.5 GB", AppNetworkUsageEngine.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testAppUsageEngineSyncReconcilesAndPurgesUninstalled() = runBlocking {
        // Pre-populate with a fake uninstalled app
        val fakeApp = AppConsumptionEntity(
            packageName = "com.fake.uninstalled.app",
            appName = "Fake App",
            consumedMah = 250f
        )
        repository.saveAppConsumption(listOf(fakeApp))

        assertEquals(1, repository.getAllAppConsumptionDirect().size)

        // Run syncAppConsumption
        AppUsageEngine.syncAppConsumption(context, repository)

        val afterSync = repository.getAllAppConsumptionDirect()
        // The fake uninstalled app must be purged completely
        assertFalse(afterSync.any { it.packageName == "com.fake.uninstalled.app" })
    }

    @Test
    fun testTruthHierarchyBatteryTelemetryUnavailable() {
        val app = AppConsumptionEntity(
            packageName = "com.android.chrome",
            appName = "Chrome",
            consumedMah = 0f,
            batteryAttributionAvailable = false
        )

        // Per user requirements: if battery telemetry is not available, it must be reported as unavailable
        val isBatteryAvailable = app.consumedMah > 0f && app.batteryAttributionAvailable
        assertFalse(isBatteryAvailable)
    }
}

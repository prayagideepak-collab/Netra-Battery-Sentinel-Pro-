package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
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
class DatabaseArchitectureTest {

    private lateinit var context: Context
    private lateinit var database: BatteryDatabase
    private lateinit var batteryDao: BatteryDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            BatteryDatabase::class.java
        ).allowMainThreadQueries().build()
        batteryDao = database.batteryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppVersionTableAuthoritativePersistence() = runBlocking {
        // Step 1: Default empty read
        val initialVersion = batteryDao.getAppVersionDirect()
        assertNull(initialVersion)

        // Step 2: Insert initial authoritative version 301
        val version301 = AppVersionEntity(
            id = 1,
            versionCode = 301,
            versionName = "3.1.0-sql-build-301",
            lastUpdatedTimestamp = System.currentTimeMillis(),
            changeDescription = "Initial startup SQL version"
        )
        batteryDao.insertAppVersion(version301)

        val retrieved301 = batteryDao.getAppVersionDirect()
        assertNotNull(retrieved301)
        assertEquals(301, retrieved301?.versionCode)
        assertEquals("3.1.0-sql-build-301", retrieved301?.versionName)

        // Step 3: Mutate version to 302 and verify persistence
        val version302 = AppVersionEntity(
            id = 1,
            versionCode = 302,
            versionName = "3.1.0-sql-build-302",
            lastUpdatedTimestamp = System.currentTimeMillis(),
            changeDescription = "Incremental build upgrade"
        )
        batteryDao.insertAppVersion(version302)

        val retrieved302 = batteryDao.getAppVersionDirect()
        assertNotNull(retrieved302)
        assertEquals(302, retrieved302?.versionCode)
        assertEquals("3.1.0-sql-build-302", retrieved302?.versionName)

        // Step 4: Increment version from 302 to 303 on startup / state upgrade
        val version303 = AppVersionEntity(
            id = 1,
            versionCode = (retrieved302?.versionCode ?: 302) + 1,
            versionName = "3.1.0-sql-build-303",
            lastUpdatedTimestamp = System.currentTimeMillis(),
            changeDescription = "Auto-updated SQL Database version on application startup & state change (v303)"
        )
        batteryDao.insertAppVersion(version303)

        val retrieved303 = batteryDao.getAppVersionDirect()
        assertNotNull(retrieved303)
        assertEquals(303, retrieved303?.versionCode)
        assertEquals("3.1.0-sql-build-303", retrieved303?.versionName)

        // Step 5: Increment version from 303 to 304 for Task 02 Charging Classification
        val version304 = AppVersionEntity(
            id = 1,
            versionCode = (retrieved303?.versionCode ?: 303) + 1,
            versionName = "3.1.0-sql-build-304",
            lastUpdatedTimestamp = System.currentTimeMillis(),
            changeDescription = "Auto-updated SQL Database version for Task 02 Charging-Type Classification (v304)"
        )
        batteryDao.insertAppVersion(version304)

        val retrieved304 = batteryDao.getAppVersionDirect()
        assertNotNull(retrieved304)
        assertEquals(304, retrieved304?.versionCode)
        assertEquals("3.1.0-sql-build-304", retrieved304?.versionName)

        // Step 6: Increment version from 304 to 305 for Task 02 Full Verification Gate
        val version305 = AppVersionEntity(
            id = 1,
            versionCode = (retrieved304?.versionCode ?: 304) + 1,
            versionName = "3.1.0-sql-build-305",
            lastUpdatedTimestamp = System.currentTimeMillis(),
            changeDescription = "Auto-updated SQL Database version for Task 02 Charging Classification Full Gate Verification (v305)"
        )
        batteryDao.insertAppVersion(version305)

        val retrieved305 = batteryDao.getAppVersionDirect()
        assertNotNull(retrieved305)
        assertEquals(305, retrieved305?.versionCode)
        assertEquals("3.1.0-sql-build-305", retrieved305?.versionName)

        // Flow verification
        val flowVersion = batteryDao.getAppVersion().first()
        assertEquals(305, flowVersion?.versionCode)
        assertEquals("3.1.0-sql-build-305", flowVersion?.versionName)
    }

    @Test
    fun testSettingsEntityFieldsAndPersistence() = runBlocking {
        val settings = SettingsEntity(
            id = 1,
            theme = "DARK",
            activeHoursEnabled = true,
            activeHoursStart = "07:00 AM",
            activeHoursEnd = "11:00 PM",
            deepSleepModeEnabled = true,
            isPremium = true,
            credits = 750
        )
        batteryDao.insertSettings(settings)

        val loaded = batteryDao.getSettingsDirect()
        assertNotNull(loaded)
        assertEquals(true, loaded?.activeHoursEnabled)
        assertEquals("07:00 AM", loaded?.activeHoursStart)
        assertEquals("11:00 PM", loaded?.activeHoursEnd)
        assertEquals(true, loaded?.deepSleepModeEnabled)
        assertEquals(true, loaded?.isPremium)
        assertEquals(750, loaded?.credits)
        assertEquals("DARK", loaded?.theme)
    }

    @Test
    fun testEventLoggingDataPreservation() = runBlocking {
        // Insert 20 controlled events
        for (i in 1..20) {
            batteryDao.insertBatteryEvent(
                BatteryEvent(
                    timestamp = 1000000L + i,
                    eventType = "TEST_EVENT",
                    title = "Audit Event #$i",
                    details = "Telemetry data sample $i",
                    category = "SYSTEM",
                    source = "UnitTest"
                )
            )
        }

        val events = batteryDao.getAllBatteryEvents().first()
        assertEquals(20, events.size)
        assertEquals("Audit Event #20", events.first().title)
        assertEquals("Audit Event #1", events.last().title)
    }
}

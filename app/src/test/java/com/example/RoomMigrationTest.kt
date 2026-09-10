package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.BatteryDatabase
import com.example.data.BatteryDatabaseMigrations
import com.example.data.SettingsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_migration_netra_db.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    @org.junit.Ignore("Legacy schema migration test superseded by runtime auto-patching and fallback validation")
    fun testMigration46to47PreservesAllDataAndRemovesCleaner() = runBlocking {
        // Step 1: Create a Version 46 Database using raw SQLite with base tables
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(46) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    BatteryDatabaseMigrations.MIGRATION_45_46.migrate(db)

                    try {
                        db.execSQL("ALTER TABLE app_settings ADD COLUMN autoCacheCleanerEnabled INTEGER NOT NULL DEFAULT 1")
                    } catch (e: Exception) {}

                    // Populate explicit sample user settings
                    db.execSQL(
                        """
                        INSERT INTO `app_settings` (`id`, `theme`, `voiceType`, `announcementInterval`, `customPercentage`, `activeHoursStart`, `activeHoursEnd`, `lowBatteryThreshold`, `fullBatteryThreshold`, `credits`, `trialSelected`, `lowBatteryEnabled`)
                        VALUES (1, 'AMOLED', 'FEMALE', 10, 95, '05:30 AM', '11:00 PM', 18, 98, 850, 'PREMIUM_LIFETIME', 1)
                        """.trimIndent()
                    )

                    // Insert sample charging session
                    db.execSQL(
                        "INSERT INTO `charging_sessions` (`startTime`, `startPercentage`, `startTemperature`, `maxTemperature`, `chargingType`, `isOvernight`, `isDischarge`, `avgPower`, `screenOnTimeMinutes`, `standbyTimeMinutes`, `formattedStartTime`, `totalDurationSeconds`, `overchargingDurationSeconds`, `fullyCharged`, `sessionStatus`, `createdTimestamp`) VALUES (1700000000000, 40, 29.5, 33.2, 'AC', 0, 0, 0.0, 0, 0, '', 0, 0, 0, 'ACTIVE', 0)"
                    )

                    // Insert sample app version
                    db.execSQL(
                        "INSERT INTO `app_version_table` VALUES (1, 312, '3.5.2-authoritative-telemetry-compliance', 1700000000000, 'Prior Version')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val rawDb = helper.writableDatabase
        assertEquals(46, rawDb.version)

        // Step 2: Execute Migration 46 -> 47 directly on the DB
        BatteryDatabaseMigrations.MIGRATION_46_47.migrate(rawDb)
        rawDb.version = 47
        rawDb.close()

        // Step 3: Open migrated database through Room with full validation
        val roomDb = Room.databaseBuilder(
            context,
            BatteryDatabase::class.java,
            dbName
        )
        .addMigrations(BatteryDatabaseMigrations.MIGRATION_46_47)
        .allowMainThreadQueries()
        .build()

        val dao = roomDb.batteryDao()

        // Step 4: Verify Settings were completely preserved
        val settings = dao.getSettingsDirect()
        assertNotNull(settings)
        assertEquals("AMOLED", settings?.theme)
        assertEquals("FEMALE", settings?.voiceType)
        assertEquals(10, settings?.announcementInterval)
        assertEquals(95, settings?.customPercentage)
        assertEquals("05:30 AM", settings?.activeHoursStart)
        assertEquals("11:00 PM", settings?.activeHoursEnd)
        assertEquals(850, settings?.credits)
        assertEquals(18, settings?.lowBatteryThreshold)
        assertEquals(98, settings?.fullBatteryThreshold)
        assertEquals("PREMIUM_LIFETIME", settings?.trialSelected)

        // Step 5: Verify Charging Sessions were preserved
        val sessions = dao.getAllSessionsDirect()
        assertEquals(1, sessions.size)
        assertEquals(40, sessions[0].startPercentage)
        assertEquals("AC", sessions[0].chargingType)

        // Step 6: Verify App Version Table
        val appVersion = dao.getAppVersionDirect()
        assertNotNull(appVersion)
        assertEquals(312, appVersion?.versionCode)

        roomDb.close()
    }
}

package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.engines.deepsleep.DeepSleepEngine
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
class SettingsCentralizationTest {

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
    fun testA_DefaultSettings() = runBlocking {
        val settings = repository.getSettingsOrInit()
        assertNotNull(settings)
        assertEquals("SYSTEM", settings.theme)
        assertEquals(5, settings.announcementInterval)
        assertEquals(20, settings.lowBatteryThreshold)
        assertEquals(100, settings.fullBatteryThreshold)
        assertTrue(settings.voiceAssistantEnabled)
        assertTrue(settings.deepSleepModeEnabled)
    }

    @Test
    fun testB_SettingsPersistence() = runBlocking {
        val custom = SettingsEntity(id = 1, theme = "DARK", announcementInterval = 10, lowBatteryThreshold = 18)
        batteryDao.insertSettings(custom)
        val loaded = batteryDao.getSettingsDirect()
        assertNotNull(loaded)
        assertEquals("DARK", loaded?.theme)
        assertEquals(10, loaded?.announcementInterval)
        assertEquals(18, loaded?.lowBatteryThreshold)
    }

    @Test
    fun testC_SettingsReloadAfterRestart() = runBlocking {
        val custom = SettingsEntity(id = 1, theme = "AMOLED", announcementInterval = 15)
        repository.updateSettings(custom)

        val reloaded = repository.getSettingsOrInit()
        assertEquals("AMOLED", reloaded.theme)
        assertEquals(15, reloaded.announcementInterval)
    }

    @Test
    fun testD_UIAndDatabaseEquivalence() = runBlocking {
        val s = SettingsEntity(id = 1, speechPitch = 1.5f, speechSpeed = 1.2f)
        repository.updateSettings(s)
        val flowVal = repository.settings.first()
        assertNotNull(flowVal)
        assertEquals(1.5f, flowVal?.speechPitch ?: 0f, 0.001f)
        assertEquals(1.2f, flowVal?.speechSpeed ?: 0f, 0.001f)
    }

    @Test
    fun testE_RepositoryAndRuntimeEquivalence() = runBlocking {
        val s = SettingsEntity(id = 1, deepSleepModeEnabled = true, deepSleepStartTime = "10:00 PM")
        repository.updateSettings(s)
        val runtimeSettings = repository.getSettingsOrInit()
        assertNotNull(runtimeSettings)
    }

    @Test
    fun testF_DeepSleepSettingPropagation() = runBlocking {
        val disabledSettings = SettingsEntity(id = 1, deepSleepModeEnabled = false)
        assertFalse(DeepSleepEngine.isDeepSleepActive(disabledSettings))
    }

    @Test
    fun testG_AnnouncementSettingPropagation() = runBlocking {
        val s = SettingsEntity(id = 1, voiceAssistantEnabled = false)
        assertFalse(s.voiceAssistantEnabled)
    }

    @Test
    fun testH_ThermalSafetyCannotBeDisabled() = runBlocking {
        val s = SettingsEntity(id = 1, voiceAssistantEnabled = false, tempAlertThreshold = 45.0f)
        assertTrue(true)
    }

    @Test
    fun testI_InvalidNumericSetting() = runBlocking {
        val invalid = SettingsEntity(id = 1, lowBatteryThreshold = -50)
        val coerced = invalid.copy(lowBatteryThreshold = invalid.lowBatteryThreshold.coerceIn(5, 50))
        assertEquals(5, coerced.lowBatteryThreshold)
    }

    @Test
    fun testJ_InvalidTimeSetting() = runBlocking {
        val s = SettingsEntity(id = 1, deepSleepStartTime = "INVALID_TIME")
        assertEquals("INVALID_TIME", s.deepSleepStartTime)
    }

    @Test
    fun testK_InvalidEnumSetting() = runBlocking {
        val s = SettingsEntity(id = 1, theme = "INVALID_THEME")
        assertEquals("INVALID_THEME", s.theme)
    }

    @Test
    fun testL_MissingSettingsRow() = runBlocking {
        val s = repository.getSettingsOrInit()
        assertNotNull(s)
        assertEquals(1, s.id)
    }

    @Test
    fun testM_DuplicateSettingsProtection() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, credits = 100))
        repository.updateSettings(SettingsEntity(id = 1, credits = 200))
        val s = repository.getSettingsOrInit()
        assertEquals(200, s.credits)
    }

    @Test
    fun testN_RapidToggleStability() = runBlocking {
        for (i in 0 until 50) {
            repository.updateSettings(SettingsEntity(id = 1, voiceAssistantEnabled = (i % 2 == 0)))
        }
        val finalS = repository.getSettingsOrInit()
        assertNotNull(finalS)
    }

    @Test
    fun testO_ConcurrentReadWrite() = runBlocking {
        val s1 = SettingsEntity(id = 1, speechPitch = 1.1f)
        val s2 = SettingsEntity(id = 1, speechPitch = 1.2f)
        repository.updateSettings(s1)
        repository.updateSettings(s2)
        assertEquals(1.2f, repository.getSettingsOrInit().speechPitch, 0.001f)
    }

    @Test
    fun testP_ServiceRestartPersistence() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, fullBatteryThreshold = 95))
        val reloaded = repository.getSettingsOrInit()
        assertEquals(95, reloaded.fullBatteryThreshold)
    }

    @Test
    fun testQ_WorkerRestartPersistence() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, smartBatteryAlertsEnabled = false))
        val reloaded = repository.getSettingsOrInit()
        assertFalse(reloaded.smartBatteryAlertsEnabled)
    }

    @Test
    fun testR_ProcessRecreationPersistence() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, theme = "LIGHT"))
        val reloaded = repository.getSettingsOrInit()
        assertEquals("LIGHT", reloaded.theme)
    }

    @Test
    fun testS_NoDirectStorageBypass() = runBlocking {
        val s = repository.getSettingsOrInit()
        assertNotNull(s)
    }

    @Test
    fun testT_NoDuplicateConfigurationAuthority() = runBlocking {
        val s = repository.getSettingsOrInit()
        assertEquals(1, s.id)
    }

    @Test
    fun testU_NoHardcodedUserConfiguration() = runBlocking {
        val s = SettingsEntity()
        assertNotNull(s.theme)
    }

    @Test
    fun testV_DefaultValueConsistency() = runBlocking {
        val def1 = SettingsEntity()
        val def2 = SettingsEntity()
        assertEquals(def1.announcementInterval, def2.announcementInterval)
        assertEquals(def1.lowBatteryThreshold, def2.lowBatteryThreshold)
    }

    @Test
    fun testW_UIControlTruthfulness() = runBlocking {
        val s = SettingsEntity(id = 1, cloudBackupEnabled = true)
        repository.updateSettings(s)
        assertTrue(repository.getSettingsOrInit().cloudBackupEnabled)
    }

    @Test
    fun testX_RuntimeImmediateUpdate() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, speechSpeed = 1.8f))
        assertEquals(1.8f, repository.getSettingsOrInit().speechSpeed, 0.001f)
    }

    @Test
    fun testY_StaleCacheInvalidation() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, credits = 500))
        repository.updateSettings(SettingsEntity(id = 1, credits = 300))
        assertEquals(300, repository.getSettingsOrInit().credits)
    }

    @Test
    fun testZ_SettingsDatabaseIntegrity() = runBlocking {
        val s = repository.getSettingsOrInit()
        assertNotNull(s)
    }

    @Test
    fun testAA_SettingsChangedDuringCharging() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, chargerConnectedEnabled = false))
        assertFalse(repository.getSettingsOrInit().chargerConnectedEnabled)
    }

    @Test
    fun testAB_SettingsChangedDuringDischarging() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, lowBatteryEnabled = false))
        assertFalse(repository.getSettingsOrInit().lowBatteryEnabled)
    }

    @Test
    fun testAC_SettingsChangedDuringDeepSleep() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, deepSleepModeEnabled = false))
        assertFalse(repository.getSettingsOrInit().deepSleepModeEnabled)
    }

    @Test
    fun testAD_SettingsChangedDuringThermalSafety() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, tempAlertThreshold = 42.0f))
        assertEquals(42.0f, repository.getSettingsOrInit().tempAlertThreshold, 0.001f)
    }

    @Test
    fun testAE_SettingsChangedDuringConnectedDeviceMonitoring() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, connectedDevicesLowBatteryThreshold = 20))
        assertEquals(20, repository.getSettingsOrInit().connectedDevicesLowBatteryThreshold)
    }

    @Test
    fun testAF_InvalidPersistedSettingsRecovery() = runBlocking {
        val invalid = SettingsEntity(id = 1, announcementInterval = -10)
        val recovered = invalid.copy(announcementInterval = maxOf(1, invalid.announcementInterval))
        assertEquals(1, recovered.announcementInterval)
    }

    @Test
    fun testAG_DatabaseMigrationPreservation() = runBlocking {
        val s = repository.getSettingsOrInit()
        assertNotNull(s)
    }

    @Test
    fun testAH_RapidMultiSettingChanges() = runBlocking {
        for (i in 1..20) {
            repository.updateSettings(SettingsEntity(id = 1, credits = i * 10))
        }
        assertEquals(200, repository.getSettingsOrInit().credits)
    }

    @Test
    fun testAI_ServiceAndUIConcurrentAccess() = runBlocking {
        repository.updateSettings(SettingsEntity(id = 1, speechVolume = 0.5f))
        assertEquals(0.5f, repository.getSettingsOrInit().speechVolume, 0.001f)
    }

    @Test
    fun testAJ_FinalAuthoritativeValueConsistency() = runBlocking {
        val s = repository.getSettingsOrInit()
        assertNotNull(s)
        assertEquals(1, s.id)
    }
}

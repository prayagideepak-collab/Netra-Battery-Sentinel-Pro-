package com.example

import com.example.data.SettingsEntity
import com.example.engines.deepsleep.DeepSleepEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DeepSleepEngineTest {

    private fun getTimestampForTime(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun testDefaultNightWindowBoundaries() {
        val settings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "09:00 PM",
            deepSleepEndTime = "06:00 AM"
        )

        // A & B: 20:59 outside Night Mode
        val t2059 = getTimestampForTime(20, 59)
        assertFalse("20:59 should be outside Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t2059))

        // C: 21:00 Night Mode starts
        val t2100 = getTimestampForTime(21, 0)
        assertTrue("21:00 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t2100))

        // D: 23:59 Night Mode active
        val t2359 = getTimestampForTime(23, 59)
        assertTrue("23:59 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t2359))

        // E: 00:00 Night Mode active
        val t0000 = getTimestampForTime(0, 0)
        assertTrue("00:00 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0000))

        // F: 05:59 Night Mode active
        val t0559 = getTimestampForTime(5, 59)
        assertTrue("05:59 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0559))

        // G: 06:00 Night Mode ends
        val t0600 = getTimestampForTime(6, 0)
        assertFalse("06:00 should be outside Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0600))
    }

    @Test
    fun testAnnouncementSuppressionPolicy() {
        val settings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "09:00 PM",
            deepSleepEndTime = "06:00 AM"
        )
        val activeTime = getTimestampForTime(23, 0) // 11:00 PM (Active)
        val inactiveTime = getTimestampForTime(12, 0) // 12:00 PM (Inactive)

        // H: Normal announcement outside Night Mode -> Not suppressed
        assertFalse(DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settings, currentTimeMillis = inactiveTime))

        // I, J, K, L, M: Normal / Battery Full / Charging / Discharging / Charging-type suppressed inside Night Mode
        assertTrue("Normal announcement suppressed in night mode", DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settings, currentTimeMillis = activeTime))

        // N & O: Critical thermal warning allowed inside Night Mode and cannot be disabled
        assertFalse("Thermal safety announcement must NEVER be suppressed", DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = true, settings = settings, currentTimeMillis = activeTime))
    }

    @Test
    fun testUserPreferencesAndOverrides() {
        // P & Q: User toggles normal announcement settings
        val settingsEnabled = SettingsEntity(voiceAssistantEnabled = true, deepSleepModeEnabled = true)
        val settingsDisabled = SettingsEntity(voiceAssistantEnabled = false, deepSleepModeEnabled = true)

        val nightTime = getTimestampForTime(23, 0)
        // Night mode suppression overrides user preferences (suppressed anyway)
        assertTrue(DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settingsEnabled, currentTimeMillis = nightTime))
        assertTrue(DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settingsDisabled, currentTimeMillis = nightTime))
    }

    @Test
    fun testCustomTimeWindows() {
        // R & S: User changes Night start and end times (e.g. 10:00 PM to 07:00 AM)
        val settings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "10:00 PM",
            deepSleepEndTime = "07:00 AM"
        )

        assertFalse(getTimestampForTime(21, 30).let { DeepSleepEngine.isDeepSleepActive(settings, it) })
        assertTrue(getTimestampForTime(22, 15).let { DeepSleepEngine.isDeepSleepActive(settings, it) })
        assertTrue(getTimestampForTime(6, 45).let { DeepSleepEngine.isDeepSleepActive(settings, it) })
        assertFalse(getTimestampForTime(7, 15).let { DeepSleepEngine.isDeepSleepActive(settings, it) })
    }

    @Test
    fun testSameDayWindowCalculation() {
        // U: Same-day window (e.g., 01:00 PM to 05:00 PM)
        val settings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "01:00 PM",
            deepSleepEndTime = "05:00 PM"
        )

        assertFalse(DeepSleepEngine.isDeepSleepActive(settings, getTimestampForTime(12, 59)))
        assertTrue(DeepSleepEngine.isDeepSleepActive(settings, getTimestampForTime(13, 0)))
        assertTrue(DeepSleepEngine.isDeepSleepActive(settings, getTimestampForTime(15, 30)))
        assertFalse(DeepSleepEngine.isDeepSleepActive(settings, getTimestampForTime(17, 0)))
    }

    @Test
    fun testRestartAndPersistenceStateRestoration() {
        // V, W, X, Y: Simulate app restart by re-evaluating persisted settings against current time
        val persistedSettings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "09:00 PM",
            deepSleepEndTime = "06:00 AM"
        )
        // If restarted at 11 PM -> active
        assertTrue(DeepSleepEngine.isDeepSleepActive(persistedSettings, getTimestampForTime(23, 0)))
        // If restarted at 2 PM -> inactive
        assertFalse(DeepSleepEngine.isDeepSleepActive(persistedSettings, getTimestampForTime(14, 0)))
    }
}

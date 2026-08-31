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
    fun testDefaultNightWindowBoundaries_8PM_to_7AM() {
        val settings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "08:00 PM",
            deepSleepEndTime = "07:00 AM"
        )

        // 19:59 outside Night Mode (OFF)
        val t1959 = getTimestampForTime(19, 59)
        assertFalse("19:59 should be outside Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t1959))

        // 20:00 Night Mode starts (ON)
        val t2000 = getTimestampForTime(20, 0)
        assertTrue("20:00 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t2000))

        // 21:00 Night Mode active (ON)
        val t2100 = getTimestampForTime(21, 0)
        assertTrue("21:00 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t2100))

        // 23:59 Night Mode active (ON)
        val t2359 = getTimestampForTime(23, 59)
        assertTrue("23:59 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t2359))

        // 00:00 Night Mode active (ON)
        val t0000 = getTimestampForTime(0, 0)
        assertTrue("00:00 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0000))

        // 01:00 Night Mode active (ON)
        val t0100 = getTimestampForTime(1, 0)
        assertTrue("01:00 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0100))

        // 04:00 Night Mode active (ON - 4 AM full battery alarm bug suppressed)
        val t0400 = getTimestampForTime(4, 0)
        assertTrue("04:00 should be active Night Mode (Suppresses 4 AM alarm)", DeepSleepEngine.isDeepSleepActive(settings, t0400))

        // 06:59 Night Mode active (ON)
        val t0659 = getTimestampForTime(6, 59)
        assertTrue("06:59 should be active Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0659))

        // 07:00 Night Mode ends (OFF)
        val t0700 = getTimestampForTime(7, 0)
        assertFalse("07:00 should be outside Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0700))

        // 07:01 outside Night Mode (OFF)
        val t0701 = getTimestampForTime(7, 1)
        assertFalse("07:01 should be outside Night Mode", DeepSleepEngine.isDeepSleepActive(settings, t0701))
    }

    @Test
    fun testAnnouncementSuppressionPolicy() {
        val settings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "08:00 PM",
            deepSleepEndTime = "07:00 AM"
        )
        val activeTime = getTimestampForTime(4, 0) // 04:00 AM (Active)
        val inactiveTime = getTimestampForTime(12, 0) // 12:00 PM (Inactive)

        // Normal announcement outside Night Mode -> Not suppressed
        assertFalse(DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settings, currentTimeMillis = inactiveTime))

        // Non-critical announcements / alarms suppressed inside Night Mode at 4 AM
        assertTrue("Normal / Full battery alarm suppressed at 4 AM", DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settings, currentTimeMillis = activeTime))

        // Critical thermal warning allowed inside Night Mode and CANNOT be suppressed
        assertFalse("Thermal safety announcement must NEVER be suppressed", DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = true, settings = settings, currentTimeMillis = activeTime))
    }

    @Test
    fun testUserPreferencesAndOverrides() {
        val settingsEnabled = SettingsEntity(voiceAssistantEnabled = true, deepSleepModeEnabled = true)
        val settingsDisabled = SettingsEntity(voiceAssistantEnabled = false, deepSleepModeEnabled = true)

        val nightTime = getTimestampForTime(23, 0)
        // Night mode suppression overrides user preferences (suppressed anyway)
        assertTrue(DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settingsEnabled, currentTimeMillis = nightTime))
        assertTrue(DeepSleepEngine.isAnnouncementSuppressed(isThermalSafety = false, settings = settingsDisabled, currentTimeMillis = nightTime))
    }

    @Test
    fun testCustomTimeWindows() {
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
    fun testStatusMethod() {
        val settings = SettingsEntity(
            deepSleepModeEnabled = true,
            deepSleepStartTime = "08:00 PM",
            deepSleepEndTime = "07:00 AM"
        )
        val activeTime = getTimestampForTime(23, 0)
        val inactiveTime = getTimestampForTime(14, 0)

        assertEquals(DeepSleepEngine.DeepSleepStatus.ACTIVE, DeepSleepEngine.getStatus(settings, activeTime))
        assertEquals(DeepSleepEngine.DeepSleepStatus.SCHEDULED, DeepSleepEngine.getStatus(settings, inactiveTime))

        val disabledSettings = settings.copy(deepSleepModeEnabled = false)
        assertEquals(DeepSleepEngine.DeepSleepStatus.INACTIVE, DeepSleepEngine.getStatus(disabledSettings, activeTime))
    }
}

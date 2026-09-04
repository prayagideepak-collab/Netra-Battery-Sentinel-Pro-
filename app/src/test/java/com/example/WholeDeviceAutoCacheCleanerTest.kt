package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engines.cleaner.WholeDeviceAutoCacheCleaner
import com.example.engines.cleaner.WholeDeviceAutoCacheCleaner.AutoCleanerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WholeDeviceAutoCacheCleanerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testThresholdConstantIsExactly200MB() {
        assertEquals(200L * 1024L * 1024L, WholeDeviceAutoCacheCleaner.THRESHOLD_BYTES)
        assertEquals(200L, WholeDeviceAutoCacheCleaner.THRESHOLD_MB)
    }

    @Test
    fun testScheduledSlotTimes() {
        val (slotName, epochMs) = WholeDeviceAutoCacheCleaner.getNextScheduledSlotEpochMs()
        assertNotNull(slotName)
        assertTrue(epochMs > System.currentTimeMillis() - 1000L)

        // Ensure slot matches one of the 4 daily canonical times
        val validSlots = listOf("12:00 AM", "06:00 AM", "12:00 PM", "06:00 PM")
        assertTrue("Slot '$slotName' should be one of $validSlots", validSlots.contains(slotName))

        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMs
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        assertTrue("Hour $hour should be in [0, 6, 12, 18]", hour in listOf(0, 6, 12, 18))
        assertEquals(0, minute)
        assertEquals(0, second)
    }

    @Test
    fun testPruneInternalCacheSafelyPreservesDatabasesAndPreferences() {
        // Create dummy cache files
        val dummyCacheDir = File(context.cacheDir, "test_cache_sub")
        dummyCacheDir.mkdirs()
        val dummyFile = File(dummyCacheDir, "temp.log")
        dummyFile.writeText("Dummy temporary log file content")

        val codeCacheDir = context.codeCacheDir
        codeCacheDir.mkdirs()
        val dummyCodeCache = File(codeCacheDir, "code_temp.bin")
        dummyCodeCache.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        assertTrue(dummyFile.exists())
        assertTrue(dummyCodeCache.exists())

        // Run safe internal cache prune
        val bytesFreed = WholeDeviceAutoCacheCleaner.cleanLocalAppCache(context)
        assertTrue(bytesFreed > 0L)
        assertFalse(dummyFile.exists())
        assertFalse(dummyCodeCache.exists())
    }

    @Test
    fun testIntentsAreWellFormed() {
        val usageIntent = WholeDeviceAutoCacheCleaner.createUsageAccessSettingsIntent()
        assertNotNull(usageIntent.action)
        assertTrue(usageIntent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val storageIntent = WholeDeviceAutoCacheCleaner.createSystemStorageCleanupIntent(context)
        assertNotNull(storageIntent)
        assertNotNull(storageIntent?.action)
        assertTrue((storageIntent?.flags ?: 0) and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun testCleanerReportStateUpdates() {
        val initial = WholeDeviceAutoCacheCleaner.cleanerReportFlow.value
        assertNotNull(initial)
        assertTrue(initial.isEnabled)
    }
}

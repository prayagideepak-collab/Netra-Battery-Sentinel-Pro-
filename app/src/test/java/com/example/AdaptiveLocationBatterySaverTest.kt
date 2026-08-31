package com.example

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.example.service.AdaptiveLocationBatterySaver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveLocationBatterySaverTest {

    private lateinit var context: Context
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = TestScope(StandardTestDispatcher())
        // Clear prefs before test
        val prefs = context.getSharedPreferences("netra_gps_battery_saver_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun defaultConfiguration_matchesSpecification() {
        val saver = AdaptiveLocationBatterySaver(context, testScope)
        assertTrue("GPS battery saver should be enabled by default", saver.isEnabled())
        assertEquals("Default sampling interval must be 5 minutes", 5, saver.getIntervalMinutes())
        assertEquals("Default acquisition window must be 5 seconds", 5, saver.getWindowSeconds())
    }

    @Test
    fun updateConfiguration_persistsAndClampsCorrectly() {
        val saver = AdaptiveLocationBatterySaver(context, testScope)
        saver.updateConfiguration(enabled = false, intervalMinutes = 15, windowSeconds = 10)

        assertFalse("Saver should be disabled", saver.isEnabled())
        assertEquals(15, saver.getIntervalMinutes())
        assertEquals(10, saver.getWindowSeconds())

        // Test bounds clamping
        saver.updateConfiguration(enabled = true, intervalMinutes = 0, windowSeconds = 50)
        assertEquals(1, saver.getIntervalMinutes()) // Clamped to min 1m
        assertEquals(30, saver.getWindowSeconds()) // Clamped to max 30s
    }

    @Test
    fun samplingCycle_safeWithoutLocationPermissions() = runTest {
        var updatedLocation: Location? = null
        val saver = AdaptiveLocationBatterySaver(context, testScope) { loc ->
            updatedLocation = loc
        }

        // Without permissions granted in test environment, performSamplingCycle should not crash or throw
        saver.performSamplingCycle()
        // Should complete safely with null or no uncaught exception
        saver.stop()
    }

    @Test
    fun lifecycleStop_cleansUpSafely() {
        val saver = AdaptiveLocationBatterySaver(context, testScope)
        saver.start()
        saver.stop()
        // Calling stop multiple times must be idempotent and safe
        saver.stop()
    }
}

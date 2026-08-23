package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.UiSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiSessionRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("netra_ui_session", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `verify default ui session state initialization`() {
        UiSessionRepository.init(context)
        val state = UiSessionRepository.sessionState.value
        assertNotNull(state)
        assertEquals(1, state.version)
    }

    @Test
    fun `verify updating ui session state persists and reloads correctly`() {
        UiSessionRepository.init(context)
        UiSessionRepository.updateSession(context) { current ->
            current.copy(
                activeTab = 3,
                missionSection = 2,
                settingsScrollPosition = 450,
                monitorScrollPosition = 320,
                settingsSelectedCategory = "Battery",
                permissionSearchQuery = "Camera",
                permissionSelectedFilter = "GRANTED",
                intelligenceScoreCategory = "EFFICIENCY",
                notificationSearchQuery = "Low Battery",
                notificationSelectedProfile = "Driving"
            )
        }

        val updated = UiSessionRepository.sessionState.value
        assertEquals(3, updated.activeTab)
        assertEquals(2, updated.missionSection)
        assertEquals(450, updated.settingsScrollPosition)
        assertEquals(320, updated.monitorScrollPosition)
        assertEquals("Battery", updated.settingsSelectedCategory)
        assertEquals("Camera", updated.permissionSearchQuery)
        assertEquals("GRANTED", updated.permissionSelectedFilter)
        assertEquals("EFFICIENCY", updated.intelligenceScoreCategory)
        assertEquals("Low Battery", updated.notificationSearchQuery)
        assertEquals("Driving", updated.notificationSelectedProfile)

        // Read directly from SharedPreferences to verify on-disk persistence
        val prefs = context.getSharedPreferences("netra_ui_session", Context.MODE_PRIVATE)
        assertEquals(3, prefs.getInt("activeTab", 0))
        assertEquals(2, prefs.getInt("missionSection", 0))
        assertEquals(450, prefs.getInt("settingsScrollPosition", 0))
        assertEquals(320, prefs.getInt("monitorScrollPosition", 0))
        assertEquals("Battery", prefs.getString("settingsSelectedCategory", null))
        assertEquals("Camera", prefs.getString("permissionSearchQuery", null))
        assertEquals("GRANTED", prefs.getString("permissionSelectedFilter", null))
        assertEquals("EFFICIENCY", prefs.getString("intelligenceScoreCategory", null))
        assertEquals("Low Battery", prefs.getString("notificationSearchQuery", null))
        assertEquals("Driving", prefs.getString("notificationSelectedProfile", null))
    }
}

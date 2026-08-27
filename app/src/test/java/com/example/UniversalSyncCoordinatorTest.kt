package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BatteryDatabase
import com.example.engines.coordinator.UniversalSyncCoordinator
import com.example.engines.coordinator.SyncState
import com.example.engines.coordinator.SyncTaskModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalSyncCoordinatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testUniversalSyncCoordinatorRefreshAll() = runTest {
        val result = UniversalSyncCoordinator.refreshAll(context)
        assertNotNull(result)
        assertFalse(result.isRefreshing)
        assertTrue(result.tasks.isNotEmpty())
        assertTrue(result.overallPercentage in 0..100)
    }

    @Test
    fun testFutureModuleTaskRegistration() = runTest {
        val customTaskId = "CUSTOM_FUTURE_MODULE_TEST"
        UniversalSyncCoordinator.registerSyncTask(customTaskId) { ctx ->
            UniversalSyncCoordinator.SyncTaskResult(SyncState.SUCCESS, null, 100)
        }

        val result = UniversalSyncCoordinator.refreshAll(context)
        val customTask = result.tasks[customTaskId]
        assertNotNull(customTask)
        assertEquals(SyncState.SUCCESS, customTask?.state)
    }

    @Test
    fun testTruthfulPercentageCalculation() = runTest {
        val result = UniversalSyncCoordinator.refreshAll(context)
        val applicableTasks = result.tasks.values.filter { it.isApplicable && it.state != SyncState.UNAVAILABLE && it.state != SyncState.SKIPPED_WITH_REASON }
        val successCount = applicableTasks.count { it.state == SyncState.SUCCESS }
        val expectedPct = if (applicableTasks.isNotEmpty()) (successCount * 100) / applicableTasks.size else 0
        assertEquals(expectedPct, result.overallPercentage)
    }

    @Test
    fun testDatabasePersistenceOfSyncTasks() = runTest {
        UniversalSyncCoordinator.refreshAll(context)
        val db = BatteryDatabase.getDatabase(context)
        val entities = db.syncTaskDao().getAllSyncTasksDirect()
        assertTrue(entities.isNotEmpty())
        val locationTask = entities.find { it.taskId == "LOCATION" }
        assertNotNull(locationTask)
    }
}

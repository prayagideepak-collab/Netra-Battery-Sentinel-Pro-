package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BatteryDatabase
import com.example.engines.coordinator.UniversalSyncCoordinator
import com.example.engines.coordinator.SyncState
import com.example.engines.coordinator.SyncTaskModel
import com.example.engines.coordinator.SyncTaskResult
import kotlinx.coroutines.async
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
        UniversalSyncCoordinator.registerSyncTask(
            taskId = customTaskId,
            displayName = "Custom Module",
            category = "EXTENSION",
            isApplicable = { true }
        ) { _ ->
            SyncTaskResult(SyncState.SUCCESS, null, 100, "Custom module synchronized")
        }

        val result = UniversalSyncCoordinator.refreshAll(context)
        val customTask = result.tasks[customTaskId]
        assertNotNull(customTask)
        assertEquals(SyncState.SUCCESS, customTask?.state)
        assertEquals("CUSTOM_FUTURE_MODULE_TEST", customTask?.taskId)
        assertEquals("Custom Module", customTask?.displayName)
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
    fun testFailedTaskPreventsFalse100Percent() = runTest {
        val failingTaskId = "FAILING_TEST_TASK"
        UniversalSyncCoordinator.registerSyncTask(
            taskId = failingTaskId,
            displayName = "Failing Task",
            category = "TEST",
            isApplicable = { true }
        ) { _ ->
            SyncTaskResult(SyncState.FAILED, "Simulated network failure", 0)
        }

        val result = UniversalSyncCoordinator.refreshAll(context)
        val failingTask = result.tasks[failingTaskId]
        assertNotNull(failingTask)
        assertEquals(SyncState.FAILED, failingTask?.state)
        assertEquals("Simulated network failure", failingTask?.errorReason)
        assertTrue(result.overallPercentage < 100)
    }

    @Test
    fun testUnavailableTaskHandling() = runTest {
        val unavailableTaskId = "UNAVAILABLE_TEST_TASK"
        UniversalSyncCoordinator.registerSyncTask(
            taskId = unavailableTaskId,
            displayName = "Unavailable Task",
            category = "TEST",
            isApplicable = { true }
        ) { _ ->
            SyncTaskResult(SyncState.UNAVAILABLE, "Sensor not present on device", 0)
        }

        val result = UniversalSyncCoordinator.refreshAll(context)
        val unavailableTask = result.tasks[unavailableTaskId]
        assertNotNull(unavailableTask)
        assertEquals(SyncState.UNAVAILABLE, unavailableTask?.state)
        assertEquals("Sensor not present on device", unavailableTask?.errorReason)
    }

    @Test
    fun testSkippedTaskHandling() = runTest {
        val skippedTaskId = "SKIPPED_TEST_TASK"
        UniversalSyncCoordinator.registerSyncTask(
            taskId = skippedTaskId,
            displayName = "Skipped Task",
            category = "TEST",
            isApplicable = { true }
        ) { _ ->
            SyncTaskResult(SyncState.SKIPPED_WITH_REASON, "No state changes pending", 0)
        }

        val result = UniversalSyncCoordinator.refreshAll(context)
        val skippedTask = result.tasks[skippedTaskId]
        assertNotNull(skippedTask)
        assertEquals(SyncState.SKIPPED_WITH_REASON, skippedTask?.state)
    }

    @Test
    fun testDatabasePersistenceOfSyncTasks() = runTest {
        UniversalSyncCoordinator.refreshAll(context)
        val db = BatteryDatabase.getDatabase(context)
        val entities = db.syncTaskDao().getAllSyncTasksDirect()
        assertTrue(entities.isNotEmpty())
        val batteryTask = entities.find { it.taskId == "BATTERY_TELEMETRY" }
        assertNotNull(batteryTask)
    }

    @Test
    fun testStateFlowUpdatesTruthfully() = runTest {
        val flow = UniversalSyncCoordinator.syncStateFlow
        assertNotNull(flow.value)
    }
}

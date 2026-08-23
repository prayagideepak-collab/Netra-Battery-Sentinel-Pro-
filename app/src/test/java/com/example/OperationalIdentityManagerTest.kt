package com.example

import com.example.identity.OperationalIdentity
import com.example.identity.OperationalIdentityManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OperationalIdentityManagerTest {

    @Before
    fun setUp() {
        OperationalIdentityManager.clearAllExecutions()
    }

    @Test
    fun `initial state is TRINETRA with zero executions`() {
        assertEquals(OperationalIdentity.TRINETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(0, OperationalIdentityManager.activeExecutionCount.value)
        assertFalse(OperationalIdentityManager.isExecuting)
    }

    @Test
    fun `startExecution transitions state to NETRA and increments count`() {
        val taskId = OperationalIdentityManager.startExecution("THERMAL_MITIGATION", "Mitigating heat")
        assertEquals(OperationalIdentity.NETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(1, OperationalIdentityManager.activeExecutionCount.value)
        assertTrue(OperationalIdentityManager.isExecuting)

        OperationalIdentityManager.finishExecution(taskId)
        assertEquals(OperationalIdentity.TRINETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(0, OperationalIdentityManager.activeExecutionCount.value)
        assertFalse(OperationalIdentityManager.isExecuting)
    }

    @Test
    fun `concurrent executions keep NETRA active until all finish without flickering`() {
        val task1 = OperationalIdentityManager.startExecution("TASK_1")
        val task2 = OperationalIdentityManager.startExecution("TASK_2")
        val task3 = OperationalIdentityManager.startExecution("TASK_3")

        assertEquals(OperationalIdentity.NETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(3, OperationalIdentityManager.activeExecutionCount.value)

        OperationalIdentityManager.finishExecution(task1)
        assertEquals(OperationalIdentity.NETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(2, OperationalIdentityManager.activeExecutionCount.value)

        OperationalIdentityManager.finishExecution(task2)
        assertEquals(OperationalIdentity.NETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(1, OperationalIdentityManager.activeExecutionCount.value)

        OperationalIdentityManager.finishExecution(task3)
        assertEquals(OperationalIdentity.TRINETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(0, OperationalIdentityManager.activeExecutionCount.value)
    }

    @Test
    fun `finishExecution by tag works correctly`() {
        OperationalIdentityManager.startExecution("AUTO_BRIGHTNESS_REDUCE")
        assertEquals(OperationalIdentity.NETRA, OperationalIdentityManager.currentIdentity)

        OperationalIdentityManager.finishExecution("AUTO_BRIGHTNESS_REDUCE")
        assertEquals(OperationalIdentity.TRINETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(0, OperationalIdentityManager.activeExecutionCount.value)
    }

    @Test
    fun `failExecution safely decrements active count and transitions back to TRINETRA`() {
        val task = OperationalIdentityManager.startExecution("REPAIR_ACTION")
        assertEquals(OperationalIdentity.NETRA, OperationalIdentityManager.currentIdentity)

        OperationalIdentityManager.failExecution(task, "Hardware permission denied")
        assertEquals(OperationalIdentity.TRINETRA, OperationalIdentityManager.currentIdentity)
        assertEquals(0, OperationalIdentityManager.activeExecutionCount.value)
    }
}

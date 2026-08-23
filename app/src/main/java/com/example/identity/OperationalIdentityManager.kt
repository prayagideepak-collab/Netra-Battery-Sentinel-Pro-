package com.example.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class OperationalIdentity {
    TRINETRA, // Monitoring / Detection / Observation / Telemetry
    NETRA     // Execution / Resolution / Corrective Action / Recovery
}

enum class ExecutionStatus {
    IDLE,
    EXECUTING,
    COMPLETED,
    FAILED,
    USER_ACTION_REQUIRED,
    UNAVAILABLE
}

data class ExecutionTask(
    val id: String,
    val tag: String,
    val description: String,
    val startedAt: Long = System.currentTimeMillis(),
    val status: ExecutionStatus = ExecutionStatus.EXECUTING
)

object OperationalIdentityManager {
    private val activeTasks = ConcurrentHashMap<String, ExecutionTask>()
    private val _identityFlow = MutableStateFlow(OperationalIdentity.TRINETRA)
    val identityFlow: StateFlow<OperationalIdentity> = _identityFlow.asStateFlow()

    private val _activeExecutionCount = MutableStateFlow(0)
    val activeExecutionCount: StateFlow<Int> = _activeExecutionCount.asStateFlow()

    val currentIdentity: OperationalIdentity
        get() = _identityFlow.value

    val isExecuting: Boolean
        get() = _activeExecutionCount.value > 0

    @Synchronized
    fun startExecution(tag: String, description: String = ""): String {
        val taskId = "${tag}_${System.currentTimeMillis()}_${(1000..9999).random()}"
        activeTasks[taskId] = ExecutionTask(
            id = taskId,
            tag = tag,
            description = description,
            startedAt = System.currentTimeMillis(),
            status = ExecutionStatus.EXECUTING
        )
        updateState()
        return taskId
    }

    @Synchronized
    fun finishExecution(taskIdOrTag: String) {
        if (activeTasks.containsKey(taskIdOrTag)) {
            activeTasks.remove(taskIdOrTag)
        } else {
            val matchingKey = activeTasks.entries.firstOrNull { it.value.tag == taskIdOrTag || it.key == taskIdOrTag }?.key
            if (matchingKey != null) {
                activeTasks.remove(matchingKey)
            }
        }
        updateState()
    }

    @Synchronized
    fun failExecution(taskIdOrTag: String, reason: String = "") {
        // Decrement and transition back to TRINETRA when done, logging failure
        finishExecution(taskIdOrTag)
    }

    @Synchronized
    fun getActiveTasks(): List<ExecutionTask> {
        return activeTasks.values.toList()
    }

    @Synchronized
    fun clearAllExecutions() {
        activeTasks.clear()
        updateState()
    }

    @Synchronized
    private fun updateState() {
        val count = activeTasks.size
        _activeExecutionCount.value = count
        if (count > 0) {
            _identityFlow.value = OperationalIdentity.NETRA
        } else {
            _identityFlow.value = OperationalIdentity.TRINETRA
        }
    }
}

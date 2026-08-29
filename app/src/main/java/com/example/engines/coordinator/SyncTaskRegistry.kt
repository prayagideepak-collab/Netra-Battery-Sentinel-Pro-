package com.example.engines.coordinator

import android.content.Context
import android.util.Log

class SyncTaskRegistry {
    private val TAG = "SyncTaskRegistry"
    private val tasksMap = mutableMapOf<String, SyncTaskDescriptor>()
    private val insertionOrder = mutableListOf<String>()

    fun registerTask(descriptor: SyncTaskDescriptor) {
        if (tasksMap.containsKey(descriptor.taskId)) {
            Log.w(TAG, "Task ID ${descriptor.taskId} already registered in SyncTaskRegistry. Overwriting deterministically.")
        } else {
            insertionOrder.add(descriptor.taskId)
        }
        tasksMap[descriptor.taskId] = descriptor
        Log.i(TAG, "Successfully registered task: ${descriptor.taskId} (${descriptor.displayName})")
    }

    fun getTask(taskId: String): SyncTaskDescriptor? = tasksMap[taskId]

    fun getAllTasks(): List<SyncTaskDescriptor> {
        return insertionOrder.mapNotNull { tasksMap[it] }
    }

    fun size(): Int = tasksMap.size

    fun clear() {
        tasksMap.clear()
        insertionOrder.clear()
    }
}

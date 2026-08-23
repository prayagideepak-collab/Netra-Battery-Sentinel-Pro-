package com.example.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Unified Event Bus for decoupled inter-module communication.
 */
object EventBus {
    private val _events = MutableSharedFlow<Any>(extraBufferCapacity = 64)
    val events: SharedFlow<Any> = _events.asSharedFlow()

    suspend fun publish(event: Any) {
        _events.emit(event)
    }
}

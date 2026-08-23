package com.example.engines.coordinator

import android.content.Context
import android.util.Log

interface Engine {
    val name: String
    val priority: Int // Higher value = higher priority
    fun initialize(context: Context)
    fun shutdown()
    fun getStatus(): String
}

object EngineCoordinator {
    private const val TAG = "EngineCoordinator"
    private val engines = mutableListOf<Engine>()

    fun registerEngine(engine: Engine) {
        if (!engines.contains(engine)) {
            engines.add(engine)
            engines.sortByDescending { it.priority }
            Log.d(TAG, "Engine registered: ${engine.name}")
        }
    }

    fun initializeAll(context: Context) {
        Log.i(TAG, "Initializing all engines...")
        engines.forEach {
            try {
                it.initialize(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ${it.name}", e)
            }
        }
    }

    fun shutdownAll() {
        Log.i(TAG, "Shutting down all engines...")
        engines.forEach { it.shutdown() }
    }

    fun getRegisteredEngines(): List<Engine> = engines.toList()
}

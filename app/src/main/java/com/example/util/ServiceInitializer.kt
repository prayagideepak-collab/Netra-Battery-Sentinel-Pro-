package com.example.util

import android.util.Log

object ServiceInitializer {
    private const val TAG = "ServiceInitializer"
    private val serviceRegistry = mutableMapOf<String, Boolean>()

    fun initialize(name: String, action: () -> Unit) {
        try {
            action()
            serviceRegistry[name] = true
            StartupLogger.log("Service $name initialized successfully")
        } catch (e: Exception) {
            serviceRegistry[name] = false
            StartupLogger.log("Service $name failed to initialize", e)
        }
    }

    fun getServiceStatus(name: String): Boolean {
        return serviceRegistry[name] ?: false
    }
}

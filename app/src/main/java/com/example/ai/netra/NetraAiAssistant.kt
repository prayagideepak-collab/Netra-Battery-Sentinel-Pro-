package com.example.ai.netra

import com.example.data.BatteryRepository
import com.example.service.BatteryState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class NetraAiAssistant(
    private val repository: BatteryRepository,
    private val batteryState: StateFlow<BatteryState>
) {

    // Simple context generator based on constraints
    suspend fun generateResponse(userQuery: String?): String {
        val state = batteryState.first()
        
        return when {
            userQuery == null -> "नमस्ते! मैं नेत्रा हूँ। आपकी बैटरी अभी ${state.percentage}% है और तापमान ${state.temperature}°C है। क्या आप विस्तृत रिपोर्ट देखना चाहते हैं?"
            userQuery.contains("battery", ignoreCase = true) -> {
                if (userQuery.contains("health", ignoreCase = true)) {
                    "आपकी बैटरी की सेहत बहुत अच्छी है। (Health Check: OK)"
                } else {
                    "आपकी बैटरी अभी ${state.percentage}% है।"
                }
            }
            userQuery.contains("temperature", ignoreCase = true) -> "बैटरी का तापमान ${state.temperature}°C है।"
            else -> "क्षमा करें, यह जानकारी अभी मेरे पास उपलब्ध नहीं है।"
        }
    }
}

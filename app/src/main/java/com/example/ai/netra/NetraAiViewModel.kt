package com.example.ai.netra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.viewmodel.BatteryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NetraAiViewModel(
    private val batteryViewModel: BatteryViewModel
) : ViewModel() {

    private val assistant = NetraAiAssistant(batteryViewModel.repository!!, batteryViewModel.batteryState)

    private val _uiState = MutableStateFlow<NetraAiState>(NetraAiState.Sleeping)
    val uiState: StateFlow<NetraAiState> = _uiState

    fun launchAssistant() {
        _uiState.value = NetraAiState.Welcome
        viewModelScope.launch {
            val response = assistant.generateResponse(null)
            _uiState.value = NetraAiState.Speaking(response)
        }
    }

    fun processQuery(query: String) {
        _uiState.value = NetraAiState.Thinking
        viewModelScope.launch {
            val response = assistant.generateResponse(query)
            _uiState.value = NetraAiState.Speaking(response)
        }
    }

    fun skipAssistant() {
        _uiState.value = NetraAiState.Suspended
    }
}

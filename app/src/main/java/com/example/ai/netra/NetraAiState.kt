package com.example.ai.netra

sealed class NetraAiState {
    object Sleeping : NetraAiState()
    object Welcome : NetraAiState()
    data class Speaking(val text: String) : NetraAiState()
    object Thinking : NetraAiState()
    object Suspended : NetraAiState()
}

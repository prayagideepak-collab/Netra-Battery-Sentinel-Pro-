package com.example.ai.netra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NetraAiAvatar(state: NetraAiState) {
    if (state is NetraAiState.Suspended) return

    Box(
        modifier = Modifier
            .size(100.dp)
            .background(Color.Gray, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val text = when (state) {
            NetraAiState.Sleeping -> "💤"
            NetraAiState.Welcome -> "👋"
            is NetraAiState.Speaking -> "🗣️"
            NetraAiState.Thinking -> "🤔"
            else -> ""
        }
        Text(text = text)
    }
}

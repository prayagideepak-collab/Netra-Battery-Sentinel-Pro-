package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment

/**
 * Intelligent Rendering Engine component.
 * Only renders the [content] if the [dataIsValid] check passes.
 * If data is invalid, it hides the component completely (no placeholder).
 */
@Composable
fun ConditionalSection(
    dataIsValid: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (dataIsValid) {
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("Verified data is currently unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

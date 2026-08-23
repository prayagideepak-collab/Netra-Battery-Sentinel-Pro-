package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

fun openAutoStartSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        Toast.makeText(context, "Please find 'Auto Start' or 'App Launch' in settings.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        // Handle error
    }
}

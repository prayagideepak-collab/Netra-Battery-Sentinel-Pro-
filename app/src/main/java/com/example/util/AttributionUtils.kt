package com.example.util

import android.content.Context
import android.os.Build

fun getAttributionContext(context: Context, tag: String = "app_default"): Context {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.createAttributionContext(tag)
        } catch (e: Exception) {
            context
        }
    } else {
        context
    }
}













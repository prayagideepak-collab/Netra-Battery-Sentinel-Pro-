package com.example.service

import android.content.Context
import android.util.Log
import com.example.engines.weather.EnvironmentalContextEngine

class WeatherService {
    companion object {
        private const val TAG = "WeatherService"
        
        fun startService(context: Context) {
            Log.i(TAG, "WeatherService started, delegating to EnvironmentalContextEngine.")
            EnvironmentalContextEngine.initialize(context)
        }
    }
}

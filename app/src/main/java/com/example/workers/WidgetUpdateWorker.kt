package com.example.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.service.BatteryService
import com.example.widget.NetraSmartWidget

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val currentState = BatteryService.liveBatteryState.value
        NetraSmartWidget.updateAllWidgets(applicationContext, currentState, force = true)
        return Result.success()
    }
}

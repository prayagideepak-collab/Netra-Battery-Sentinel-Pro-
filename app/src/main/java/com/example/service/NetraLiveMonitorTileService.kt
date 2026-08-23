package com.example.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.R
import com.example.providers.SafeServiceHealthProvider

@RequiresApi(Build.VERSION_CODES.N)
class NetraLiveMonitorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = BatteryService.isServiceRunning.value
        val intent = Intent(this, BatteryService::class.java)
        if (isRunning) {
            stopService(intent)
        } else {
            SafeServiceHealthProvider.safeStartForegroundService(this, intent)
        }
        
        // Update QS Tile UI
        qsTile?.let { tile ->
            val nextState = !isRunning
            tile.state = if (nextState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (nextState) "Monitoring Active" else "Paused"
            }
            tile.updateTile()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = BatteryService.isServiceRunning.value
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Live Monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) "24/7 Active" else "Paused"
        }
        try {
            tile.icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
        } catch (e: Exception) {
            // Retain default icon
        }
        tile.updateTile()
    }
}

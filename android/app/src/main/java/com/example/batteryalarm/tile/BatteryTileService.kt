package com.example.batteryalarm.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.batteryalarm.service.BatteryAlertService
import com.example.batteryalarm.settings.SettingsRepository
import kotlinx.coroutines.runBlocking

class BatteryTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        runBlocking {
            val repository = SettingsRepository(applicationContext)
            val current = repository.getOnce()
            val next = !current.monitoringEnabled
            repository.update { it.copy(monitoringEnabled = next) }

            if (next) {
                BatteryAlertService.start(applicationContext)
            } else {
                BatteryAlertService.stop(applicationContext)
            }
        }

        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = runBlocking {
            SettingsRepository(applicationContext).getOnce().monitoringEnabled
        }

        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Waza Battery"
        tile.contentDescription = if (enabled) {
            "Waza Battery Alarm monitoring is active"
        } else {
            "Waza Battery Alarm monitoring is off"
        }
        tile.updateTile()
    }
}

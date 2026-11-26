package com.example.batteryalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.batteryalarm.service.BatteryAlertService

class PowerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> BatteryAlertService.ACTION_CHARGING_CONNECTED
            Intent.ACTION_POWER_DISCONNECTED -> BatteryAlertService.ACTION_CHARGING_DISCONNECTED
            else -> return
        }

        BatteryAlertService.send(context.applicationContext, action)
    }
}

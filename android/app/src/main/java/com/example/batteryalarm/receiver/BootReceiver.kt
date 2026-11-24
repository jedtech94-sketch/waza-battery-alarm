package com.example.batteryalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.batteryalarm.service.BatteryAlertService
import com.example.batteryalarm.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        receiverScope.launch {
            try {
                val settings = withContext(Dispatchers.IO) {
                    SettingsRepository(context.applicationContext).getOnce()
                }
                if (settings.monitoringEnabled) {
                    BatteryAlertService.start(context.applicationContext)
                }
            } catch (_: Exception) {
                // If Android blocks a background start, the user can re-enable from the app.
            } finally {
                pendingResult.finish()
                receiverScope.cancel()
            }
        }
    }
}

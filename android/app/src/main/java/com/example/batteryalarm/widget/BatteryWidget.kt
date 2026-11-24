package com.example.batteryalarm.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.batteryalarm.MainActivity
import com.example.batteryalarm.monitor.BatterySnapshot
import com.example.batteryalarm.service.BatteryAlertService
import com.example.batteryalarm.settings.AppSettings
import com.example.batteryalarm.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}

class BatteryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = withContext(Dispatchers.IO) {
            SettingsRepository(context).getOnce()
        }
        val snapshot = withContext(Dispatchers.IO) {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            sticky?.let { BatterySnapshot.from(it) }
        }

        provideContent {
            WidgetContent(settings, snapshot)
        }
    }

    @Composable
    private fun WidgetContent(settings: AppSettings, snapshot: BatterySnapshot?) {
        val context = LocalContext.current
        val toggleIntent = Intent(context, BatteryAlertService::class.java).apply {
            this.action = BatteryAlertService.ACTION_TOGGLE
        }

        Column(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Waza Battery Alarm",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = "${snapshot?.percent?.let { "$it%" } ?: "—"} · ${snapshot?.chargingLabel ?: "Unknown"}",
                style = TextStyle(fontSize = 18.sp),
            )
            Text(
                text = if (settings.monitoringEnabled) "Monitoring active" else "Monitoring off",
                style = TextStyle(fontSize = 12.sp),
            )
            Row {
                Button(
                    text = if (settings.monitoringEnabled) "Stop" else "Start",
                    onClick = actionStartService(toggleIntent, isForegroundService = true),
                )
                Button(
                    text = "Open",
                    onClick = actionStartActivity<MainActivity>(),
                )
            }
        }
    }
}

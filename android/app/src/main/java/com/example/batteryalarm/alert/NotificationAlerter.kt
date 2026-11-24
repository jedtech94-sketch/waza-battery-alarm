package com.example.batteryalarm.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.batteryalarm.MainActivity
import com.example.batteryalarm.R
import com.example.batteryalarm.monitor.BatterySnapshot
import com.example.batteryalarm.service.BatteryAlertService

class NotificationAlerter(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    fun buildMonitoringNotification(
        snapshot: BatterySnapshot?,
        enabled: Boolean,
        timeLeftMinutes: Int? = null,
    ): Notification {
        val title = if (enabled) "Waza Battery Alarm" else "Waza Battery Alarm — paused"
        val text = if (snapshot == null) {
            "Waiting for battery update…"
        } else {
            val power = snapshot.chargingLabel
            val timeLeft = if (!snapshot.isCharging && timeLeftMinutes != null) {
                " · ~${timeLeftMinutes}m left"
            } else {
                ""
            }
            "Battery ${snapshot.percent}% · $power$timeLeft"
        }

        return baseBuilder(CHANNEL_MONITORING)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    fun updateMonitoring(snapshot: BatterySnapshot?, enabled: Boolean, timeLeftMinutes: Int? = null) {
        runCatching {
            manager.notify(
                ID_MONITORING,
                buildMonitoringNotification(snapshot, enabled, timeLeftMinutes),
            )
        }
    }

    fun showAlert(
        snapshot: BatterySnapshot,
        message: String,
        severity: AlertSeverity,
        kind: AlertKind,
    ) {
        if (!manager.areNotificationsEnabled()) return

        val title = when (kind) {
            AlertKind.LOW_BATTERY -> when (severity) {
                AlertSeverity.CRITICAL -> "Battery critical — connect the charger"
                AlertSeverity.WARNING -> "Battery very low — connect the charger"
                AlertSeverity.REMINDER -> "Low battery — connect the charger"
            }
            AlertKind.UNPLUG -> "Battery target reached"
            AlertKind.SLOW_CHARGE -> "Slow charger detected"
        }

        val builder = baseBuilder(channelFor(severity))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentInfo("${snapshot.percent}%")
            .setPriority(
                if (severity == AlertSeverity.CRITICAL) {
                    NotificationCompat.PRIORITY_MAX
                } else {
                    NotificationCompat.PRIORITY_HIGH
                },
            )
            .setCategory(
                if (kind == AlertKind.LOW_BATTERY) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_STATUS
                },
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setVibrate(longArrayOf(0, 500, 250, 500))

        if (kind == AlertKind.LOW_BATTERY) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Stop alarm",
                servicePendingIntent(BatteryAlertService.ACTION_STOP, REQUEST_STOP),
            )
            builder.addAction(
                android.R.drawable.ic_media_ff,
                "Snooze",
                servicePendingIntent(BatteryAlertService.ACTION_SNOOZE, REQUEST_SNOOZE),
            )
        }

        manager.notify(ID_ALERT, builder.build())
    }

    fun clearAlert() {
        manager.cancel(ID_ALERT)
    }

    private fun channelFor(severity: AlertSeverity): String = when (severity) {
        AlertSeverity.REMINDER -> CHANNEL_REMINDER
        AlertSeverity.WARNING -> CHANNEL_WARNING
        AlertSeverity.CRITICAL -> CHANNEL_CRITICAL
    }

    private fun baseBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentIntent(openAppPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, BatteryAlertService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITORING,
                "Battery monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing status while Waza Battery Alarm is monitoring"
                setSound(null, null)
            },
        )

        val channelNames = mapOf(
            CHANNEL_REMINDER to "Low battery reminders",
            CHANNEL_WARNING to "Low battery warnings",
            CHANNEL_CRITICAL to "Critical battery alerts",
        )

        listOf(
            CHANNEL_REMINDER to NotificationManager.IMPORTANCE_HIGH,
            CHANNEL_WARNING to NotificationManager.IMPORTANCE_HIGH,
            CHANNEL_CRITICAL to NotificationManager.IMPORTANCE_MAX,
        ).forEach { (id, importance) ->
            notificationManager.createNotificationChannel(
                NotificationChannel(id, channelNames[id] ?: id, importance).apply {
                    description = "Low battery reminder level: $id"
                    enableVibration(true)
                    setSound(null, null)
                },
            )
        }
    }

    private companion object {
        const val CHANNEL_MONITORING = "battery_monitoring"
        const val CHANNEL_REMINDER = "battery_reminder"
        const val CHANNEL_WARNING = "battery_warning"
        const val CHANNEL_CRITICAL = "battery_critical"

        const val ID_MONITORING = 1001
        const val ID_ALERT = 1002

        const val REQUEST_OPEN = 10
        const val REQUEST_STOP = 11
        const val REQUEST_SNOOZE = 12
    }
}

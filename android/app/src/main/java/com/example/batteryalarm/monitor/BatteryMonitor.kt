package com.example.batteryalarm.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class BatteryMonitor(
    private val context: Context,
    private val onSnapshot: (BatterySnapshot) -> Unit,
) {
    private val appContext = context.applicationContext
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val snapshot = intent?.let { BatterySnapshot.from(it) }
            if (snapshot != null) {
                onSnapshot(snapshot)
            } else {
                readSticky()
            }
        }
    }

    fun start() {
        if (started) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        started = true
        readSticky()
    }

    fun peek(): BatterySnapshot? {
        val sticky = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return sticky?.let { BatterySnapshot.from(it) }
    }

    fun readSticky() {
        peek()?.let(onSnapshot)
    }

    fun stop() {
        if (!started) return
        runCatching { appContext.unregisterReceiver(receiver) }
        started = false
    }
}

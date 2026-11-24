package com.example.batteryalarm.monitor

import android.content.Intent
import android.os.BatteryManager

data class BatterySnapshot(
    val percent: Int,
    val isCharging: Boolean,
    val plugType: Int,
    val status: Int,
    val temperatureCelsius: Float?,
    val health: Int,
) {
    val chargingLabel: String
        get() = if (isCharging) "Charging" else "On battery"

    companion object {
        fun from(intent: Intent): BatterySnapshot? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null

            val percent = ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
            val plugType = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val status = intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN,
            )
            val temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val temperature = if (temperatureTenths == Int.MIN_VALUE) {
                null
            } else {
                temperatureTenths / 10f
            }
            val health = intent.getIntExtra(
                BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN,
            )

            return BatterySnapshot(
                percent = percent,
                isCharging = plugType != 0,
                plugType = plugType,
                status = status,
                temperatureCelsius = temperature,
                health = health,
            )
        }
    }
}

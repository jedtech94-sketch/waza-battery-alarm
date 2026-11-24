package com.example.batteryalarm.monitor

import kotlin.math.roundToInt

class BatteryHistory {

    private var lastLevel: Int? = null
    private var lastLevelChangeAtMs = 0L
    private var minutesPerPercent: Double? = null

    fun update(snapshot: BatterySnapshot, now: Long): Double? {
        if (snapshot.isCharging) return null

        val level = snapshot.percent
        val previous = lastLevel

        if (previous == null) {
            lastLevel = level
            lastLevelChangeAtMs = now
            return minutesPerPercent
        }

        if (level < previous) {
            val drop = previous - level
            val elapsedMs = (now - lastLevelChangeAtMs).coerceIn(1_000L, 3_600_000L)
            val sample = elapsedMs / 60_000.0 / drop
            minutesPerPercent = if (minutesPerPercent == null) {
                sample
            } else {
                minutesPerPercent!! * 0.7 + sample * 0.3
            }
            lastLevel = level
            lastLevelChangeAtMs = now
        } else if (level > previous) {
            lastLevel = level
            lastLevelChangeAtMs = now
            minutesPerPercent = null
        }

        return minutesPerPercent
    }

    fun estimateMinutesLeft(percent: Int): Int? {
        val rate = minutesPerPercent ?: return null
        return (percent * rate).roundToInt().coerceIn(1, 1_440)
    }

    fun reset() {
        lastLevel = null
        lastLevelChangeAtMs = 0L
        minutesPerPercent = null
    }
}

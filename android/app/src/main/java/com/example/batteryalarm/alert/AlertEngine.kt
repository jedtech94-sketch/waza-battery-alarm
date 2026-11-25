package com.example.batteryalarm.alert

import com.example.batteryalarm.monitor.BatterySnapshot
import com.example.batteryalarm.settings.AppSettings
import java.util.Calendar
import kotlin.math.max

enum class AlertSeverity(val channelKey: String) {
    REMINDER("reminder"),
    WARNING("warning"),
    CRITICAL("critical"),
}

enum class AlertKind {
    LOW_BATTERY,
    UNPLUG,
    SLOW_CHARGE,
}

data class AlertDecision(
    val kind: AlertKind? = null,
    val severity: AlertSeverity = AlertSeverity.REMINDER,
    val shouldClear: Boolean = false,
    val silent: Boolean = false,
    val timeLeftMinutes: Int? = null,
    val emergency: Boolean = false,
) {
    val shouldAlert: Boolean
        get() = kind != null
}

class AlertEngine(private val hysteresisPercent: Int = 5) {

    private val lastAlertAt = mutableMapOf<AlertSeverity, Long>()
    private var snoozedUntil = 0L
    private var chargingSessionStartLevel: Int? = null
    private var chargingSessionStartAt = 0L
    private var unplugAlerted = false
    private var slowChargeAlerted = false
    private var emergencyActive = false
    private var lastEmergencyAt = 0L

    fun evaluate(
        snapshot: BatterySnapshot,
        settings: AppSettings,
        now: Long,
        timeLeftMinutes: Int? = null,
    ): AlertDecision {
        if (!settings.monitoringEnabled) {
            reset()
            return AlertDecision(shouldClear = true)
        }

        if (snapshot.isCharging) {
            if (emergencyActive) {
                emergencyActive = false
                lastEmergencyAt = 0L
            }
            return evaluateCharging(snapshot, settings, now)
        }

        val emergencyLevel = settings.emergencyPercent.coerceIn(1, 20)

        if (emergencyActive || snapshot.percent <= emergencyLevel) {
            emergencyActive = true
            if (now - lastEmergencyAt >= EMERGENCY_REPEAT_MS) {
                lastEmergencyAt = now
                return AlertDecision(
                    kind = AlertKind.LOW_BATTERY,
                    severity = AlertSeverity.CRITICAL,
                    silent = false,
                    emergency = true,
                )
            }
            return AlertDecision()
        }

        return if (settings.simpleMode) {
            evaluateSimple(snapshot, settings, now)
        } else {
            evaluateAdvanced(snapshot, settings, now, timeLeftMinutes)
        }
    }

    private fun evaluateSimple(
        snapshot: BatterySnapshot,
        settings: AppSettings,
        now: Long,
    ): AlertDecision {
        chargingSessionStartLevel = null
        unplugAlerted = false
        slowChargeAlerted = false

        val resetLevel = settings.thresholdPercent + hysteresisPercent
        if (snapshot.percent > resetLevel) {
            resetLowBatteryState()
            return AlertDecision(shouldClear = true)
        }

        if (snapshot.percent > settings.thresholdPercent) {
            return AlertDecision()
        }

        val repeatIntervalMs = max(1, settings.repeatIntervalMinutes) * 60_000L
        val last = lastAlertAt[AlertSeverity.REMINDER] ?: 0L

        if (now >= snoozedUntil && now - last >= repeatIntervalMs) {
            lastAlertAt[AlertSeverity.REMINDER] = now
            return AlertDecision(
                kind = AlertKind.LOW_BATTERY,
                severity = AlertSeverity.REMINDER,
                silent = false,
            )
        }

        return AlertDecision()
    }

    private fun evaluateAdvanced(
        snapshot: BatterySnapshot,
        settings: AppSettings,
        now: Long,
        timeLeftMinutes: Int?,
    ): AlertDecision {
        chargingSessionStartLevel = null
        unplugAlerted = false
        slowChargeAlerted = false

        val resetLevel = settings.thresholdPercent + hysteresisPercent
        if (snapshot.percent > resetLevel) {
            resetLowBatteryState()
            return AlertDecision(shouldClear = true)
        }

        if (snapshot.percent > settings.thresholdPercent) {
            return AlertDecision()
        }

        val severity = severityFor(snapshot.percent, settings)
        val quiet = isQuietHours(now, settings)
        val silent = quiet && !(severity == AlertSeverity.CRITICAL && settings.criticalOverridesQuietHours)

        val repeatIntervalMs = max(1, settings.repeatIntervalMinutes) * 60_000L
        val last = lastAlertAt[severity] ?: 0L

        if (now >= snoozedUntil && now - last >= repeatIntervalMs) {
            lastAlertAt[severity] = now
            return AlertDecision(
                kind = AlertKind.LOW_BATTERY,
                severity = severity,
                silent = silent,
                timeLeftMinutes = if (settings.includeTimeLeft) timeLeftMinutes else null,
            )
        }

        return AlertDecision()
    }

    private fun evaluateCharging(
        snapshot: BatterySnapshot,
        settings: AppSettings,
        now: Long,
    ): AlertDecision {
        if (settings.simpleMode) {
            resetLowBatteryState()
            return AlertDecision(shouldClear = true)
        }

        if (chargingSessionStartLevel == null) {
            chargingSessionStartLevel = snapshot.percent
            chargingSessionStartAt = now
            unplugAlerted = false
            slowChargeAlerted = false
            resetLowBatteryState()
        }

        val quiet = isQuietHours(now, settings)

        if (
            settings.unplugReminderEnabled &&
            snapshot.percent >= settings.unplugTargetPercent &&
            !unplugAlerted &&
            now - chargingSessionStartAt >= 1_000L
        ) {
            unplugAlerted = true
            return AlertDecision(
                kind = AlertKind.UNPLUG,
                severity = AlertSeverity.REMINDER,
                silent = quiet,
            )
        }

        val checkMs = max(1, settings.slowChargeCheckMinutes) * 60_000L
        val startLevel = chargingSessionStartLevel ?: snapshot.percent
        val levelGain = snapshot.percent - startLevel

        if (
            settings.slowChargeWarningEnabled &&
            snapshot.percent < 95 &&
            !slowChargeAlerted &&
            now - chargingSessionStartAt >= checkMs &&
            levelGain < 1
        ) {
            slowChargeAlerted = true
            return AlertDecision(
                kind = AlertKind.SLOW_CHARGE,
                severity = AlertSeverity.WARNING,
                silent = quiet,
            )
        }

        return AlertDecision(shouldClear = true)
    }

    private fun severityFor(percent: Int, settings: AppSettings): AlertSeverity {
        if (!settings.alertLevelsEnabled) return AlertSeverity.REMINDER

        return when {
            percent <= settings.criticalPercent.coerceAtLeast(1) -> AlertSeverity.CRITICAL
            percent <= settings.warningPercent.coerceAtLeast(1) -> AlertSeverity.WARNING
            else -> AlertSeverity.REMINDER
        }
    }

    private fun isQuietHours(now: Long, settings: AppSettings): Boolean {
        if (!settings.quietHoursEnabled) return false

        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val start = settings.quietStartHour.coerceIn(0, 23)
        val end = settings.quietEndHour.coerceIn(0, 23)

        return if (start <= end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }

    fun snooze(minutes: Int, now: Long = System.currentTimeMillis()) {
        snoozedUntil = now + max(1, minutes) * 60_000L
    }

    private fun resetLowBatteryState() {
        lastAlertAt.clear()
        snoozedUntil = 0L
    }

    fun reset() {
        resetLowBatteryState()
        chargingSessionStartLevel = null
        chargingSessionStartAt = 0L
        unplugAlerted = false
        slowChargeAlerted = false
        emergencyActive = false
        lastEmergencyAt = 0L
    }

    private companion object {
        const val EMERGENCY_REPEAT_MS = 10_000L
    }
}

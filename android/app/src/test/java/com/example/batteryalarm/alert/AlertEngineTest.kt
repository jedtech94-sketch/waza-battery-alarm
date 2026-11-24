package com.example.batteryalarm.alert

import com.example.batteryalarm.monitor.BatterySnapshot
import com.example.batteryalarm.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {

    private fun snapshot(percent: Int, charging: Boolean = false) = BatterySnapshot(
        percent = percent,
        isCharging = charging,
        plugType = if (charging) 1 else 0,
        status = 0,
        temperatureCelsius = null,
        health = 0,
    )

    private fun settings(threshold: Int = 20, repeatMinutes: Int = 5) = AppSettings(
        monitoringEnabled = true,
        thresholdPercent = threshold,
        warningPercent = 10,
        criticalPercent = 5,
        alertLevelsEnabled = true,
        repeatIntervalMinutes = repeatMinutes,
        quietHoursEnabled = false,
    )

    @Test
    fun `alerts when battery is below threshold`() {
        val decision = AlertEngine().evaluate(
            snapshot(percent = 19),
            settings(),
            now = 1_000_000L,
        )

        assertTrue(decision.shouldAlert)
        assertEquals(AlertKind.LOW_BATTERY, decision.kind)
        assertEquals(AlertSeverity.REMINDER, decision.severity)
    }

    @Test
    fun `uses warning and critical levels`() {
        val engine = AlertEngine()
        val settings = settings()

        assertEquals(
            AlertSeverity.WARNING,
            engine.evaluate(snapshot(10), settings, 1_000_000L).severity,
        )
        assertEquals(
            AlertSeverity.CRITICAL,
            engine.evaluate(snapshot(5), settings, 2_000_000L).severity,
        )
    }

    @Test
    fun `does not alert while charging and clears existing alert`() {
        val decision = AlertEngine().evaluate(
            snapshot(percent = 10, charging = true),
            settings(),
            now = 1_000_000L,
        )

        assertFalse(decision.shouldAlert)
        assertTrue(decision.shouldClear)
    }

    @Test
    fun `respects repeat interval`() {
        val engine = AlertEngine()
        val settings = settings(repeatMinutes = 5)

        assertTrue(engine.evaluate(snapshot(15), settings, now = 1_000_000L).shouldAlert)
        assertFalse(engine.evaluate(snapshot(15), settings, now = 1_060_000L).shouldAlert)
        assertTrue(engine.evaluate(snapshot(15), settings, now = 1_301_000L).shouldAlert)
    }

    @Test
    fun `rearms after recovery above threshold plus hysteresis`() {
        val engine = AlertEngine()
        val settings = settings()

        assertTrue(engine.evaluate(snapshot(18), settings, now = 1_000_000L).shouldAlert)
        assertFalse(engine.evaluate(snapshot(22), settings, now = 1_010_000L).shouldAlert)
        assertTrue(engine.evaluate(snapshot(26), settings, now = 1_020_000L).shouldClear)
        assertTrue(engine.evaluate(snapshot(18), settings, now = 1_030_000L).shouldAlert)
    }

    @Test
    fun `unplug reminder fires once per charge`() {
        val engine = AlertEngine()
        val settings = settings().copy(unplugTargetPercent = 80)

        engine.evaluate(snapshot(75, charging = true), settings, 1_000_000L)
        val decision = engine.evaluate(snapshot(80, charging = true), settings, 1_010_000L)

        assertEquals(AlertKind.UNPLUG, decision.kind)
        assertFalse(engine.evaluate(snapshot(81, charging = true), settings, 1_020_000L).shouldAlert)
    }

    @Test
    fun `slow charge warning fires after check window`() {
        val engine = AlertEngine()
        val settings = settings().copy(slowChargeCheckMinutes = 5)

        engine.evaluate(snapshot(70, charging = true), settings, 1_000_000L)
        val decision = engine.evaluate(
            snapshot(70, charging = true),
            settings,
            1_000_000L + 6 * 60_000L,
        )

        assertEquals(AlertKind.SLOW_CHARGE, decision.kind)
    }
}

package com.example.batteryalarm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.batteryalarm.alert.AlertDecision
import com.example.batteryalarm.alert.AlertEngine
import com.example.batteryalarm.alert.AlertKind
import com.example.batteryalarm.alert.AlertSeverity
import com.example.batteryalarm.alert.NotificationAlerter
import com.example.batteryalarm.alert.SoundAlerter
import com.example.batteryalarm.alert.VoiceAlerter
import com.example.batteryalarm.monitor.BatteryHistory
import com.example.batteryalarm.monitor.BatteryMonitor
import com.example.batteryalarm.monitor.BatterySnapshot
import com.example.batteryalarm.settings.AppSettings
import com.example.batteryalarm.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatteryAlertService : Service() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var batteryHistory: BatteryHistory
    private lateinit var alertEngine: AlertEngine
    private lateinit var voiceAlerter: VoiceAlerter
    private lateinit var soundAlerter: SoundAlerter
    private lateinit var notificationAlerter: NotificationAlerter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager: AudioManager by lazy { getSystemService(AudioManager::class.java) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WazaBatteryAlarm:emergency")
    }

    private var settings = AppSettings()
    private var snapshot: BatterySnapshot? = null
    private var timeLeftMinutes: Int? = null
    private var settingsObserver: Job? = null
    private var tickerJob: Job? = null
    private var foregroundStarted = false
    private var monitorStarted = false
    private var lastKnownCharging: Boolean? = null
    private var appliedSoundKey: String? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        batteryMonitor = BatteryMonitor(this) { battery -> onBatteryChanged(battery) }
        batteryHistory = BatteryHistory()
        alertEngine = AlertEngine()
        voiceAlerter = VoiceAlerter(this)
        soundAlerter = SoundAlerter(this)
        notificationAlerter = NotificationAlerter(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        if (action == ACTION_TOGGLE) {
            scope.launch {
                val current = withContext(Dispatchers.IO) { settingsRepository.getOnce() }
                val next = !current.monitoringEnabled
                withContext(Dispatchers.IO) {
                    settingsRepository.update { it.copy(monitoringEnabled = next) }
                }

                if (!next) {
                    stopMonitoring()
                    return@launch
                }

                settings = current.copy(monitoringEnabled = true)
                startForegroundIfNeeded()
                observeSettings()
                ensureMonitorStarted()
                startTickerIfNeeded()
                evaluateNow()
            }
            return START_STICKY
        }

        startForegroundIfNeeded()

        scope.launch {
            settings = withContext(Dispatchers.IO) { settingsRepository.getOnce() }

            if (action == ACTION_TEST) {
                val monitoringWasActive = monitorStarted
                snapshot = batteryMonitor.peek()
                val testBase = if (settings.simpleMode) {
                    "Battery low. Connect the charger"
                } else {
                    settings.voiceMessage
                }
                playAlertMessage(
                    message = withPercent(testBase, snapshot?.percent),
                    severity = AlertSeverity.REMINDER,
                    kind = AlertKind.LOW_BATTERY,
                    silent = false,
                )

                if (!monitoringWasActive) {
                    delay(TEST_ALERT_DURATION_MS)
                    stopMonitoring()
                }
                return@launch
            }

            observeSettings()
            ensureMonitorStarted()
            startTickerIfNeeded()

            when (action) {
                ACTION_SNOOZE -> {
                    alertEngine.snooze(settings.snoozeMinutes)
                    clearAlert()
                }

                else -> evaluateNow()
            }
        }

        return if (action == ACTION_TEST) START_NOT_STICKY else START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        batteryMonitor.stop()
        settingsObserver?.cancel()
        tickerJob?.cancel()
        clearAlert()
        voiceAlerter.shutdown()
        soundAlerter.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundIfNeeded() {
        if (foregroundStarted) return

        val notification = notificationAlerter.buildMonitoringNotification(
            snapshot,
            settings.monitoringEnabled,
            timeLeftMinutes,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        foregroundStarted = true
    }

    private fun observeSettings() {
        settingsObserver?.cancel()
        settingsObserver = scope.launch {
            settingsRepository.settingsFlow.collect { updated ->
                settings = updated
                voiceAlerter.applyVoice(updated.selectedVoiceName)
                ensureSound(if (updated.simpleMode) "built_in" else updated.selectedSoundKey)

                if (!updated.monitoringEnabled) {
                    if (monitorStarted) stopMonitoring()
                    return@collect
                }

                notificationAlerter.updateMonitoring(snapshot, enabled = true, timeLeftMinutes)
                evaluateNow()
            }
        }
    }

    private fun ensureMonitorStarted() {
        if (monitorStarted) return
        batteryMonitor.start()
        monitorStarted = true
    }

    private fun startTickerIfNeeded() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                batteryMonitor.readSticky()
                evaluateNow()
            }
        }
    }

    private fun onBatteryChanged(battery: BatterySnapshot) {
        snapshot = battery
        val now = System.currentTimeMillis()

        if (battery.isCharging && lastKnownCharging == false) {
            batteryHistory.reset()
        }

        timeLeftMinutes = if (battery.isCharging) {
            null
        } else {
            batteryHistory.update(battery, now)?.let { batteryHistory.estimateMinutesLeft(battery.percent) }
        }

        startForegroundIfNeeded()
        notificationAlerter.updateMonitoring(battery, settings.monitoringEnabled, timeLeftMinutes)

        val justConnected = battery.isCharging && lastKnownCharging == false
        lastKnownCharging = battery.isCharging

        evaluateNow()

        if (justConnected && settings.monitoringEnabled && settings.voiceEnabled) {
            maximizeAlertVolume()
            voiceAlerter.speak(
                settings.chargerConnectedMessage.ifBlank { "Charger connected" },
            )
        }
    }

    private fun evaluateNow() {
        val current = snapshot ?: return
        val decision = alertEngine.evaluate(
            snapshot = current,
            settings = settings,
            now = System.currentTimeMillis(),
            timeLeftMinutes = timeLeftMinutes,
        )

        if (decision.shouldAlert) {
            playAlert(decision)
        }

        if (decision.shouldClear) {
            clearAlert()
        }
    }

    private fun playAlert(decision: AlertDecision) {
        playAlertMessage(
            message = messageFor(decision),
            severity = decision.severity,
            kind = decision.kind ?: AlertKind.LOW_BATTERY,
            silent = decision.silent,
            emergency = decision.emergency,
        )
    }

    private fun messageFor(decision: AlertDecision): String {
        val percent = snapshot?.percent

        if (decision.emergency) {
            return withPercent("Battery critical. Connect the charger now", percent)
        }

        if (settings.simpleMode && decision.kind == AlertKind.LOW_BATTERY) {
            return withPercent("Battery low. Connect the charger", percent)
        }

        return when (decision.kind) {
            AlertKind.UNPLUG -> withPercent(settings.unplugMessage, percent)
            AlertKind.SLOW_CHARGE -> withPercent(settings.slowChargeMessage, percent)
            AlertKind.LOW_BATTERY, null -> {
                val base = when (decision.severity) {
                    AlertSeverity.CRITICAL -> settings.criticalMessage
                    AlertSeverity.WARNING -> settings.warningMessage
                    AlertSeverity.REMINDER -> settings.voiceMessage
                }
                val timeLeft = if (decision.timeLeftMinutes != null) {
                    " About ${decision.timeLeftMinutes} minutes left."
                } else {
                    ""
                }
                "${withPercent(base, percent).trimEnd()}$timeLeft"
            }
        }
    }

    private fun withPercent(base: String, percent: Int?): String {
        val cleaned = base.trim().trimEnd('.', '!', '?')
        val battery = if (percent == null) "Battery is low." else "Battery is at $percent percent."
        return "$cleaned. $battery"
    }

    private fun playAlertMessage(
        message: String,
        severity: AlertSeverity,
        kind: AlertKind,
        silent: Boolean,
        emergency: Boolean = false,
    ) {
        val soundKey = if (emergency || settings.simpleMode) "built_in" else settings.selectedSoundKey
        ensureSound(soundKey)

        if (emergency) {
            runCatching { wakeLock.acquire(EMERGENCY_WAKE_LOCK_MS) }
        }

        if (!silent) {
            maximizeAlertVolume()
        }

        if (settings.voiceEnabled && !silent) voiceAlerter.speak(message)
        if (settings.soundEnabled && !silent) soundAlerter.play()

        if (settings.notificationEnabled) {
            snapshot?.let { notificationAlerter.showAlert(it, message, severity, kind) }
        }
    }

    private fun ensureSound(key: String) {
        if (appliedSoundKey == key) return
        soundAlerter.setSound(key)
        appliedSoundKey = key
    }

    private fun maximizeAlertVolume() {
        runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
    }

    private fun clearAlert() {
        voiceAlerter.stop()
        soundAlerter.stop()
        notificationAlerter.clearAlert()
    }

    private fun stopMonitoring() {
        alertEngine.reset()
        batteryHistory.reset()
        clearAlert()
        batteryMonitor.stop()
        monitorStarted = false
        lastKnownCharging = null
        settingsObserver?.cancel()
        tickerJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.example.batteryalarm.action.START"
        const val ACTION_STOP = "com.example.batteryalarm.action.STOP"
        const val ACTION_TOGGLE = "com.example.batteryalarm.action.TOGGLE"
        const val ACTION_SNOOZE = "com.example.batteryalarm.action.SNOOZE"
        const val ACTION_TEST = "com.example.batteryalarm.action.TEST"

        private const val NOTIFICATION_ID = 1001
        private const val TICK_INTERVAL_MS = 5_000L
        private const val TEST_ALERT_DURATION_MS = 8_000L
        private const val EMERGENCY_WAKE_LOCK_MS = 15_000L

        fun start(context: Context) {
            val intent = Intent(context, BatteryAlertService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, BatteryAlertService::class.java).apply {
                this.action = action
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BatteryAlertService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
}

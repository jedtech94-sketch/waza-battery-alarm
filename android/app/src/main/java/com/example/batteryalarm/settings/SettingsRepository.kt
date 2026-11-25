package com.example.batteryalarm.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "battery_alarm_settings")

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        preferences.toSettings()
    }

    suspend fun getOnce(): AppSettings = context.dataStore.data.first().toSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.updateData { current ->
            transform(current.toSettings()).writeTo(current)
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        monitoringEnabled = this[Keys.MONITORING_ENABLED] ?: false,
        thresholdPercent = this[Keys.THRESHOLD_PERCENT] ?: 20,
        warningPercent = this[Keys.WARNING_PERCENT] ?: 10,
        criticalPercent = this[Keys.CRITICAL_PERCENT] ?: 6,
        emergencyPercent = this[Keys.EMERGENCY_PERCENT] ?: 6,
        simpleMode = this[Keys.SIMPLE_MODE] ?: true,
        alertLevelsEnabled = this[Keys.ALERT_LEVELS_ENABLED] ?: true,
        repeatIntervalMinutes = this[Keys.REPEAT_INTERVAL_MINUTES] ?: 5,
        snoozeMinutes = this[Keys.SNOOZE_MINUTES] ?: 5,
        voiceMessage = this[Keys.VOICE_MESSAGE] ?: "Please connect the charger",
        warningMessage = this[Keys.WARNING_MESSAGE] ?: "Battery is very low. Connect the charger now.",
        criticalMessage = this[Keys.CRITICAL_MESSAGE] ?: "Battery critical. Connect the charger immediately.",
        chargerConnectedMessage = this[Keys.CHARGER_CONNECTED_MESSAGE] ?: "Charger connected",
        quietHoursEnabled = this[Keys.QUIET_HOURS_ENABLED] ?: true,
        quietStartHour = this[Keys.QUIET_START_HOUR] ?: 22,
        quietEndHour = this[Keys.QUIET_END_HOUR] ?: 7,
        criticalOverridesQuietHours = this[Keys.CRITICAL_OVERRIDES_QUIET] ?: true,
        unplugReminderEnabled = this[Keys.UNPLUG_REMINDER_ENABLED] ?: true,
        unplugTargetPercent = this[Keys.UNPLUG_TARGET_PERCENT] ?: 80,
        unplugMessage = this[Keys.UNPLUG_MESSAGE] ?: "Battery reached the target. You can unplug the charger.",
        slowChargeWarningEnabled = this[Keys.SLOW_CHARGE_ENABLED] ?: true,
        slowChargeCheckMinutes = this[Keys.SLOW_CHARGE_CHECK_MINUTES] ?: 7,
        slowChargeMessage = this[Keys.SLOW_CHARGE_MESSAGE] ?: "Slow charger detected. Check the cable.",
        includeTimeLeft = this[Keys.INCLUDE_TIME_LEFT] ?: true,
        selectedVoiceName = this[Keys.SELECTED_VOICE_NAME] ?: "",
        selectedSoundKey = this[Keys.SELECTED_SOUND_KEY] ?: "built_in",
        themeMode = this[Keys.THEME_MODE] ?: "system",
        voiceEnabled = this[Keys.VOICE_ENABLED] ?: true,
        soundEnabled = this[Keys.SOUND_ENABLED] ?: true,
        notificationEnabled = this[Keys.NOTIFICATION_ENABLED] ?: true,
    )

    private fun AppSettings.writeTo(base: Preferences): Preferences = base.toMutablePreferences().apply {
        this[Keys.MONITORING_ENABLED] = monitoringEnabled
        this[Keys.THRESHOLD_PERCENT] = thresholdPercent.coerceIn(5, 50)
        this[Keys.WARNING_PERCENT] = warningPercent.coerceIn(1, 50)
        this[Keys.CRITICAL_PERCENT] = criticalPercent.coerceIn(1, 50)
        this[Keys.EMERGENCY_PERCENT] = emergencyPercent.coerceIn(1, 20)
        this[Keys.SIMPLE_MODE] = simpleMode
        this[Keys.ALERT_LEVELS_ENABLED] = alertLevelsEnabled
        this[Keys.REPEAT_INTERVAL_MINUTES] = repeatIntervalMinutes.coerceIn(1, 60)
        this[Keys.SNOOZE_MINUTES] = snoozeMinutes.coerceIn(1, 60)
        this[Keys.VOICE_MESSAGE] = voiceMessage.ifBlank { "Please connect the charger" }
        this[Keys.WARNING_MESSAGE] = warningMessage.ifBlank { "Battery is very low. Connect the charger now." }
        this[Keys.CRITICAL_MESSAGE] = criticalMessage.ifBlank { "Battery critical. Connect the charger immediately." }
        this[Keys.CHARGER_CONNECTED_MESSAGE] = chargerConnectedMessage.ifBlank { "Charger connected" }
        this[Keys.QUIET_HOURS_ENABLED] = quietHoursEnabled
        this[Keys.QUIET_START_HOUR] = quietStartHour.coerceIn(0, 23)
        this[Keys.QUIET_END_HOUR] = quietEndHour.coerceIn(0, 23)
        this[Keys.CRITICAL_OVERRIDES_QUIET] = criticalOverridesQuietHours
        this[Keys.UNPLUG_REMINDER_ENABLED] = unplugReminderEnabled
        this[Keys.UNPLUG_TARGET_PERCENT] = unplugTargetPercent.coerceIn(50, 100)
        this[Keys.UNPLUG_MESSAGE] = unplugMessage.ifBlank { "Battery reached the target. You can unplug the charger." }
        this[Keys.SLOW_CHARGE_ENABLED] = slowChargeWarningEnabled
        this[Keys.SLOW_CHARGE_CHECK_MINUTES] = slowChargeCheckMinutes.coerceIn(1, 60)
        this[Keys.SLOW_CHARGE_MESSAGE] = slowChargeMessage.ifBlank { "Slow charger detected. Check the cable." }
        this[Keys.INCLUDE_TIME_LEFT] = includeTimeLeft
        this[Keys.SELECTED_VOICE_NAME] = selectedVoiceName
        this[Keys.SELECTED_SOUND_KEY] = selectedSoundKey
        this[Keys.THEME_MODE] = themeMode
        this[Keys.VOICE_ENABLED] = voiceEnabled
        this[Keys.SOUND_ENABLED] = soundEnabled
        this[Keys.NOTIFICATION_ENABLED] = notificationEnabled
    }.toPreferences()

    private object Keys {
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val THRESHOLD_PERCENT = intPreferencesKey("threshold_percent")
        val WARNING_PERCENT = intPreferencesKey("warning_percent")
        val CRITICAL_PERCENT = intPreferencesKey("critical_percent")
        val EMERGENCY_PERCENT = intPreferencesKey("emergency_percent")
        val SIMPLE_MODE = booleanPreferencesKey("simple_mode")
        val ALERT_LEVELS_ENABLED = booleanPreferencesKey("alert_levels_enabled")
        val REPEAT_INTERVAL_MINUTES = intPreferencesKey("repeat_interval_minutes")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val VOICE_MESSAGE = stringPreferencesKey("voice_message")
        val WARNING_MESSAGE = stringPreferencesKey("warning_message")
        val CRITICAL_MESSAGE = stringPreferencesKey("critical_message")
        val CHARGER_CONNECTED_MESSAGE = stringPreferencesKey("charger_connected_message")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val CRITICAL_OVERRIDES_QUIET = booleanPreferencesKey("critical_overrides_quiet")
        val UNPLUG_REMINDER_ENABLED = booleanPreferencesKey("unplug_reminder_enabled")
        val UNPLUG_TARGET_PERCENT = intPreferencesKey("unplug_target_percent")
        val UNPLUG_MESSAGE = stringPreferencesKey("unplug_message")
        val SLOW_CHARGE_ENABLED = booleanPreferencesKey("slow_charge_enabled")
        val SLOW_CHARGE_CHECK_MINUTES = intPreferencesKey("slow_charge_check_minutes")
        val SLOW_CHARGE_MESSAGE = stringPreferencesKey("slow_charge_message")
        val INCLUDE_TIME_LEFT = booleanPreferencesKey("include_time_left")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val SELECTED_SOUND_KEY = stringPreferencesKey("selected_sound_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
    }
}

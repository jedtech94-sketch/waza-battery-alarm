package com.example.batteryalarm.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.batteryalarm.monitor.BatterySnapshot
import com.example.batteryalarm.service.BatteryAlertService
import com.example.batteryalarm.settings.AppSettings
import com.example.batteryalarm.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _battery = MutableStateFlow<BatterySnapshot?>(null)
    val battery: StateFlow<BatterySnapshot?> = _battery

    fun refreshBattery() {
        _battery.value = readBattery(getApplication())
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.update(transform)
        }
    }

    fun setMonitoring(enabled: Boolean) {
        update { it.copy(monitoringEnabled = enabled) }
        refreshBattery()

        if (enabled) {
            BatteryAlertService.start(getApplication())
        } else {
            BatteryAlertService.stop(getApplication())
        }
    }

    fun testAlert() {
        BatteryAlertService.send(getApplication(), BatteryAlertService.ACTION_TEST)
    }

    fun snooze() {
        BatteryAlertService.send(getApplication(), BatteryAlertService.ACTION_SNOOZE)
    }

    fun resetSettings() {
        BatteryAlertService.stop(getApplication())
        update { AppSettings() }
        refreshBattery()
    }

    private fun readBattery(context: Context): BatterySnapshot? {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return sticky?.let { BatterySnapshot.from(it) }
    }
}

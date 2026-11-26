package com.example.batteryalarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.batteryalarm.monitor.BatterySnapshot
import com.example.batteryalarm.settings.AppSettings

private data class SettingsTab(
    val label: String,
    val icon: ImageVector,
)

private val SETTINGS_TABS = listOf(
    SettingsTab("Alerts", Icons.Filled.Notifications),
    SettingsTab("Charging", Icons.Filled.BatteryChargingFull),
    SettingsTab("Voice", Icons.Filled.RecordVoiceOver),
    SettingsTab("Appearance", Icons.Filled.DarkMode),
    SettingsTab("Advanced", Icons.Filled.Settings),
)

private val THEME_OPTIONS = listOf(
    "system" to "System",
    "light" to "Light",
    "dark" to "Dark",
)

private val SOUND_OPTIONS = listOf(
    "built_in" to "Built-in beep",
    "alarm" to "System alarm",
    "notification" to "System notification",
    "ringtone" to "System ringtone",
    "silent" to "Silent",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()

    val voiceCatalog = remember { VoiceCatalog(context) }
    DisposableEffect(Unit) {
        onDispose { voiceCatalog.close() }
    }
    val voiceNames by voiceCatalog.voices.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var notificationGranted by remember { mutableStateOf(true) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBattery()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Waza Battery Alarm") })
        },
        bottomBar = {
            NavigationBar {
                SETTINGS_TABS.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(battery = battery, settings = settings, onRefresh = { viewModel.refreshBattery() })

            when (selectedTab) {
                0 -> AlertsTab(viewModel, settings)
                1 -> ChargingTab(viewModel, settings)
                2 -> VoiceTab(viewModel, settings, voiceNames)
                3 -> AppearanceTab(viewModel, settings)
                4 -> AdvancedTab(
                    context = context,
                    viewModel = viewModel,
                    notificationGranted = notificationGranted,
                    onRequestNotification = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    battery: BatterySnapshot?,
    settings: AppSettings,
    onRefresh: () -> Unit,
) {
    SectionCard {
        Text("Status", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Battery: ${battery?.percent?.let { "$it%" } ?: "—"}")
        Text("Power: ${battery?.chargingLabel ?: "Unknown"}")
        battery?.temperatureCelsius?.let { temp ->
            Text("Temperature: ${String.format("%.1f", temp)}°C")
        }
        Text("Monitoring: ${if (settings.monitoringEnabled) "Active" else "Off"}")
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRefresh) {
            Text("Refresh battery")
        }
    }
}

@Composable
private fun AlertsTab(viewModel: SettingsViewModel, settings: AppSettings) {
    SectionCard {
        SwitchRow(
            label = "Enable monitoring",
            description = "Runs in the background and alerts while unplugged.",
            checked = settings.monitoringEnabled,
            onCheckedChange = { viewModel.setMonitoring(it) },
        )
    }

    SectionCard {
        SwitchRow(
            label = "Simple mode",
            description = "Classic beep + 'Battery low. Connect the charger.'",
            checked = settings.simpleMode,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(simpleMode = enabled) }
            },
        )
    }

    if (settings.simpleMode) {
        SectionCard {
            Text("Classic alert", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Uses the built-in beep and the original voice message.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))

            Text("Alert at ${settings.thresholdPercent}%")
            Slider(
                value = settings.thresholdPercent.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(thresholdPercent = value.toInt().coerceIn(5, 50)) }
                },
                valueRange = 5f..50f,
                steps = 45,
            )

            Text("Repeat every ${settings.repeatIntervalMinutes} min")
            Slider(
                value = settings.repeatIntervalMinutes.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(repeatIntervalMinutes = value.toInt().coerceIn(1, 60)) }
                },
                valueRange = 1f..60f,
                steps = 59,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = "Emergency: at ${settings.emergencyPercent}% and below, the alert repeats every 10 seconds until the charger is connected.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        SectionCard {
            Text("Alert levels", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                label = "Use escalating levels",
                description = "Reminder, warning, and critical messages.",
                checked = settings.alertLevelsEnabled,
                onCheckedChange = { enabled ->
                    viewModel.update { it.copy(alertLevelsEnabled = enabled) }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Reminder at ${settings.thresholdPercent}%")
            Slider(
                value = settings.thresholdPercent.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(thresholdPercent = value.toInt().coerceIn(5, 50)) }
                },
                valueRange = 5f..50f,
                steps = 45,
            )

            Text("Warning at ${settings.warningPercent}%")
            Slider(
                value = settings.warningPercent.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(warningPercent = value.toInt().coerceIn(1, 50)) }
                },
                valueRange = 1f..50f,
                steps = 48,
            )

            Text("Critical at ${settings.criticalPercent}%")
            Slider(
                value = settings.criticalPercent.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(criticalPercent = value.toInt().coerceIn(1, 50)) }
                },
                valueRange = 1f..50f,
                steps = 48,
            )

            Spacer(Modifier.height(8.dp))
            MessageField(
                label = "Reminder message",
                value = settings.voiceMessage,
                onChange = { viewModel.update { s -> s.copy(voiceMessage = it) } },
            )
            MessageField(
                label = "Warning message",
                value = settings.warningMessage,
                onChange = { viewModel.update { s -> s.copy(warningMessage = it) } },
            )
            MessageField(
                label = "Critical message",
                value = settings.criticalMessage,
                onChange = { viewModel.update { s -> s.copy(criticalMessage = it) } },
            )
        }

        SectionCard {
            Text("Timing", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Repeat every ${settings.repeatIntervalMinutes} min")
            Slider(
                value = settings.repeatIntervalMinutes.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(repeatIntervalMinutes = value.toInt().coerceIn(1, 60)) }
                },
                valueRange = 1f..60f,
                steps = 59,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Snooze for ${settings.snoozeMinutes} min")
            Slider(
                value = settings.snoozeMinutes.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(snoozeMinutes = value.toInt().coerceIn(1, 60)) }
                },
                valueRange = 1f..60f,
                steps = 59,
            )

            SwitchRow(
                label = "Include time left",
                description = "Add an estimated time-left to low-battery voice alerts.",
                checked = settings.includeTimeLeft,
                onCheckedChange = { enabled ->
                    viewModel.update { it.copy(includeTimeLeft = enabled) }
                },
            )
        }

        SectionCard {
            Text("Quiet hours", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                label = "Enable quiet hours",
                description = "Normal alerts become notification-only at night.",
                checked = settings.quietHoursEnabled,
                onCheckedChange = { enabled ->
                    viewModel.update { it.copy(quietHoursEnabled = enabled) }
                },
            )

            Text("Start ${hourLabel(settings.quietStartHour)}")
            Slider(
                value = settings.quietStartHour.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(quietStartHour = value.toInt().coerceIn(0, 23)) }
                },
                valueRange = 0f..23f,
                steps = 22,
            )

            Text("End ${hourLabel(settings.quietEndHour)}")
            Slider(
                value = settings.quietEndHour.toFloat(),
                onValueChange = { value ->
                    viewModel.update { it.copy(quietEndHour = value.toInt().coerceIn(0, 23)) }
                },
                valueRange = 0f..23f,
                steps = 22,
            )

            SwitchRow(
                label = "Critical alerts still speak",
                checked = settings.criticalOverridesQuietHours,
                onCheckedChange = { enabled ->
                    viewModel.update { it.copy(criticalOverridesQuietHours = enabled) }
                },
            )
        }
    }
}

@Composable
private fun ChargingTab(
    viewModel: SettingsViewModel,
    settings: AppSettings,
) {
    SectionCard {
        Text("Charging announcements", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            label = "Enable charging announcements",
            description = "Speak plug, unplug, and full-battery status.",
            checked = settings.chargingAnnouncementsEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(chargingAnnouncementsEnabled = enabled) }
            },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SwitchRow(
            label = "Charging started",
            checked = settings.chargingStartedAnnouncementEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(chargingStartedAnnouncementEnabled = enabled) }
            },
        )
        MessageField(
            label = "Started message",
            value = settings.chargingStartedMessage,
            onChange = { viewModel.update { s -> s.copy(chargingStartedMessage = it) } },
        )

        SwitchRow(
            label = "Charger unplugged",
            checked = settings.chargingUnpluggedAnnouncementEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(chargingUnpluggedAnnouncementEnabled = enabled) }
            },
        )
        MessageField(
            label = "Unplugged message",
            value = settings.chargingUnpluggedMessage,
            onChange = { viewModel.update { s -> s.copy(chargingUnpluggedMessage = it) } },
        )
        Text("The current battery percentage is added automatically.", style = MaterialTheme.typography.bodySmall)

        SwitchRow(
            label = "Charging complete at 100%",
            checked = settings.chargingCompleteAnnouncementEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(chargingCompleteAnnouncementEnabled = enabled) }
            },
        )
        MessageField(
            label = "Complete message",
            value = settings.chargingCompleteMessage,
            onChange = { viewModel.update { s -> s.copy(chargingCompleteMessage = it) } },
        )
    }

    SectionCard {
        SwitchRow(
            label = "Unplug reminder",
            description = "Remind you once when the target is reached.",
            checked = settings.unplugReminderEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(unplugReminderEnabled = enabled) }
            },
        )

        Text("Unplug at ${settings.unplugTargetPercent}%")
        Slider(
            value = settings.unplugTargetPercent.toFloat(),
            onValueChange = { value ->
                viewModel.update { it.copy(unplugTargetPercent = value.toInt().coerceIn(50, 100)) }
            },
            valueRange = 50f..100f,
            steps = 49,
        )
        MessageField(
            label = "Unplug message",
            value = settings.unplugMessage,
            onChange = { viewModel.update { s -> s.copy(unplugMessage = it) } },
        )
    }

    SectionCard {
        SwitchRow(
            label = "Slow-charge warning",
            description = "Warn if the battery is not rising after a few minutes.",
            checked = settings.slowChargeWarningEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(slowChargeWarningEnabled = enabled) }
            },
        )

        Text("Check after ${settings.slowChargeCheckMinutes} min")
        Slider(
            value = settings.slowChargeCheckMinutes.toFloat(),
            onValueChange = { value ->
                viewModel.update { it.copy(slowChargeCheckMinutes = value.toInt().coerceIn(1, 60)) }
            },
            valueRange = 1f..60f,
            steps = 59,
        )
        MessageField(
            label = "Slow-charge message",
            value = settings.slowChargeMessage,
            onChange = { viewModel.update { s -> s.copy(slowChargeMessage = it) } },
        )
    }
}

@Composable
private fun VoiceTab(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    voiceNames: List<String>,
) {
    SectionCard {
        Text("Voice and sound", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SelectionRow(
            label = "Alert voice",
            selectedValue = settings.selectedVoiceName,
            options = voiceOptions(voiceNames),
            onSelect = { selected ->
                viewModel.update { it.copy(selectedVoiceName = selected) }
            },
        )
        SelectionRow(
            label = "Alarm sound",
            selectedValue = settings.selectedSoundKey,
            options = SOUND_OPTIONS,
            onSelect = { selected ->
                viewModel.update { it.copy(selectedSoundKey = selected) }
            },
        )

        SwitchRow(
            label = "Speak messages",
            checked = settings.voiceEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(voiceEnabled = enabled) }
            },
        )
        SwitchRow(
            label = "Play alarm sound",
            checked = settings.soundEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(soundEnabled = enabled) }
            },
        )
        SwitchRow(
            label = "Show notification",
            checked = settings.notificationEnabled,
            onCheckedChange = { enabled ->
                viewModel.update { it.copy(notificationEnabled = enabled) }
            },
        )
    }

    SectionCard {
        Text("Quick actions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { viewModel.testAlert() }) {
                Text("Test alert")
            }
            OutlinedButton(
                onClick = { viewModel.snooze() },
                enabled = settings.monitoringEnabled,
            ) {
                Text("Snooze")
            }
        }
    }
}

@Composable
private fun AppearanceTab(viewModel: SettingsViewModel, settings: AppSettings) {
    SectionCard {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SelectionRow(
            label = "Dark mode",
            selectedValue = settings.themeMode,
            options = THEME_OPTIONS,
            onSelect = { selected ->
                viewModel.update { it.copy(themeMode = selected) }
            },
        )
        Text(
            text = "System follows your device theme. Light and Dark stay fixed.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AdvancedTab(
    context: Context,
    viewModel: SettingsViewModel,
    notificationGranted: Boolean,
    onRequestNotification: () -> Unit,
) {
    SectionCard {
        Text("Advanced", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
            Button(onClick = onRequestNotification) {
                Text("Allow notifications")
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(onClick = { openBatterySettings(context) }) {
            Text("Unrestricted battery")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.resetSettings() }) {
            Text("Reset all settings")
        }
    }

    SectionCard {
        Text("About", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Waza Battery Alarm")
        Text("Version 1.2.0")
        Text(
            text = "Use the home-screen widget or Quick Settings tile to toggle monitoring without opening the app.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun voiceOptions(voiceNames: List<String>): List<Pair<String, String>> =
    listOf("" to "System default") + voiceNames.map { it to it }

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MessageField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SelectionRow(
    label: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
        ?: selectedValue.ifBlank { "System default" }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $selectedLabel")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

private fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0') + ":00"

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(intent) }
}

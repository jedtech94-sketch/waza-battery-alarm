package com.example.batteryalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.batteryalarm.ui.BatteryAlarmTheme
import com.example.batteryalarm.ui.SettingsScreen
import com.example.batteryalarm.ui.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            BatteryAlarmTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

package com.orbit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.ui.LocalDataViewModel
import com.orbit.app.ui.navigation.OrbitApp
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.reminders.ReminderNotificationWorker

class MainActivity : ComponentActivity() {
    private var reminderToOpen by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Orbit)
        super.onCreate(savedInstanceState)
        readReminderIntent(intent)
        enableEdgeToEdge()

        setContent {
            val container = (application as OrbitApplication).container
            val localDataViewModel: LocalDataViewModel = viewModel(
                factory = LocalDataViewModel.Factory(container),
            )
            val settings by localDataViewModel.settings.collectAsStateWithLifecycle()

            OrbitTheme(settings = settings) {
                val systemInDarkTheme = isSystemInDarkTheme()
                val useDarkSystemBars = when (settings.themeMode) {
                    SettingsThemeMode.Light -> false
                    SettingsThemeMode.Dark -> true
                    SettingsThemeMode.Auto -> systemInDarkTheme
                }
                val view = LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !useDarkSystemBars
                    controller.isAppearanceLightNavigationBars = !useDarkSystemBars
                }
                OrbitApp(
                    container = container,
                    settings = settings,
                    onSettingsChanged = localDataViewModel::updateSettings,
                    reminderToOpen = reminderToOpen,
                    onReminderOpened = { reminderToOpen = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReminderIntent(intent)
    }

    private fun readReminderIntent(intent: Intent?) {
        reminderToOpen = intent
            ?.getLongExtra(ReminderNotificationWorker.EXTRA_REMINDER_ID, 0L)
            ?.takeIf { it != 0L }
    }
}

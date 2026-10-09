package com.orbit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.ui.LocalDataViewModel
import com.orbit.app.ui.navigation.OrbitApp
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.reminders.ReminderNotificationWorker
import com.orbit.app.reminders.ReminderNotifier
import com.orbit.app.reminders.ReminderRescheduleWorker
import com.orbit.app.ui.localization.AppLanguage

class MainActivity : AppCompatActivity() {
    private var reminderToOpen by mutableStateOf<Long?>(null)
    private var openReviewRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Orbit)
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, language change, process restore) keeps the
        // launching intent; its deep link was already handled, so it must not reopen.
        if (savedInstanceState == null) {
            readLaunchIntent(intent)
            // Restores alarms lost to a force-stop and surfaces reminders missed meanwhile.
            ReminderRescheduleWorker.enqueue(this)
        }
        enableEdgeToEdge()

        setContent {
            val container = (application as OrbitApplication).container
            val localDataViewModel: LocalDataViewModel = viewModel(
                factory = LocalDataViewModel.Factory(container),
            )
            val settings by localDataViewModel.settings.collectAsStateWithLifecycle()
            val loadedSettings = settings ?: return@setContent
            val applicationLanguage = AppLanguage.fromLanguageTags(
                AppCompatDelegate.getApplicationLocales().toLanguageTags(),
            )

            OrbitTheme(settings = loadedSettings) {
                val systemInDarkTheme = isSystemInDarkTheme()
                val useDarkSystemBars = when (loadedSettings.themeMode) {
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
                    settings = loadedSettings,
                    onSettingsChanged = localDataViewModel::updateSettings,
                    applicationLanguage = applicationLanguage,
                    onApplicationLanguageChanged = ::setApplicationLanguage,
                    reminderToOpen = reminderToOpen,
                    onReminderOpened = ::consumeReminderRequest,
                    openReviewRequested = openReviewRequested,
                    onOpenReviewHandled = { openReviewRequested = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunchIntent(intent)
    }

    private fun readLaunchIntent(intent: Intent?) {
        reminderToOpen = intent
            ?.getLongExtra(ReminderNotificationWorker.EXTRA_REMINDER_ID, 0L)
            ?.takeIf { it != 0L }
        if (intent?.getBooleanExtra(ReminderNotifier.EXTRA_OPEN_REVIEW, false) == true) {
            openReviewRequested = true
        }
    }

    private fun consumeReminderRequest() {
        reminderToOpen = null
        intent?.removeExtra(ReminderNotificationWorker.EXTRA_REMINDER_ID)
        intent?.removeExtra(ReminderNotifier.EXTRA_OPEN_REVIEW)
    }

    private fun setApplicationLanguage(language: AppLanguage) {
        val locales = language.languageTag
            .takeIf(String::isNotEmpty)
            ?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

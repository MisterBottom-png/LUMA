package com.orbit.app.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.GlassEffect
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.localization.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun everydayChoicesAreSetOnTheMainPage_andSubpagesGoStraightBack() {
        var settings by mutableStateOf(AppSettings())
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(411.dp).height(891.dp)) {
                    SettingsScreen(
                        settings = settings,
                        onSettingsChanged = { settings = it },
                        applicationLanguage = AppLanguage.English,
                        onApplicationLanguageChanged = {},
                        aiSettings = AiSettingsUiState(),
                        onSaveGeminiKey = {},
                        onDeleteGeminiKey = {},
                        onClearAiLearningData = {},
                        onUpdateLearnedRule = {},
                        onDeleteLearnedRule = {},
                        onTestGeminiConnection = { _, _ -> },
                        localDataTools = LocalDataToolsUiState(),
                        onExportJson = {},
                        onRestoreFileSelected = {},
                        onConfirmRestore = {},
                        onCancelRestore = {},
                        onResetAllData = {},
                        onRetryReminderSetup = {},
                        onOpenFirstTimeGuide = {},
                        onSettingsSubsectionChanged = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(SettingsThemeMode.Dark, settings.themeMode)

        composeRule.onNodeWithContentDescription("Sage").performScrollTo().performClick()
        assertEquals(AppAccentColor.Sage, settings.accentColor)

        composeRule.onNodeWithText("Off").performScrollTo().performClick()
        assertEquals(GlassEffect.Off, settings.glassEffect)

        composeRule.onNodeWithText("Language").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your data").assertExists()
    }
}

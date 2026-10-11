package com.orbit.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Backup in plain words: two buttons, with the file and replace rule as small text under them. */
@RunWith(AndroidJUnit4::class)
class LocalDataBackupWordsJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private fun show(state: LocalDataToolsUiState = LocalDataToolsUiState()) {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LocalDataSettingsSection(
                        localDataTools = state,
                        onExportJson = {},
                        onRestoreFileSelected = {},
                        onConfirmRestore = {},
                        onCancelRestore = {},
                        onResetAllData = {},
                    )
                }
            }
        }
    }

    @Test
    fun theButtonsSayCreateAndRestoreBackup_withTheirNotesUnderThem() {
        show()

        composeRule.onNodeWithText("Create backup").assertExists()
        composeRule.onNodeWithText("Saved as one .json file, wherever you choose.").assertExists()
        composeRule.onNodeWithText("Restore backup").performScrollTo().assertExists()
        composeRule.onNodeWithText("Restoring replaces what is on this phone. Nothing changes until you confirm.")
            .assertExists()
        composeRule.onNodeWithText("Export JSON").assertDoesNotExist()
        composeRule.onNodeWithText("Choose export to restore").assertDoesNotExist()
    }

    @Test
    fun createBackupStillWarnsBeforeWritingTheFile() {
        show()

        composeRule.onNodeWithText("Create backup").performClick()

        composeRule.onNodeWithText("Choose location").assertExists()
    }
}

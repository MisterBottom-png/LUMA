package com.orbit.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.ui.localization.AppLanguage

internal enum class SystemMenuSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
) {
    Time(R.string.settings_time_title, R.string.settings_time_subtitle, Icons.Filled.AccessTime),
    Language(R.string.settings_language_title, R.string.settings_language_subtitle, Icons.Filled.Language),
    Ai(R.string.settings_ai_title, R.string.settings_ai_subtitle, Icons.Filled.AutoAwesome),
    LocalData(R.string.settings_local_data_title, R.string.settings_local_data_subtitle, Icons.Filled.Storage),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeSettingsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_time_format)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingsTimeFormatMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.timeFormatMode == mode,
                        onClick = {
                            onSettingsChanged(settings.copy(timeFormatMode = mode))
                        },
                        label = { Text(stringResource(mode.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_time_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguageSettingsSection(
    applicationLanguage: AppLanguage,
    onApplicationLanguageChanged: (AppLanguage) -> Unit,
) {
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_language_title)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = applicationLanguage == language,
                        onClick = { onApplicationLanguageChanged(language) },
                        label = { Text(stringResource(language.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.language_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

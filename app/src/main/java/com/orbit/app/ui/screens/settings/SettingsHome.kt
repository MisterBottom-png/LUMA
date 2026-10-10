package com.orbit.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.BuildConfig
import com.orbit.app.R
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.GlassEffect
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.domain.model.withDefaultAppearance
import com.orbit.app.ui.components.GroupDivider
import com.orbit.app.ui.components.GroupedCard
import com.orbit.app.ui.components.SectionHeader
import com.orbit.app.ui.components.TintedIconChip
import com.orbit.app.ui.localization.AppLanguage

/** Soft hues for the row icons, one per group, so the page is easy to scan. */
private object SettingsHues {
    val Profile = Color(0xFF5E7FA8)
    val Look = Color(0xFF7B68C8)
    val Reminders = Color(0xFFC98A1B)
    val Language = Color(0xFF2D8A86)
    val Time = Color(0xFF3E7CB1)
    val Ai = Color(0xFF9A5FC0)
    val Data = Color(0xFF4F8A5B)
    val About = Color(0xFF7A7F87)
}

/** Where a row on the Settings page leads. */
internal sealed interface SettingsDestination {
    data class Appearance(val section: AppearanceMenuSection) : SettingsDestination
    data class System(val section: SystemMenuSection) : SettingsDestination
}

/**
 * Settings on one page: the everyday choices (theme, colour, glass, reminder switches)
 * are set right here; the rest is one tap away.
 */
@Composable
internal fun SettingsHome(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    applicationLanguage: AppLanguage,
    aiSettings: AiSettingsUiState,
    localDataTools: LocalDataToolsUiState,
    onOpenFirstTimeGuide: () -> Unit,
    onOpen: (SettingsDestination) -> Unit,
) {
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        GroupedCard {
            ProfileRow(
                // The stored placeholder is not a name: the row says "Add your name".
                name = com.orbit.app.domain.model.chosenUserName(settings.userName).orEmpty(),
                onClick = { onOpen(SettingsDestination.Appearance(AppearanceMenuSection.Profile)) },
            )
        }

        SettingsGroupBlock(title = stringResource(R.string.settings_appearance_title)) {
            ControlBlock(icon = Icons.Filled.Contrast, title = stringResource(R.string.settings_theme)) {
                ChoiceRow(
                    choices = SettingsThemeMode.entries,
                    selected = settings.themeMode,
                    label = { stringResource(it.labelRes()) },
                    onSelected = { onSettingsChanged(settings.copy(themeMode = it)) },
                )
            }
            GroupDivider(startInset = 64.dp)
            ControlBlock(
                icon = Icons.Filled.Tune,
                title = stringResource(R.string.settings_overall_color),
                value = stringResource(settings.accentColor.labelRes()),
                indentContent = false,
            ) {
                AccentSwatches(
                    selected = settings.accentColor,
                    onSelected = { onSettingsChanged(settings.copy(accentColor = it)) },
                )
            }
            GroupDivider(startInset = 64.dp)
            SettingsLinkRow(
                title = stringResource(R.string.settings_text_color),
                value = stringResource(settings.textColor.labelRes()),
                icon = Icons.Filled.FormatColorText,
                iconColor = SettingsHues.Look,
                onClick = { onOpen(SettingsDestination.Appearance(AppearanceMenuSection.Colors)) },
            )
            GroupDivider(startInset = 64.dp)
            SettingsLinkRow(
                title = stringResource(R.string.settings_background_title),
                value = if (settings.customBackgroundUri != null) {
                    stringResource(R.string.settings_custom_image)
                } else {
                    stringResource(settings.backgroundPreset.labelRes())
                },
                leading = {
                    if (settings.customBackgroundUri != null) {
                        TintedIconChip(icon = Icons.Filled.Image, color = SettingsHues.Look)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Brush.linearGradient(presetPreviewColors(settings.backgroundPreset)),
                                    RoundedCornerShape(12.dp),
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                    RoundedCornerShape(12.dp),
                                ),
                        )
                    }
                },
                onClick = { onOpen(SettingsDestination.Appearance(AppearanceMenuSection.Background)) },
            )
            GroupDivider(startInset = 64.dp)
            ControlBlock(icon = Icons.Filled.AutoAwesome, title = stringResource(R.string.settings_glass_effect)) {
                ChoiceRow(
                    choices = GlassEffect.entries,
                    selected = settings.glassEffect,
                    label = { stringResource(it.labelRes()) },
                    onSelected = { onSettingsChanged(settings.copy(glassEffect = it)) },
                )
            }
            GroupDivider(startInset = 64.dp)
            SettingsLinkRow(
                title = stringResource(R.string.settings_transparency_title),
                value = stringResource(settings.glassPreference.labelRes()),
                icon = Icons.Filled.Tune,
                iconColor = SettingsHues.Look,
                onClick = { onOpen(SettingsDestination.Appearance(AppearanceMenuSection.Glass)) },
            )
        }

        RemindersGroup(settings = settings, onSettingsChanged = onSettingsChanged)

        SettingsGroupBlock(title = stringResource(R.string.settings_section_language_time)) {
            SettingsLinkRow(
                title = stringResource(R.string.settings_language_title),
                value = stringResource(applicationLanguage.labelRes()),
                icon = Icons.Filled.Language,
                iconColor = SettingsHues.Language,
                onClick = { onOpen(SettingsDestination.System(SystemMenuSection.Language)) },
            )
            GroupDivider(startInset = 64.dp)
            SettingsLinkRow(
                title = stringResource(R.string.settings_time_format),
                value = stringResource(settings.timeFormatMode.labelRes()),
                icon = Icons.Filled.AccessTime,
                iconColor = SettingsHues.Time,
                onClick = { onOpen(SettingsDestination.System(SystemMenuSection.Time)) },
            )
        }

        // One row needs no "AI" heading above an "AI" row.
        SettingsGroupBlock(
            title = null,
            footer = stringResource(R.string.settings_ai_footer),
        ) {
            SettingsLinkRow(
                title = stringResource(R.string.settings_ai_title),
                value = if (settings.aiMode == AiMode.GeminiApi && aiSettings.hasKey) {
                    stringResource(R.string.settings_status_gemini_ready)
                } else {
                    stringResource(settings.aiMode.labelRes())
                },
                icon = Icons.Filled.AutoAwesome,
                iconColor = SettingsHues.Ai,
                onClick = { onOpen(SettingsDestination.System(SystemMenuSection.Ai)) },
            )
        }

        SettingsGroupBlock(
            title = stringResource(R.string.settings_section_data),
            footer = stringResource(R.string.settings_data_footer),
        ) {
            SettingsLinkRow(
                title = stringResource(R.string.settings_backup_title),
                value = if (localDataTools.exportCompleted) {
                    stringResource(R.string.settings_status_last_export)
                } else {
                    null
                },
                icon = Icons.Filled.SaveAlt,
                iconColor = SettingsHues.Data,
                onClick = { onOpen(SettingsDestination.System(SystemMenuSection.LocalData)) },
            )
        }

        SettingsGroupBlock(title = stringResource(R.string.settings_section_about)) {
            SettingsLinkRow(
                title = stringResource(R.string.settings_first_time_guide_title),
                icon = Icons.Filled.School,
                iconColor = SettingsHues.About,
                onClick = onOpenFirstTimeGuide,
            )
            GroupDivider(startInset = 64.dp)
            SettingsLinkRow(
                title = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME,
                icon = Icons.Filled.Info,
                iconColor = SettingsHues.About,
                onClick = null,
            )
        }

        // A rarely used action: quiet text at the very end, with a confirmation.
        TextButton(
            onClick = { showResetConfirmation = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.settings_reset_appearance),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.settings_reset_appearance)) },
            confirmButton = {
                TextButton(onClick = {
                    onSettingsChanged(settings.withDefaultAppearance())
                    showResetConfirmation = false
                }) { Text(stringResource(R.string.settings_reset_appearance)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

/** Reminder status with a direct fix, plus the two capture switches. */
@Composable
internal fun RemindersGroup(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    SettingsGroupBlock(title = stringResource(R.string.settings_section_reminders)) {
        ReminderStatusRow(icon = Icons.Filled.Notifications, iconColor = SettingsHues.Reminders)
        GroupDivider(startInset = 64.dp)
        Box(modifier = Modifier.padding(start = 64.dp, end = 16.dp)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_sort_right_after_saving),
                body = stringResource(R.string.settings_sort_right_after_saving_body),
                checked = settings.sortRightAfterSaving,
                onCheckedChange = { onSettingsChanged(settings.copy(sortRightAfterSaving = it)) },
            )
        }
        GroupDivider(startInset = 64.dp)
        Box(modifier = Modifier.padding(start = 64.dp, end = 16.dp)) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_focus_capture),
                body = stringResource(R.string.settings_focus_capture_body),
                checked = settings.focusCaptureOnOpen,
                onCheckedChange = { onSettingsChanged(settings.copy(focusCaptureOnOpen = it)) },
            )
        }
    }
}

@Composable
internal fun SettingsGroupBlock(
    title: String?,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        title?.let { SectionHeader(title = it) }
        GroupedCard(content = content)
        footer?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One row: icon, title, the current value on the right and a chevron when it opens
 * something. [onClick] null makes it a plain information row.
 */
@Composable
internal fun SettingsLinkRow(
    title: String,
    onClick: (() -> Unit)?,
    value: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null -> TintedIconChip(icon = icon, color = iconColor)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        value?.let {
            Text(
                text = it,
                modifier = Modifier.widthIn(max = 168.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProfileRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val initial = name.trim().firstOrNull()?.uppercaseChar()
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(SettingsHues.Profile.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(
                    text = initial.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name.ifBlank { stringResource(R.string.settings_add_name) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.settings_profile_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A titled row with a control under the title, such as the theme choice. */
@Composable
private fun ControlBlock(
    icon: ImageVector,
    title: String,
    value: String? = null,
    indentContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TintedIconChip(icon = icon, color = SettingsHues.Look)
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.padding(start = if (indentContent) 48.dp else 0.dp)) { content() }
    }
}

@Composable
private fun <T> ChoiceRow(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = choice == selected,
                onClick = { onSelected(choice) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                icon = {},
                modifier = Modifier.weight(1f),
            ) {
                Text(label(choice), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AccentSwatches(
    selected: AppAccentColor,
    onSelected: (AppAccentColor) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        AppAccentColor.entries.forEach { choice ->
            val isSelected = choice == selected
            val colors = accentSwatchColors(choice)
            val label = stringResource(choice.labelRes())
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(choice) })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 40.dp else 34.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, colors.first(), CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 30.dp else 34.dp)
                            .background(Brush.linearGradient(colors), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A tonal button small enough to sit at the end of a row. */
@Composable
internal fun RowActionButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

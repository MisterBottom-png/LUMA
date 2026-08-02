package com.orbit.app.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.AppearancePaletteMode
import com.orbit.app.domain.model.BackgroundBlur
import com.orbit.app.domain.model.BackgroundDimmingMode
import com.orbit.app.domain.model.BackgroundPreset
import com.orbit.app.domain.model.GlassPreference
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.domain.model.withDefaultAppearance
import com.orbit.app.ui.components.GlassRolePreview
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.orbitPressFeedback
import kotlin.math.roundToInt

@Composable
internal fun AppearanceSettingsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    selectedMenuSection: AppearanceMenuSection?,
    onSectionSelected: (AppearanceMenuSection) -> Unit,
) {
    val context = LocalContext.current
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val customBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onSettingsChanged(settings.copy(customBackgroundUri = uri.toString()))
    }

    Column(
        modifier = Modifier.padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selectedMenuSection == null) {
            AppearanceMenuCard(
                settings = settings,
                onSectionSelected = onSectionSelected,
            )
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_reset_appearance))
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
        } else {
            when (selectedMenuSection) {
                AppearanceMenuSection.Profile -> AppearanceProfileSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                )

                AppearanceMenuSection.Colors -> AppearanceColorsSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                )

                AppearanceMenuSection.Background -> AppearanceBackgroundSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                    onChooseCustomBackground = {
                        customBackgroundPicker.launch(arrayOf("image/*"))
                    },
                )

                AppearanceMenuSection.Glass -> AppearanceGlassSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                )
            }
        }
    }
}

internal enum class AppearanceMenuSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
) {
    Profile(R.string.settings_profile_title, R.string.settings_profile_subtitle, Icons.Filled.Person),
    Colors(R.string.settings_colors_title, R.string.settings_colors_subtitle, Icons.Filled.Palette),
    Background(
        R.string.settings_background_title,
        R.string.settings_background_subtitle,
        Icons.Filled.Image,
    ),
    Glass(
        R.string.settings_transparency_title,
        R.string.settings_transparency_subtitle,
        Icons.Filled.Tune,
    ),
}

@Composable
private fun AppearanceMenuCard(
    settings: AppSettings,
    onSectionSelected: (AppearanceMenuSection) -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            AppearanceMenuRow(
                section = AppearanceMenuSection.Profile,
                status = settings.userName.ifBlank { stringResource(R.string.settings_no_name) },
                onClick = { onSectionSelected(AppearanceMenuSection.Profile) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Colors,
                status = stringResource(
                    R.string.settings_status_colors,
                    stringResource(settings.themeMode.labelRes()),
                    stringResource(settings.accentColor.labelRes()),
                    stringResource(settings.textColor.labelRes()),
                ),
                onClick = { onSectionSelected(AppearanceMenuSection.Colors) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Background,
                status = if (settings.customBackgroundUri != null) {
                    stringResource(R.string.settings_custom_image)
                } else {
                    stringResource(settings.backgroundPreset.labelRes())
                },
                onClick = { onSectionSelected(AppearanceMenuSection.Background) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Glass,
                status = stringResource(
                    R.string.settings_status_surface_opacity,
                    stringResource(settings.glassPreference.labelRes()),
                ),
                onClick = { onSectionSelected(AppearanceMenuSection.Glass) },
            )
        }
    }
}

@Composable
private fun AppearanceMenuRow(
    section: AppearanceMenuSection,
    status: String,
    onClick: () -> Unit,
) = SettingsMenuRow(
    icon = section.icon,
    title = stringResource(section.titleRes),
    status = status,
    onClick = onClick,
)

@Composable
private fun AppearanceProfileSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard {
        OutlinedTextField(
            value = settings.userName,
            onValueChange = { value ->
                onSettingsChanged(settings.copy(userName = value.take(MaxUserNameLength)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_your_name)) },
            supportingText = { Text(stringResource(R.string.settings_name_supporting)) },
            singleLine = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceColorsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_theme)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingsThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = {
                            onSettingsChanged(settings.copy(themeMode = mode))
                        },
                        label = { Text(stringResource(mode.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_theme_auto_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_overall_color)) {
            AppAccentColor.entries.chunked(2).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowChoices.forEach { choice ->
                        AccentColorOption(
                            choice = choice,
                            selected = settings.accentColor == choice,
                            onSelected = {
                                onSettingsChanged(settings.copy(accentColor = choice))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowChoices.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            FilterChip(
                selected = settings.paletteMode == AppearancePaletteMode.FullPalette,
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            paletteMode = if (settings.paletteMode == AppearancePaletteMode.FullPalette) {
                                AppearancePaletteMode.Standard
                            } else {
                                AppearancePaletteMode.FullPalette
                            },
                        ),
                    )
                },
                label = { Text(stringResource(R.string.settings_advanced_full_palette)) },
                colors = readableFilterChipColors(),
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_text_color)) {
            AppTextColor.entries.chunked(2).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowChoices.forEach { choice ->
                        TextColorOption(
                            choice = choice,
                            selected = settings.textColor == choice,
                            onSelected = {
                                onSettingsChanged(settings.copy(textColor = choice))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowChoices.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentColorOption(
    choice: AppAccentColor,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = accentSwatchColors(choice)
    ColorSwatchOption(
        label = stringResource(choice.labelRes()),
        selected = selected,
        onSelected = onSelected,
        colors = colors,
        modifier = modifier,
    )
}

@Composable
private fun TextColorOption(
    choice: AppTextColor,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ColorSwatchOption(
        label = stringResource(choice.labelRes()),
        selected = selected,
        onSelected = onSelected,
        colors = listOf(textSwatchColor(choice)),
        modifier = modifier,
    )
}

@Composable
private fun ColorSwatchOption(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val selectedDescription = stringResource(
        if (selected) R.string.settings_selected else R.string.settings_not_selected,
    )
    Column(
        modifier = modifier
            .height(88.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
                },
                shape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
                },
                shape = shape,
            )
            .selectable(
                selected = selected,
                onClick = onSelected,
                role = Role.RadioButton,
            )
            .semantics {
                stateDescription = selectedDescription
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(color, CircleShape)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                CircleShape,
                            ),
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun AppearanceBackgroundSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onChooseCustomBackground: () -> Unit,
) {
    AppearanceCard {
        val backgroundMode = if (settings.customBackgroundUri != null) {
            BackgroundMode.Custom
        } else {
            BackgroundMode.Preset
        }
        BackgroundModeButtons(
            selectedMode = backgroundMode,
            onCustomSelected = {
                if (settings.customBackgroundUri == null) {
                    onChooseCustomBackground()
                }
            },
            onPresetSelected = {
                if (settings.customBackgroundUri != null) {
                    onSettingsChanged(settings.copy(customBackgroundUri = null))
                }
            },
        )

        if (backgroundMode == BackgroundMode.Custom) {
            CustomBackgroundOption(
                selected = true,
                onChoose = onChooseCustomBackground,
                onRemove = { onSettingsChanged(settings.copy(customBackgroundUri = null)) },
            )
        } else {
            BackgroundPreset.entries.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowPresets.forEach { preset ->
                        BackgroundPresetOption(
                            preset = preset,
                            selected = settings.backgroundPreset == preset,
                            onSelected = {
                                onSettingsChanged(
                                    settings.copy(
                                        backgroundPreset = preset,
                                        customBackgroundUri = null,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceGlassSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    GlassRolePreview(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.settings_surface_preview),
    )
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_image_blur)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgroundBlur.entries.forEach { choice ->
                    FilterChip(
                        selected = settings.backgroundBlur == choice,
                        enabled = settings.customBackgroundUri != null,
                        onClick = { onSettingsChanged(settings.copy(backgroundBlur = choice)) },
                        label = { Text(stringResource(choice.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
        }
        AppearanceSlider(
            title = stringResource(R.string.settings_background_dim),
            value = settings.backgroundDim,
            supportingText = stringResource(R.string.settings_dim_explanation),
            rangeStartLabel = stringResource(R.string.settings_bright),
            rangeEndLabel = stringResource(R.string.settings_dim),
            onValueChange = { value ->
                onSettingsChanged(settings.copy(backgroundDim = value))
            },
        )
        SettingsGroup(title = stringResource(R.string.settings_surface_opacity)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPreference.entries.forEach { choice ->
                    FilterChip(
                        selected = settings.glassPreference == choice,
                        onClick = { onSettingsChanged(settings.copy(glassPreference = choice)) },
                        label = { Text(stringResource(choice.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            FilterChip(
                selected = settings.backgroundDimmingMode == BackgroundDimmingMode.Adaptive,
                onClick = {
                    onSettingsChanged(settings.copy(backgroundDimmingMode = if (settings.backgroundDimmingMode == BackgroundDimmingMode.Adaptive) BackgroundDimmingMode.Manual else BackgroundDimmingMode.Adaptive))
                },
                label = {
                    Text(
                        stringResource(
                            if (settings.backgroundDimmingMode == BackgroundDimmingMode.Adaptive) {
                                R.string.settings_adaptive_dimming
                            } else {
                                R.string.settings_manual_dimming
                            },
                        ),
                    )
                },
                colors = readableFilterChipColors(),
            )
        }
    }
}

private enum class BackgroundMode {
    Custom,
    Preset,
}

@Composable
private fun BackgroundModeButtons(
    selectedMode: BackgroundMode,
    onCustomSelected: () -> Unit,
    onPresetSelected: () -> Unit,
) {
    val modes = BackgroundMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = when (mode) {
                    BackgroundMode.Custom -> onCustomSelected
                    BackgroundMode.Preset -> onPresetSelected
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = modes.size,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = when (mode) {
                        BackgroundMode.Custom -> stringResource(R.string.settings_custom)
                        BackgroundMode.Preset -> stringResource(R.string.settings_preset)
                    },
                )
            }
        }
    }
}

@Composable
private fun BackgroundPresetOption(
    preset: BackgroundPreset,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val previewColors = presetPreviewColors(preset)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(76.dp)
            .background(Brush.linearGradient(previewColors), shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                shape = shape,
            )
            .orbitPressFeedback(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onSelected,
            )
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = stringResource(preset.labelRes()),
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    RoundedCornerShape(11.dp),
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CustomBackgroundOption(
    selected: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                        ),
                    ),
                    shape = shape,
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    },
                    shape = shape,
                )
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                            RoundedCornerShape(15.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_custom_image),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            if (selected) {
                                R.string.settings_custom_image_selected
                            } else {
                                R.string.settings_custom_image_available
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onChoose,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (selected) {
                            R.string.settings_change_image
                        } else {
                            R.string.settings_choose_image
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = onRemove,
                enabled = selected,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_remove))
            }
        }
    }
}

@Composable
private fun AppearanceSlider(
    title: String,
    value: Float,
    enabled: Boolean = true,
    supportingText: String? = null,
    rangeStartLabel: String? = null,
    rangeEndLabel: String? = null,
    onValueChange: (Float) -> Unit,
) {
    val safeValue = value.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${(safeValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = safeValue,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f),
                thumbColor = MaterialTheme.colorScheme.primary,
            ),
        )
        if (rangeStartLabel != null && rangeEndLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rangeStartLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = rangeEndLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun presetPreviewColors(preset: BackgroundPreset): List<Color> = when (preset) {
    BackgroundPreset.InkPaper -> listOf(Color(0xFFFBF9F5), Color(0xFFEEF2F0))
    BackgroundPreset.SoftDawn -> listOf(Color(0xFFFFD8CA), Color(0xFFDCCAF1))
    BackgroundPreset.VioletMist -> listOf(Color(0xFFDCCAF4), Color(0xFF9D86CD))
    BackgroundPreset.CalmSky -> listOf(Color(0xFFBCE3EE), Color(0xFFBBD4F0))
    BackgroundPreset.NightOrbit -> listOf(Color(0xFF34315D), Color(0xFF163C4C))
}

private fun accentSwatchColors(choice: AppAccentColor): List<Color> = when (choice) {
    AppAccentColor.InkPaper -> listOf(Color(0xFF3D5962), Color(0xFF705D4A))
    AppAccentColor.LumaViolet -> listOf(Color(0xFF6550C8), Color(0xFF3F7479))
    AppAccentColor.Sage -> listOf(Color(0xFF3E6F45), Color(0xFF74642F))
    AppAccentColor.Rose -> listOf(Color(0xFF99415E), Color(0xFF725A42))
    AppAccentColor.Amber -> listOf(Color(0xFF865400), Color(0xFF5D6F47))
    AppAccentColor.Ocean -> listOf(Color(0xFF2D6684), Color(0xFF5C6090))
}

private fun textSwatchColor(choice: AppTextColor): Color = when (choice) {
    AppTextColor.Neutral -> Color(0xFF1B1C19)
    AppTextColor.Plum -> Color(0xFF2A173C)
    AppTextColor.Forest -> Color(0xFF152A1D)
    AppTextColor.WarmIvory -> Color(0xFFFFF1DB)
}

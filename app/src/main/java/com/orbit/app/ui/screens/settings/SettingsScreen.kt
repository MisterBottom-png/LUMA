package com.orbit.app.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.BackgroundPreset
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    aiSettings: AiSettingsUiState,
    onSaveGeminiKey: (String) -> Unit,
    onDeleteGeminiKey: () -> Unit,
    onTestGeminiConnection: (String, String) -> Unit,
    localDataTools: LocalDataToolsUiState,
    onExportJson: () -> Unit,
    onRestoreFileSelected: (android.net.Uri?) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    var currentSection by rememberSaveable { mutableStateOf(SettingsSection.Overview) }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val imeVisible = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this) > 0
    }
    val bottomContentPadding = if (imeVisible) {
        28.dp
    } else {
        OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 30.dp),
    ) {
        SettingsHeader(
            section = currentSection,
            onBack = { currentSection = SettingsSection.Overview },
        )

        when (currentSection) {
            SettingsSection.Overview -> SettingsOverview(
                settings = settings,
                aiSettings = aiSettings,
                localDataTools = localDataTools,
                onSectionSelected = { currentSection = it },
            )

            SettingsSection.Appearance -> AppearanceSettingsSection(
                settings = settings,
                onSettingsChanged = onSettingsChanged,
            )

            SettingsSection.Ai -> AiSettingsCard(
                settings = settings,
                onSettingsChanged = onSettingsChanged,
                aiSettings = aiSettings,
                onSaveGeminiKey = onSaveGeminiKey,
                onDeleteGeminiKey = onDeleteGeminiKey,
                onTestGeminiConnection = onTestGeminiConnection,
            )

            SettingsSection.LocalData -> LocalDataSettingsSection(
                localDataTools = localDataTools,
                onExportJson = onExportJson,
                onRestoreFileSelected = onRestoreFileSelected,
                onConfirmRestore = onConfirmRestore,
                onCancelRestore = onCancelRestore,
            )
        }

        Spacer(modifier = Modifier.height(bottomContentPadding))
    }
}

@Composable
private fun SettingsHeader(
    section: SettingsSection,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (section != SettingsSection.Overview) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Settings",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (section != SettingsSection.Overview) {
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsOverview(
    settings: AppSettings,
    aiSettings: AiSettingsUiState,
    localDataTools: LocalDataToolsUiState,
    onSectionSelected: (SettingsSection) -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingsCategoryRow(
                icon = Icons.Filled.Palette,
                title = SettingsSection.Appearance.title,
                subtitle = SettingsSection.Appearance.subtitle,
                status = if (settings.customBackgroundUri != null) {
                    "${settings.themeMode.label}, ${settings.timeFormatMode.label}, custom background"
                } else {
                    "${settings.themeMode.label}, ${settings.timeFormatMode.label}, ${settings.backgroundPreset.label}"
                },
                onClick = { onSectionSelected(SettingsSection.Appearance) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            SettingsCategoryRow(
                icon = Icons.Filled.AutoAwesome,
                title = SettingsSection.Ai.title,
                subtitle = SettingsSection.Ai.subtitle,
                status = if (settings.aiMode == AiMode.GeminiApi && aiSettings.hasKey) {
                    "Gemini API ready"
                } else {
                    "${settings.aiMode.label}, key ${if (aiSettings.hasKey) "saved" else "not saved"}"
                },
                onClick = { onSectionSelected(SettingsSection.Ai) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            SettingsCategoryRow(
                icon = Icons.Filled.Storage,
                title = SettingsSection.LocalData.title,
                subtitle = SettingsSection.LocalData.subtitle,
                status = localDataTools.exportPath?.let { "Last export ready" } ?: "Export JSON",
                onClick = { onSectionSelected(SettingsSection.LocalData) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    RoundedCornerShape(15.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSettingsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    var selectedMenuSection by rememberSaveable {
        mutableStateOf<AppearanceMenuSection?>(null)
    }
    val context = LocalContext.current
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
        AppearancePreviewCard(settings = settings)

        if (selectedMenuSection == null) {
            AppearanceMenuCard(
                settings = settings,
                onSectionSelected = { selectedMenuSection = it },
            )
        } else {
            val activeSection = selectedMenuSection
            if (activeSection != null) {
                AppearanceSubsectionHeader(
                    section = activeSection,
                    onBack = { selectedMenuSection = null },
                )
                when (activeSection) {
                    AppearanceMenuSection.Profile -> AppearanceProfileSection(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged,
                    )

                    AppearanceMenuSection.Colors -> AppearanceColorsSection(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged,
                    )

                    AppearanceMenuSection.Time -> AppearanceTimeSection(
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
}

private enum class AppearanceMenuSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Profile("Profile", "Name shown in LUMA", Icons.Filled.Person),
    Colors("Colors", "Theme, accent, and text", Icons.Filled.Palette),
    Time("Time", "Device default, 12-hour, or 24-hour", Icons.Filled.AccessTime),
    Background("Background", "Preset or custom image", Icons.Filled.Image),
    Glass("Glass", "Blur, dim, and surface strength", Icons.Filled.Tune),
}

@Composable
private fun AppearanceMenuCard(
    settings: AppSettings,
    onSectionSelected: (AppearanceMenuSection) -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            AppearanceMenuRow(
                section = AppearanceMenuSection.Profile,
                status = settings.userName.ifBlank { "No name set" },
                onClick = { onSectionSelected(AppearanceMenuSection.Profile) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Colors,
                status = "${settings.themeMode.label}, ${settings.accentColor.label}, ${settings.textColor.label} text",
                onClick = { onSectionSelected(AppearanceMenuSection.Colors) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Time,
                status = settings.timeFormatMode.label,
                onClick = { onSectionSelected(AppearanceMenuSection.Time) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Background,
                status = if (settings.customBackgroundUri != null) {
                    "Custom image"
                } else {
                    settings.backgroundPreset.label
                },
                onClick = { onSectionSelected(AppearanceMenuSection.Background) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Glass,
                status = "Blur ${settings.backgroundBlur.percentLabel()}, dim ${settings.backgroundDim.percentLabel()}, glass ${settings.glassStrength.percentLabel()}",
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSubsectionHeader(
    section: AppearanceMenuSection,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Appearance menu",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = section.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearanceProfileSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard(title = "Profile") {
        OutlinedTextField(
            value = settings.userName,
            onValueChange = { value ->
                onSettingsChanged(settings.copy(userName = value.take(MaxUserNameLength)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your name") },
            supportingText = { Text("Shown in your greeting") },
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
    AppearanceCard(title = "Colors") {
        SettingsGroup(title = "Theme") {
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
                        label = { Text(mode.label) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = "Auto follows your Android system setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup(title = "Overall color") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppAccentColor.entries.forEach { choice ->
                    AccentColorOption(
                        choice = choice,
                        selected = settings.accentColor == choice,
                        onSelected = {
                            onSettingsChanged(settings.copy(accentColor = choice))
                        },
                    )
                }
            }
        }

        SettingsGroup(title = "Text color") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTextColor.entries.forEach { choice ->
                    TextColorOption(
                        choice = choice,
                        selected = settings.textColor == choice,
                        onSelected = {
                            onSettingsChanged(settings.copy(textColor = choice))
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceTimeSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard(title = "Time") {
        SettingsGroup(title = "Time format") {
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
                        label = { Text(mode.label) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = "Device default follows your Android time setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearanceBackgroundSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onChooseCustomBackground: () -> Unit,
) {
    AppearanceCard(title = "Background") {
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
    AppearanceCard(title = "Glass") {
        AppearanceSlider(
            title = "Background blur",
            value = settings.backgroundBlur,
            onValueChange = { value ->
                onSettingsChanged(settings.copy(backgroundBlur = value))
            },
        )
        AppearanceSlider(
            title = "Background dim",
            value = settings.backgroundDim,
            onValueChange = { value ->
                onSettingsChanged(settings.copy(backgroundDim = value))
            },
        )
        AppearanceSlider(
            title = "Glass strength",
            value = settings.glassStrength,
            onValueChange = { value ->
                onSettingsChanged(settings.copy(glassStrength = value))
            },
        )
    }
}

private enum class BackgroundMode {
    Custom,
    Preset,
}

@Composable
private fun AppearancePreviewCard(settings: AppSettings) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
                            ),
                        ),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        RoundedCornerShape(22.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = settings.userName.ifBlank { "Your name" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${settings.accentColor.label} accents, ${settings.textColor.label.lowercase()} text",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AppearanceCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun AccentColorOption(
    choice: AppAccentColor,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val colors = accentSwatchColors(choice)
    ColorSwatchOption(
        label = choice.label,
        selected = selected,
        onSelected = onSelected,
        colors = colors,
    )
}

@Composable
private fun TextColorOption(
    choice: AppTextColor,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    ColorSwatchOption(
        label = choice.label,
        selected = selected,
        onSelected = onSelected,
        colors = listOf(textSwatchColor(choice)),
    )
}

@Composable
private fun ColorSwatchOption(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
    colors: List<Color>,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(112.dp)
            .height(78.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f), shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
                },
                shape = shape,
            )
            .clickable(onClick = onSelected)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun BackgroundModeButtons(
    selectedMode: BackgroundMode,
    onCustomSelected: () -> Unit,
    onPresetSelected: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selectedMode == BackgroundMode.Custom) {
            Button(
                onClick = onCustomSelected,
                modifier = Modifier.weight(1f),
            ) {
                Text("Custom")
            }
            OutlinedButton(
                onClick = onPresetSelected,
                modifier = Modifier.weight(1f),
            ) {
                Text("Preset")
            }
        } else {
            OutlinedButton(
                onClick = onCustomSelected,
                modifier = Modifier.weight(1f),
            ) {
                Text("Custom")
            }
            Button(
                onClick = onPresetSelected,
                modifier = Modifier.weight(1f),
            ) {
                Text("Preset")
            }
        }
    }
}

@Composable
private fun LocalDataSettingsSection(
    localDataTools: LocalDataToolsUiState,
    onExportJson: () -> Unit,
    onRestoreFileSelected: (android.net.Uri?) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onRestoreFileSelected,
    )
    localDataTools.restorePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Replace local data?") },
            text = {
                Text(
                    "Restore ${plan.restoredCounts.notes} notes, " +
                        "${plan.restoredCounts.tasks} tasks, " +
                        "${plan.restoredCounts.reminders} reminders, " +
                        "${plan.restoredCounts.captures} source captures, and " +
                        "${plan.restoredCounts.spaces} Spaces. This will replace " +
                        "${plan.existingCounts.notes} notes, " +
                        "${plan.existingCounts.tasks} tasks, " +
                        "${plan.existingCounts.reminders} reminders, " +
                        "${plan.existingCounts.captures} source captures, and " +
                        "${plan.existingCounts.spaces} Spaces currently on this device. " +
                        "App settings and AI configuration are not included and will be kept. " +
                        "Learning records are kept where their restored source or Space still exists; " +
                        "links to replaced data are cleared. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                Button(onClick = onConfirmRestore, enabled = !localDataTools.isRestoring) {
                    Text(if (localDataTools.isRestoring) "Restoring..." else "Replace local data")
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore, enabled = !localDataTools.isRestoring) {
                    Text("Cancel")
                }
            },
        )
    }
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Create a JSON export stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onExportJson,
                enabled = !localDataTools.isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (localDataTools.isExporting) "Exporting..." else "Export JSON")
            }
            localDataTools.exportPath?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            localDataTools.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = "Restore validates a LUMA export before offering full replacement. Existing data is not changed until you confirm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { restorePicker.launch(arrayOf("application/json", "text/json")) },
                enabled = !localDataTools.isPreparingRestore && !localDataTools.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (localDataTools.isPreparingRestore) "Validating..." else "Choose export to restore")
            }
            localDataTools.restoreMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private enum class SettingsSection(
    val title: String,
    val subtitle: String,
) {
    Overview("Settings", ""),
    Appearance("Appearance", "Colors, background, and glass"),
    Ai("AI", "Mode, key, models, and feature access"),
    LocalData("Local data", "Exports and device-held data"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSettingsCard(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    aiSettings: AiSettingsUiState,
    onSaveGeminiKey: (String) -> Unit,
    onDeleteGeminiKey: () -> Unit,
    onTestGeminiConnection: (String, String) -> Unit,
) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    val canUseGeminiFeatures = settings.aiMode == AiMode.GeminiApi && aiSettings.hasKey

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "AI",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SettingsGroup(title = "Mode") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AiMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.aiMode == mode,
                            onClick = { onSettingsChanged(settings.copy(aiMode = mode)) },
                            label = { Text(mode.label) },
                            colors = readableFilterChipColors(),
                        )
                    }
                }
                Text(
                    text = "Local only remains the default. Gemini is optional cloud AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsGroup(title = "Gemini API key") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text(if (aiSettings.hasKey) "A key is saved on this device." else "No key saved.")
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            onSaveGeminiKey(apiKey)
                            apiKey = ""
                        },
                        enabled = apiKey.isNotBlank() && !aiSettings.isSavingKey,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (aiSettings.isSavingKey) "Saving..." else "Save key")
                    }
                    OutlinedButton(
                        onClick = onDeleteGeminiKey,
                        enabled = aiSettings.hasKey,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Remove")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onTestGeminiConnection(
                            settings.geminiFastModelId,
                            settings.geminiReasoningModelId,
                        )
                    },
                    enabled = aiSettings.hasKey && !aiSettings.isTestingConnection,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (aiSettings.isTestingConnection) "Testing..." else "Test connection")
                }
                aiSettings.connectionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (aiSettings.connectionSucceeded == false) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            SettingsGroup(title = "Models") {
                OutlinedTextField(
                    value = settings.geminiFastModelId,
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(geminiFastModelId = value.trim()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fast model") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = settings.geminiReasoningModelId,
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(geminiReasoningModelId = value.trim()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reasoning model") },
                    singleLine = true,
                )
            }

            SettingsGroup(title = "Use Gemini for") {
                AiFeatureSwitch(
                    title = "Capture suggestions",
                    checked = settings.useGeminiForCapture,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForCapture = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = "Make Smaller",
                    checked = settings.useGeminiForMakeSmaller,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForMakeSmaller = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = "Brain Dump",
                    checked = settings.useGeminiForBrainDump,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForBrainDump = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = "Situation AI",
                    checked = settings.useGeminiForSituation,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForSituation = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = "Review",
                    checked = settings.useGeminiForReview,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForReview = enabled))
                    },
                )
            }

            Text(
                text = "LUMA stores your data locally. Gemini API is optional cloud AI. When enabled, LUMA sends only selected text or context needed for the requested action. Raw captures are saved locally before cloud analysis. You stay in control.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiFeatureSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
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
            .clickable(onClick = onSelected)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = preset.label,
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
                        text = "Custom image",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (selected) "Using your selected background" else "Use an image from this device",
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
                Text(if (selected) "Change image" else "Choose image")
            }
            OutlinedButton(
                onClick = onRemove,
                enabled = selected,
                modifier = Modifier.weight(1f),
            ) {
                Text("Remove")
            }
        }
    }
}

@Composable
private fun AppearanceSlider(
    title: String,
    value: Float,
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
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f),
                thumbColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun readableFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

private fun presetPreviewColors(preset: BackgroundPreset): List<Color> = when (preset) {
    BackgroundPreset.SoftDawn -> listOf(Color(0xFFFFD8CA), Color(0xFFDCCAF1))
    BackgroundPreset.VioletMist -> listOf(Color(0xFFDCCAF4), Color(0xFF9D86CD))
    BackgroundPreset.CalmSky -> listOf(Color(0xFFBCE3EE), Color(0xFFBBD4F0))
    BackgroundPreset.NightOrbit -> listOf(Color(0xFF34315D), Color(0xFF163C4C))
}

private fun accentSwatchColors(choice: AppAccentColor): List<Color> = when (choice) {
    AppAccentColor.LumaViolet -> listOf(Color(0xFF6550C8), Color(0xFF3F7479))
    AppAccentColor.Sage -> listOf(Color(0xFF3E6F45), Color(0xFF74642F))
    AppAccentColor.Rose -> listOf(Color(0xFF99415E), Color(0xFF725A42))
    AppAccentColor.Amber -> listOf(Color(0xFF865400), Color(0xFF5D6F47))
    AppAccentColor.Ocean -> listOf(Color(0xFF2D6684), Color(0xFF5C6090))
}

private fun textSwatchColor(choice: AppTextColor): Color = when (choice) {
    AppTextColor.Default -> Color(0xFF211D27)
    AppTextColor.Ink -> Color(0xFF17141B)
    AppTextColor.Plum -> Color(0xFF2A173C)
    AppTextColor.Forest -> Color(0xFF152A1D)
    AppTextColor.WarmIvory -> Color(0xFFFFF1DB)
}

private fun Float.percentLabel(): String = "${(coerceIn(0f, 1f) * 100).roundToInt()}%"

private const val MaxUserNameLength = 40

package com.orbit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.R
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** A bounded production-component preview; its Haze source never leaves this box. */
@Composable
fun GlassRolePreview(
    modifier: Modifier = Modifier,
    title: String = "Glass roles",
) {
    val previewHazeState = rememberHazeState()
    CompositionLocalProvider(
        LocalOrbitHazeState provides previewHazeState,
        LocalGlassRenderingPolicy provides GlassRenderingPolicy.LiveAllowed,
    ) {
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeSource(previewHazeState)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LiveGlassSurface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("glass-preview-live"),
                        shape = OrbitShapes.Standard,
                        style = GlassSurfaceStyle.Subtle,
                    ) {
                        PreviewLabel(stringResource(R.string.settings_live_glass), Modifier.padding(14.dp))
                    }
                    SoftGlassSurface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("glass-preview-soft"),
                        shape = OrbitShapes.Standard,
                        style = GlassSurfaceStyle.Subtle,
                    ) {
                        PreviewLabel(stringResource(R.string.settings_soft_glass), Modifier.padding(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Preview(name = "Glass roles - light", widthDp = 360, showBackground = true)
@Composable
private fun GlassRolesLightPreview() {
    OrbitTheme(settings = AppSettings(themeMode = SettingsThemeMode.Light)) {
        GlassRolePreview(Modifier.fillMaxWidth())
    }
}

@Preview(name = "Glass roles - dark", widthDp = 360, showBackground = true)
@Composable
private fun GlassRolesDarkPreview() {
    OrbitTheme(settings = AppSettings(themeMode = SettingsThemeMode.Dark)) {
        GlassRolePreview(Modifier.fillMaxWidth())
    }
}

@Preview(name = "Modal surface - light", widthDp = 360, showBackground = true)
@Composable
private fun ModalSurfaceLightPreview() {
    OrbitTheme(settings = AppSettings(themeMode = SettingsThemeMode.Light)) {
        ModalSurface(Modifier.fillMaxWidth()) {
            PreviewLabel("Modal Surface", Modifier.padding(24.dp))
        }
    }
}

@Preview(name = "Modal surface - dark", widthDp = 360, showBackground = true)
@Composable
private fun ModalSurfaceDarkPreview() {
    OrbitTheme(settings = AppSettings(themeMode = SettingsThemeMode.Dark)) {
        ModalSurface(Modifier.fillMaxWidth()) {
            PreviewLabel("Modal Surface", Modifier.padding(24.dp))
        }
    }
}

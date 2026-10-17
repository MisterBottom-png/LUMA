package com.orbit.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.domain.model.TabBarStyle
import com.orbit.app.R
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.ui.theme.TalleleTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * A bounded preview of the two tab bar surfaces, on the old Appearance screen only (removed in P5).
 * "Live" is the Glass tab bar surface over a blur source that never leaves this box, lowered to
 * Frosted where this phone cannot blur; "soft" is the Frosted one. They are the real tab bar
 * surface, not a copy. The bands behind are neutral tokens (never ochre, which is a signal colour).
 */
@Composable
fun GlassRolePreview(
    modifier: Modifier = Modifier,
    title: String = "Glass roles",
) {
    val previewHazeState = rememberHazeState()
    val colors = TalleleTheme.colors
    // The sample shows the look this phone would really get: Frosted where Glass cannot blur
    // (older Android, battery saver, "Remove animations").
    val liveLook = resolveTabBarLook(
        chosen = TabBarStyle.Glass,
        platformApi = Build.VERSION.SDK_INT,
        batterySaver = rememberBatterySaver(),
        animationsRemoved = rememberReducedMotion(),
        hasHazeSource = true,
        routeAllowsLive = true,
    )
    CompositionLocalProvider(
        LocalOrbitHazeState provides previewHazeState,
        LocalGlassRenderingPolicy provides GlassRenderingPolicy.LiveAllowed,
    ) {
        Box(modifier = modifier) {
            // Flat colour bands behind the samples, so the blur has something to blur.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .hazeSource(previewHazeState)
                    .background(colors.page),
            ) {
                listOf(colors.inkSoft, colors.well, colors.text3, colors.hairline).forEach { band ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(band),
                    )
                }
            }
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
                    TabBarSurface(
                        look = liveLook,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("glass-preview-live"),
                        shape = OrbitShapes.Standard,
                    ) {
                        PreviewLabel(stringResource(R.string.settings_live_glass), Modifier.padding(14.dp))
                    }
                    TabBarSurface(
                        look = TabBarStyle.Frosted,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("glass-preview-soft"),
                        shape = OrbitShapes.Standard,
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

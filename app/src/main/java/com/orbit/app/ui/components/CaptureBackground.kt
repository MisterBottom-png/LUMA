package com.orbit.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.domain.model.AppBackground
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.ui.theme.TalleleTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text

/**
 * One built-in Capture background (proposal section 7.17): a light and a dark image and a fixed
 * wash of the `page` colour laid over each, tuned so text over the image reaches 4.5:1.
 *
 * Washes follow section 7.17 except Harbour mist and Pine fog in the light theme, which decision
 * D-23 raised after measuring the shipped images (`docs/limestone-harbour/p1-assets.md`).
 */
data class TalleleBackgroundSpec(
    val background: AppBackground,
    @param:DrawableRes val light: Int,
    @param:DrawableRes val dark: Int,
    /** Opacity of the page colour over the light image, 0 to 1. */
    val lightWash: Float,
    /** Opacity of the page colour over the dark image, 0 to 1. */
    val darkWash: Float,
) {
    @DrawableRes
    fun drawableFor(isDark: Boolean): Int = if (isDark) dark else light

    fun washFor(isDark: Boolean): Float = if (isDark) darkWash else lightWash
}

/** The five built-in backgrounds. [AppBackground.None] has no entry: it is plain `page`. */
val TalleleBackgroundSpecs: List<TalleleBackgroundSpec> = listOf(
    TalleleBackgroundSpec(AppBackground.Limestone, R.drawable.bg_limestone_light, R.drawable.bg_limestone_dark, 0.10f, 0f),
    TalleleBackgroundSpec(AppBackground.HarbourMist, R.drawable.bg_harbour_mist_light, R.drawable.bg_harbour_mist_dark, 0.45f, 0f),
    TalleleBackgroundSpec(AppBackground.InkWash, R.drawable.bg_ink_wash_light, R.drawable.bg_ink_wash_dark, 0.10f, 0f),
    TalleleBackgroundSpec(AppBackground.PineFog, R.drawable.bg_pine_fog_light, R.drawable.bg_pine_fog_dark, 0.35f, 0f),
    TalleleBackgroundSpec(AppBackground.LinenLight, R.drawable.bg_linen_light_light, R.drawable.bg_linen_light_dark, 0.35f, 0f),
)

/** The spec for a background, or null for [AppBackground.None]. */
fun talleleBackgroundSpecFor(background: AppBackground): TalleleBackgroundSpec? =
    TalleleBackgroundSpecs.firstOrNull { it.background == background }

/** The image for a background in the given theme, or null for [AppBackground.None]. */
@DrawableRes
fun backgroundDrawableFor(background: AppBackground, isDark: Boolean): Int? =
    talleleBackgroundSpecFor(background)?.drawableFor(isDark)

/**
 * The Capture screen's background: the image for the current theme, filling the space and cropped
 * to fit, with a solid wash of the `page` colour on top. None draws nothing, so `page` shows through.
 * No blur and no photo picker; the images are already soft. Capture (P3) places it behind its
 * content; it is not on any screen yet (decision D-26).
 */
@Composable
fun CaptureBackground(
    background: AppBackground,
    modifier: Modifier = Modifier,
) {
    val spec = talleleBackgroundSpecFor(background) ?: return
    val colors = TalleleTheme.colors
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(spec.drawableFor(colors.isDark)),
            // Decorative: the words on Capture carry the meaning.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val wash = spec.washFor(colors.isDark)
        if (wash > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.page.copy(alpha = wash)),
            )
        }
    }
}

@Composable
private fun CaptureBackgroundPreviewFrame(background: AppBackground, dark: Boolean) {
    OrbitTheme(settings = AppSettings(themeMode = if (dark) SettingsThemeMode.Dark else SettingsThemeMode.Light)) {
        Box(Modifier.fillMaxSize().background(TalleleTheme.colors.page)) {
            CaptureBackground(background)
            Text(
                text = "Sample words over the background",
                color = TalleleTheme.colors.text,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Preview(name = "Harbour mist - light", widthDp = 360, heightDp = 640)
@Composable
private fun HarbourMistLightPreview() = CaptureBackgroundPreviewFrame(AppBackground.HarbourMist, dark = false)

@Preview(name = "Harbour mist - dark", widthDp = 360, heightDp = 640)
@Composable
private fun HarbourMistDarkPreview() = CaptureBackgroundPreviewFrame(AppBackground.HarbourMist, dark = true)

@Preview(name = "Ink wash - light", widthDp = 360, heightDp = 640)
@Composable
private fun InkWashLightPreview() = CaptureBackgroundPreviewFrame(AppBackground.InkWash, dark = false)

@Preview(name = "Pine fog - dark", widthDp = 360, heightDp = 640)
@Composable
private fun PineFogDarkPreview() = CaptureBackgroundPreviewFrame(AppBackground.PineFog, dark = true)

@Preview(name = "None - light", widthDp = 360, heightDp = 640)
@Composable
private fun NoneLightPreview() = CaptureBackgroundPreviewFrame(AppBackground.None, dark = false)

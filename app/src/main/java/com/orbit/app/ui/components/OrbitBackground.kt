package com.orbit.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.BackgroundPreset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OrbitBackground(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hazeState = remember { HazeState() }
    val palette = backgroundPalette(settings.backgroundPreset, isDark)
    val backgroundBlur = settings.backgroundBlur.coerceIn(0f, 1f) * 16f
    val dimAlpha = settings.backgroundDim.coerceIn(0f, 1f) * if (isDark) 0.52f else 0.20f
    val customBackgroundBitmap = rememberCustomBackgroundBitmap(settings.customBackgroundUri)
    val visibleCustomBackground = customBackgroundBitmap.value

    CompositionLocalProvider(
        LocalOrbitHazeState provides hazeState,
        LocalOrbitAppearance provides settings,
        LocalOrbitUsesCustomBackground provides (visibleCustomBackground != null),
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(hazeState),
            ) {
                visibleCustomBackground?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(backgroundBlur.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                if (dimAlpha > 0f) {
                                    drawRect(Color.Black.copy(alpha = dimAlpha))
                                }
                            },
                    )
                    CustomBackgroundContrastOverlay(isDark = isDark)
                } ?: PresetBackground(
                    palette = palette,
                    backgroundBlur = backgroundBlur,
                    dimAlpha = dimAlpha,
                )
            }
            content()
        }
    }
}

@Composable
private fun CustomBackgroundContrastOverlay(isDark: Boolean) {
    val style = customBackgroundContrastStyle(
        isDark = isDark,
        tonalColor = MaterialTheme.colorScheme.background,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(style.tonalColor.copy(alpha = style.bodyAlpha))
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to style.tonalColor.copy(alpha = style.systemBarAlpha),
                            style.headerStop to style.tonalColor.copy(alpha = style.headerAlpha),
                            1f to Color.Transparent,
                        ),
                        endY = size.height * style.endHeightFraction,
                    ),
                )
            },
    )
}

internal data class CustomBackgroundContrastStyle(
    val tonalColor: Color,
    val systemBarAlpha: Float,
    val headerAlpha: Float,
    val bodyAlpha: Float,
    val headerStop: Float,
    val endHeightFraction: Float,
)

internal fun customBackgroundContrastStyle(
    isDark: Boolean,
    tonalColor: Color,
): CustomBackgroundContrastStyle = CustomBackgroundContrastStyle(
    tonalColor = tonalColor,
    systemBarAlpha = if (isDark) 0.88f else 0.92f,
    headerAlpha = if (isDark) 0.82f else 0.86f,
    bodyAlpha = if (isDark) 0.68f else 0.72f,
    headerStop = 0.58f,
    endHeightFraction = 0.36f,
)

@Composable
private fun rememberCustomBackgroundBitmap(uriString: String?): State<Bitmap?> {
    val context = LocalContext.current
    val bitmapState = remember(uriString) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uriString) {
        if (uriString != null) {
            bitmapState.value = withContext(Dispatchers.IO) {
                loadScaledBitmap(context, uriString)
            }
        }
    }
    return bitmapState
}

@Composable
private fun PresetBackground(
    palette: BackgroundPalette,
    backgroundBlur: Float,
    dimAlpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(backgroundBlur.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        palette.baseColors,
                        endY = size.height,
                    ),
                )
                if (dimAlpha > 0f) {
                    drawRect(Color.Black.copy(alpha = dimAlpha))
                }
            },
    )
}

private fun loadScaledBitmap(context: Context, uriString: String): Bitmap? {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
    }.getOrNull()
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        }
    }.getOrNull()
}

private fun calculateSampleSize(width: Int, height: Int): Int {
    var sampleSize = 1
    while ((width / sampleSize) > MaxCustomBackgroundDimension ||
        (height / sampleSize) > MaxCustomBackgroundDimension
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private data class BackgroundPalette(
    val baseColors: List<Color>,
)

private fun backgroundPalette(
    preset: BackgroundPreset,
    isDark: Boolean,
): BackgroundPalette = when (preset) {
    BackgroundPreset.SoftDawn -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF171218), Color(0xFF251B29), Color(0xFF202125)),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFFFF8F4), Color(0xFFF6EDF7), Color(0xFFF5F1EC)),
        )
    }

    BackgroundPreset.VioletMist -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF15111D), Color(0xFF241A34), Color(0xFF181827)),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFFBF8FF), Color(0xFFEFE8FA), Color(0xFFF4EEFA)),
        )
    }

    BackgroundPreset.CalmSky -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF10181D), Color(0xFF15262D), Color(0xFF17202B)),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFF6FBFD), Color(0xFFE7F3F7), Color(0xFFEBF0FA)),
        )
    }

    BackgroundPreset.NightOrbit -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF0B0C16), Color(0xFF15172B), Color(0xFF101A25)),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFF5F5FC), Color(0xFFE8E9F6), Color(0xFFE9F0F4)),
        )
    }
}

private const val MaxCustomBackgroundDimension = 1600

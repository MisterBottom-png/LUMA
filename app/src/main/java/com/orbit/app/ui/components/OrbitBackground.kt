package com.orbit.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.BackgroundBlur
import com.orbit.app.domain.model.BackgroundDimmingMode
import com.orbit.app.domain.model.BackgroundPreset
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OrbitBackground(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    glassRenderingPolicy: GlassRenderingPolicy = GlassRenderingPolicy.LiveAllowed,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hazeState = rememberHazeState()
    val palette = backgroundPalette(settings.backgroundPreset, isDark)
    val dimAlpha = backgroundDimAlpha(
        strength = settings.backgroundDim,
        isDark = isDark,
        mode = settings.backgroundDimmingMode,
    )
    val customBackgroundBitmap = rememberCustomBackgroundBitmap(
        uriString = settings.customBackgroundUri,
        blur = settings.backgroundBlur,
    )
    val visibleCustomBackground = customBackgroundBitmap.value

    CompositionLocalProvider(
        LocalOrbitHazeState provides hazeState,
        LocalGlassRenderingPolicy provides glassRenderingPolicyFor(settings.glassEffect, glassRenderingPolicy),
        LocalOrbitAppearance provides settings,
        LocalOrbitUsesCustomBackground provides (visibleCustomBackground != null),
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                visibleCustomBackground?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CustomBackgroundContrastOverlay(
                        isDark = isDark,
                        dimAlpha = dimAlpha,
                    )
                } ?: PresetBackground(
                    palette = palette,
                    accent = MaterialTheme.colorScheme.primary,
                    isDark = isDark,
                    // Built-in backgrounds are already calm; a forced dim only turns them grey.
                    // Dimming stays for photos, and for presets when the person set it by hand.
                    dimAlpha = if (settings.backgroundDimmingMode == BackgroundDimmingMode.Manual) dimAlpha else 0f,
                )
            }
            content()
        }
    }
}

@Composable
private fun CustomBackgroundContrastOverlay(isDark: Boolean, dimAlpha: Float) {
    val style = customBackgroundContrastStyle(
        isDark = isDark,
        tonalColor = MaterialTheme.colorScheme.background,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (dimAlpha > 0f) {
                    drawRect(Color.Black.copy(alpha = dimAlpha))
                }
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
private fun rememberCustomBackgroundBitmap(
    uriString: String?,
    blur: BackgroundBlur,
): State<Bitmap?> {
    val context = LocalContext.current
    val blurRadius = backgroundBlurRadius(blur)
    val bitmapState = remember(uriString) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uriString, blurRadius) {
        if (uriString != null) {
            bitmapState.value = withContext(Dispatchers.IO) {
                processedBackgroundBitmap(context, uriString, blurRadius)
            }
        } else {
            bitmapState.value = null
        }
    }
    return bitmapState
}

internal fun backgroundBlurRadius(blur: BackgroundBlur): Int = when (blur) {
    BackgroundBlur.None -> 0
    BackgroundBlur.Soft -> 8
    BackgroundBlur.Medium -> 16
    BackgroundBlur.Strong -> MaxCachedBackgroundBlurRadius
}

internal fun backgroundDimAlpha(
    strength: Float,
    isDark: Boolean,
    mode: BackgroundDimmingMode = BackgroundDimmingMode.Adaptive,
): Float {
    val protectedStrength = if (mode == BackgroundDimmingMode.Adaptive) {
        maxOf(strength.coerceIn(0f, 1f), 0.22f)
    } else {
        strength.coerceIn(0f, 1f)
    }
    return protectedStrength * if (isDark) 0.64f else 0.56f
}

private fun processedBackgroundBitmap(
    context: Context,
    uriString: String,
    blurRadius: Int,
): Bitmap? {
    val key = "$uriString#$blurRadius"
    synchronized(processedBackgroundCache) {
        processedBackgroundCache[key]?.let { return it }
    }
    val decoded = loadScaledBitmap(context, uriString) ?: return null
    val processed = if (blurRadius == 0) decoded else decoded.boxBlur(blurRadius)
    synchronized(processedBackgroundCache) {
        processedBackgroundCache[key] = processed
    }
    return processed
}

private fun Bitmap.boxBlur(radius: Int): Bitmap {
    val width = width
    val height = height
    val source = IntArray(width * height)
    getPixels(source, 0, width, 0, 0, width, height)
    val horizontal = IntArray(source.size)
    val output = IntArray(source.size)
    blurPass(source, horizontal, width, height, radius, horizontalPass = true)
    blurPass(horizontal, output, width, height, radius, horizontalPass = false)
    return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
}

private fun blurPass(
    source: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontalPass: Boolean,
) {
    val outer = if (horizontalPass) height else width
    val inner = if (horizontalPass) width else height
    val window = radius * 2 + 1
    repeat(outer) { outerIndex ->
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        fun pixel(innerIndex: Int): Int {
            val clamped = innerIndex.coerceIn(0, inner - 1)
            return if (horizontalPass) {
                source[outerIndex * width + clamped]
            } else {
                source[clamped * width + outerIndex]
            }
        }
        for (index in -radius..radius) {
            val color = pixel(index)
            alpha += color ushr 24
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        repeat(inner) { innerIndex ->
            val outputIndex = if (horizontalPass) {
                outerIndex * width + innerIndex
            } else {
                innerIndex * width + outerIndex
            }
            output[outputIndex] =
                ((alpha / window) shl 24) or
                ((red / window) shl 16) or
                ((green / window) shl 8) or
                (blue / window)
            val outgoing = pixel(innerIndex - radius)
            val incoming = pixel(innerIndex + radius + 1)
            alpha += (incoming ushr 24) - (outgoing ushr 24)
            red += (incoming shr 16 and 0xFF) - (outgoing shr 16 and 0xFF)
            green += (incoming shr 8 and 0xFF) - (outgoing shr 8 and 0xFF)
            blue += (incoming and 0xFF) - (outgoing and 0xFF)
        }
    }
}

@Composable
private fun PresetBackground(
    palette: BackgroundPalette,
    accent: Color,
    isDark: Boolean,
    dimAlpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        palette.baseColors,
                        endY = size.height,
                    ),
                )
                // Two very soft light pools. Glass needs gentle variation behind it to read
                // as glass; on a flat fill it reads as grey.
                val accentAlpha = if (isDark) 0.16f else 0.10f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = accentAlpha), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.10f),
                        radius = size.maxDimension * 0.62f,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.bloom.copy(alpha = if (isDark) 0.20f else 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 0.78f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                if (dimAlpha > 0f) {
                    drawRect(Color.Black.copy(alpha = dimAlpha))
                }
            },
    )
}

private fun loadScaledBitmap(context: Context, uriString: String): Bitmap? {
    val uri = runCatching { uriString.toUri() }.getOrNull() ?: return null
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
    /** A second, warmer or cooler light pool that keeps the page from looking flat. */
    val bloom: Color,
)

private fun backgroundPalette(
    preset: BackgroundPreset,
    isDark: Boolean,
): BackgroundPalette = when (preset) {
    BackgroundPreset.InkPaper -> if (isDark) {
        BackgroundPalette(baseColors = listOf(Color(0xFF101311), Color(0xFF151B1C), Color(0xFF111615)), bloom = Color(0xFF2F4A4E))
    } else {
        BackgroundPalette(baseColors = listOf(Color(0xFFFCFBF8), Color(0xFFF6F4EF), Color(0xFFF1F3F1)), bloom = Color(0xFFE6EEEC))
    }

    BackgroundPreset.SoftDawn -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF151116), Color(0xFF1E1722), Color(0xFF1A1A1F)),
            bloom = Color(0xFF5A3A4A),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFFFFAF7), Color(0xFFF9F2F8), Color(0xFFF7F3EE)),
            bloom = Color(0xFFFBE3D8),
        )
    }

    BackgroundPreset.VioletMist -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF13101A), Color(0xFF1C1628), Color(0xFF151520)),
            bloom = Color(0xFF3E2F66),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFFCFAFF), Color(0xFFF4F0FB), Color(0xFFF6F2FA)),
            bloom = Color(0xFFE6DDFA),
        )
    }

    BackgroundPreset.CalmSky -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF0F161A), Color(0xFF132027), Color(0xFF141C25)),
            bloom = Color(0xFF244656),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFF8FCFD), Color(0xFFEEF6F9), Color(0xFFF0F3FA)),
            bloom = Color(0xFFDCEDF4),
        )
    }

    BackgroundPreset.NightOrbit -> if (isDark) {
        BackgroundPalette(
            baseColors = listOf(Color(0xFF0B0C16), Color(0xFF121427), Color(0xFF0F1722)),
            bloom = Color(0xFF26305E),
        )
    } else {
        BackgroundPalette(
            baseColors = listOf(Color(0xFFF7F7FD), Color(0xFFEEEFF8), Color(0xFFEEF3F6)),
            bloom = Color(0xFFDDE2F6),
        )
    }
}

private const val MaxCustomBackgroundDimension = 1600
private const val MaxCachedBackgroundBlurRadius = 24
private val processedBackgroundCache = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 4
}

package com.orbit.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.BackgroundPreset
import com.orbit.app.domain.model.GlassPreference
import com.orbit.app.ui.theme.defaultOrbitColorScheme
import com.orbit.app.ui.theme.readabilityRatio
import com.orbit.app.ui.theme.textPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body text on the live glass surfaces must reach WCAG AA (4.5:1) on every built-in background,
 * light and dark. This is arithmetic on the tint over the lightest and darkest colour the preset
 * gradient can put under a surface: a gate, not proof. The real look is checked on a phone.
 * A preset that fails gets a higher tint alpha, not a smaller font.
 */
class GlassContrastTest {
    @Test
    fun bodyTextOnLiveGlassReachesAaOnEveryPresetInBothThemes() {
        val failures = mutableListOf<String>()
        BackgroundPreset.entries.forEach { preset ->
            listOf(false, true).forEach { isDark ->
                val scheme = defaultOrbitColorScheme(isDark)
                val under = backgroundExtremes(preset, isDark, accent = scheme.primary)
                LiveGlassStyles.forEach { style ->
                    GlassPreference.entries.forEach { preference ->
                        val tint = glassFallbackTint(
                            isDark = isDark,
                            tintAlpha = glassTintAlpha(
                                style = style,
                                isDark = isDark,
                                glassStrength = preference.legacyStrength,
                                hasCustomBackground = false,
                            ),
                        )
                        under.forEach { background ->
                            val surface = tint.compositeOver(background)
                            AppTextColor.entries.forEach { textColor ->
                                val body = textPalette(textColor, isDark, scheme.onSurface).primary
                                val ratio = readabilityRatio(body, surface)
                                if (ratio < 4.5f) {
                                    failures += "$preset dark=$isDark $style $preference $textColor: " +
                                        "%.2f:1".format(ratio)
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue("Body text below 4.5:1 on glass:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Lightest and darkest colour the preset can put under a surface. */
    private fun backgroundExtremes(preset: BackgroundPreset, isDark: Boolean, accent: Color): List<Color> {
        val candidates = presetUnderColors(preset, isDark, accent)
        return listOf(
            candidates.maxBy { it.luminance() },
            candidates.minBy { it.luminance() },
        )
    }

    /**
     * Every colour the preset background can show: each gradient stop on its own, under the
     * centre of the accent pool, under the centre of the bloom pool, and under both.
     */
    private fun presetUnderColors(preset: BackgroundPreset, isDark: Boolean, accent: Color): List<Color> {
        val palette = backgroundPalette(preset, isDark)
        val accentPool = accent.copy(alpha = presetAccentPoolAlpha(isDark))
        val bloomPool = palette.bloom.copy(alpha = presetBloomAlpha(isDark))
        return palette.baseColors.flatMap { base ->
            listOf(
                base,
                accentPool.compositeOver(base),
                bloomPool.compositeOver(base),
                bloomPool.compositeOver(accentPool.compositeOver(base)),
            )
        }
    }
}

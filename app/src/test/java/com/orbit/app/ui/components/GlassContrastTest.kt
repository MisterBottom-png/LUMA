package com.orbit.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.orbit.app.domain.model.AppAccent
import com.orbit.app.domain.model.TabBarStyle
import com.orbit.app.ui.theme.TalleleColors
import com.orbit.app.ui.theme.TextContrastMinimum
import com.orbit.app.ui.theme.readabilityRatio
import com.orbit.app.ui.theme.talleleColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tab bar and solid-surface contrast (proposal section 6.4). Arithmetic, a gate and not proof: the
 * real look is checked on a phone.
 *
 * Worst case for the bar's labels is full-strength main text directly under the bar with no blur:
 * the bar's `surface` at its look's opacity composited over `text`. The proposal lists 5.2 / 5.3 for
 * Glass, 6.4 / 7.3 for Frosted and 7.2 / 8.4 for Solid (light / dark, Ink accent).
 */
class GlassContrastTest {
    private val looks = TabBarStyle.entries
    private val themes = listOf(false, true)

    /** The bar fill seen when [text] sits directly beneath it. */
    private fun barOverWorstCase(colors: TalleleColors, look: TabBarStyle): Color =
        colors.surface.copy(alpha = tabBarSurfaceAlpha(look)).compositeOver(colors.text)

    @Test
    fun unselectedTabLabelsReachAaOverTheBarInEveryLookThemeAndAccent() {
        val failures = mutableListOf<String>()
        looks.forEach { look ->
            themes.forEach { isDark ->
                AppAccent.entries.forEach { accent ->
                    val colors = talleleColors(isDark, accent)
                    val ratio = readabilityRatio(colors.text2, barOverWorstCase(colors, look))
                    if (ratio < TextContrastMinimum) failures += "$look dark=$isDark $accent: %.2f:1".format(ratio)
                }
            }
        }
        assertTrue("Tab labels below 4.5:1:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun selectedTabLabelsReachAaOnTheirInkSoftPillInEveryThemeAndAccent() {
        AppAccent.entries.forEach { accent ->
            themes.forEach { isDark ->
                val colors = talleleColors(isDark, accent)
                val ratio = readabilityRatio(colors.onInkSoft, colors.inkSoft)
                assertTrue("$accent dark=$isDark: %.2f:1".format(ratio), ratio >= TextContrastMinimum)
            }
        }
    }

    @Test
    fun theBadgeCountReachesAaOnItsFill() {
        themes.forEach { isDark ->
            val colors = talleleColors(isDark, AppAccent.Ink)
            val ratio = readabilityRatio(colors.onOchreSoft, colors.ochreSoft)
            assertTrue("dark=$isDark: %.2f:1".format(ratio), ratio >= TextContrastMinimum)
        }
    }

    @Test
    fun mainAndSecondaryTextReachAaOnEverySolidSurfaceInEveryTheme() {
        val failures = mutableListOf<String>()
        themes.forEach { isDark ->
            AppAccent.entries.forEach { accent ->
                val colors = talleleColors(isDark, accent)
                GlassSurfaceStyle.entries.forEach { style ->
                    val fill = solidSurfaceColorFor(style, colors)
                    val content = solidSurfaceContentColorFor(style, colors)
                    val ratio = readabilityRatio(content, fill)
                    if (ratio < TextContrastMinimum) failures += "$style dark=$isDark $accent: %.2f:1".format(ratio)
                    if (style != GlassSurfaceStyle.NavigationAction) {
                        val secondary = readabilityRatio(colors.text2, fill)
                        if (secondary < TextContrastMinimum) {
                            failures += "$style text-2 dark=$isDark $accent: %.2f:1".format(secondary)
                        }
                    }
                }
            }
        }
        assertTrue("Text below 4.5:1 on a solid surface:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun printMeasuredTabLabelContrast() {
        // Evidence for the phase report; the assertions above are the gate.
        looks.forEach { look ->
            val light = readabilityRatio(talleleColors(false, AppAccent.Ink).text2, barOverWorstCase(talleleColors(false, AppAccent.Ink), look))
            val dark = readabilityRatio(talleleColors(true, AppAccent.Ink).text2, barOverWorstCase(talleleColors(true, AppAccent.Ink), look))
            println("TABLABEL $look light=%.2f dark=%.2f".format(light, dark))
            AppAccent.entries.forEach { accent ->
                val l = talleleColors(false, accent)
                val d = talleleColors(true, accent)
                println(
                    "TABLABEL $look $accent selected light=%.2f dark=%.2f".format(
                        readabilityRatio(l.onInkSoft, l.inkSoft),
                        readabilityRatio(d.onInkSoft, d.inkSoft),
                    ),
                )
            }
        }
    }
}

package com.orbit.app.ui.components

import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppAccent
import com.orbit.app.ui.theme.talleleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the tab bar is glass (proposal sections 0 and 6.4, decisions D-03 and D-24). Every other
 * surface is an opaque token colour; Haze's blur is used in the tab bar and nowhere else.
 */
class GlassRolePolicyTest {
    private val everyTheme = listOf(false, true)

    @Test
    fun everySurfaceStyleIsAnOpaqueTokenColourInEveryThemeAndAccent() {
        everyTheme.forEach { isDark ->
            AppAccent.entries.forEach { accent ->
                val colors = talleleColors(isDark, accent)
                GlassSurfaceStyle.entries.forEach { style ->
                    val fill = solidSurfaceColorFor(style, colors)
                    assertEquals("$style dark=$isDark $accent", 1f, fill.alpha)
                    val expected = when (style) {
                        GlassSurfaceStyle.Sheet -> colors.sheet
                        GlassSurfaceStyle.NavigationAction -> colors.inkSoft
                        else -> colors.surface
                    }
                    assertEquals("$style dark=$isDark $accent", expected, fill)
                }
            }
        }
    }

    @Test
    fun cardsAndTheCaptureBoxUseSurfaceAndSheetsUseSheet() {
        everyTheme.forEach { isDark ->
            val colors = talleleColors(isDark, AppAccent.Ink)
            listOf(
                GlassSurfaceStyle.Standard,
                GlassSurfaceStyle.Prominent,
                GlassSurfaceStyle.Subtle,
                GlassSurfaceStyle.HomeCapture,
                GlassSurfaceStyle.HomeNavigation,
            ).forEach { style -> assertEquals("$style", colors.surface, solidSurfaceColorFor(style, colors)) }
            assertEquals(colors.sheet, solidSurfaceColorFor(GlassSurfaceStyle.Sheet, colors))
            assertEquals(colors.inkSoft, solidSurfaceColorFor(GlassSurfaceStyle.NavigationAction, colors))
        }
    }

    @Test
    fun contentColourIsMainTextExceptOnAnInkSoftAction() {
        everyTheme.forEach { isDark ->
            val colors = talleleColors(isDark, AppAccent.Ink)
            GlassSurfaceStyle.entries.forEach { style ->
                val expected = if (style == GlassSurfaceStyle.NavigationAction) colors.onInkSoft else colors.text
                assertEquals("$style", expected, solidSurfaceContentColorFor(style, colors))
            }
        }
    }

    @Test
    fun onlySheetsAndDialogsFloat_contentStaysFlat() {
        assertTrue(solidSurfaceElevationFor(GlassSurfaceStyle.Sheet) > 0.dp)
        GlassSurfaceStyle.entries.filter { it != GlassSurfaceStyle.Sheet }.forEach { style ->
            assertEquals("$style", 0.dp, solidSurfaceElevationFor(style))
        }
    }

    @Test
    fun modalScrimStaysDarkerInTheDarkTheme() {
        assertEquals(0.32f, modalScrimAlpha(isDark = false))
        assertEquals(0.44f, modalScrimAlpha(isDark = true))
    }

    @Test
    fun hazeBlurIsOnlyUsedByTheTabBar() {
        val users = mainKotlinSources()
            .filter { it.readText().contains("hazeEffect(") }
            .map { it.name }
            .sorted()
        // TabBarSurface is the only blur user; the Appearance preview reuses that same surface.
        assertEquals(listOf("TalleleTabBar.kt"), users)
    }

    /**
     * D-27: what the bar blurs is the page (OrbitBackground) and the shell's NavHost container
     * (tabBarBlurSource, applied once in OrbitApp). No list, card or route may become a source.
     */
    @Test
    fun hazeSourcesAreOnlyThePageAndTheShellContainer() {
        val sourceFiles = mainKotlinSources()
            .filter { Regex("""\.hazeSource\(|\bhazeSource\(hazeState""").containsMatchIn(stripComments(it.readText())) }
            .map { it.name }
            .sorted()
        // GlassRolePreview (old Appearance screen) and TalleleTabBar's @Preview frame own bounded,
        // local preview states; everything in the running app goes through the two shell sources.
        assertEquals(
            listOf("GlassRolePreview.kt", "OrbitBackground.kt", "TabBarLook.kt", "TalleleTabBar.kt"),
            sourceFiles,
        )
        val tabBarSource = mainKotlinSources().single { it.name == "TalleleTabBar.kt" }.readText()
        val previewStart = tabBarSource.indexOf("private fun TabBarPreviewFrame")
        assertTrue("TalleleTabBar.kt keeps its haze source inside the preview frame", previewStart >= 0)
        assertTrue(
            "TalleleTabBar.kt may only use hazeSource inside TabBarPreviewFrame",
            tabBarSource.indexOf(".hazeSource(") > previewStart,
        )
        val shellUses = mainKotlinSources()
            .sumOf { Regex("""(?<!fun Modifier)\.tabBarBlurSource\(""").findAll(stripComments(it.readText())).count() }
        assertEquals(1, shellUses)
        val shellFile = mainKotlinSources().single { Regex("""(?<!fun Modifier)\.tabBarBlurSource\(""").containsMatchIn(stripComments(it.readText())) }
        assertEquals("OrbitApp.kt", shellFile.name)
    }

    @Test
    fun noOtherFileInTheAppDrawsAHazeEffect() {
        val offenders = mainKotlinSources()
            .filter { it.name != "TalleleTabBar.kt" }
            .filter { Regex("""\bhazeEffect\b|\bhazeChild\b""").containsMatchIn(stripComments(it.readText())) }
            .map { it.name }
        assertTrue("Blur outside the tab bar: $offenders", offenders.isEmpty())
    }

    @Test
    fun theSolidSurfaceFilesHaveNoGradientNoiseOrBlur() {
        val files = listOf("GlassSurface.kt", "LumaMenu.kt", "LumaModal.kt", "OrbitBackground.kt")
        val banned = listOf("Brush.", "noiseFactor", "HazeStyle", "blurRadius", "HazeTint", "RenderEffect", "Modifier.blur")
        files.forEach { name ->
            val source = stripComments(mainKotlinSources().first { it.name == name }.readText())
            banned.forEach { token ->
                assertFalse("$name uses $token", source.contains(token))
            }
        }
    }

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }
}

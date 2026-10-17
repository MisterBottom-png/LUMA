package com.orbit.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.orbit.app.domain.model.AppAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimestoneColorsTest {
    private val allCombos = listOf(false, true).flatMap { dark -> AppAccent.entries.map { dark to it } }

    private fun hex(color: Color): String {
        val argb = color.toArgb()
        return "%06X".format(argb and 0xFFFFFF)
    }

    private fun assertText(name: String, fg: Color, bg: Color, min: Float = TextContrastMinimum) {
        val ratio = readabilityRatio(fg, bg)
        assertTrue("$name: ${hex(fg)} on ${hex(bg)} is $ratio, needs $min", ratio >= min)
    }

    // ---- exact section 6.1 and 7.17 values ----

    @Test
    fun `neutral tokens match section 6-1 exactly`() {
        val l = talleleColors(false, AppAccent.Ink)
        assertEquals(
            listOf("F3F1EC", "FBFAF7", "FFFFFF", "EAE7E0", "1B1F27", "4F5562", "5F646E", "DDD9D0", "8A8780"),
            listOf(l.page, l.surface, l.sheet, l.well, l.text, l.text2, l.text3, l.hairline, l.outline).map(::hex),
        )
        val d = talleleColors(true, AppAccent.Ink)
        assertEquals(
            listOf("121417", "1A1D22", "22262C", "0D0F12", "ECEAE4", "B4B7BE", "9A9EA7", "2C3037", "6F747D"),
            listOf(d.page, d.surface, d.sheet, d.well, d.text, d.text2, d.text3, d.hairline, d.outline).map(::hex),
        )
    }

    @Test
    fun `signal and status tokens match section 6-1 exactly`() {
        val l = talleleColors(false, AppAccent.Ink)
        assertEquals(
            listOf("8A5300", "F4E4C9", "3F2600", "2F6B3F", "A3302A", "F7DEDA"),
            listOf(l.ochre, l.ochreSoft, l.onOchreSoft, l.success, l.error, l.errorSoft).map(::hex),
        )
        val d = talleleColors(true, AppAccent.Ink)
        assertEquals(
            listOf("E6B566", "3B2B12", "F6DDB2", "93C6A0", "F2A49C", "4A1F1C"),
            listOf(d.ochre, d.ochreSoft, d.onOchreSoft, d.success, d.error, d.errorSoft).map(::hex),
        )
    }

    @Test
    fun `Ink accent matches section 6-1 exactly`() {
        val l = talleleColors(false, AppAccent.Ink)
        assertEquals(listOf("2D4B7E", "FFFFFF", "E0E7F3", "1A2D50"), listOf(l.ink, l.onInk, l.inkSoft, l.onInkSoft).map(::hex))
        val d = talleleColors(true, AppAccent.Ink)
        assertEquals(listOf("A8BFE8", "0F2147", "263A5E", "DCE6F8"), listOf(d.ink, d.onInk, d.inkSoft, d.onInkSoft).map(::hex))
    }

    @Test
    fun `accent ink values match the section 7-17 table`() {
        val expected = mapOf(
            AppAccent.Ink to ("2D4B7E" to "A8BFE8"),
            AppAccent.Harbour to ("1F5F66" to "8FCDD3"),
            AppAccent.Moss to ("3D6136" to "A9CB9C"),
            AppAccent.Plum to ("69406F" to "D6B4DC"),
        )
        expected.forEach { (accent, pair) ->
            assertEquals(pair.first, hex(talleleColors(false, accent).ink))
            assertEquals(pair.second, hex(talleleColors(true, accent).ink))
        }
    }

    @Test
    fun `light Space colours match section 6-1 and dark values are derived`() {
        val light = talleleColors(false, AppAccent.Ink).spaces
        assertEquals(
            listOf("5B6B82", "5A7A4E", "9A5B44", "7D6A4A", "7A5582", "2F7178", "9C4F62", "6F6A60"),
            light.all().map(::hex),
        )
        assertEquals(8, talleleColors(true, AppAccent.Ink).spaces.all().distinct().size)
    }

    @Test
    fun `accent does not change neutrals`() {
        listOf(false, true).forEach { dark ->
            val base = talleleColors(dark, AppAccent.Ink)
            AppAccent.entries.forEach { accent ->
                val c = talleleColors(dark, accent)
                assertEquals(base.page, c.page)
                assertEquals(base.surface, c.surface)
                assertEquals(base.text, c.text)
                assertEquals(base.ochre, c.ochre)
                assertEquals(base.error, c.error)
                assertEquals(dark, c.isDark)
                assertEquals(accent, c.accent)
            }
        }
    }

    // ---- contrast: text ----

    @Test
    fun `text tokens read on every background in both themes`() {
        listOf(false, true).forEach { dark ->
            val c = talleleColors(dark, AppAccent.Ink)
            listOf("page" to c.page, "surface" to c.surface, "sheet" to c.sheet, "well" to c.well).forEach { (bgName, bg) ->
                assertText("dark=$dark text on $bgName", c.text, bg)
                assertText("dark=$dark text-2 on $bgName", c.text2, bg)
                assertText("dark=$dark text-3 on $bgName", c.text3, bg)
            }
        }
    }

    @Test
    fun `accent ink links read on page surface sheet and well for every accent`() {
        allCombos.forEach { (dark, accent) ->
            val c = talleleColors(dark, accent)
            listOf("page" to c.page, "surface" to c.surface, "sheet" to c.sheet, "well" to c.well).forEach { (n, bg) ->
                assertText("dark=$dark $accent ink on $n", c.ink, bg)
            }
        }
    }

    @Test
    fun `on-ink on ink and on-ink-soft on ink-soft pass for every accent`() {
        allCombos.forEach { (dark, accent) ->
            val c = talleleColors(dark, accent)
            assertText("dark=$dark $accent on-ink", c.onInk, c.ink)
            assertText("dark=$dark $accent on-ink-soft", c.onInkSoft, c.inkSoft)
            // Selected chips read at the enhanced level too.
            assertText("dark=$dark $accent on-ink-soft (7:1)", c.onInkSoft, c.inkSoft, 7f)
        }
    }

    @Test
    fun `ochre success and error read on page surface and sheet`() {
        listOf(false, true).forEach { dark ->
            val c = talleleColors(dark, AppAccent.Ink)
            listOf("page" to c.page, "surface" to c.surface, "sheet" to c.sheet).forEach { (n, bg) ->
                assertText("dark=$dark ochre on $n", c.ochre, bg)
                assertText("dark=$dark error on $n", c.error, bg)
            }
            listOf("page" to c.page, "surface" to c.surface).forEach { (n, bg) ->
                assertText("dark=$dark success on $n", c.success, bg)
            }
        }
    }

    @Test
    fun `soft signal fills carry readable text`() {
        listOf(false, true).forEach { dark ->
            val c = talleleColors(dark, AppAccent.Ink)
            assertText("dark=$dark on-ochre-soft", c.onOchreSoft, c.ochreSoft)
            assertText("dark=$dark on-error", c.onError, c.error)
            assertText("dark=$dark on-error-soft", c.onErrorSoft, c.errorSoft)
        }
    }

    // ---- contrast: non-text UI ----

    @Test
    fun `outline meets 3 to 1 against page and surface`() {
        listOf(false, true).forEach { dark ->
            val c = talleleColors(dark, AppAccent.Ink)
            assertText("dark=$dark outline on page", c.outline, c.page, UiContrastMinimum)
            assertText("dark=$dark outline on surface", c.outline, c.surface, UiContrastMinimum)
            assertText("dark=$dark outline on sheet", c.outline, c.sheet, UiContrastMinimum)
        }
    }

    @Test
    fun `focus ring ink meets 3 to 1 against page for every accent`() {
        allCombos.forEach { (dark, accent) ->
            val c = talleleColors(dark, accent)
            assertText("dark=$dark $accent focus ring on page", c.ink, c.page, UiContrastMinimum)
            assertText("dark=$dark $accent focus ring on surface", c.ink, c.surface, UiContrastMinimum)
        }
    }

    @Test
    fun `Space colours meet 3 to 1 against page and surface in both themes`() {
        listOf(false, true).forEach { dark ->
            val c = talleleColors(dark, AppAccent.Ink)
            c.spaces.all().forEach { space ->
                assertText("dark=$dark space ${hex(space)} on page", space, c.page, UiContrastMinimum)
                assertText("dark=$dark space ${hex(space)} on surface", space, c.surface, UiContrastMinimum)
                assertText("dark=$dark space ${hex(space)} on sheet", space, c.sheet, UiContrastMinimum)
            }
        }
    }

    // ---- Material mapping ----

    @Test
    fun `Material scheme maps Limestone tokens`() {
        allCombos.forEach { (dark, accent) ->
            val c = talleleColors(dark, accent)
            val s = c.toMaterialColorScheme()
            assertEquals(c.ink, s.primary)
            assertEquals(c.onInk, s.onPrimary)
            assertEquals(c.inkSoft, s.primaryContainer)
            assertEquals(c.onInkSoft, s.onPrimaryContainer)
            assertEquals(c.page, s.background)
            assertEquals(c.text, s.onBackground)
            assertEquals(c.surface, s.surface)
            assertEquals(c.text, s.onSurface)
            assertEquals(c.text2, s.onSurfaceVariant)
            assertEquals(c.well, s.surfaceVariant)
            assertEquals(c.outline, s.outline)
            assertEquals(c.hairline, s.outlineVariant)
            assertEquals(c.error, s.error)
            assertEquals(c.onError, s.onError)
            assertEquals(c.errorSoft, s.errorContainer)
            assertEquals(c.onErrorSoft, s.onErrorContainer)
            assertEquals(Color.Black, s.scrim)
        }
    }

    @Test
    fun `secondary and tertiary are ink and ochre is not mapped`() {
        allCombos.forEach { (dark, accent) ->
            val c = talleleColors(dark, accent)
            val s = c.toMaterialColorScheme()
            assertEquals(s.primary, s.secondary)
            assertEquals(s.primary, s.tertiary)
            assertEquals(s.onPrimary, s.onSecondary)
            assertEquals(s.primaryContainer, s.secondaryContainer)
            assertEquals(s.primaryContainer, s.tertiaryContainer)
            val ochreRoles = setOf(c.ochre, c.ochreSoft, c.onOchreSoft)
            val schemeRoles = listOf(
                s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer, s.secondary, s.onSecondary,
                s.secondaryContainer, s.onSecondaryContainer, s.tertiary, s.onTertiary, s.tertiaryContainer,
                s.onTertiaryContainer, s.background, s.onBackground, s.surface, s.onSurface, s.surfaceVariant,
                s.onSurfaceVariant, s.surfaceTint, s.inverseSurface, s.inverseOnSurface, s.inversePrimary,
                s.error, s.onError, s.errorContainer, s.onErrorContainer, s.outline, s.outlineVariant, s.scrim,
                s.surfaceBright, s.surfaceDim, s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
                s.surfaceContainerHigh, s.surfaceContainerHighest,
            )
            schemeRoles.forEach { role -> assertFalse("ochre leaked into the Material scheme", role in ochreRoles) }
        }
    }

    @Test
    fun `dialogs sheets and menus resolve to the solid sheet colour and cards to surface`() {
        listOf(false, true).forEach { dark ->
            val c = talleleColors(dark, AppAccent.Ink)
            val s = c.toMaterialColorScheme()
            assertEquals("ModalBottomSheet default", c.sheet, s.surfaceContainerLow)
            assertEquals("DropdownMenu default", c.sheet, s.surfaceContainer)
            assertEquals("AlertDialog default", c.sheet, s.surfaceContainerHigh)
            assertEquals("filled TextField default", c.well, s.surfaceContainerHighest)
            assertEquals(c.surface, s.surfaceContainerLowest)
            listOf(s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest, s.surface, s.background)
                .forEach { assertEquals("surfaces are opaque", 1f, it.alpha, 0f) }
        }
    }

    @Test
    fun `surface tint equals surface so tonal elevation never tints`() {
        allCombos.forEach { (dark, accent) ->
            val s = talleleColors(dark, accent).toMaterialColorScheme()
            assertEquals(s.surface, s.surfaceTint)
        }
    }

    @Test
    fun `inverse roles stay readable for snackbars`() {
        allCombos.forEach { (dark, accent) ->
            val s = talleleColors(dark, accent).toMaterialColorScheme()
            assertText("dark=$dark $accent inverseOnSurface", s.inverseOnSurface, s.inverseSurface)
            assertText("dark=$dark $accent inversePrimary", s.inversePrimary, s.inverseSurface)
        }
    }

    @Test
    fun `default scheme is the Ink scheme and light differs from dark`() {
        listOf(false, true).forEach { dark ->
            val c = talleleColors(dark, AppAccent.Ink)
            val s = defaultOrbitColorScheme(dark)
            assertEquals(c.ink, s.primary)
            assertEquals(c.page, s.background)
            assertEquals(c.sheet, s.surfaceContainerHigh)
        }
        assertNotEquals(defaultOrbitColorScheme(false).background, defaultOrbitColorScheme(true).background)
    }

    @Test
    fun `readability ratio follows WCAG 2 for black and white`() {
        assertEquals(21f, readabilityRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, readabilityRatio(Color.White, Color.White), 0.001f)
    }
}

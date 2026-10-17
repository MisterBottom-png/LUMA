package com.orbit.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decisions D-22 and D-26: the app background is the plain page colour. The own-photo loader, the
 * dimming overlay and the gradient presets are gone, so there is no custom-background contrast boost
 * left to protect; the built-in images are checked in CaptureBackgroundTest. This test keeps them gone.
 */
class CustomBackgroundContrastTest {
    private val source: String by lazy {
        mainKotlinSources().first { it.name == "OrbitBackground.kt" }.readText()
            .lines().joinToString("\n") { it.substringBefore("//") }
    }

    @Test
    fun theAppBackgroundHasNoOwnPhotoLoading() {
        listOf("BitmapFactory", "Bitmap", "customBackgroundUri", "contentResolver", "boxBlur", "asImageBitmap").forEach {
            assertFalse("OrbitBackground.kt still uses $it", source.contains(it))
        }
    }

    @Test
    fun theAppBackgroundHasNoGradientLightPoolOrDimming() {
        listOf("Brush.", "verticalGradient", "radialGradient", "linearGradient", "backgroundDim", "BackgroundPreset")
            .forEach { assertFalse("OrbitBackground.kt still uses $it", source.contains(it)) }
    }

    @Test
    fun theAppBackgroundIsThePageColour() {
        assertTrue(source.contains(".background(colors.page)"))
    }

    @Test
    fun noAppSourceStillReadsTheRemovedBackgroundHelpers() {
        val removed = listOf(
            "customBackgroundContrastStyle", "LocalOrbitUsesCustomBackground", "backgroundPalette(",
            "presetAccentPoolAlpha", "presetBloomAlpha", "processedBackgroundCache",
        )
        val offenders = mainKotlinSources().flatMap { file ->
            val text = file.readText()
            removed.filter { text.contains(it) }.map { "${file.name}: $it" }
        }
        assertTrue("Removed helpers still referenced: $offenders", offenders.isEmpty())
    }
}

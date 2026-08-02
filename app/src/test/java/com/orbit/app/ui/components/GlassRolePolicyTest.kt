package com.orbit.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import com.orbit.app.domain.model.BackgroundBlur
import com.orbit.app.domain.model.BackgroundDimmingMode

class GlassRolePolicyTest {
    @Test
    fun liveGlassRequiresPolicyPlatformAndSource() {
        assertTrue(
            shouldRenderLiveGlass(
                policy = GlassRenderingPolicy.LiveAllowed,
                platformApi = 35,
                hasHazeSource = true,
            ),
        )
        assertFalse(
            shouldRenderLiveGlass(
                policy = GlassRenderingPolicy.SoftOnly,
                platformApi = 35,
                hasHazeSource = true,
            ),
        )
        assertFalse(
            shouldRenderLiveGlass(
                policy = GlassRenderingPolicy.LiveAllowed,
                platformApi = 30,
                hasHazeSource = true,
            ),
        )
        assertFalse(
            shouldRenderLiveGlass(
                policy = GlassRenderingPolicy.LiveAllowed,
                platformApi = 35,
                hasHazeSource = false,
            ),
        )
    }

    @Test
    fun backgroundBlurIsBoundedAndOnlyChangesAtCachedRadiusSteps() {
        assertEquals(0, backgroundBlurRadius(BackgroundBlur.None))
        assertEquals(8, backgroundBlurRadius(BackgroundBlur.Soft))
        assertEquals(16, backgroundBlurRadius(BackgroundBlur.Medium))
        assertEquals(24, backgroundBlurRadius(BackgroundBlur.Strong))
    }

    @Test
    fun backgroundDimHasAVisibleBoundedRangeInBothThemes() {
        assertEquals(0.1232f, backgroundDimAlpha(-1f, isDark = false))
        assertEquals(0.1408f, backgroundDimAlpha(0f, isDark = true))
        assertEquals(0f, backgroundDimAlpha(-1f, isDark = false, mode = BackgroundDimmingMode.Manual))
        assertEquals(0.28f, backgroundDimAlpha(0.5f, isDark = false))
        assertEquals(0.32f, backgroundDimAlpha(0.5f, isDark = true))
        assertEquals(0.56f, backgroundDimAlpha(2f, isDark = false))
        assertEquals(0.64f, backgroundDimAlpha(2f, isDark = true))
    }

    @Test
    fun surfaceOpacityProducesAVisibleRangeWithoutBecomingOpaque() {
        GlassSurfaceStyle.entries
            .filterNot {
                it == GlassSurfaceStyle.Sheet || it == GlassSurfaceStyle.NavigationAction
            }
            .forEach { style ->
            val transparent = softGlassContainerAlpha(
                style = style,
                isDark = false,
                glassStrength = 0f,
                hasCustomBackground = true,
            )
            val solid = softGlassContainerAlpha(
                style = style,
                isDark = false,
                glassStrength = 1f,
                hasCustomBackground = true,
            )

            assertTrue(solid - transparent >= 0.45f)
            assertTrue(transparent >= 0.12f)
            assertTrue(solid <= 0.85f)
        }
    }

    @Test
    fun modalSurfaceKeepsAReadableTranslucentFloorAtBothStrengthExtremes() {
        listOf(false, true).forEach { isDark ->
            listOf(false, true).forEach { hasCustomBackground ->
                val lowest = softGlassContainerAlpha(
                    style = GlassSurfaceStyle.Sheet,
                    isDark = isDark,
                    glassStrength = 0f,
                    hasCustomBackground = hasCustomBackground,
                )
                val highest = softGlassContainerAlpha(
                    style = GlassSurfaceStyle.Sheet,
                    isDark = isDark,
                    glassStrength = 1f,
                    hasCustomBackground = hasCustomBackground,
                )

                assertTrue(lowest >= 0.70f)
                assertTrue(highest <= 0.94f)
                assertTrue(highest - lowest >= 0.17f)
            }
        }
    }

    @Test
    fun modalScrimSeparatesTheSheetFromRoutesInBothThemes() {
        assertEquals(0.32f, modalScrimAlpha(isDark = false))
        assertEquals(0.44f, modalScrimAlpha(isDark = true))
    }

    @Test
    fun displayedThirtyPercentProducesActuallyTranslucentSharedSurfaces() {
        val navigation = glassTintAlpha(
            style = GlassSurfaceStyle.Standard,
            isDark = false,
            glassStrength = 0.30f,
            hasCustomBackground = true,
        )
        val card = softGlassContainerAlpha(
            style = GlassSurfaceStyle.Prominent,
            isDark = false,
            glassStrength = 0.30f,
            hasCustomBackground = true,
        )

        assertTrue(navigation in 0.27f..0.31f)
        assertTrue(card in 0.33f..0.36f)
    }

    @Test
    fun softSurfaceColorsApplyCalculatedOpacityInLightAndDarkThemes() {
        val baseColor = Color(0xFF123456)

        listOf(false, true).forEach { isDark ->
            listOf(0f, 1f).forEach { strength ->
                val expectedAlpha = softGlassContainerAlpha(
                    style = GlassSurfaceStyle.Subtle,
                    isDark = isDark,
                    glassStrength = strength,
                    hasCustomBackground = true,
                )

                assertEquals(
                    expectedAlpha,
                    baseColor.withAlpha(expectedAlpha).alpha,
                    1f / 255f,
                )
            }
        }
    }

    @Test
    fun navigationActionKeepsAStableOpaqueFillAcrossBackgroundSettings() {
        listOf(false, true).forEach { isDark ->
            listOf(0f, 1f).forEach { strength ->
                listOf(false, true).forEach { hasCustomBackground ->
                    assertEquals(
                        if (isDark) 0.84f else 0.92f,
                        softGlassContainerAlpha(
                            style = GlassSurfaceStyle.NavigationAction,
                            isDark = isDark,
                            glassStrength = strength,
                            hasCustomBackground = hasCustomBackground,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun liveGlassAccentTintStaysRestrainedAcrossRolesAndThemes() {
        GlassSurfaceStyle.entries.forEach { style ->
            val lightAlpha = glassAccentTintAlpha(style = style, isDark = false)
            val darkAlpha = glassAccentTintAlpha(style = style, isDark = true)

            assertTrue(lightAlpha in 0.03f..0.05f)
            assertTrue(darkAlpha in 0.04f..0.06f)
            assertTrue(darkAlpha > lightAlpha)
        }
    }
}

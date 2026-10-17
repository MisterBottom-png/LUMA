package com.orbit.app.ui.components

import com.orbit.app.domain.model.TabBarStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The automatic tab bar fallback (proposal section 6.4): a truth table, tested branch by branch. */
class TabBarLookTest {
    private fun glass(
        api: Int = 35,
        batterySaver: Boolean = false,
        animationsRemoved: Boolean = false,
        hasHazeSource: Boolean = true,
        routeAllowsLive: Boolean = true,
    ) = resolveTabBarLook(
        chosen = TabBarStyle.Glass,
        platformApi = api,
        batterySaver = batterySaver,
        animationsRemoved = animationsRemoved,
        hasHazeSource = hasHazeSource,
        routeAllowsLive = routeAllowsLive,
    )

    @Test
    fun glassStaysGlassWhenEveryConditionHolds() {
        assertEquals(TabBarStyle.Glass, glass())
        assertEquals(TabBarStyle.Glass, glass(api = 33))
    }

    @Test
    fun glassNeedsAndroid13BecauseOnlyTheShaderPathIsWorthItsCost() {
        listOf(26, 28, 30, 31, 32).forEach { api ->
            assertEquals("api=$api", TabBarStyle.Frosted, glass(api = api))
        }
        listOf(33, 34, 35, 36).forEach { api ->
            assertEquals("api=$api", TabBarStyle.Glass, glass(api = api))
        }
    }

    @Test
    fun batterySaverDropsTheBlur() {
        assertEquals(TabBarStyle.Frosted, glass(batterySaver = true))
    }

    @Test
    fun removedAnimationsDropTheBlur() {
        assertEquals(TabBarStyle.Frosted, glass(animationsRemoved = true))
    }

    @Test
    fun noBlurSourceMeansNoBlur() {
        assertEquals(TabBarStyle.Frosted, glass(hasHazeSource = false))
    }

    @Test
    fun routesThatDoNotAllowLiveRenderingDropTheBlur() {
        assertEquals(TabBarStyle.Frosted, glass(routeAllowsLive = false))
    }

    @Test
    fun solidAndFrostedAreWhatThePersonChoseWhateverTheConditions() {
        val apis = listOf(26, 32, 33, 36)
        val flags = listOf(false, true)
        apis.forEach { api ->
            flags.forEach { saver ->
                flags.forEach { removed ->
                    flags.forEach { haze ->
                        flags.forEach { route ->
                            assertEquals(
                                TabBarStyle.Solid,
                                resolveTabBarLook(TabBarStyle.Solid, api, saver, removed, haze, route),
                            )
                            assertEquals(
                                TabBarStyle.Frosted,
                                resolveTabBarLook(TabBarStyle.Frosted, api, saver, removed, haze, route),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theFullTruthTableForGlass() {
        // Glass survives in exactly one of the 2^5 combinations of the five conditions.
        var glassCount = 0
        val flags = listOf(false, true)
        listOf(32, 33).forEach { api ->
            flags.forEach { saver ->
                flags.forEach { removed ->
                    flags.forEach { haze ->
                        flags.forEach { route ->
                            val look = resolveTabBarLook(TabBarStyle.Glass, api, saver, removed, haze, route)
                            val expectGlass = api >= 33 && !saver && !removed && haze && route
                            assertEquals(
                                "api=$api saver=$saver removed=$removed haze=$haze route=$route",
                                if (expectGlass) TabBarStyle.Glass else TabBarStyle.Frosted,
                                look,
                            )
                            if (look == TabBarStyle.Glass) glassCount++
                        }
                    }
                }
            }
        }
        assertEquals(1, glassCount)
    }

    @Test
    fun glassNeverFallsBackToSolid() {
        // Falling back lowers the blur, not the see-through look the person picked.
        val flags = listOf(false, true)
        listOf(26, 33).forEach { api ->
            flags.forEach { a ->
                flags.forEach { b ->
                    flags.forEach { c ->
                        flags.forEach { d ->
                            val look = resolveTabBarLook(TabBarStyle.Glass, api, a, b, c, d)
                            assertTrue(look != TabBarStyle.Solid)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun surfaceAlphasFollowTheProposal() {
        assertEquals(0.84f, tabBarSurfaceAlpha(TabBarStyle.Glass))
        assertEquals(0.94f, tabBarSurfaceAlpha(TabBarStyle.Frosted))
        assertEquals(1f, tabBarSurfaceAlpha(TabBarStyle.Solid))
    }
}

package com.orbit.app.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.TabBarStyle
import com.orbit.app.domain.model.limestoneAppearance
import dev.chrisbanes.haze.hazeSource

/*
 * The tab bar looks (proposal section 6.4) and the automatic fallback between them. Pure functions
 * live here so the truth table is unit-tested; the composable glue is at the bottom.
 */

/** Glass: `surface` at 84 % over a live blur. */
internal const val GlassTabBarSurfaceAlpha = 0.84f

/** Frosted: `surface` at 94 %, no blur. */
internal const val FrostedTabBarSurfaceAlpha = 0.94f

/** Solid: `surface` at 100 %. */
internal const val SolidTabBarSurfaceAlpha = 1f

/** How much the bar blurs what scrolls behind it, so text there reads as colour. */
internal val TabBarBlurRadius: Dp = 22.dp

/** The opacity of the bar's `surface` fill for a resolved look. */
internal fun tabBarSurfaceAlpha(look: TabBarStyle): Float = when (look) {
    TabBarStyle.Glass -> GlassTabBarSurfaceAlpha
    TabBarStyle.Frosted -> FrostedTabBarSurfaceAlpha
    TabBarStyle.Solid -> SolidTabBarSurfaceAlpha
}

/**
 * Which look the tab bar really uses. Solid and Frosted are what the person chose and never change.
 * Glass needs everything to line up: Android 13 or newer (where Haze uses a runtime shader), battery
 * saver off, "Remove animations" off, a blur source in the tree and a route that allows live
 * rendering. If any is missing the bar falls back to Frosted.
 */
fun resolveTabBarLook(
    chosen: TabBarStyle,
    platformApi: Int,
    batterySaver: Boolean,
    animationsRemoved: Boolean,
    hasHazeSource: Boolean,
    routeAllowsLive: Boolean,
): TabBarStyle = when (chosen) {
    TabBarStyle.Solid -> TabBarStyle.Solid
    TabBarStyle.Frosted -> TabBarStyle.Frosted
    TabBarStyle.Glass ->
        if (platformApi >= Build.VERSION_CODES.TIRAMISU &&
            !batterySaver &&
            !animationsRemoved &&
            hasHazeSource &&
            routeAllowsLive
        ) {
            TabBarStyle.Glass
        } else {
            TabBarStyle.Frosted
        }
}

/**
 * The look the tab bar uses right now: the person's choice from their settings, reduced by
 * [resolveTabBarLook] for this phone, this moment and this route.
 */
@Composable
internal fun rememberTabBarLook(): TabBarStyle = resolveTabBarLook(
    chosen = LocalOrbitAppearance.current.limestoneAppearance().tabBar,
    platformApi = Build.VERSION.SDK_INT,
    batterySaver = rememberBatterySaver(),
    animationsRemoved = rememberReducedMotion(),
    hasHazeSource = LocalOrbitHazeState.current != null,
    routeAllowsLive = LocalGlassRenderingPolicy.current == GlassRenderingPolicy.LiveAllowed,
)

/**
 * Marks the content the tab bar should blur (the screen area behind the bar, not the bar itself).
 * It records nothing unless the bar really is Glass right now, so Frosted, Solid, battery saver and
 * reduced motion pay no capture cost. Apply it to the container that holds the screens and sits
 * behind the tab bar. It is drawn above the page source in [OrbitBackground] (decision D-27).
 */
@Composable
fun Modifier.tabBarBlurSource(): Modifier {
    val hazeState = LocalOrbitHazeState.current ?: return this
    return if (rememberTabBarLook() == TabBarStyle.Glass) {
        hazeSource(hazeState, zIndex = TabBarContentSourceZIndex)
    } else {
        this
    }
}

/** Screens sit above the page colour (zIndex 0) in what the glass tab bar blurs. */
internal const val TabBarContentSourceZIndex = 1f

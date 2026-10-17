package com.orbit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.TabBarStyle
import com.orbit.app.ui.theme.TalleleTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The app background: the plain `page` colour (proposal sections 0 and 6.1, decisions D-22 and D-26).
 * There are no gradients, light pools, noise or photos here. Built-in images show on Capture only,
 * through [CaptureBackground]; in P1 every old background setting maps to None.
 *
 * It also provides what the tab bar needs to blur: a Haze state (and a source behind the page, only
 * while the bar really is Glass), the route's rendering policy, and the person's settings. Text on
 * the page defaults to the main text colour.
 *
 * It resolves the tab bar look once ([LocalTabBarLook]), reads reduced motion once, and owns the
 * [TabBarHeight] holder the bar writes to ([LocalTabBarHeight]).
 */
@Composable
fun OrbitBackground(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    glassRenderingPolicy: GlassRenderingPolicy = GlassRenderingPolicy.LiveAllowed,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TalleleTheme.colors
    val hazeState = rememberHazeState()

    val tabBarHeight = remember { TabBarHeight() }

    CompositionLocalProvider(
        LocalOrbitHazeState provides hazeState,
        LocalGlassRenderingPolicy provides glassRenderingPolicy,
        LocalOrbitAppearance provides settings,
        LocalContentColor provides colors.text,
    ) {
        // Read "Remove animations" once for the whole app, so nothing below registers its own observer.
        val reducedMotion = rememberReducedMotion()
        CompositionLocalProvider(LocalReducedMotionOverride provides reducedMotion) {
            // The tab bar look is worked out once here (one battery-saver receiver, one lifecycle
            // observer), so the page source, the screens' source and the bar always agree.
            val look = rememberTabBarLook()
            CompositionLocalProvider(
                LocalTabBarLook provides look,
                LocalTabBarHeight provides tabBarHeight,
            ) {
                // Recording the page for the blur costs a layer, so only do it while the bar is Glass.
                val recordForBlur = look == TabBarStyle.Glass
                Box(modifier = modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (recordForBlur) Modifier.hazeSource(hazeState) else Modifier)
                            .background(colors.page),
                    )
                    content()
                }
            }
        }
    }
}

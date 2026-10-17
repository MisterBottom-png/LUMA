package com.orbit.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.navigation.OrbitDestination

object OrbitBottomNavigationDefaults {
    val ContainerHeight: Dp = 92.dp

    /**
     * Space a scrolling screen leaves at the bottom so its last item clears the bar.
     *
     * This is a fixed number, so at 200 % text (when the bar grows taller) it can be too small.
     * [LocalTabBarHeight] carries the bar's real, measured height; P2 replaces this constant (and
     * [ScrollFadeHeight]) with the measured value when it rebuilds the screens. P1 only writes it.
     */
    val ContentClearance: Dp = 120.dp

    internal val BarMinHeight: Dp = 68.dp
    internal val HorizontalPadding: Dp = 16.dp
    internal val BottomPadding: Dp = 10.dp
    internal val MinimumTouchTargetSize: Dp = 48.dp
    internal val MaxBarWidth: Dp = 560.dp

    /** Scrolled content fades out over this much just above the bar, so no words show through it. */
    val ScrollFadeLength: Dp = 24.dp

    /**
     * From the screen's bottom edge (above the system bar) to where the fade above the bar starts.
     * Like [ContentClearance], P2 derives it from [LocalTabBarHeight] instead of the bar's minimum.
     */
    val ScrollFadeHeight: Dp = BarMinHeight + BottomPadding + ScrollFadeLength
}

/**
 * The tab bar's real height, written by [TalleleTabBar] and read by screens.
 *
 * [height] runs from the bottom of the layout (the bar's bottom padding and the system-bar inset
 * included) up to the top edge of the bar, so a screen that pads its content by it clears the bar at
 * any text size. It is 0 dp while the bar is hidden. [OrbitBackground] creates the one holder and
 * provides it through [LocalTabBarHeight]; read it with [currentTabBarHeight].
 */
@Stable
class TabBarHeight {
    var height: Dp by mutableStateOf(0.dp)
        internal set
}

/** The shared [TabBarHeight]; null outside [OrbitBackground] (previews, tests). */
val LocalTabBarHeight = staticCompositionLocalOf<TabBarHeight?> { null }

/**
 * The tab bar's measured height right now, or 0 dp while it is hidden or there is no bar. P2 uses
 * it in place of [OrbitBottomNavigationDefaults.ContentClearance].
 */
@Composable
fun currentTabBarHeight(): Dp = LocalTabBarHeight.current?.height ?: 0.dp

/**
 * Home · Spaces · Calendar · Review, each with a visible label, drawn by [TalleleTabBar]. The bar is
 * the app's only glass: blurred where Android, battery saver, "Remove animations" and the Tab bar
 * setting allow it, Frosted or Solid otherwise.
 */
@Composable
fun FloatingBottomNavigation(
    selectedRoute: String?,
    onDestinationSelected: (OrbitDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** Tabs that show a small dot: only when something failed there, never as a count. */
    alertDestinations: Set<OrbitDestination> = emptySet(),
) {
    val alertDescription = stringResource(R.string.needs_you_attention)
    val tabs = OrbitDestination.bottomBar.map { destination ->
        TalleleTab(
            label = stringResource(destination.contentDescriptionRes),
            icon = destination.icon,
            selected = isBottomBarDestinationSelected(selectedRoute, destination),
            onClick = { onDestinationSelected(destination) },
            badge = if (destination in alertDestinations) TalleleTabBadge.Dot(alertDescription) else null,
        )
    }
    TalleleTabBar(tabs = tabs, modifier = modifier)
}

/** A Space's own page ("spaces/…") still belongs to the Spaces tab. */
internal fun isBottomBarDestinationSelected(selectedRoute: String?, destination: OrbitDestination): Boolean =
    selectedRoute == destination.route ||
        (destination == OrbitDestination.Spaces && selectedRoute?.startsWith("${OrbitDestination.Spaces.route}/") == true)

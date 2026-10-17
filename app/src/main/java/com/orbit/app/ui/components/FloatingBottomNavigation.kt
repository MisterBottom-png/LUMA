package com.orbit.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.navigation.OrbitDestination

object OrbitBottomNavigationDefaults {
    val ContainerHeight: Dp = 92.dp

    /** Space a scrolling screen leaves at the bottom so its last item clears the bar. */
    val ContentClearance: Dp = 120.dp

    internal val BarMinHeight: Dp = 68.dp
    internal val HorizontalPadding: Dp = 16.dp
    internal val BottomPadding: Dp = 10.dp
    internal val MinimumTouchTargetSize: Dp = 48.dp
    internal val MaxBarWidth: Dp = 560.dp

    /** Scrolled content fades out over this much just above the bar, so no words show through it. */
    val ScrollFadeLength: Dp = 24.dp

    /** From the screen's bottom edge (above the system bar) to where the fade above the bar starts. */
    val ScrollFadeHeight: Dp = BarMinHeight + BottomPadding + ScrollFadeLength
}

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

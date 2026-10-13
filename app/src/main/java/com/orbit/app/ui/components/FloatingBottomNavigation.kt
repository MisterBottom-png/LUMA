package com.orbit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.orbit.app.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.navigation.OrbitDestination
import com.orbit.app.ui.theme.OrbitMotion

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
 * Home · Spaces · Calendar · Review, each with a visible label. The bar is calm and solid
 * in every mode (review §9.4), so content scrolling behind it never mixes with the icons.
 */
@Composable
fun FloatingBottomNavigation(
    selectedRoute: String?,
    onDestinationSelected: (OrbitDestination) -> Unit,
    modifier: Modifier = Modifier,
    /** Tabs that show a small dot when something waits there; never a count. */
    alertDestinations: Set<OrbitDestination> = emptySet(),
    /** Of those, tabs where a reminder is really blocked: the dot is red, not the accent (§9.4). */
    blockedDestinations: Set<OrbitDestination> = emptySet(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = OrbitBottomNavigationDefaults.HorizontalPadding)
            .padding(bottom = OrbitBottomNavigationDefaults.BottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .widthIn(max = OrbitBottomNavigationDefaults.MaxBarWidth)
                .fillMaxWidth()
                .heightIn(min = OrbitBottomNavigationDefaults.BarMinHeight),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 6.dp,
        ) {
            // Review §9.4: a hairline on top only.
            val hairline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawBehind { drawLine(hairline, Offset(size.height / 2, 0f), Offset(size.width - size.height / 2, 0f), 1.dp.toPx()) },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                OrbitDestination.bottomBar.forEach { destination ->
                    NavTab(
                        destination = destination,
                        selected = isBottomBarDestinationSelected(selectedRoute, destination),
                        alert = destination in alertDestinations,
                        blocked = destination in blockedDestinations,
                        onClick = { onDestinationSelected(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTab(
    destination: OrbitDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alert: Boolean = false,
    blocked: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(OrbitMotion.StandardDurationMillis),
        label = "Bottom navigation content",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(OrbitMotion.StandardDurationMillis),
        label = "Bottom navigation pill",
    )
    val label = stringResource(destination.contentDescriptionRes)
    val alertDescription = stringResource(if (blocked) R.string.needs_you_attention else R.string.needs_you_waiting)
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            )
            .orbitPressFeedback(interactionSource = interactionSource, clipShape = RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(pillColor, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) destination.icon else destination.outlinedIcon,
                // The visible label below already names the tab for TalkBack.
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            if (alert) {
                // N028: the dot is read after the tab's name, so "Selected" is still announced.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 10.dp)
                        .size(8.dp)
                        .background(if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = alertDescription },
                )
            }
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A Space's own page ("spaces/…") still belongs to the Spaces tab. */
internal fun isBottomBarDestinationSelected(selectedRoute: String?, destination: OrbitDestination): Boolean =
    selectedRoute == destination.route ||
        (destination == OrbitDestination.Spaces && selectedRoute?.startsWith("${OrbitDestination.Spaces.route}/") == true)

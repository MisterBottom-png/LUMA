package com.orbit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.orbit.app.R
import com.orbit.app.ui.navigation.OrbitDestination
import com.orbit.app.ui.theme.OrbitSpacing
import com.orbit.app.ui.theme.OrbitMotion

object OrbitBottomNavigationDefaults {
    val ContainerHeight: Dp = 94.dp
    val ContentClearance: Dp = 136.dp

    internal val BarHeight: Dp = 62.dp
    internal val HorizontalPadding: Dp = 24.dp
    internal val TopPadding: Dp = 12.dp
    internal val BottomPadding: Dp = 12.dp
    internal val MinimumTouchTargetSize: Dp = 48.dp
    internal val CenterButtonSize: Dp = 56.dp
    internal val CenterButtonVerticalOffset: Dp = 10.dp
}

@Composable
fun FloatingBottomNavigation(
    selectedRoute: String?,
    onDestinationSelected: (OrbitDestination) -> Unit,
    onSituationAiSelected: () -> Unit,
    situationAiFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val situationInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .height(OrbitBottomNavigationDefaults.ContainerHeight)
            .padding(horizontal = OrbitBottomNavigationDefaults.HorizontalPadding)
            .padding(
                top = OrbitBottomNavigationDefaults.TopPadding,
                bottom = OrbitBottomNavigationDefaults.BottomPadding,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SoftGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(OrbitBottomNavigationDefaults.BarHeight),
            shape = RoundedCornerShape(34.dp),
            style = GlassSurfaceStyle.Subtle,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OrbitSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavSlot {
                    NavIcon(
                        destination = OrbitDestination.Home,
                        selected = selectedRoute == OrbitDestination.Home.route,
                        onClick = { onDestinationSelected(OrbitDestination.Home) },
                    )
                }
                NavSlot {
                    NavIcon(
                        destination = OrbitDestination.Spaces,
                        selected = selectedRoute == OrbitDestination.Spaces.route,
                        onClick = { onDestinationSelected(OrbitDestination.Spaces) },
                    )
                }
                NavSlot {
                    Spacer(Modifier.size(OrbitBottomNavigationDefaults.CenterButtonSize))
                }
                NavSlot {
                    NavIcon(
                        destination = OrbitDestination.Review,
                        selected = selectedRoute == OrbitDestination.Review.route,
                        onClick = { onDestinationSelected(OrbitDestination.Review) },
                    )
                }
                NavSlot {
                    NavIcon(
                        destination = OrbitDestination.Settings,
                        selected = selectedRoute == OrbitDestination.Settings.route,
                        onClick = { onDestinationSelected(OrbitDestination.Settings) },
                    )
                }
            }
        }

        SoftGlassSurface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -OrbitBottomNavigationDefaults.CenterButtonVerticalOffset)
                .size(OrbitBottomNavigationDefaults.CenterButtonSize),
            shape = CircleShape,
            style = GlassSurfaceStyle.NavigationAction,
            shadowElevation = 2.dp,
        ) {
            IconButton(
                onClick = onSituationAiSelected,
                modifier = Modifier
                    .size(OrbitBottomNavigationDefaults.CenterButtonSize)
                    .orbitPressFeedback(situationInteractionSource)
                    .focusRequester(situationAiFocusRequester),
                interactionSource = situationInteractionSource,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = stringResource(R.string.navigation_situation_ai),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavSlot(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun NavIcon(
    destination: OrbitDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(OrbitMotion.StandardDurationMillis),
        label = "Bottom navigation tint",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(OrbitMotion.StandardDurationMillis),
        label = "Bottom navigation container",
    )
    val size by animateDpAsState(
        targetValue = if (selected) 26.dp else 24.dp,
        animationSpec = tween(OrbitMotion.StandardDurationMillis),
        label = "Bottom navigation size",
    )
    Box(
        modifier = Modifier
            .size(OrbitBottomNavigationDefaults.MinimumTouchTargetSize)
            .orbitPressFeedback(interactionSource)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.contentDescriptionRes),
                tint = tint,
                modifier = Modifier.size(size),
            )
        }
    }
}

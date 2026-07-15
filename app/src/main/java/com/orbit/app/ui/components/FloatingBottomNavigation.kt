package com.orbit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.navigation.OrbitDestination

object OrbitBottomNavigationDefaults {
    val ContainerHeight: Dp = 112.dp
    val ContentClearance: Dp = 144.dp

    internal val BarHeight: Dp = 68.dp
    internal val HorizontalPadding: Dp = 20.dp
    internal val TopPadding: Dp = 10.dp
    internal val BottomPadding: Dp = 18.dp
    internal val CenterButtonSize: Dp = 64.dp
    internal val CenterButtonTouchSize: Dp = 58.dp
}

@Composable
fun FloatingBottomNavigation(
    selectedRoute: String?,
    onDestinationSelected: (OrbitDestination) -> Unit,
    onSituationAiSelected: () -> Unit,
    situationAiFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(OrbitBottomNavigationDefaults.BarHeight),
            shape = RoundedCornerShape(34.dp),
            style = GlassSurfaceStyle.Subtle,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
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

        key(isDark) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(OrbitBottomNavigationDefaults.CenterButtonSize)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                    )
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF8F80E8),
                                Color(0xFF6557C8),
                                Color(0xFF5B91A0),
                            ),
                        ),
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.72f),
                                Color.White.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            ),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onSituationAiSelected,
                    modifier = Modifier
                        .size(OrbitBottomNavigationDefaults.CenterButtonTouchSize)
                        .focusRequester(situationAiFocusRequester),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Situation AI",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp),
                    )
                }
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
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            },
            modifier = Modifier.size(if (selected) 27.dp else 24.dp),
        )
    }
}

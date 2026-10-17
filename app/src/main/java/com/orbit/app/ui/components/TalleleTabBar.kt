package com.orbit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import androidx.compose.ui.res.stringResource
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.domain.model.TabBarStyle
import com.orbit.app.ui.navigation.OrbitDestination
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleMotion
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * A small mark on a tab. It never carries meaning by colour alone: a dot has a spoken description
 * (for example "needs you") and a count shows its number and also has a spoken description.
 */
sealed interface TalleleTabBadge {
    /** Read by TalkBack as the tab's state, for example "2 to decide". */
    val description: String

    /** A dot: something failed or needs a look. Never a count. */
    @Immutable
    data class Dot(override val description: String) : TalleleTabBadge

    /** A number, shown as "99+" from 100 up. */
    @Immutable
    data class Count(val count: Int, override val description: String) : TalleleTabBadge
}

/** One tab of [TalleleTabBar]. The owner decides what is selected and what a tap does. */
@Immutable
data class TalleleTab(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** The selected tab may use a filled icon (section 6.6). Defaults to [icon]. */
    val selectedIcon: ImageVector? = null,
    val badge: TalleleTabBadge? = null,
)

/**
 * The floating tab bar (proposal sections 6.3, 6.4, 6.5): the only see-through surface in the app.
 *
 * It takes any number of tabs, so Tallele's three tabs and the current four use the same part.
 * [look] is the already resolved look; by default it is chosen from the person's setting and
 * lowered to Frosted where the phone, battery saver, "Remove animations" or the route do not allow
 * blur (see [resolveTabBarLook]).
 *
 * The bar grows in height with the font size; tab labels may use two lines and never need to
 * shrink. Every tab is at least 48 dp square. It keeps clear of the system bars.
 */
@Composable
fun TalleleTabBar(
    tabs: List<TalleleTab>,
    modifier: Modifier = Modifier,
    look: TabBarStyle = rememberTabBarLook(),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = OrbitBottomNavigationDefaults.HorizontalPadding)
            .padding(bottom = OrbitBottomNavigationDefaults.BottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        TabBarSurface(
            look = look,
            modifier = Modifier
                .widthIn(max = OrbitBottomNavigationDefaults.MaxBarWidth)
                .fillMaxWidth()
                .heightIn(min = OrbitBottomNavigationDefaults.BarMinHeight),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tabs.forEach { tab ->
                    TalleleTabItem(
                        tab = tab,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * The bar's surface. Glass is `surface` at 84 % over a live blur of what is behind it; Frosted is
 * `surface` at 94 %; Solid is `surface`. A 1 dp hairline edge. This is the only place in the app that
 * blurs. A tap on the bar never reaches what is behind it.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun TabBarSurface(
    look: TabBarStyle,
    modifier: Modifier = Modifier,
    shape: Shape = TalleleShapes.TabBar,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TalleleTheme.colors
    val hazeState = LocalOrbitHazeState.current
    // Glass without a blur source cannot blur; it must not be see-through without one.
    val blurs = look == TabBarStyle.Glass && hazeState != null
    val fillAlpha = if (blurs) {
        GlassTabBarSurfaceAlpha
    } else {
        tabBarSurfaceAlpha(if (look == TabBarStyle.Glass) TabBarStyle.Frosted else look)
    }
    val hazeStyle = remember(colors.page, colors.surface) {
        HazeStyle(
            backgroundColor = colors.page,
            tints = listOf(HazeTint(colors.surface.copy(alpha = GlassTabBarSurfaceAlpha))),
            blurRadius = TabBarBlurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(colors.surface.copy(alpha = FrostedTabBarSurfaceAlpha)),
        )
    }
    CompositionLocalProvider(LocalContentColor provides colors.text) {
        Box(
            modifier = modifier
                .orbitSoftShadow(TabBarShadowElevation, shape, orbitShadowColor(colors.isDark))
                .semantics(mergeDescendants = false) { isTraversalGroup = true }
                // A tap on the bar never reaches what is behind it.
                .pointerInput(Unit) {}
                .clip(shape)
                .then(
                    if (blurs && hazeState != null) {
                        Modifier.hazeEffect(state = hazeState, style = hazeStyle) {
                            inputScale = HazeInputScale.Auto
                            progressive = null
                        }
                    } else {
                        Modifier.background(colors.surface.copy(alpha = fillAlpha))
                    },
                )
                .border(1.dp, colors.hairline, shape),
            content = content,
        )
    }
}

/** The fade between the selected and unselected tab looks: 180 ms, or instant with reduced motion. */
fun <T> talleleTabFadeSpec(reducedMotion: Boolean): FiniteAnimationSpec<T> =
    if (reducedMotion) snap() else tween(TalleleMotion.TabsMillis)

@Composable
private fun TalleleTabItem(
    tab: TalleleTab,
    modifier: Modifier = Modifier,
) {
    val colors = TalleleTheme.colors
    val reducedMotion = rememberReducedMotion()
    val fade = talleleTabFadeSpec<Color>(reducedMotion)
    val interactionSource = remember { MutableInteractionSource() }
    // Selected: ink-soft pill, on-ink-soft icon and label. Unselected: text-2, no pill. Transparent
    // is the pill colour at zero alpha so the fade does not pass through black.
    val contentColor by animateColorAsState(
        targetValue = if (tab.selected) colors.onInkSoft else colors.text2,
        animationSpec = fade,
        label = "Tab content",
    )
    val pillColor by animateColorAsState(
        targetValue = if (tab.selected) colors.inkSoft else colors.inkSoft.copy(alpha = 0f),
        animationSpec = fade,
        label = "Tab pill",
    )
    val badgeDescription = tab.badge?.description
    Column(
        modifier = modifier
            .semantics { if (badgeDescription != null) stateDescription = badgeDescription }
            .defaultMinSize(minWidth = TalleleDimens.MinTouchTarget, minHeight = TabMinHeight)
            .clip(TabPillShape)
            .selectable(
                selected = tab.selected,
                onClick = tab.onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            )
            .orbitPressFeedback(interactionSource = interactionSource, clipShape = TabPillShape)
            .background(pillColor, TabPillShape)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (tab.selected) tab.selectedIcon ?: tab.icon else tab.icon,
                // The visible label below already names the tab for TalkBack.
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            tab.badge?.let { badge ->
                TabBadge(
                    badge = badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp),
                )
            }
        }
        Text(
            text = tab.label,
            modifier = Modifier.padding(top = 2.dp),
            style = TalleleTheme.typography.tabLabel,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TabBadge(badge: TalleleTabBadge, modifier: Modifier = Modifier) {
    val colors = TalleleTheme.colors
    when (badge) {
        // The dot marks a failure, so it uses the error colour; the tab's state description says why.
        is TalleleTabBadge.Dot -> Box(
            modifier = modifier
                .size(8.dp)
                .background(colors.error, CircleShape),
        )

        // A count of things waiting for a choice: ochre-soft, with the number in the badge itself.
        is TalleleTabBadge.Count -> Box(
            modifier = modifier
                .clearAndSetSemantics { }
                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                .background(colors.ochreSoft, CircleShape)
                .border(1.dp, colors.ochre, CircleShape)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (badge.count > 99) "99+" else badge.count.toString(),
                style = TalleleTheme.typography.tabLabel,
                color = colors.onOchreSoft,
                maxLines = 1,
            )
        }
    }
}

private val TabPillShape: Shape = RoundedCornerShape(16.dp)

/** A tab is at least this tall; with the bar's padding the bar is [OrbitBottomNavigationDefaults.BarMinHeight]. */
private val TabMinHeight = 56.dp

private val TabBarShadowElevation = 6.dp

// ---------------------------------------------------------------------------------------------
// Previews: three looks, two themes, text at 1x and 2x. The sample content behind the bar is fake.
// ---------------------------------------------------------------------------------------------

@Preview(name = "Text 1x", widthDp = 360, heightDp = 220, fontScale = 1f)
@Preview(name = "Text 2x", widthDp = 360, heightDp = 300, fontScale = 2f)
private annotation class TabBarFontScales

@Composable
private fun TabBarPreviewFrame(look: TabBarStyle, dark: Boolean) {
    OrbitTheme(
        settings = AppSettings(themeMode = if (dark) SettingsThemeMode.Dark else SettingsThemeMode.Light),
    ) {
        val colors = TalleleTheme.colors
        val hazeState = rememberHazeState()
        CompositionLocalProvider(LocalOrbitHazeState provides hazeState) {
            Box(Modifier.fillMaxSize().background(colors.page)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(8) {
                        Text(text = "Sample words behind the bar", color = colors.text)
                    }
                }
                val tabs = listOf(
                    TalleleTab(
                        label = stringResource(R.string.navigation_home),
                        icon = Icons.Rounded.Edit,
                        selected = true,
                        onClick = {},
                    ),
                    TalleleTab(
                        label = stringResource(R.string.navigation_spaces),
                        icon = Icons.Rounded.Bookmark,
                        selected = false,
                        onClick = {},
                    ),
                    TalleleTab(
                        label = stringResource(R.string.navigation_review),
                        icon = Icons.Rounded.Inbox,
                        selected = false,
                        onClick = {},
                        badge = TalleleTabBadge.Count(2, "2"),
                    ),
                )
                TalleleTabBar(
                    tabs = tabs,
                    look = look,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@TabBarFontScales
@Composable
private fun TabBarGlassLightPreview() = TabBarPreviewFrame(TabBarStyle.Glass, dark = false)

@TabBarFontScales
@Composable
private fun TabBarGlassDarkPreview() = TabBarPreviewFrame(TabBarStyle.Glass, dark = true)

@TabBarFontScales
@Composable
private fun TabBarFrostedLightPreview() = TabBarPreviewFrame(TabBarStyle.Frosted, dark = false)

@TabBarFontScales
@Composable
private fun TabBarFrostedDarkPreview() = TabBarPreviewFrame(TabBarStyle.Frosted, dark = true)

@TabBarFontScales
@Composable
private fun TabBarSolidLightPreview() = TabBarPreviewFrame(TabBarStyle.Solid, dark = false)

@TabBarFontScales
@Composable
private fun TabBarSolidDarkPreview() = TabBarPreviewFrame(TabBarStyle.Solid, dark = true)

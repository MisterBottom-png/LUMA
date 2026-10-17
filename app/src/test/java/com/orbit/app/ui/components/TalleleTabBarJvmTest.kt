package com.orbit.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.domain.model.TabBarStyle
import com.orbit.app.testing.JvmComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [TalleleTabBar] on the JVM at 200 % text, in every look. */
@RunWith(AndroidJUnit4::class)
class TalleleTabBarJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun threeTabs(selected: Int, onClick: (Int) -> Unit = {}, badge: TalleleTabBadge? = null) = listOf(
        TalleleTab("Capture", Icons.Rounded.Edit, selected == 0, { onClick(0) }),
        TalleleTab("Saved", Icons.Rounded.Bookmark, selected == 1, { onClick(1) }),
        TalleleTab("Needs you", Icons.Rounded.Inbox, selected == 2, { onClick(2) }, badge = badge),
    )

    @Test
    fun atTwoHundredPercentText_everyTabIsShownWithTabRoleSelectionAndA48dpTouchArea_inEveryLook() {
        var look by mutableStateOf(TabBarStyle.Glass)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp)) {
                        TalleleTabBar(tabs = threeTabs(selected = 1), look = look)
                    }
                }
            }
        }
        TabBarStyle.entries.forEach { style ->
            look = style
            composeRule.waitForIdle()
            composeRule.onAllNodes(tabRole).assertCountEquals(3)
            listOf("Capture", "Saved", "Needs you").forEachIndexed { index, label ->
                val node = composeRule.onNodeWithText(label)
                    .assertIsDisplayed()
                    .assert(tabRole)
                    .assertWidthIsAtLeast(48.dp)
                    .assertHeightIsAtLeast(48.dp)
                if (index == 1) node.assertIsSelected() else node.assertIsNotSelected()
            }
        }
    }

    @Test
    fun aTapReportsTheTabThatWasTapped() {
        var tapped = -1
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    TalleleTabBar(tabs = threeTabs(selected = 0, onClick = { tapped = it }), look = TabBarStyle.Solid)
                }
            }
        }
        composeRule.onNodeWithText("Needs you").performClick()
        assertEquals(2, tapped)
    }

    @Test
    fun aCountBadgeIsSpokenAsTheTabStateAndNeverMergedIntoTheLabel() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    TalleleTabBar(
                        tabs = threeTabs(selected = 0, badge = TalleleTabBadge.Count(2, "2 to decide")),
                        look = TabBarStyle.Frosted,
                    )
                }
            }
        }
        val spoken = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 to decide")
        composeRule.onAllNodes(spoken).assertCountEquals(1)
        composeRule.onNodeWithText("Needs you").assertExists()
    }

    @Test
    fun theTabFadeIsA180MillisecondTweenOrInstantWithReducedMotion() {
        val normal = talleleTabFadeSpec<Float>(reducedMotion = false)
        val reduced = talleleTabFadeSpec<Float>(reducedMotion = true)
        assertEquals(androidx.compose.animation.core.tween<Float>(180), normal)
        assertEquals(androidx.compose.animation.core.snap<Float>(), reduced)
    }

    @Test
    fun glassWithABlurSourceComposesAndKeepsTabsClickable() {
        var tapped = -1
        composeRule.setContent {
            val hazeState = dev.chrisbanes.haze.rememberHazeState()
            CompositionLocalProvider(LocalOrbitHazeState provides hazeState) {
                MaterialTheme {
                    Box(Modifier.width(360.dp)) {
                        TalleleTabBar(tabs = threeTabs(selected = 0, onClick = { tapped = it }), look = TabBarStyle.Glass)
                    }
                }
            }
        }
        composeRule.onNodeWithText("Saved").performClick()
        assertEquals(1, tapped)
    }
}

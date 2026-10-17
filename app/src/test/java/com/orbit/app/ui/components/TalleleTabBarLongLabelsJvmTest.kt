package com.orbit.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.domain.model.TabBarStyle
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.components.parts.assertTextFullyShown
import com.orbit.app.ui.navigation.OrbitDestination
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proposal 6.2: tab labels grow as needed and are never cut. The four real tabs, with their real
 * English, Estonian and Russian labels, at 200 % text in a 360 dp and a 320 dp wide box.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TalleleTabBarLongLabelsJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Composable
    private fun RealTabs(widthDp: Int, fontScale: Float, labelsOut: MutableList<String>, bar: @Composable (List<TalleleTab>) -> Unit = {
        TalleleTabBar(tabs = it, look = TabBarStyle.Solid)
    }) {
        CompositionLocalProvider(
            LocalReducedMotionOverride provides true,
            LocalDensity provides Density(LocalDensity.current.density, fontScale),
        ) {
            OrbitTheme(AppSettings()) {
                val tabs = OrbitDestination.bottomBar.mapIndexed { index, destination ->
                    val label = stringResource(destination.contentDescriptionRes)
                    labelsOut += label
                    TalleleTab(label, Icons.Rounded.Edit, selected = index == 0, onClick = {})
                }
                Box(Modifier.width(widthDp.dp)) { bar(tabs) }
            }
        }
    }

    private fun assertEveryLabelWhole(widthDp: Int, expected: List<String>) {
        val labels = mutableListOf<String>()
        composeRule.setContent { RealTabs(widthDp, fontScale = 2f, labelsOut = labels) }
        composeRule.waitForIdle()
        assertEquals(expected, labels.takeLast(expected.size))
        composeRule.onAllNodes(tabRole).assertCountEquals(4)
        expected.forEach { label ->
            composeRule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed().assertTextFullyShown()
        }
        composeRule.onAllNodes(tabRole).fetchSemanticsNodes().indices.forEach { index ->
            val tab = composeRule.onAllNodes(tabRole)[index]
            tab.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val bounds = tab.getUnclippedBoundsInRoot()
            assertTrue("tab $index fits inside the ${widthDp} dp box", bounds.right <= widthDp.dp)
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h2400dp-xxhdpi")
    fun english_360dp() = assertEveryLabelWhole(360, listOf("Home", "Spaces", "Calendar", "Needs you"))

    @Test
    @Config(qualifiers = "en-w320dp-h2400dp-xxhdpi")
    fun english_320dp() = assertEveryLabelWhole(320, listOf("Home", "Spaces", "Calendar", "Needs you"))

    @Test
    @Config(qualifiers = "et-w360dp-h2400dp-xxhdpi")
    fun estonian_360dp() = assertEveryLabelWhole(360, listOf("Avaleht", "Alad", "Kalender", "Vajab sind"))

    @Test
    @Config(qualifiers = "et-w320dp-h2400dp-xxhdpi")
    fun estonian_320dp() = assertEveryLabelWhole(320, listOf("Avaleht", "Alad", "Kalender", "Vajab sind"))

    @Test
    @Config(qualifiers = "ru-w360dp-h2400dp-xxhdpi")
    fun russian_360dp() = assertEveryLabelWhole(360, listOf("Главная", "Пространства", "Календарь", "Ждёт вас"))

    @Test
    @Config(qualifiers = "ru-w320dp-h2400dp-xxhdpi")
    fun russian_320dp() = assertEveryLabelWhole(320, listOf("Главная", "Пространства", "Календарь", "Ждёт вас"))

    @Test
    @Config(qualifiers = "ru-w320dp-h2400dp-xxhdpi")
    fun theBarWritesItsRealHeightForScreensToClear_andResetsItWhenItLeaves() {
        val holder = TabBarHeight()
        val shown = mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalTabBarHeight provides holder) {
                RealTabs(320, fontScale = 2f, labelsOut = mutableListOf()) { tabs ->
                    if (shown.value) TalleleTabBar(tabs = tabs, look = TabBarStyle.Solid)
                }
            }
        }
        composeRule.waitForIdle()
        val tall: Dp = holder.height
        assertTrue("measured $tall should be at least the 68 dp minimum bar", tall >= 68.dp)
        shown.value = false
        composeRule.waitForIdle()
        assertEquals(0.dp, holder.height)
    }
}

package com.orbit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.domain.model.AppAccent
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.theme.LocalTalleleColors
import com.orbit.app.ui.theme.talleleColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Solid surfaces keep the behaviour glass had: taps stay on them, clicks work. */
@RunWith(AndroidJUnit4::class)
class SolidSurfaceJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun aTapOnASurfaceNeverReachesWhatIsBehindIt() {
        var behind by mutableIntStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { behind++ })
                    SoftGlassSurface(
                        modifier = Modifier.size(100.dp).testTag("card"),
                        shape = RoundedCornerShape(12.dp),
                    ) { Box(Modifier.size(100.dp)) }
                    ModalSurface(modifier = Modifier.size(100.dp).testTag("sheet")) { Box(Modifier.size(100.dp)) }
                }
            }
        }
        composeRule.onNodeWithTag("card").performClick()
        composeRule.onNodeWithTag("sheet").performClick()
        assertEquals(0, behind)
    }

    @Test
    fun aClickableSurfaceReportsItsClick() {
        var clicks = 0
        composeRule.setContent {
            MaterialTheme {
                SoftGlassSurface(
                    modifier = Modifier.size(100.dp).testTag("card"),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { clicks++ },
                ) { Box(Modifier.size(100.dp)) }
            }
        }
        composeRule.onNodeWithTag("card").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun orbitBackgroundProvidesMainTextAsTheDefaultContentColour() {
        val dark = talleleColors(isDark = true, accent = AppAccent.Harbour)
        var seen = Color.Unspecified
        composeRule.setContent {
            CompositionLocalProvider(LocalTalleleColors provides dark) {
                OrbitBackground(settings = AppSettings()) {
                    seen = LocalContentColor.current
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(dark.text, seen)
    }
}

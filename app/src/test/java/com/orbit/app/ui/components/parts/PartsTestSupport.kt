package com.orbit.app.ui.components.parts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.ui.components.LocalReducedMotionOverride
import com.orbit.app.ui.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/** Samples in the app's other languages. Generic words only. */
internal const val EstonianSample = "Ära unusta homme hommikul piima osta"
internal const val RussianSample = "Помнить купить молоко завтра утром"

/** Font scale 2 in a 320 dp wide box, inside the real theme, with motion reduced so tests stay still. */
@Composable
internal fun PartsHost(
    fontScale: Float = 2f,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalReducedMotionOverride provides true,
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
    ) {
        OrbitTheme(AppSettings(themeMode = if (dark) SettingsThemeMode.Dark else SettingsThemeMode.Light)) {
            Box(Modifier.width(320.dp)) { content() }
        }
    }
}

/** Asserts the text node laid out all its text: nothing cut by a line limit or a fixed size. */
internal fun SemanticsNodeInteraction.assertTextNotClipped(): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
    assertNotNull("node has no text layout result", action)
    action!!.invoke(results)
    assertFalse("no layout result", results.isEmpty())
    results.forEach { assertFalse("text has visual overflow (${it.size}, lines ${it.lineCount})", it.hasVisualOverflow) }
    return this
}

/**
 * Asserts every character of a (usually short) text is laid out and visible: no line limit cut it, no
 * line is ellipsized, no line is taller than its box and none is wider than the node.
 *
 * [assertTextNotClipped] reads `hasVisualOverflow`, which also reports true for a short label whose
 * node is narrower than the width it was measured against; this check looks at what is really cut.
 */
internal fun SemanticsNodeInteraction.assertTextFullyShown(): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
    assertNotNull("node has no text layout result", action)
    action!!.invoke(results)
    assertFalse("no layout result", results.isEmpty())
    results.forEach { result ->
        val paragraph = result.multiParagraph
        val label = "(${result.size}, lines ${result.lineCount})"
        assertFalse("text exceeded its line limit $label", paragraph.didExceedMaxLines)
        assertTrue("text is cut in height $label", paragraph.height <= result.size.height)
        val lastLine = result.lineCount - 1
        assertEquals("not every character is laid out $label", result.layoutInput.text.length, result.getLineEnd(lastLine, visibleEnd = false))
        for (line in 0..lastLine) {
            assertFalse("line $line is ellipsized $label", result.isLineEllipsized(line))
            assertTrue("line $line runs past the node $label", result.getLineRight(line) - result.getLineLeft(line) <= result.size.width + 1f)
        }
    }
    return this
}

/** Counts haptic ticks so tests can prove one tick per click. */
internal class CountingHaptics : HapticFeedback {
    val types = mutableListOf<HapticFeedbackType>()
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        types += hapticFeedbackType
    }
}

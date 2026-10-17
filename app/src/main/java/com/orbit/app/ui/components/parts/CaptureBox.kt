package com.orbit.app.ui.components.parts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme

/**
 * The capture box (proposal 6.3 and 7.1): a solid surface card with 20 dp corners, a 2 dp ink rule
 * down the left side, and the person's words in Literata 20/30.
 *
 * - [minLines] (default 4) is the resting height; the box grows with the text and with the font
 *   scale, and never clips. Scrolling for very long text is the owner's call.
 * - [label] is the TalkBack name of the field (for example "New thought"); [placeholder] is shown in
 *   text-3 while the field is empty.
 * - [contextSlot] sits above the text (for example a removable date chip). [trailing] sits at the
 *   start of the action row (Voice) and [actions] at the end (Keep). The row wraps at large sizes.
 * - Focus shows a 2 dp ink ring; unfocused the edge is the outline token.
 * - The part holds no state: the owner stores [value] and clears it only after the thought is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptureBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    minLines: Int = 4,
    contextSlot: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val edge = if (focused) {
        BorderStroke(2.dp, colors.ink)
    } else {
        BorderStroke(1.dp, colors.outline)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, TalleleShapes.CaptureBox)
            .border(edge, TalleleShapes.CaptureBox)
            .inkRule(colors.ink, startInset = 14.dp, verticalInset = 16.dp)
            .padding(start = 14.dp + TalleleDimens.InkRuleWidth + 12.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (contextSlot != null) contextSlot()
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                minLines = minLines,
                textStyle = typography.captureText.copy(color = colors.text),
                cursorBrush = SolidColor(colors.ink),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (label != null) Modifier.semantics { contentDescription = label } else Modifier,
                    ),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = typography.captureText.copy(color = colors.text3),
                            )
                        }
                        inner()
                    }
                },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (trailing != null) Arrangement.SpaceBetween else Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (trailing != null) trailing()
                actions()
            }
        }
    }
}

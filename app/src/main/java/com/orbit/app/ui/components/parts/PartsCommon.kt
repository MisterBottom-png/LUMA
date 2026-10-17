package com.orbit.app.ui.components.parts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme

/**
 * Internal building blocks shared by the Limestone Harbour parts (proposal section 10.3).
 *
 * Parts are presentational: every visible word, content description and state is passed in by the
 * owner (decision D-25), and no part decides whether a save or a schedule worked.
 */

/** A label and what it does. Used for receipt actions and any other simple text action. */
data class ReceiptAction(val label: String, val onClick: () -> Unit)

/**
 * A quiet text action in the ink colour. The touch area is at least 48 x 48 dp, the label wraps
 * instead of clipping, and the node is a button for TalkBack.
 */
@Composable
internal fun PartTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TalleleTheme.colors.ink,
) {
    Box(
        modifier = modifier
            .heightIn(min = TalleleDimens.MinTouchTarget)
            .widthIn(min = TalleleDimens.MinTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = TalleleTheme.typography.control.copy(color = color))
    }
}

/** An outlined, full-width answer button (decisions). At least 48 dp tall; grows with the text. */
@Composable
internal fun PartOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TalleleShapes.SmallButton,
) {
    val colors = TalleleTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TalleleDimens.MinTouchTarget)
            .background(colors.surface, shape)
            .border(BorderStroke(1.dp, colors.outline), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = TalleleTheme.typography.control.copy(color = colors.ink))
    }
}

/**
 * The thin ink line down the left side of the capture box and the receipt (proposal 6.3). It is drawn
 * behind the content, [startInset] from the left edge and [verticalInset] from the top and bottom, so
 * it needs no intrinsic measurement and grows with the content.
 */
internal fun Modifier.inkRule(color: Color, startInset: Dp, verticalInset: Dp): Modifier = drawBehind {
    val width = TalleleDimens.InkRuleWidth.toPx()
    val top = verticalInset.toPx()
    val height = (size.height - 2 * top).coerceAtLeast(0f)
    drawRect(color = color, topLeft = Offset(startInset.toPx(), top), size = Size(width, height))
}

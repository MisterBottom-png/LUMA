package com.orbit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's pop-up menu: rounded, solid, softly lifted, and grouped with a small gap
 * ([LumaMenuGap]) instead of a hairline. Every item should carry an icon that names
 * the action (a pencil for Edit), not the thing it acts on.
 */
@Composable
fun LumaMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 208.dp),
        offset = offset,
        shape = LumaMenuShape,
        containerColor = lumaMenuContainerColor(),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        content = content,
    )
}

/** One menu row: 48 dp tall, rounded when pressed, icon tinted quietly. */
@Composable
fun LumaMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp)) {
                text()
            }
        },
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .heightIn(min = 48.dp),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = colors.onSurface,
            leadingIconColor = colors.onSurfaceVariant,
            trailingIconColor = colors.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

/** Separates groups of actions, so the menu reads as two small cards. */
@Composable
fun LumaMenuGap() {
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(6.dp)
            .background(lumaMenuGapColor()),
    )
}

internal val LumaMenuShape = RoundedCornerShape(20.dp)

@Composable
private fun lumaMenuContainerColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() < 0.5f) colors.surfaceContainerHigh else colors.surfaceContainerLowest
}

@Composable
private fun lumaMenuGapColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() < 0.5f) colors.surfaceContainerLow else colors.surfaceContainer
}

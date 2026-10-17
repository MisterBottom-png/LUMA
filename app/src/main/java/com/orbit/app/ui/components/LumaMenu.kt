package com.orbit.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.ui.theme.TalleleTheme

/**
 * The app's pop-up menu: rounded, solid (the `sheet` token, never see-through), softly lifted, and grouped with a small gap
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
        containerColor = TalleleTheme.colors.sheet,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, TalleleTheme.colors.hairline),
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
    val colors = TalleleTheme.colors
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
            textColor = colors.text,
            leadingIconColor = colors.text2,
            trailingIconColor = colors.text2,
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
            .background(TalleleTheme.colors.well),
    )
}

internal val LumaMenuShape = RoundedCornerShape(20.dp)

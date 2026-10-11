package com.orbit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The grouped list: related rows share one soft surface, separated by faint inset lines,
 * instead of every row being its own floating card. Used by Spaces, Calendar, Review and
 * Settings so the four screens read as one product.
 */

val GroupedListShape = RoundedCornerShape(22.dp)

/** One soft surface holding related rows. */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SoftGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = GroupedListShape,
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** A faint line between rows, inset so it starts under the row text. */
@Composable
fun GroupDivider(startInset: Dp = 16.dp) {
    Box(
        modifier = Modifier
            .padding(start = startInset)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    )
}

/**
 * A section heading above a group. [large] is for the few main sections of a screen
 * (Review); the default is a small quiet label (Spaces, Settings).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (actionLabel != null) 44.dp else 0.dp)
            .padding(start = 4.dp, bottom = if (actionLabel != null) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = if (large) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp)
            },
            color = if (large) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * A small rounded-square chip holding an icon in a soft tint of [color]. The glyph is
 * darkened (light theme) or lightened (dark theme) so it stays readable on its tint.
 */
@Composable
fun TintedIconChip(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glyph = if (isDark) lerp(color, Color.White, 0.30f) else lerp(color, Color.Black, 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = if (isDark) 0.22f else 0.16f), RoundedCornerShape(size * 0.34f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = glyph, modifier = Modifier.size(iconSize))
    }
}

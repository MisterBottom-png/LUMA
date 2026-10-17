package com.orbit.app.ui.components.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleTheme

/**
 * A list row (proposal 6.3: ordinary lists are rows with dividers, never clipped).
 *
 * - [title] is the person's words in Literata; [metadata] is one line of Noto Sans under it (put the
 *   Space name here whenever [spaceColor] is used, so colour is never the only signal).
 * - Leading: [spaceColor] draws the 10 dp Space square, [leadingIcon] an icon, or [leading] any slot.
 *   They are decorative; the owner puts the meaning in words.
 * - At least 48 dp tall, grows with text, no fixed heights and no line limits unless [titleMaxLines]
 *   is passed.
 * - With [onClick] the whole row is one button target (descendants merged for TalkBack). Without it,
 *   the row is plain content, so a [trailing] control keeps its own focus.
 * - [showDivider] draws a hairline under the row.
 */
@Composable
fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    spaceColor: Color? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    titleMaxLines: Int = Int.MAX_VALUE,
) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = TalleleDimens.MinTouchTarget)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> leading()
                spaceColor != null -> Box(
                    Modifier
                        .size(10.dp)
                        .background(spaceColor, RoundedCornerShape(2.dp)),
                )
                leadingIcon != null -> Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typography.listWords.copy(color = colors.text),
                    maxLines = titleMaxLines,
                )
                if (!metadata.isNullOrBlank()) {
                    Text(text = metadata, style = typography.metadata.copy(color = colors.text2))
                }
            }
            if (trailing != null) trailing()
        }
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = colors.hairline)
    }
}

/**
 * A label and its value (proposal 7.11), label above value so both can wrap at large text sizes.
 * With [onClick] it is one button target and shows a chevron; without it the row is plain.
 */
@Composable
fun FactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = TalleleDimens.MinTouchTarget)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(text = label, style = typography.metadata.copy(color = colors.text2))
                Text(text = value, style = typography.body.copy(color = colors.text))
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.text2,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = colors.hairline)
    }
}

/**
 * A section heading in Noto Sans (never all caps) with an optional text action on the right. The
 * title is a heading for TalkBack navigation; the action is a button at least 48 dp tall.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = TalleleTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TalleleTheme.typography.bodyStrong.copy(color = colors.text),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            PartTextAction(label = actionLabel, onClick = onAction)
        }
    }
}

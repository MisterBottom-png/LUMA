package com.orbit.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * Secondary controls without outlines.
 *
 * Material's outlined chip and outlined button draw a grey ring around every option, so a
 * screen with five suggestions reads as a form. These keep the same names and parameters,
 * so screens switch by changing one import, and draw a quiet tonal pill of the accent
 * instead: the filled accent button stays the one strong element on a screen.
 */

private const val QuietContainerAlpha = 0.78f

/** Drop-in for Material's AssistChip: same parameters, tonal pill, no border. */
@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = colors.secondaryContainer.copy(alpha = QuietContainerAlpha),
            labelColor = colors.onSecondaryContainer,
            leadingIconContentColor = colors.onSecondaryContainer,
            trailingIconContentColor = colors.onSecondaryContainer,
            disabledContainerColor = colors.onSurface.copy(alpha = 0.06f),
        ),
        border = null,
    )
}

/** Drop-in for Material's OutlinedButton: same parameters, tonal fill, no border. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.secondaryContainer.copy(alpha = QuietContainerAlpha),
            contentColor = colors.onSecondaryContainer,
            disabledContainerColor = colors.onSurface.copy(alpha = 0.06f),
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
        border = null,
        content = content,
    )
}

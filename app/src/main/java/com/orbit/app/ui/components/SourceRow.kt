package com.orbit.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.ui.theme.OrbitSpacing

@Composable
fun SourceRow(
    title: String,
    itemType: ItemDetailType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dateTimeText: String? = null,
) {
    val typeLabel = stringResource(itemType.displayLabelRes())
    val untitledLabel = stringResource(itemType.untitledLabelRes())
    val visibleTitle = sourceRowVisibleTitle(title, untitledLabel)
    val visibleDateTime = dateTimeText?.trim()?.takeIf { it.isNotEmpty() }
    val metadataSeparator = stringResource(R.string.core_metadata_dot_separator)
    val accessibilityText = if (visibleDateTime == null) {
        stringResource(
            R.string.core_source_row_accessibility,
            typeLabel,
            visibleTitle,
        )
    } else {
        stringResource(
            R.string.core_source_row_accessibility_with_date,
            typeLabel,
            visibleTitle,
            visibleDateTime,
        )
    }
    val openLabel = stringResource(R.string.core_open_item_type, typeLabel)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityText
            }
            .orbitPressFeedback(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = openLabel,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = OrbitSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = itemType.sourceIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(OrbitSpacing.Medium))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.ExtraSmall),
            ) {
                Text(
                    text = visibleTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(typeLabel)
                        visibleDateTime?.let {
                            append(metadataSeparator)
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun sourceRowVisibleTitle(title: String, fallback: String): String =
    title.trim().ifBlank { fallback }

@StringRes
private fun ItemDetailType.displayLabelRes(): Int = when (this) {
    ItemDetailType.Note -> R.string.core_note
    ItemDetailType.Task -> R.string.core_task
    ItemDetailType.Reminder -> R.string.core_reminder
    ItemDetailType.Capture -> R.string.core_capture
}

@StringRes
private fun ItemDetailType.untitledLabelRes(): Int = when (this) {
    ItemDetailType.Note -> R.string.core_untitled_note
    ItemDetailType.Task -> R.string.core_untitled_task
    ItemDetailType.Reminder -> R.string.core_untitled_reminder
    ItemDetailType.Capture -> R.string.core_capture
}

private val ItemDetailType.sourceIcon: ImageVector
    get() = when (this) {
        ItemDetailType.Note -> Icons.Rounded.Description
        ItemDetailType.Task -> Icons.Rounded.CheckCircle
        ItemDetailType.Reminder -> Icons.Rounded.Alarm
        ItemDetailType.Capture -> Icons.Rounded.EditNote
    }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SourceRowPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(OrbitSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SourceRow(
                title = "Prepare the product realization notes for the next review",
                itemType = ItemDetailType.Task,
                dateTimeText = "Tomorrow, 09:30",
                onClick = {},
            )
            SourceRow(
                title = "A deliberately long source title that demonstrates how the row handles more text without turning the evidence list into a small novel",
                itemType = ItemDetailType.Note,
                onClick = {},
            )
        }
    }
}

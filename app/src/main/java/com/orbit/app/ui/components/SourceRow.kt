package com.orbit.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.navigation.ItemDetailType

@Composable
fun SourceRow(
    title: String,
    itemType: ItemDetailType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dateTimeText: String? = null,
) {
    val visibleTitle = title.trim().ifBlank { itemType.untitledLabel() }
    val visibleDateTime = dateTimeText?.trim()?.takeIf { it.isNotEmpty() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = sourceRowAccessibilityText(
                    title = visibleTitle,
                    itemType = itemType,
                    dateTimeText = visibleDateTime,
                )
            }
            .clickable(
                role = Role.Button,
                onClickLabel = "Open ${itemType.displayLabel.lowercase()}",
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = itemType.sourceIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                        append(itemType.displayLabel)
                        visibleDateTime?.let {
                            append(" · ")
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
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun sourceRowAccessibilityText(
    title: String,
    itemType: ItemDetailType,
    dateTimeText: String?,
): String = buildString {
    append(itemType.displayLabel)
    append(": ")
    append(title.trim().ifBlank { itemType.untitledLabel() })
    dateTimeText?.trim()?.takeIf { it.isNotEmpty() }?.let {
        append(", ")
        append(it)
    }
    append(". Open")
}

private val ItemDetailType.displayLabel: String
    get() = when (this) {
        ItemDetailType.Note -> "Note"
        ItemDetailType.Task -> "Task"
        ItemDetailType.Reminder -> "Reminder"
        ItemDetailType.Capture -> "Capture"
    }

private fun ItemDetailType.untitledLabel(): String = when (this) {
    ItemDetailType.Note -> "Untitled note"
    ItemDetailType.Task -> "Untitled task"
    ItemDetailType.Reminder -> "Untitled reminder"
    ItemDetailType.Capture -> "Capture"
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
            modifier = Modifier.padding(16.dp),
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

package com.orbit.app.ui.components

import androidx.annotation.StringRes
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.navigation.ItemDetailType

/**
 * A source row (proposal section 10.3): one thing a suggestion came from, as a tappable card. Solid
 * `surface` with an `outline` edge, the person's words in the list voice, the type (and date) in
 * `text-2` under it. The type is always in words, never only an icon or colour. At least 48 dp tall,
 * wraps and grows with the font size; nothing is cut off.
 */
@Composable
fun SourceRow(
    title: String,
    itemType: ItemDetailType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dateTimeText: String? = null,
) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
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
    val shape = TalleleShapes.Card

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityText
            }
            .orbitPressFeedback(
                interactionSource = interactionSource,
                clipShape = shape,
            )
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.outline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = openLabel,
                onClick = onClick,
            )
            .heightIn(min = TalleleDimens.MinTouchTarget)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = itemType.sourceIcon,
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = visibleTitle,
                style = typography.listWords,
                color = colors.text,
            )
            Text(
                text = buildString {
                    append(typeLabel)
                    visibleDateTime?.let {
                        append(metadataSeparator)
                        append(it)
                    }
                },
                style = typography.metadata,
                color = colors.text2,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = colors.text2,
            modifier = Modifier.size(20.dp),
        )
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

@Preview(name = "Light 1x", widthDp = 360, fontScale = 1f)
@Preview(name = "Dark 1x", widthDp = 360, fontScale = 1f, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Light 2x", widthDp = 360, fontScale = 2f)
@Preview(name = "Dark 2x", widthDp = 360, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
private annotation class SourceRowPreviews

@SourceRowPreviews
@Composable
private fun SourceRowPreview() {
    OrbitTheme(AppSettings()) {
        Column(
            modifier = Modifier
                .background(TalleleTheme.colors.page)
                .padding(16.dp),
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

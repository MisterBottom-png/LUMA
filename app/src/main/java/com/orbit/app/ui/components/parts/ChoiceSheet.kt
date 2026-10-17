package com.orbit.app.ui.components.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleTheme

/** One option in a [ChoiceSheet]. [selected] comes from the owner's real state. */
data class ChoiceOption(
    val label: String,
    val description: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The content of a choice sheet (proposal 7.4), for an owner to place inside its own solid sheet.
 * This part does not host a ModalBottomSheet.
 *
 * Options behave as one radio group: each row is a single target at least 48 dp tall with
 * `Role.RadioButton` and a selected state, so selection is announced in words and shown by the
 * radio mark, never by colour alone. Rows grow with the text.
 */
@Composable
fun ChoiceSheet(
    title: String,
    options: List<ChoiceOption>,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = typography.bodyStrong.copy(color = colors.text),
            modifier = Modifier
                .padding(bottom = 4.dp)
                .semantics { heading() },
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = typography.metadata.copy(color = colors.text2),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option.selected,
                            role = Role.RadioButton,
                            onClick = option.onClick,
                        )
                        .heightIn(min = TalleleDimens.MinTouchTarget)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option.selected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.ink,
                            unselectedColor = colors.outline,
                        ),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(text = option.label, style = typography.body.copy(color = colors.text))
                        if (!option.description.isNullOrBlank()) {
                            Text(
                                text = option.description,
                                style = typography.metadata.copy(color = colors.text2),
                            )
                        }
                    }
                }
                if (index != options.lastIndex) HorizontalDivider(thickness = 1.dp, color = colors.hairline)
            }
        }
    }
}

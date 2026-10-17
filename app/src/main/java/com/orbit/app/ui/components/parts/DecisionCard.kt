package com.orbit.app.ui.components.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme

/** One answer on a [DecisionCard]. */
data class DecisionChoice(val label: String, val onClick: () -> Unit)

/**
 * One card shape for every decision (proposal 7.4 and 7.12): a solid 12 dp card with the question,
 * the reason on ochre-soft (an icon plus words; ochre is used only here, where a choice is needed),
 * the thought in Literata, the answers, and an optional quieter secondary action such as "keep
 * without a reminder".
 *
 * Every answer is a full-width button at least 48 dp tall that grows with the text. The reason icon
 * is decorative because the reason words carry the meaning.
 */
@Composable
fun DecisionCard(
    title: String,
    reason: String,
    choices: List<DecisionChoice>,
    modifier: Modifier = Modifier,
    thought: String? = null,
    reasonIcon: ImageVector = Icons.Rounded.Info,
    secondaryAction: DecisionChoice? = null,
) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TalleleShapes.Card)
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = typography.bodyStrong.copy(color = colors.text),
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TalleleShapes.Chip)
                .background(colors.ochreSoft)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = reasonIcon,
                contentDescription = null,
                tint = colors.ochre,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Text(
                text = reason,
                style = typography.body.copy(color = colors.onOchreSoft),
                modifier = Modifier.weight(1f),
            )
        }
        if (!thought.isNullOrBlank()) {
            Text(text = thought, style = typography.listWords.copy(color = colors.text))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                PartOutlinedButton(label = choice.label, onClick = choice.onClick)
            }
        }
        if (secondaryAction != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PartTextAction(label = secondaryAction.label, onClick = secondaryAction.onClick)
            }
        }
    }
}

package com.orbit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One answer on a [OneQuestionCard]. */
data class OneQuestionAnswer(val label: String, val onClick: () -> Unit)

/**
 * One question and its answers: Home's time question under the capture box and every card
 * in Needs you. The first answer is the suggested one; the last ("Just keep it") is the
 * quietest. The card is a polite live region, so TalkBack reads it after current speech.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OneQuestionCard(
    question: String,
    detail: String?,
    answers: List<OneQuestionAnswer>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SoftGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                answers.forEachIndexed { index, answer ->
                    when {
                        index == 0 -> Button(onClick = answer.onClick, enabled = enabled) { Text(answer.label) }
                        index == answers.lastIndex -> TextButton(onClick = answer.onClick, enabled = enabled) {
                            Text(answer.label)
                        }
                        else -> OutlinedButton(onClick = answer.onClick, enabled = enabled) { Text(answer.label) }
                    }
                }
            }
        }
    }
}

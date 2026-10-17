package com.orbit.app.ui.components.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.components.rememberReducedMotion
import com.orbit.app.ui.theme.TalleleDimens
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme

/**
 * What the receipt shows. The owner decides which state is true (proposal 10.3: no part guesses
 * whether a save worked); the receipt only draws it.
 */
sealed interface ReceiptState {
    /** The save is still running. [label] is the owner's word for it (for example "Keeping"). */
    data class Saving(val label: String? = null) : ReceiptState

    /** The thought is really stored. [message] is the first line, [detail] the second, [actions] follow. */
    data class Kept(
        val message: String,
        val detail: String? = null,
        val actions: List<ReceiptAction> = emptyList(),
    ) : ReceiptState

    /** The save failed. [detail] says what is still safe (for example that the text is still there). */
    data class Failed(
        val message: String,
        val retryLabel: String,
        val onRetry: () -> Unit,
        val detail: String? = null,
    ) : ReceiptState
}

/**
 * The receipt under the capture box (proposal 5, 6.3 and 8).
 *
 * - A 2 dp ink rule runs down the left, linking it to what was written. Solid surface, no glass.
 * - Kept: a check icon in the success colour plus the words, never colour alone.
 * - Failed: an error icon plus the words on the soft error surface, and a filled Try again button.
 * - Saving: a quiet progress (still when animations are off) plus the owner's word.
 * - The message block is a polite live region, so a change is announced once without taking focus.
 *   Actions stay separate focus targets (each at least 48 dp).
 * - [thought] shows the owner's quoted words in Literata; it is not announced again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Receipt(
    state: ReceiptState,
    modifier: Modifier = Modifier,
    thought: String? = null,
) {
    val colors = TalleleTheme.colors
    val typography = TalleleTheme.typography
    val failed = state is ReceiptState.Failed
    val container = if (failed) colors.errorSoft else colors.surface
    val onContainer = if (failed) colors.onErrorSoft else colors.text
    val secondary = if (failed) colors.onErrorSoft else colors.text2
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(TalleleShapes.Card)
            .background(container)
            .inkRule(colors.ink, startInset = 12.dp, verticalInset = 12.dp)
            .padding(start = 12.dp + TalleleDimens.InkRuleWidth + 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!thought.isNullOrBlank()) {
                Text(
                    text = thought,
                    style = typography.listWords.copy(color = secondary),
                )
            }
            when (state) {
                is ReceiptState.Saving -> Row(
                    modifier = Modifier
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SavingProgress(colors.text2)
                    if (!state.label.isNullOrBlank()) {
                        Text(state.label, style = typography.body.copy(color = onContainer))
                    }
                }

                is ReceiptState.Kept -> {
                    MessageBlock(
                        icon = {
                            // Section 6.6: the keep mark appears on the receipt.
                            KeepMark(size = 20.dp, tint = colors.success)
                        },
                        message = state.message,
                        detail = state.detail,
                        messageColor = onContainer,
                        detailColor = secondary,
                    )
                    if (state.actions.isNotEmpty()) {
                        FlowRow {
                            state.actions.forEach { action ->
                                PartTextAction(label = action.label, onClick = action.onClick)
                            }
                        }
                    } else {
                        Box(Modifier.height(8.dp))
                    }
                }

                is ReceiptState.Failed -> {
                    MessageBlock(
                        icon = {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = colors.error,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        message = state.message,
                        detail = state.detail,
                        messageColor = onContainer,
                        detailColor = secondary,
                    )
                    Box(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .heightIn(min = TalleleDimens.MinTouchTarget)
                            .widthIn(min = TalleleDimens.MinTouchTarget)
                            .clip(TalleleShapes.SmallButton)
                            .background(colors.error)
                            .clickable(role = Role.Button, onClick = state.onRetry)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.retryLabel, style = typography.control.copy(color = colors.onError))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBlock(
    icon: @Composable () -> Unit,
    message: String,
    detail: String?,
    messageColor: Color,
    detailColor: Color,
) {
    val typography = TalleleTheme.typography
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.padding(top = 2.dp)) { icon() }
        Column(Modifier.weight(1f)) {
            Text(message, style = typography.bodyStrong.copy(color = messageColor))
            if (!detail.isNullOrBlank()) {
                Text(detail, style = typography.metadata.copy(color = detailColor))
            }
        }
    }
}

@Composable
private fun SavingProgress(color: Color) {
    val colors = TalleleTheme.colors
    if (rememberReducedMotion()) {
        CircularProgressIndicator(
            progress = { 0.75f },
            modifier = Modifier.size(18.dp),
            color = color,
            strokeWidth = 2.dp,
            trackColor = colors.surface,
        )
    } else {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = color, strokeWidth = 2.dp)
    }
}

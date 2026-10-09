package com.orbit.app.ui.screens.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Spa
import com.orbit.app.ui.components.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.rememberReducedMotion
import kotlinx.coroutines.delay

/** The questions Ask LUMA answers from the user's own items. */
enum class AskLumaPrompt(val labelRes: Int) {
    WhatNow(R.string.review_ask_now),
    WhatCanWait(R.string.review_ask_can_wait),
    DependsOnOthers(R.string.review_ask_depends),
    SmallestStep(R.string.review_ask_smallest),
    AnythingUrgent(R.string.review_ask_urgent),
}

/** Ask LUMA at the top of Review: one calm suggestion, never a score. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AskLumaCard(onAsk: (AskLumaPrompt?) -> Unit) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.review_ask_title),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            FilledTonalButton(
                onClick = { onAsk(AskLumaPrompt.WhatNow) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(AskLumaPrompt.WhatNow.labelRes))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AskLumaPrompt.entries.drop(1).forEach { prompt ->
                    AssistChip(
                        onClick = { onAsk(prompt) },
                        label = { Text(stringResource(prompt.labelRes)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.review_ask_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * An optional, brief breathing moment (about five seconds). It never blocks Review
 * and is skipped entirely when animations are turned off.
 */
@Composable
internal fun BreathingMoment() {
    val reduceMotion = rememberReducedMotion()
    var running by rememberSaveable { mutableStateOf(false) }
    var inhaling by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        inhaling = true
        delay(BreathInMillis.toLong())
        inhaling = false
        delay(BreathOutMillis.toLong())
        running = false
    }
    val scale by animateFloatAsState(
        targetValue = if (running && inhaling) 1f else 0.72f,
        animationSpec = tween(if (inhaling) BreathInMillis else BreathOutMillis),
        label = "reviewBreath",
    )
    if (!running || reduceMotion) {
        TextButton(
            onClick = { if (!reduceMotion) running = true },
            enabled = !reduceMotion,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Rounded.Spa, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.review_breathe_start), modifier = Modifier.padding(start = 8.dp))
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
        )
        Text(
            text = stringResource(if (inhaling) R.string.core_review_breathe_in else R.string.core_review_breathe_out),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private const val BreathInMillis = 2_000
private const val BreathOutMillis = 3_000

/** Shown only when nothing at all is waiting. */
@Composable
internal fun AllSortedCard() {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.review_all_sorted_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.review_all_sorted_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Weekly look back: a button on any day; the weekend only adds a hint. */
@Composable
internal fun WeeklyLookBackEntry(
    expanded: Boolean,
    suggested: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(if (expanded) R.string.review_weekly_close else R.string.review_weekly_button))
        }
        if (suggested && !expanded) {
            Text(
                text = stringResource(R.string.review_weekly_weekend_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

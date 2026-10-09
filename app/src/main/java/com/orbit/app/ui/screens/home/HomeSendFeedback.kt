package com.orbit.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.time.OrbitTimeFormat

/** "Saved. You can let it go." — shown briefly after every send. */
@Composable
internal fun SavedConfirmation(visible: Boolean, reduceMotion: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(400)),
    ) {
        Text(
            text = stringResource(R.string.core_home_saved_let_go),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The single question Home may ask: only for a thought that clearly needs a
 * reminder, so it is not missed. "Not now" leaves it in To sort.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickReminderCard(
    question: QuickReminderQuestion,
    timeFormat: OrbitTimeFormat,
    isWorking: Boolean,
    onConfirm: () -> Unit,
    onChangeTime: () -> Unit,
    onNotNow: () -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = question.reminderAt?.let {
                    stringResource(R.string.core_home_quick_reminder_question, timeFormat.formatWeekdayDateTime(it))
                } ?: stringResource(R.string.core_home_quick_reminder_when),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = question.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, enabled = !isWorking) {
                    Text(
                        stringResource(
                            if (question.reminderAt != null) R.string.core_home_quick_reminder_set
                            else R.string.core_home_quick_reminder_pick,
                        ),
                    )
                }
                if (question.reminderAt != null) {
                    TextButton(onClick = onChangeTime, enabled = !isWorking) {
                        Text(stringResource(R.string.core_home_quick_reminder_change))
                    }
                }
                TextButton(onClick = onNotNow, enabled = !isWorking) {
                    Text(stringResource(R.string.core_home_quick_reminder_not_now))
                }
            }
        }
    }
}

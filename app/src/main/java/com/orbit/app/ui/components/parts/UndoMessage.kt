package com.orbit.app.ui.components.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.theme.TalleleShapes
import com.orbit.app.ui.theme.TalleleTheme

/**
 * A solid inverse-surface message bar with one action (proposal 4 and 7.1), for example a short
 * confirmation with Undo. It is not glass.
 *
 * - The message is a polite live region, announced once when it appears.
 * - The action is a button at least 48 dp tall; both texts wrap at large sizes.
 * - There is no timer in here: the owner shows and hides it, and must only offer the action when the
 *   undo can really happen (CLAUDE.md invariants).
 */
@Composable
fun UndoMessage(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(TalleleShapes.Card)
            .background(scheme.inverseSurface)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = TalleleTheme.typography.body.copy(color = scheme.inverseOnSurface),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        PartTextAction(label = actionLabel, onClick = onAction, color = scheme.inversePrimary)
    }
}

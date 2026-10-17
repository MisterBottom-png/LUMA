package com.orbit.app.ui.components.parts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.ui.theme.TalleleTheme

/**
 * One line of the day. [timeLabel] is already formatted by the owner ("09:00"); null means the
 * entry has no time (date-only items stay date-only, never "00:00").
 */
data class AgendaEntry(
    val timeLabel: String? = null,
    val title: String,
    val metadata: String? = null,
    val onClick: () -> Unit,
)

/**
 * The agenda for one day (proposal 7.10): an "any time" group first, then an "at a time" group. Times
 * use tabular numbers and share one column width that scales with the font size, so they line up
 * without clipping. Each entry is a one-target row (at least 48 dp). Group headings come from the
 * owner; when null the groups are listed without headings.
 *
 * With no entries, [emptyText] is shown (for example "Nothing planned."). The owner supplies any
 * action that goes with it.
 */
@Composable
fun DayAgenda(
    entries: List<AgendaEntry>,
    emptyText: String,
    modifier: Modifier = Modifier,
    anyTimeHeading: String? = null,
    atTimeHeading: String? = null,
) {
    val colors = TalleleTheme.colors
    if (entries.isEmpty()) {
        Text(
            text = emptyText,
            style = TalleleTheme.typography.body.copy(color = colors.text2),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
        return
    }
    val anyTime = entries.filter { it.timeLabel.isNullOrBlank() }
    val atTime = entries.filter { !it.timeLabel.isNullOrBlank() }
    // 56 sp so the column grows with the font scale and "09:00" never wraps.
    val timeColumn = with(LocalDensity.current) { 56.sp.toDp() }
    Column(modifier = modifier.fillMaxWidth()) {
        if (anyTime.isNotEmpty()) {
            if (anyTimeHeading != null) SectionHeader(title = anyTimeHeading)
            anyTime.forEachIndexed { index, entry ->
                AgendaRow(entry, timeColumn, showDivider = index != anyTime.lastIndex || atTime.isNotEmpty())
            }
        }
        if (atTime.isNotEmpty()) {
            if (atTimeHeading != null) SectionHeader(title = atTimeHeading)
            atTime.forEachIndexed { index, entry ->
                AgendaRow(entry, timeColumn, showDivider = index != atTime.lastIndex)
            }
        }
    }
}

@Composable
private fun AgendaRow(entry: AgendaEntry, timeColumn: androidx.compose.ui.unit.Dp, showDivider: Boolean) {
    val colors = TalleleTheme.colors
    ItemRow(
        title = entry.title,
        metadata = entry.metadata,
        onClick = entry.onClick,
        showDivider = showDivider,
        leading = entry.timeLabel?.takeIf { it.isNotBlank() }?.let { time ->
            {
                Text(
                    text = time,
                    style = TalleleTheme.typography.timeTabular.copy(color = colors.text2),
                    modifier = Modifier.widthIn(min = timeColumn),
                )
            }
        },
    )
}

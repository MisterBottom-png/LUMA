package com.orbit.app.ui.components.parts

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.ui.theme.OrbitTheme
import com.orbit.app.ui.theme.TalleleTheme

/*
 * Previews for the Limestone Harbour parts: light and dark, font scale 1 and 2, 360 dp wide.
 * Sample text only (decision D-25: no string resources in P1, and never a person's name).
 */

@Preview(name = "Light 1x", widthDp = 360, fontScale = 1f)
@Preview(name = "Dark 1x", widthDp = 360, fontScale = 1f, uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Light 2x", widthDp = 360, fontScale = 2f)
@Preview(name = "Dark 2x", widthDp = 360, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
internal annotation class PartsPreviews

@Preview(name = "Light 320 2x", widthDp = 320, fontScale = 2f)
@Preview(name = "Dark 320 2x", widthDp = 320, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES)
internal annotation class PartsNarrowPreviews

@Composable
private fun PreviewPage(content: @Composable () -> Unit) {
    OrbitTheme(AppSettings()) {
        Column(
            modifier = Modifier
                .background(TalleleTheme.colors.page)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@PartsPreviews
@Composable
private fun KeepMarkPreview() = PreviewPage {
    KeepMark(size = 48.dp, tint = TalleleTheme.colors.text)
    KeepMark(size = 24.dp, tint = TalleleTheme.colors.ink)
}

@PartsPreviews
@Composable
private fun KeepButtonPreview() = PreviewPage {
    KeepButton(label = "Keep", onClick = {})
    KeepButton(label = "Keeping", onClick = {}, busy = true)
    KeepButton(label = "Keep", onClick = {}, enabled = false)
}

@PartsPreviews
@PartsNarrowPreviews
@Composable
private fun CaptureBoxPreview() = PreviewPage {
    CaptureBox(
        value = "",
        onValueChange = {},
        placeholder = "A thought, a plan, something to remember",
        label = "New thought",
        trailing = { PartTextAction(label = "Voice", onClick = {}) },
        actions = { KeepButton(label = "Keep", onClick = {}) },
    )
    CaptureBox(
        value = "Buy oat milk and call the dentist before Friday",
        onValueChange = {},
        placeholder = "A thought",
        minLines = 2,
        actions = { KeepButton(label = "Keep", onClick = {}) },
    )
}

@PartsPreviews
@PartsNarrowPreviews
@Composable
private fun ReceiptPreview() = PreviewPage {
    Receipt(ReceiptState.Saving("Keeping"))
    Receipt(
        state = ReceiptState.Kept(
            message = "Kept on this device.",
            detail = "Created a task in Household.",
            actions = listOf(ReceiptAction("Open", {}), ReceiptAction("Change", {}), ReceiptAction("Undo", {})),
        ),
        thought = "Buy oat milk",
    )
    Receipt(
        state = ReceiptState.Failed(
            message = "Couldn't keep that.",
            retryLabel = "Try again",
            onRetry = {},
            detail = "Your text is still here.",
        ),
    )
}

@PartsPreviews
@PartsNarrowPreviews
@Composable
private fun DecisionCardPreview() = PreviewPage {
    DecisionCard(
        title = "When should this remind you?",
        reason = "Missing a time",
        thought = "Call the dentist",
        choices = listOf(DecisionChoice("Tomorrow 09:00", {}), DecisionChoice("Pick a date and time", {})),
        secondaryAction = DecisionChoice("Keep without a reminder", {}),
    )
}

@PartsPreviews
@Composable
private fun ItemRowPreview() = PreviewPage {
    ItemRow(
        title = "Buy oat milk",
        metadata = "Household - Task",
        spaceColor = TalleleTheme.colors.spaces.moss,
        onClick = {},
    )
    ItemRow(
        title = "Ära unusta homme hommikul piima osta ja arstile helistada",
        metadata = "Kodu",
        leadingIcon = Icons.Rounded.Event,
        showDivider = false,
    )
}

@PartsPreviews
@Composable
private fun FactRowPreview() = PreviewPage {
    FactRow(label = "Due", value = "Wed 14 Oct, 09:00", icon = Icons.Rounded.Event, onClick = {})
    FactRow(label = "Space", value = "Household", showDivider = false)
}

@PartsPreviews
@Composable
private fun SectionHeaderPreview() = PreviewPage {
    SectionHeader(title = "To decide", actionLabel = "Sort one by one", onAction = {})
    SectionHeader(title = "Look after")
}

@PartsPreviews
@Composable
private fun ChoiceSheetPreview() = PreviewPage {
    ChoiceSheet(
        title = "Remind me",
        supportingText = "Pick when to be notified.",
        options = listOf(
            ChoiceOption("Tomorrow 09:00", description = "Wed 14 Oct", selected = true, onClick = {}),
            ChoiceOption("In an hour", selected = false, onClick = {}),
            ChoiceOption("Pick a date and time", selected = false, onClick = {}),
        ),
    )
}

@PartsPreviews
@Composable
private fun ReminderStatusPreview() = PreviewPage {
    ReminderStatus(
        delivery = ReminderDelivery.Scheduled("Reminder scheduled"),
        dueLabel = "Due", dueValue = "Wed 14 Oct, 09:00",
        notifyLabel = "Notify", notifyValue = "08:45 (15 minutes before)",
        repeatLabel = "Repeat", repeatValue = "Does not repeat",
        onDueClick = {}, onNotifyClick = {}, onRepeatClick = {},
    )
    ReminderStatus(
        delivery = ReminderDelivery.NotScheduled("Notifications are off", "Turn on", {}),
        dueLabel = "Due", dueValue = "Wed 14 Oct, 09:00",
        notifyLabel = "Notify", notifyValue = "At the time",
        repeatLabel = "Repeat", repeatValue = "Weekly",
    )
    ReminderStatus(
        delivery = ReminderDelivery.Problem("The reminder could not be scheduled.", "Try again", {}),
        dueLabel = "Due", dueValue = "Wed 14 Oct, 09:00",
        notifyLabel = "Notify", notifyValue = "At the time",
        repeatLabel = "Repeat", repeatValue = "Does not repeat",
    )
}

@PartsPreviews
@Composable
private fun UndoMessagePreview() = PreviewPage {
    UndoMessage(message = "Filed in Household.", actionLabel = "Undo", onAction = {})
    Text("Sample text under the bar", color = TalleleTheme.colors.text2)
}

@PartsPreviews
@Composable
private fun DayAgendaPreview() = PreviewPage {
    DayAgenda(
        entries = listOf(
            AgendaEntry(title = "Buy oat milk", metadata = "Household", onClick = {}),
            AgendaEntry(timeLabel = "09:00", title = "Call the dentist", metadata = "Notify 08:45", onClick = {}),
            AgendaEntry(timeLabel = "18:30", title = "Water the plants", onClick = {}),
        ),
        emptyText = "Nothing planned.",
        anyTimeHeading = "Any time",
        atTimeHeading = "At a time",
    )
    DayAgenda(entries = emptyList(), emptyText = "Nothing planned.")
}

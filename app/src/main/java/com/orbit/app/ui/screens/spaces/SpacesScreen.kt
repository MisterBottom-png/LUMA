package com.orbit.app.ui.screens.spaces

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.time.OrbitTimeFormat

private val iconChoices = listOf(
    "work",
    "person",
    "directions_car",
    "pets",
    "payments",
    "lightbulb",
    "home",
    "favorite",
    "school",
    "folder",
    "palette",
)

private val accentChoices = listOf(
    "#6D7CFF",
    "#B270D6",
    "#4E91D8",
    "#D58B62",
    "#62A77A",
    "#D7798D",
    "#59A6A6",
    "#E0A84F",
)

private data class SpaceFeedItem(
    val reference: SpaceItemReference,
    val icon: ImageVector,
    val title: String,
    val preview: String?,
    val badge: String,
    val timestamp: Long,
)

@Composable
fun SpacesScreen(
    uiState: SpacesUiState,
    timeFormat: OrbitTimeFormat,
    onSpaceSelected: (Long?) -> Unit,
    onCreateSpace: (String, String, String) -> Unit,
    onUpdateSpace: (Long, String, String, String) -> Unit,
    onHideSpace: (Long) -> Unit,
    onArchiveSpace: (Long) -> Unit,
    onRestoreSpace: (Long) -> Unit,
    onMoveSpace: (Long, Int) -> Unit,
    onMoveItem: (SpaceItemReference, Long) -> Unit,
    onOpenSearch: () -> Unit,
    onItemSelected: (SpaceItemReference) -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<SpaceEntity?>(null) }
    var itemToMove by remember { mutableStateOf<SpaceItemReference?>(null) }

    val selectedSpace = uiState.selectedSpace
    if (selectedSpace == null) {
        SpacesOverview(
            uiState = uiState,
            onCreate = { showCreateDialog = true },
            onSelect = { onSpaceSelected(it.id) },
            onEdit = { editingSpace = it },
            onHide = onHideSpace,
            onArchive = onArchiveSpace,
            onRestore = onRestoreSpace,
            onMove = onMoveSpace,
            onOpenSearch = onOpenSearch,
        )
    } else {
        SpaceDetail(
            space = selectedSpace,
            contents = uiState.selectedContents,
            timeFormat = timeFormat,
            onBack = { onSpaceSelected(null) },
            onMoveItem = { itemToMove = it },
            onItemSelected = onItemSelected,
        )
    }

    if (showCreateDialog) {
        SpaceEditorDialog(
            space = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, icon, accent ->
                onCreateSpace(name, icon, accent)
                showCreateDialog = false
            },
        )
    }

    editingSpace?.let { space ->
        SpaceEditorDialog(
            space = space,
            onDismiss = { editingSpace = null },
            onConfirm = { name, icon, accent ->
                onUpdateSpace(space.id, name, icon, accent)
                editingSpace = null
            },
        )
    }

    itemToMove?.let { item ->
        MoveItemDialog(
            spaces = uiState.visibleSpaces.filterNot { it.id == selectedSpace?.id },
            onDismiss = { itemToMove = null },
            onMove = { targetId ->
                onMoveItem(item, targetId)
                itemToMove = null
            },
        )
    }
}

@Composable
private fun SpacesOverview(
    uiState: SpacesUiState,
    onCreate: () -> Unit,
    onSelect: (SpaceEntity) -> Unit,
    onEdit: (SpaceEntity) -> Unit,
    onHide: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val visible = uiState.visibleSpaces
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = statusTopPadding + 26.dp,
            end = 24.dp,
            bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spaces",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "A place for each part of life.",
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = onOpenSearch) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                    FilledTonalIconButton(onClick = onCreate) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create Space")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (visible.isEmpty()) {
            item {
                EmptySpacesCard(onCreate)
            }
        } else {
            items(visible.size, key = { visible[it].id }) { index ->
                val space = visible[index]
                SpaceCard(
                    space = space,
                    itemCount = uiState.itemCounts[space.id] ?: 0,
                    canMoveUp = index > 0,
                    canMoveDown = index < visible.lastIndex,
                    onClick = { onSelect(space) },
                    onEdit = { onEdit(space) },
                    onHide = { onHide(space.id) },
                    onArchive = { onArchive(space.id) },
                    onMoveUp = { onMove(space.id, -1) },
                    onMoveDown = { onMove(space.id, 1) },
                )
            }
        }

        if (uiState.inactiveSpaces.isNotEmpty()) {
            item {
                Text(
                    text = "Hidden & archived",
                    modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(uiState.inactiveSpaces.size, key = { "inactive_${uiState.inactiveSpaces[it].id}" }) { index ->
                InactiveSpaceRow(uiState.inactiveSpaces[index], onRestore)
            }
        }
    }
}

@Composable
private fun SpaceCard(
    space: SpaceEntity,
    itemCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onArchive: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    SoftGlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 15.dp, bottom = 15.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpaceIcon(space, modifier = Modifier.size(48.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Space options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(space.icon.asImageVector(), contentDescription = null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                        enabled = canMoveUp,
                        onClick = { menuExpanded = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                        enabled = canMoveDown,
                        onClick = { menuExpanded = false; onMoveDown() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Hide") },
                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                        onClick = { menuExpanded = false; onHide() },
                    )
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                        onClick = { menuExpanded = false; onArchive() },
                    )
                }
            }
        }
    }
}

@Composable
private fun InactiveSpaceRow(space: SpaceEntity, onRestore: (Long) -> Unit) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpaceIcon(space, modifier = Modifier.size(38.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (space.archived) "Archived" else "Hidden",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onRestore(space.id) }) { Text("Restore") }
        }
    }
}

@Composable
private fun EmptySpacesCard(onCreate: () -> Unit) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No visible Spaces",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Restore one below or make a new place.",
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreate) { Text("Create Space") }
        }
    }
}

@Composable
private fun SpaceDetail(
    space: SpaceEntity,
    contents: SpaceContents,
    timeFormat: OrbitTimeFormat,
    onBack: () -> Unit,
    onMoveItem: (SpaceItemReference) -> Unit,
    onItemSelected: (SpaceItemReference) -> Unit,
) {
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = statusTopPadding + 20.dp,
            end = 24.dp,
            bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Spaces",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpaceIcon(space, modifier = Modifier.size(58.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = space.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${contents.size} ${if (contents.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (contents.size == 0) {
            item {
                SoftGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "This Space is quiet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Notes, tasks, and reminders saved here will appear together.",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        val feed = contents.asFeedItems(timeFormat)
        if (feed.isNotEmpty()) {
            item(key = "life_feed_heading") {
                Text(
                    text = "Life Feed",
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(feed, key = { "${it.reference.type}_${it.reference.id}" }) { item ->
                SpaceFeedRow(
                    item = item,
                    onClick = { onItemSelected(item.reference) },
                    onMove = { onMoveItem(item.reference) },
                )
            }
        }
    }
}

private fun SpaceContents.asFeedItems(timeFormat: OrbitTimeFormat): List<SpaceFeedItem> = buildList {
    notes.mapTo(this) { note ->
        SpaceFeedItem(
            reference = SpaceItemReference(SpaceItemType.Note, note.id),
            icon = Icons.Rounded.Description,
            title = note.title.ifBlank { "Untitled note" },
            preview = null,
            badge = "Note",
            timestamp = note.updatedAt,
        )
    }
    tasks.mapTo(this) { task ->
        SpaceFeedItem(
            reference = SpaceItemReference(SpaceItemType.Task, task.id),
            icon = task.status.feedIcon(),
            title = task.title,
            preview = task.notes.takeIf { it.isNotBlank() },
            badge = task.status.feedLabel(),
            timestamp = task.updatedAt,
        )
    }
    reminders.mapTo(this) { reminder ->
        SpaceFeedItem(
            reference = SpaceItemReference(SpaceItemType.Reminder, reminder.id),
            icon = Icons.Rounded.Notifications,
            title = reminder.title,
            preview = reminder.notes.takeIf { it.isNotBlank() },
            badge = "Reminder · ${timeFormat.formatShortDateTime(reminder.dueAt)}",
            timestamp = reminder.dueAt,
        )
    }
}.sortedByDescending { it.timestamp }

private fun com.orbit.app.data.local.entity.TaskStatus.feedLabel(): String = when (this) {
    com.orbit.app.data.local.entity.TaskStatus.Open -> "Task"
    com.orbit.app.data.local.entity.TaskStatus.Done -> "Task · Done"
    com.orbit.app.data.local.entity.TaskStatus.Archived -> "Archived"
    com.orbit.app.data.local.entity.TaskStatus.WaitingFor -> "Waiting for"
    com.orbit.app.data.local.entity.TaskStatus.Someday -> "Someday"
}

private fun com.orbit.app.data.local.entity.TaskStatus.feedIcon(): ImageVector = when (this) {
    com.orbit.app.data.local.entity.TaskStatus.Done -> Icons.Rounded.CheckCircle
    else -> Icons.Rounded.CheckBoxOutlineBlank
}

@Composable
private fun SpaceItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onMove: () -> Unit,
) {
    SoftGlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 13.dp),
            ) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onMove) {
                Icon(
                    Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = "Move item to another Space",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpaceFeedRow(
    item: SpaceFeedItem,
    onClick: () -> Unit,
    onMove: () -> Unit,
) {
    SpaceItemRow(
        icon = item.icon,
        title = item.title,
        subtitle = listOfNotNull(item.badge, item.preview).joinToString(" · "),
        onClick = onClick,
        onMove = onMove,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceEditorDialog(
    space: SpaceEntity?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var name by rememberSaveable(space?.id) { mutableStateOf(space?.name.orEmpty()) }
    var icon by rememberSaveable(space?.id) { mutableStateOf(space?.icon ?: "folder") }
    var accent by rememberSaveable(space?.id) {
        mutableStateOf(space?.colorAccent ?: accentChoices.first())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            style = GlassSurfaceStyle.Sheet,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (space == null) "Create Space" else "Edit Space",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Name") },
                    )
                    Text(
                        text = "Icon",
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        iconChoices.forEach { choice ->
                            Surface(
                                onClick = { icon = choice },
                                modifier = Modifier
                                    .size(42.dp)
                                    .then(
                                        if (icon == choice) {
                                            Modifier.border(2.dp, accent.asColor(), CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = choice.asImageVector(),
                                        contentDescription = choice,
                                        modifier = Modifier.size(21.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Accent",
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        accentChoices.chunked(4).forEach { rowChoices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    12.dp,
                                    Alignment.CenterHorizontally,
                                ),
                            ) {
                                rowChoices.forEach { choice ->
                                    Surface(
                                        onClick = { accent = choice },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .then(
                                                if (accent == choice) {
                                                    Modifier.border(
                                                        3.dp,
                                                        MaterialTheme.colorScheme.onSurface,
                                                        CircleShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                        shape = CircleShape,
                                        color = choice.asColor(),
                                    ) {}
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = { onConfirm(name, icon, accent) },
                        enabled = name.isNotBlank(),
                    ) {
                        Text(if (space == null) "Create" else "Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveItemDialog(
    spaces: List<SpaceEntity>,
    onDismiss: () -> Unit,
    onMove: (Long) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            style = GlassSurfaceStyle.Sheet,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Move to Space",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (spaces.isEmpty()) {
                    Text(
                        text = "Create or restore another Space before moving this item.",
                        modifier = Modifier.padding(top = 14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        spaces.forEach { space ->
                            SoftGlassSurface(
                                onClick = { onMove(space.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SpaceIcon(space, modifier = Modifier.size(34.dp))
                                    Text(space.name, modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 14.dp),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SpaceIcon(space: SpaceEntity, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = space.colorAccent.asColor().copy(alpha = 0.16f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = space.icon.asImageVector(),
                contentDescription = null,
                tint = space.colorAccent.asColor(),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun String.asImageVector(): ImageVector = when (this) {
    "work" -> Icons.Rounded.Work
    "person" -> Icons.Rounded.Person
    "directions_car" -> Icons.Rounded.DirectionsCar
    "pets" -> Icons.Rounded.Pets
    "payments" -> Icons.Rounded.Payments
    "lightbulb" -> Icons.Rounded.Lightbulb
    "home" -> Icons.Rounded.Home
    "favorite" -> Icons.Rounded.Favorite
    "school" -> Icons.Rounded.School
    "palette" -> Icons.Rounded.Palette
    else -> Icons.Rounded.Folder
}

private fun String.asColor(): Color = runCatching {
    Color(android.graphics.Color.parseColor(this))
}.getOrElse { Color(0xFF6D7CFF) }

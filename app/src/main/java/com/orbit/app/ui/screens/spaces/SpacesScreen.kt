package com.orbit.app.ui.screens.spaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import com.orbit.app.ui.components.GroupDivider
import com.orbit.app.ui.components.GroupedCard
import com.orbit.app.ui.components.GroupedListShape
import com.orbit.app.ui.components.SectionHeader
import com.orbit.app.ui.components.TintedIconChip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.orbit.app.data.local.SpaceNames
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.R
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.ModalSurface
import com.orbit.app.ui.components.OrbitModalDefaults
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.ui.components.orbitScrollEdgeFade
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import com.orbit.app.ui.theme.OrbitSpacing
import com.orbit.app.ui.theme.OrbitMotion

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

private data class SpaceDetailTarget(
    val space: SpaceEntity?,
    val isUnfiled: Boolean,
) {
    val key: String = if (isUnfiled) "unfiled" else "space_${space!!.id}"
}

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
    onMoveItem: (SpaceItemReference, Long?) -> Unit,
    onUndoMove: () -> Unit,
    onRetryMove: () -> Unit,
    onUnfiledSelected: () -> Unit,
    onOpenSearch: () -> Unit,
    onItemSelected: (SpaceItemReference) -> Unit,
    onOpenToSort: () -> Unit = {},
    onOpenToday: () -> Unit = {},
    onToggleDone: (SpaceItemReference) -> Unit = {},
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingSpace by remember { mutableStateOf<SpaceEntity?>(null) }
    var itemToMove by remember { mutableStateOf<SpaceItemReference?>(null) }

    val selectedSpace = uiState.selectedSpace
    val detailTarget = selectedSpace?.let { SpaceDetailTarget(space = it, isUnfiled = false) }
        ?: uiState.isUnfiledSelected.takeIf { it }?.let { SpaceDetailTarget(space = null, isUnfiled = true) }
    BackHandler(enabled = detailTarget != null) {
        onSpaceSelected(null)
    }
    val moveTargets = remember(uiState.visibleSpaces, selectedSpace?.id) {
        uiState.visibleSpaces.filterNot { it.id == selectedSpace?.id }
    }
    AnimatedContent(
        targetState = detailTarget,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val opening = targetState != null
            (fadeIn(tween(OrbitMotion.StandardDurationMillis)) +
                slideInHorizontally(
                    animationSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    initialOffsetX = { if (opening) it / 9 else -it / 9 },
                )) togetherWith
                (fadeOut(tween(OrbitMotion.QuickDurationMillis)) +
                    slideOutHorizontally(
                        animationSpec = tween(OrbitMotion.StandardDurationMillis),
                        targetOffsetX = { if (opening) -it / 12 else it / 12 },
                    ))
        },
        contentKey = { it?.key },
        label = "Spaces pane",
    ) { target ->
        if (target == null) {
            SpacesOverview(
                uiState = uiState,
                timeFormat = timeFormat,
                onOpenToSort = onOpenToSort,
                onOpenToday = onOpenToday,
                onCreate = { showCreateDialog = true },
                onSelect = { onSpaceSelected(it.id) },
                onSelectUnfiled = onUnfiledSelected,
                onEdit = { editingSpace = it },
                onHide = onHideSpace,
                onArchive = onArchiveSpace,
                onRestore = onRestoreSpace,
                onMove = onMoveSpace,
                onOpenSearch = onOpenSearch,
            )
        } else {
            SpaceDetail(
                space = target.space,
                contents = uiState.selectedContents,
                timeFormat = timeFormat,
                onBack = { onSpaceSelected(null) },
                onMoveItem = { itemToMove = it },
                onToggleDone = onToggleDone,
                onUndoMove = onUndoMove,
                hasUndoMove = uiState.moveUndo != null,
                hasMoveFailure = uiState.moveFailure != null,
                onRetryMove = onRetryMove,
                onItemSelected = onItemSelected,
            )
        }
    }

    if (showCreateDialog) {
        SpaceEditorDialog(
            space = null,
            existingSpaces = uiState.spaces,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, icon, accent ->
                onCreateSpace(name, icon, accent)
                showCreateDialog = false
            },
            onRestoreExisting = { spaceId ->
                onRestoreSpace(spaceId)
                showCreateDialog = false
            },
        )
    }

    editingSpace?.let { space ->
        SpaceEditorDialog(
            space = space,
            existingSpaces = uiState.spaces,
            onDismiss = { editingSpace = null },
            onConfirm = { name, icon, accent ->
                onUpdateSpace(space.id, name, icon, accent)
                editingSpace = null
            },
            onRestoreExisting = null,
        )
    }

    itemToMove?.let { item ->
        MoveItemDialog(
            spaces = moveTargets,
            onDismiss = { itemToMove = null },
            onMove = { targetId ->
                onMoveItem(item, targetId)
                itemToMove = null
            },
        )
    }
}

internal fun shouldHandleSpaceDetailBack(selectedSpace: SpaceEntity?): Boolean = selectedSpace != null

@Composable
private fun SpacesOverview(
    uiState: SpacesUiState,
    timeFormat: OrbitTimeFormat,
    onOpenToSort: () -> Unit,
    onOpenToday: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (SpaceEntity) -> Unit,
    onSelectUnfiled: () -> Unit,
    onEdit: (SpaceEntity) -> Unit,
    onHide: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val visible = uiState.visibleSpaces
    var hiddenExpanded by rememberSaveable { mutableStateOf(false) }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val measuredHeaderClearance = with(LocalDensity.current) {
        headerHeightPx.toDp() + 16.dp
    }
    val headerClearance = maxOf(statusTopPadding + 84.dp, measuredHeaderClearance)
    val now = remember(uiState) { System.currentTimeMillis() }
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .orbitScrollEdgeFade(
                    top = headerClearance,
                    bottom = OrbitBottomNavigationDefaults.ContentClearance,
                ),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = headerClearance,
                end = 20.dp,
                bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
            ),
        ) {
            item(key = "tiles") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountTile(
                        icon = Icons.Rounded.Inbox,
                        color = MaterialTheme.colorScheme.primary,
                        count = uiState.toSortCount,
                        label = stringResource(R.string.review_to_sort_title),
                        onClick = onOpenToSort,
                        modifier = Modifier.weight(1f),
                    )
                    CountTile(
                        icon = Icons.Rounded.WbSunny,
                        color = TodayTileColor,
                        count = uiState.todayCount,
                        label = stringResource(R.string.core_today),
                        onClick = onOpenToday,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "yours_label") {
                SectionHeader(
                    title = stringResource(R.string.spaces_section_yours),
                    modifier = Modifier.padding(top = 26.dp),
                )
            }
            if (visible.isEmpty() && !uiState.hasUnfiledItems) {
                item(key = "empty") { EmptySpacesCard(onCreate) }
            } else {
                item(key = "spaces") {
                    GroupedCard {
                        visible.forEachIndexed { index, space ->
                            if (index > 0) GroupDivider(startInset = 64.dp)
                            SpaceRow(
                                space = space,
                                openCount = uiState.openCounts[space.id] ?: 0,
                                itemCount = uiState.itemCounts[space.id] ?: 0,
                                nextItem = uiState.nextItems[space.id],
                                now = now,
                                timeFormat = timeFormat,
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
                        if (uiState.hasUnfiledItems) {
                            if (visible.isNotEmpty()) GroupDivider(startInset = 64.dp)
                            UnfiledRow(onClick = onSelectUnfiled)
                        }
                    }
                }
            }
            item(key = "new_space") {
                TextButton(
                    onClick = onCreate,
                    modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.spaces_new_space), style = MaterialTheme.typography.labelLarge)
                }
            }
            val inactive = uiState.archivedSpaces + uiState.hiddenSpaces
            if (inactive.isNotEmpty()) {
                item(key = "inactive") {
                    InactiveSpacesSection(
                        spaces = inactive,
                        expanded = hiddenExpanded,
                        onToggle = { hiddenExpanded = !hiddenExpanded },
                        onRestore = onRestore,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(start = 20.dp, top = statusTopPadding + 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.core_spaces_title),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = onOpenSearch) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.core_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Amber for the Today tile: the only warm colour on the screen, so "today" is found at a glance. */
private val TodayTileColor = Color(0xFFC98A1B)

/** A fixed bucket above the list: an icon, a large count and a name. */
@Composable
private fun CountTile(
    icon: ImageVector,
    color: Color,
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftGlassSurface(
        onClick = onClick,
        modifier = modifier,
        shape = GroupedListShape,
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TintedIconChip(icon = icon, color = color, size = 34.dp)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = label,
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpaceRow(
    space: SpaceEntity,
    openCount: Int,
    itemCount: Int,
    nextItem: SpaceNextItem?,
    now: Long,
    timeFormat: OrbitTimeFormat,
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
    val optionsLabel = stringResource(R.string.core_spaces_options)
    val subtitle = when {
        nextItem != null && nextItem.isEarlier -> stringResource(R.string.spaces_next_earlier, nextItem.title)
        nextItem != null -> stringResource(
            R.string.spaces_next_line,
            whenLabel(nextItem.at, nextItem.hasTime, now, timeFormat),
            nextItem.title,
        )
        itemCount > 0 -> pluralStringResource(R.plurals.core_spaces_item_count, itemCount, itemCount)
        else -> stringResource(R.string.spaces_nothing_yet)
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = optionsLabel,
                )
                .heightIn(min = 64.dp)
                .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpaceIcon(space)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp),
            ) {
                Text(
                    text = localizedSpaceName(space.name),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (openCount > 0) {
                Text(
                    text = openCount.toString(),
                    modifier = Modifier.padding(end = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowChevron()
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.core_edit)) },
                leadingIcon = { Icon(space.icon.asImageVector(), contentDescription = null) },
                onClick = { menuExpanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.core_spaces_move_up)) },
                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                enabled = canMoveUp,
                onClick = { menuExpanded = false; onMoveUp() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.core_spaces_move_down)) },
                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                enabled = canMoveDown,
                onClick = { menuExpanded = false; onMoveDown() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.core_hide)) },
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                onClick = { menuExpanded = false; onHide() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.core_archive)) },
                leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                onClick = { menuExpanded = false; onArchive() },
            )
        }
    }
}

@Composable
private fun RowChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun UnfiledRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIconChip(icon = Icons.Rounded.Folder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = stringResource(R.string.core_spaces_unfiled),
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        RowChevron()
    }
}

/** Hidden and archived Spaces, folded behind one quiet line. */
@Composable
private fun InactiveSpacesSection(
    spaces: List<SpaceEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRestore: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.core_spaces_hide_hidden)
                } else {
                    pluralStringResource(R.plurals.core_spaces_hidden_count, spaces.size, spaces.size)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(OrbitMotion.StandardDurationMillis)) +
                expandVertically(tween(OrbitMotion.EmphasizedDurationMillis)),
            exit = fadeOut(tween(OrbitMotion.QuickDurationMillis)) +
                shrinkVertically(tween(OrbitMotion.StandardDurationMillis)),
        ) {
            GroupedCard {
                spaces.forEachIndexed { index, space ->
                    if (index > 0) GroupDivider(startInset = 64.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .padding(start = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpaceIcon(space)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                        ) {
                            Text(
                                text = localizedSpaceName(space.name),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(
                                    if (space.archived) R.string.core_spaces_archived else R.string.core_spaces_hidden,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRestore(space.id) }) { Text(stringResource(R.string.core_restore)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySpacesCard(onCreate: () -> Unit) {
    GroupedCard {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.core_spaces_none_visible),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.core_spaces_none_visible_subtitle),
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreate) { Text(stringResource(R.string.core_spaces_create)) }
        }
    }
}

/**
 * When something is due, said the short way: "Today 11:00", "Tomorrow", "Tue 09:00",
 * or a date further out. The time comes first so it never gets cut off.
 */
@Composable
internal fun whenLabel(at: Long, hasTime: Boolean, now: Long, timeFormat: OrbitTimeFormat): String {
    val zone = java.time.ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = if (hasTime) timeFormat.formatTime(at) else null
    val dayText = when {
        day == today -> stringResource(R.string.core_today)
        day == today.plusDays(1) -> stringResource(R.string.core_tomorrow)
        day.isAfter(today) && day.isBefore(today.plusDays(7)) ->
            day.format(java.time.format.DateTimeFormatter.ofPattern("EEE", locale))
        else -> timeFormat.formatDate(at)
    }
    return if (time != null) "$dayText $time" else dayText
}

@Composable
private fun SpaceDetail(
    space: SpaceEntity?,
    contents: SpaceContents,
    timeFormat: OrbitTimeFormat,
    onBack: () -> Unit,
    onMoveItem: (SpaceItemReference) -> Unit,
    onToggleDone: (SpaceItemReference) -> Unit,
    onUndoMove: () -> Unit,
    hasUndoMove: Boolean,
    hasMoveFailure: Boolean,
    onRetryMove: () -> Unit,
    onItemSelected: (SpaceItemReference) -> Unit,
) {
    // Items ticked on this visit stay where they were (shown as done), so the list does not
    // jump under the finger; they move to "done" the next time the Space opens.
    val keepInPlace = remember { mutableStateListOf<SpaceItemReference>() }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val now = remember(contents) { System.currentTimeMillis() }
    val agenda = remember(contents, keepInPlace.toList()) { contents.agenda(now, keepInPlace = keepInPlace.toSet()) }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val measuredHeaderClearance = with(LocalDensity.current) {
        headerHeightPx.toDp() + 8.dp
    }
    val headerClearance = maxOf(statusTopPadding + 140.dp, measuredHeaderClearance)
    val toggle: (SpaceItemReference) -> Unit = { reference ->
        if (reference !in keepInPlace) keepInPlace.add(reference)
        onToggleDone(reference)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .orbitScrollEdgeFade(
                    top = headerClearance,
                    bottom = OrbitBottomNavigationDefaults.ContentClearance,
                ),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = headerClearance,
                end = 20.dp,
                bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
            ),
        ) {
            if (contents.size == 0) {
                item(key = "quiet") {
                    GroupedCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.core_spaces_quiet_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.core_spaces_quiet_subtitle),
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (hasUndoMove || hasMoveFailure) {
                item(key = "move_feedback") {
                    TextButton(
                        onClick = if (hasMoveFailure) onRetryMove else onUndoMove,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            stringResource(if (hasMoveFailure) R.string.core_spaces_move_retry else R.string.core_spaces_undo_move),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            val sections = listOf(
                R.string.core_today to agenda.today,
                R.string.spaces_section_earlier to agenda.earlier,
                R.string.core_spaces_upcoming to agenda.upcoming,
                R.string.spaces_section_no_date to agenda.noDate,
            )
            sections.forEachIndexed { sectionIndex, (heading, rows) ->
                if (rows.isEmpty()) return@forEachIndexed
                item(key = "section_$sectionIndex") {
                    Column(modifier = Modifier.padding(bottom = 20.dp)) {
                        SectionHeader(title = stringResource(heading))
                        GroupedCard {
                            rows.forEachIndexed { index, row ->
                                if (index > 0) GroupDivider(startInset = 56.dp)
                                AgendaRow(
                                    item = row,
                                    trailing = agendaTrailing(row, sectionIndex, now, timeFormat),
                                    onOpen = { onItemSelected(row.reference) },
                                    onToggle = { toggle(row.reference) },
                                    onMove = { onMoveItem(row.reference) },
                                )
                            }
                        }
                    }
                }
            }
            if (agenda.notes.isNotEmpty()) {
                item(key = "notes") {
                    Column(modifier = Modifier.padding(bottom = 20.dp)) {
                        SectionHeader(title = stringResource(R.string.spaces_section_notes))
                        GroupedCard {
                            agenda.notes.forEachIndexed { index, note ->
                                if (index > 0) GroupDivider()
                                NoteRow(
                                    note = note,
                                    onOpen = { onItemSelected(SpaceItemReference(SpaceItemType.Note, note.id)) },
                                    onMove = { onMoveItem(SpaceItemReference(SpaceItemType.Note, note.id)) },
                                )
                            }
                        }
                    }
                }
            }
            if (agenda.done.isNotEmpty()) {
                item(key = "done_toggle") {
                    TextButton(
                        onClick = { showDone = !showDone },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = if (showDone) {
                                stringResource(R.string.spaces_done_hide)
                            } else {
                                stringResource(R.string.spaces_done_show, agenda.done.size)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showDone) {
                    item(key = "done_list") {
                        GroupedCard {
                            agenda.done.forEachIndexed { index, row ->
                                if (index > 0) GroupDivider(startInset = 56.dp)
                                AgendaRow(
                                    item = row,
                                    trailing = null,
                                    onOpen = { onItemSelected(row.reference) },
                                    onToggle = { toggle(row.reference) },
                                    onMove = { onMoveItem(row.reference) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .onSizeChanged { headerHeightPx = it.height }
                .padding(start = 8.dp, top = statusTopPadding + 12.dp, end = 20.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.core_spaces_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (space != null) {
                    SpaceIcon(space, size = 44.dp, iconSize = 24.dp)
                } else {
                    TintedIconChip(
                        icon = Icons.Rounded.Folder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 44.dp,
                        iconSize = 24.dp,
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = if (space != null) {
                            localizedSpaceName(space.name)
                        } else {
                            stringResource(R.string.core_spaces_unfiled)
                        },
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val openCount = agenda.today.size + agenda.earlier.size + agenda.upcoming.size + agenda.noDate.size -
                        (agenda.today + agenda.earlier + agenda.upcoming + agenda.noDate).count { it.isDone }
                    Text(
                        text = listOfNotNull(
                            stringResource(R.string.spaces_detail_open_count, openCount),
                            agenda.notes.size.takeIf { it > 0 }?.let {
                                pluralStringResource(R.plurals.spaces_detail_note_count, it, it)
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Today shows the time; earlier shows the day it was due; upcoming shows when. */
@Composable
private fun agendaTrailing(item: SpaceAgendaItem, sectionIndex: Int, now: Long, timeFormat: OrbitTimeFormat): String? {
    val at = item.at ?: return null
    return when (sectionIndex) {
        0 -> if (item.hasTime) timeFormat.formatTime(at) else stringResource(R.string.core_calendar_any_time)
        1 -> timeFormat.formatDate(at)
        else -> whenLabel(at, item.hasTime, now, timeFormat)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgendaRow(
    item: SpaceAgendaItem,
    trailing: String?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onMove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val moveLabel = stringResource(R.string.core_spaces_move_item)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = moveLabel,
                )
                .heightIn(min = 56.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.isReminder && !item.isDone) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = stringResource(R.string.core_reminder),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (item.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = stringResource(
                            if (item.isDone) R.string.spaces_mark_not_done else R.string.spaces_mark_done,
                            item.title,
                        ),
                        tint = if (item.isDone) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                    )
                }
            }
            Text(
                text = item.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(moveLabel) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                onClick = { menuExpanded = false; onMove() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: com.orbit.app.data.local.entity.NoteEntity,
    onOpen: () -> Unit,
    onMove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val moveLabel = stringResource(R.string.core_spaces_move_item)
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = moveLabel,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = note.title.ifBlank { stringResource(R.string.core_untitled_note) },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(moveLabel) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                onClick = { menuExpanded = false; onMove() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceEditorDialog(
    space: SpaceEntity?,
    existingSpaces: List<SpaceEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onRestoreExisting: ((Long) -> Unit)?,
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
        ModalSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.ExtraLarge)
                .calmPressHaptics(),
            shape = OrbitModalDefaults.DialogShape,
        ) {
            Column(modifier = Modifier.padding(OrbitSpacing.ExtraLarge)) {
                Text(
                    text = stringResource(
                        if (space == null) R.string.core_spaces_create else R.string.core_spaces_edit,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val conflict = SpaceNames.conflict(name, existingSpaces, excludingSpaceId = space?.id)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.core_name)) },
                        isError = conflict != null,
                        supportingText = conflict?.let { existing ->
                            {
                                Text(
                                    text = stringResource(
                                        when {
                                            existing.archived -> R.string.spaces_name_taken_archived
                                            existing.hidden -> R.string.spaces_name_taken_hidden
                                            else -> R.string.spaces_name_taken
                                        },
                                        existing.name,
                                    ),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        },
                    )
                    if (conflict != null && (conflict.archived || conflict.hidden) && onRestoreExisting != null) {
                        TextButton(
                            onClick = { onRestoreExisting(conflict.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.core_restore))
                        }
                    }
                    Text(
                        text = stringResource(R.string.core_icon),
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
                                        contentDescription = stringResource(choice.iconContentDescriptionRes()),
                                        modifier = Modifier.size(21.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.core_accent),
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Medium),
                    ) {
                        accentChoices.chunked(4).forEach { rowChoices ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    OrbitSpacing.Medium,
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
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_cancel)) }
                    TextButton(
                        onClick = { onConfirm(name, icon, accent) },
                        enabled = name.isNotBlank() &&
                            SpaceNames.conflict(name, existingSpaces, excludingSpaceId = space?.id) == null,
                    ) {
                        Text(stringResource(if (space == null) R.string.core_create else R.string.core_save))
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
    onMove: (Long?) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ModalSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrbitSpacing.ExtraLarge)
                .calmPressHaptics(),
            shape = OrbitModalDefaults.DialogShape,
        ) {
            Column(modifier = Modifier.padding(OrbitSpacing.ExtraLarge)) {
                Text(
                    text = stringResource(R.string.core_spaces_move_to),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (spaces.isEmpty()) {
                    Text(
                        text = stringResource(R.string.core_spaces_move_empty),
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
                                    modifier = Modifier.padding(OrbitSpacing.Medium),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SpaceIcon(space, modifier = Modifier.size(34.dp))
                                    Text(
                                        localizedSpaceName(space.name),
                                        modifier = Modifier.padding(start = OrbitSpacing.Medium),
                                    )
                                }
                            }
                        }
                    }
                }
                SoftGlassSurface(
                    onClick = { onMove(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.core_spaces_unfiled),
                        modifier = Modifier.padding(OrbitSpacing.Medium),
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 14.dp),
                ) {
                    Text(stringResource(R.string.core_cancel))
                }
            }
        }
    }
}

@Composable
private fun SpaceIcon(
    space: SpaceEntity,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    TintedIconChip(
        icon = space.icon.asImageVector(),
        color = space.colorAccent.asColor(),
        modifier = modifier,
        size = size,
        iconSize = iconSize,
    )
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

private fun String.iconContentDescriptionRes(): Int = when (this) {
    "work" -> R.string.core_spaces_icon_work
    "person" -> R.string.core_spaces_icon_person
    "directions_car" -> R.string.core_spaces_icon_transport
    "pets" -> R.string.core_spaces_icon_pets
    "payments" -> R.string.core_spaces_icon_payments
    "lightbulb" -> R.string.core_spaces_icon_ideas
    "home" -> R.string.core_spaces_icon_home
    "favorite" -> R.string.core_spaces_icon_favorite
    "school" -> R.string.core_spaces_icon_school
    "palette" -> R.string.core_spaces_icon_palette
    else -> R.string.core_spaces_icon_folder
}

private fun String.asColor(): Color = runCatching {
    Color(toColorInt())
}.getOrElse { Color(0xFF6D7CFF) }

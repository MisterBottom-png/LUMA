package com.orbit.app.ui.screens.spaces

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.R
import com.orbit.app.ui.components.ModalSurface
import com.orbit.app.ui.components.OrbitModalDefaults
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.ui.components.orbitScrollEdgeFade
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import com.orbit.app.ui.theme.OrbitShapes
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

internal data class SpaceFeedItem(
    val reference: SpaceItemReference,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
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
    BackHandler(enabled = shouldHandleSpaceDetailBack(selectedSpace)) {
        onSpaceSelected(null)
    }
    val moveTargets = remember(uiState.visibleSpaces, selectedSpace?.id) {
        uiState.visibleSpaces.filterNot { it.id == selectedSpace?.id }
    }
    AnimatedContent(
        targetState = selectedSpace,
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
        contentKey = { it?.id },
        label = "Spaces pane",
    ) { space ->
        if (space == null) {
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
                space = space,
                contents = uiState.selectedContents,
                timeFormat = timeFormat,
                onBack = { onSpaceSelected(null) },
                onMoveItem = { itemToMove = it },
                onItemSelected = onItemSelected,
            )
        }
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
    var hiddenExpanded by rememberSaveable { mutableStateOf(false) }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val measuredHeaderClearance = with(LocalDensity.current) {
        headerHeightPx.toDp() + 20.dp
    }
    val headerClearance = maxOf(statusTopPadding + 128.dp, measuredHeaderClearance)
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .orbitScrollEdgeFade(
                    top = headerClearance,
                    bottom = OrbitBottomNavigationDefaults.ContentClearance,
                ),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = headerClearance,
                end = 24.dp,
                bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                            placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                            fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                        ),
                    )
                }
            }

            if (uiState.archivedSpaces.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.core_spaces_archived),
                        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(
                    uiState.archivedSpaces.size,
                    key = { "inactive_${uiState.archivedSpaces[it].id}" },
                ) { index ->
                    InactiveSpaceRow(
                        space = uiState.archivedSpaces[index],
                        onRestore = onRestore,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                            placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                            fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                        ),
                    )
                }
            }

            if (uiState.hiddenSpaces.isNotEmpty()) {
                item(key = "hidden_disclosure") {
                    HiddenSpacesDisclosure(
                        count = uiState.hiddenSpaces.size,
                        expanded = hiddenExpanded,
                        onToggle = { hiddenExpanded = !hiddenExpanded },
                        spaces = uiState.hiddenSpaces,
                        onRestore = onRestore,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                            placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                            fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                        ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(start = 24.dp, top = statusTopPadding + 26.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.core_spaces_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.core_spaces_subtitle),
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = onOpenSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.core_search))
                }
                FilledTonalIconButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.core_spaces_create))
                }
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
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    SoftGlassSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
                    text = localizedSpaceName(space.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.core_spaces_item_count, itemCount, itemCount),
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.core_spaces_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.calmPressHaptics(),
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
    }
}

@Composable
private fun InactiveSpaceRow(
    space: SpaceEntity,
    onRestore: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftGlassSurface(
        modifier = modifier.fillMaxWidth(),
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
                    text = localizedSpaceName(space.name),
                    style = MaterialTheme.typography.titleSmall,
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

@Composable
private fun HiddenSpacesDisclosure(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    spaces: List<SpaceEntity>,
    onRestore: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Disclosure toggle row
        SoftGlassSurface(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (expanded) 0.dp else 20.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (expanded) {
                        stringResource(R.string.core_spaces_hide_hidden)
                    } else {
                        pluralStringResource(R.plurals.core_spaces_hidden_count, count, count)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = stringResource(
                        if (expanded) R.string.core_collapse else R.string.core_expand,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Expandable list of hidden spaces
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(OrbitMotion.StandardDurationMillis)) +
                expandVertically(tween(OrbitMotion.EmphasizedDurationMillis)),
            exit = fadeOut(tween(OrbitMotion.QuickDurationMillis)) +
                shrinkVertically(tween(OrbitMotion.StandardDurationMillis)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                spaces.forEach { space ->
                    InactiveSpaceRow(
                        space = space,
                        onRestore = onRestore,
                    )
                }
            }
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
                text = stringResource(R.string.core_spaces_none_visible),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.core_spaces_none_visible_subtitle),
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreate) { Text(stringResource(R.string.core_spaces_create)) }
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
    val feedPresentation = SpaceFeedPresentation(
        note = stringResource(R.string.core_spaces_feed_note),
        untitledNote = stringResource(R.string.core_untitled_note),
        task = stringResource(R.string.core_spaces_feed_task),
        taskDone = stringResource(R.string.core_spaces_feed_task_done),
        archived = stringResource(R.string.core_spaces_feed_archived),
        waitingFor = stringResource(R.string.core_spaces_feed_waiting_for),
        someday = stringResource(R.string.core_spaces_feed_someday),
        reminder = stringResource(R.string.core_spaces_feed_reminder, "%s"),
        subtitle = stringResource(R.string.core_spaces_feed_subtitle, "%1\$s", "%2\$s"),
    )
    val feed = remember(contents, timeFormat, feedPresentation) {
        contents.asFeedItems(timeFormat, feedPresentation)
    }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val measuredHeaderClearance = with(LocalDensity.current) {
        headerHeightPx.toDp() + 20.dp
    }
    val headerClearance = maxOf(statusTopPadding + 152.dp, measuredHeaderClearance)

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .orbitScrollEdgeFade(
                    top = headerClearance,
                    bottom = OrbitBottomNavigationDefaults.ContentClearance,
                ),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = headerClearance,
                end = 24.dp,
                bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

        if (contents.size == 0) {
            item {
                SoftGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.core_spaces_quiet_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.core_spaces_quiet_subtitle),
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (feed.isNotEmpty()) {
            item(key = "life_feed_heading") {
                Text(
                    text = stringResource(R.string.core_spaces_life_feed),
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
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                        placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                        fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                    ),
                )
            }
        }
        }

        Column(
            modifier = Modifier
                .onSizeChanged { headerHeightPx = it.height }
                .padding(start = 24.dp, top = statusTopPadding + 20.dp, end = 24.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.core_spaces_back),
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
                        text = localizedSpaceName(space.name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.core_spaces_item_count,
                            contents.size,
                            contents.size,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal data class SpaceFeedPresentation(
    val note: String = "Note",
    val untitledNote: String = "Untitled note",
    val task: String = "Task",
    val taskDone: String = "Task · Done",
    val archived: String = "Archived",
    val waitingFor: String = "Waiting for",
    val someday: String = "Someday",
    val reminder: String = "Reminder · %s",
    val subtitle: String = "%s · %s",
)

internal fun SpaceContents.asFeedItems(
    timeFormat: OrbitTimeFormat,
    presentation: SpaceFeedPresentation = SpaceFeedPresentation(),
): List<SpaceFeedItem> = buildList {
    notes.mapTo(this) { note ->
        SpaceFeedItem(
            reference = SpaceItemReference(SpaceItemType.Note, note.id),
            icon = Icons.Rounded.Description,
            title = note.title.ifBlank { presentation.untitledNote },
            subtitle = presentation.note,
            timestamp = note.updatedAt,
        )
    }
    tasks.mapTo(this) { task ->
        SpaceFeedItem(
            reference = SpaceItemReference(SpaceItemType.Task, task.id),
            icon = task.status.feedIcon(),
            title = task.title,
            subtitle = feedSubtitle(task.status.feedLabel(presentation), task.notes, presentation),
            timestamp = task.updatedAt,
        )
    }
    reminders.mapTo(this) { reminder ->
        SpaceFeedItem(
            reference = SpaceItemReference(SpaceItemType.Reminder, reminder.id),
            icon = Icons.Rounded.Notifications,
            title = reminder.title,
            subtitle = feedSubtitle(
                presentation.reminder.format(timeFormat.formatShortDateTime(reminder.dueAt)),
                reminder.notes,
                presentation,
            ),
            timestamp = reminder.dueAt,
        )
    }
}.sortedByDescending { it.timestamp }

private fun feedSubtitle(
    badge: String,
    preview: String,
    presentation: SpaceFeedPresentation,
): String = if (preview.isBlank()) badge else presentation.subtitle.format(badge, preview)

private fun com.orbit.app.data.local.entity.TaskStatus.feedLabel(
    presentation: SpaceFeedPresentation,
): String = when (this) {
    com.orbit.app.data.local.entity.TaskStatus.Open -> presentation.task
    com.orbit.app.data.local.entity.TaskStatus.Done -> presentation.taskDone
    com.orbit.app.data.local.entity.TaskStatus.Archived -> presentation.archived
    com.orbit.app.data.local.entity.TaskStatus.WaitingFor -> presentation.waitingFor
    com.orbit.app.data.local.entity.TaskStatus.Someday -> presentation.someday
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
    modifier: Modifier = Modifier,
) {
    SoftGlassSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
                    contentDescription = stringResource(R.string.core_spaces_move_item),
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
    modifier: Modifier = Modifier,
) {
    SpaceItemRow(
        icon = item.icon,
        title = item.title,
        subtitle = item.subtitle,
        onClick = onClick,
        onMove = onMove,
        modifier = modifier,
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.core_name)) },
                    )
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
                        enabled = name.isNotBlank(),
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
    onMove: (Long) -> Unit,
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

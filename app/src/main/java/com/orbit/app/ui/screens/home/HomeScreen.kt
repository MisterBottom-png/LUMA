package com.orbit.app.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeCaptureViewModel,
    weekUiState: HomeWeekUiState,
    calendarDateContext: LocalDate?,
    onCalendarDateContextConsumed: () -> Unit,
    onCalendarDateSelected: (LocalDate) -> Unit,
    userName: String,
    timeFormat: OrbitTimeFormat,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val imeVisible = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this) > 0
    }
    val motionSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val bottomContentPadding by animateDpAsState(
        targetValue = if (imeVisible) {
            16.dp
        } else {
            OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding
        },
        animationSpec = motionSpec,
        label = "homeBottomContentPadding",
    )
    val headerAlpha by animateFloatAsState(
        targetValue = if (imeVisible) 0.78f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "homeHeaderAlpha",
    )
    val headerOffsetY by animateDpAsState(
        targetValue = if (imeVisible) (-8).dp else 0.dp,
        animationSpec = motionSpec,
        label = "homeHeaderOffsetY",
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onNotificationPermissionResult,
    )

    LaunchedEffect(uiState.notificationPermissionRequestPending) {
        if (!uiState.notificationPermissionRequestPending) return@LaunchedEffect
        viewModel.notificationPermissionRequestStarted()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onNotificationPermissionResult(granted = true)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    LaunchedEffect(calendarDateContext, uiState.suggestion) {
        val contextEpochDay = calendarDateContext?.toEpochDay()
        if (
            contextEpochDay != null &&
            uiState.suggestion?.calendarDateContextEpochDay == contextEpochDay
        ) {
            onCalendarDateContextConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val headerTranslationY = with(LocalDensity.current) { headerOffsetY.toPx() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 26.dp, bottom = bottomContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = headerAlpha
                    translationY = headerTranslationY
                },
            ) {
                Text(
                    text = greetingFor(LocalTime.now().hour),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = userName.ifBlank { "Vitautas" },
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(24.dp))
                WeekStrip(
                    uiState = weekUiState,
                    onDateSelected = onCalendarDateSelected,
                )

                calendarDateContext?.let { date ->
                    CalendarCaptureContextBanner(
                        date = date,
                        onClear = onCalendarDateContextConsumed,
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val captureCardHeight by animateDpAsState(
                    targetValue = captureCardHeightFor(
                        text = uiState.inputText,
                        imeVisible = imeVisible,
                        availableHeight = maxHeight,
                    ),
                    animationSpec = motionSpec,
                    label = "captureCardHeight",
                )
                CaptureCard(
                    text = uiState.inputText,
                    isAnalyzing = uiState.isAnalyzing,
                    onTextChanged = viewModel::onInputChanged,
                    onAnalyze = {
                        viewModel.analyzeCapture(calendarDateContext?.toEpochDay())
                    },
                    height = captureCardHeight,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = bottomContentPadding),
        )
    }

    uiState.suggestion?.let { suggestion ->
        CaptureSuggestionSheet(
            suggestion = suggestion,
            timeFormat = timeFormat,
            brainDumpHandledItemIds = uiState.brainDumpHandledItemIds,
            mondayConfigured = uiState.mondayConfigured,
            isPerformingAction = uiState.isPerformingAction,
            onSaveNote = viewModel::saveNote,
            onCreateTask = viewModel::createTask,
            onCreateReminder = viewModel::createReminder,
            onSaveBrainDumpItem = viewModel::saveBrainDumpItem,
            onKeepBrainDumpItemInInbox = viewModel::keepBrainDumpItemInInbox,
            onSkipBrainDumpItem = viewModel::skipBrainDumpItem,
            onKeepInInbox = viewModel::keepInInbox,
            onCancel = viewModel::cancelSuggestion,
        )
    }
}

@Composable
private fun CalendarCaptureContextBanner(
    date: LocalDate,
    onClear: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f))
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Adding for ${date.format(formatter)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TextButton(onClick = onClear) {
            Text("Clear")
        }
    }
}

@Composable
private fun WeekStrip(
    uiState: HomeWeekUiState,
    onDateSelected: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(uiState.today, locale) { homeWeekDates(uiState.today, locale) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dates.forEach { date ->
            val isToday = date == uiState.today
            val isSelected = date == uiState.selectedDate
            val hasItems = date in uiState.datesWithItems
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                },
                animationSpec = tween(durationMillis = 140),
                label = "homeWeekSelection",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(CircleShape)
                    .then(
                        if (isToday) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.56f),
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .background(backgroundColor)
                    .clickable { onDateSelected(date) }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        selected = isSelected
                        contentDescription = homeDateContentDescription(
                            date = date,
                            locale = locale,
                            isToday = isToday,
                            isSelected = isSelected,
                            hasItems = hasItems,
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = homeWeekdayLabel(date, locale),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday || isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            color = if (hasItems) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(
    text: String,
    isAnalyzing: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier
            .height(height),
        shape = RoundedCornerShape(30.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
        ) {
            CaptureTextField(
                text = text,
                onTextChanged = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(modifier = Modifier.height(CaptureTextActionGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CaptureActionButton(
                    contentDescription = "Save and analyze capture",
                    emphasized = true,
                    enabled = text.isNotBlank() && !isAnalyzing,
                    onClick = onAnalyze,
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureTextField(
    text: String,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = text,
        onValueChange = onTextChanged,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (text.isEmpty()) {
                    Text(
                        text = "Type anything...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CaptureActionButton(
    contentDescription: String,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(42.dp)
            .semantics { this.contentDescription = contentDescription }
            .background(
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f)
                },
                shape = CircleShape,
            ),
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides if (emphasized) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                },
            ) {
                Box(
                    modifier = Modifier,
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }
}

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun captureCardHeightFor(
    text: String,
    imeVisible: Boolean,
    availableHeight: Dp,
): Dp {
    if (text.isBlank()) {
        return (if (imeVisible) CaptureCardCompactHeight else CaptureCardExpandedHeight)
            .coerceAtMost(availableHeight)
    }

    val estimatedLines = estimatedCaptureLineCount(text)
    val desiredHeight = CaptureCardVerticalChromeHeight +
        (estimatedLines * CaptureEstimatedLineHeight).dp
    val preferredMinHeight = if (imeVisible) {
        CaptureCardTypingMinHeight
    } else {
        CaptureCardExpandedHeight
    }
    val preferredMaxHeight = if (imeVisible) {
        CaptureCardTypingMaxHeight
    } else {
        CaptureCardExpandedTextMaxHeight
    }
    val boundedMaxHeight = availableHeight.coerceAtMost(preferredMaxHeight)
    val boundedMinHeight = preferredMinHeight.coerceAtMost(boundedMaxHeight)
    return desiredHeight.coerceIn(boundedMinHeight, boundedMaxHeight)
}

private fun estimatedCaptureLineCount(text: String): Int = text
    .lineSequence()
    .sumOf { line ->
        maxOf(
            1,
            (line.length + CaptureEstimatedCharactersPerLine - 1) / CaptureEstimatedCharactersPerLine,
        )
    }

private val CaptureCardExpandedHeight = 244.dp
private val CaptureCardCompactHeight = 132.dp
private val CaptureCardTypingMinHeight = 156.dp
private val CaptureCardTypingMaxHeight = 228.dp
private val CaptureCardExpandedTextMaxHeight = 392.dp
private val CaptureTextActionGap = 12.dp
private val CaptureCardVerticalChromeHeight = 22.dp + 22.dp + CaptureTextActionGap + 42.dp
private const val CaptureEstimatedCharactersPerLine = 34
private const val CaptureEstimatedLineHeight = 29

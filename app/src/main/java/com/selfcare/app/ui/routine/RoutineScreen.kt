package com.selfcare.app.ui.routine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.ui.components.AnimatedStreakFlamePill
import com.selfcare.app.ui.components.CelebrationOverlay
import com.selfcare.app.ui.components.ExpressiveMotion
import com.selfcare.app.ui.components.HeroScoreRing
import com.selfcare.app.ui.components.expressiveSpringSize
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.theme.NeonGreen
import com.selfcare.app.ui.theme.NeonWarning

@Composable
fun RoutineScreen(
    viewModel: RoutineViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var celebrationTrigger by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        RoutineScreenContent(
            state = state,
            onSelectPeriod = viewModel::selectPeriod,
            onCompleteRoutine = {
                viewModel.completeCurrentRoutine()
                celebrationTrigger++
            },
            onToggleProduct = viewModel::toggleProductOverride,
            onRenameProduct = viewModel::renameProduct,
            onMoveProduct = viewModel::moveProductStep,
            onQuickAddWater250 = viewModel::quickAddWater250,
            onLogShaveToday = viewModel::logShaveToday,
            onToggleCustomHabit = viewModel::toggleCustomHabit,
            onAddCustomHabit = viewModel::addCustomHabit,
            modifier = Modifier.fillMaxSize()
        )
        CelebrationOverlay(triggerKey = celebrationTrigger)
    }
}

@Composable
fun RoutineScreenContent(
    state: RoutineUiState,
    onSelectPeriod: (String) -> Unit,
    onCompleteRoutine: () -> Unit,
    onToggleProduct: (Long) -> Unit,
    onRenameProduct: (Long, String) -> Unit = { _, _ -> },
    onMoveProduct: (Long, Int) -> Unit = { _, _ -> },
    onQuickAddWater250: () -> Unit = {},
    onLogShaveToday: () -> Unit = {},
    onToggleCustomHabit: (String, String) -> Unit = { _, _ -> },
    onAddCustomHabit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val period = state.selectedPeriod
    val haptics = LocalHapticFeedback.current

    var renamingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var newHabitLabel by remember { mutableStateOf("") }

    renamingProduct?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingProduct = null },
            title = { Text(stringResource(R.string.routine_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.routine_rename_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("routine_rename_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = renameText.trim()
                        if (clean.isNotEmpty()) {
                            onRenameProduct(target.id, clean)
                        }
                        renamingProduct = null
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingProduct = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showAddHabitDialog) {
        AlertDialog(
            onDismissRequest = { showAddHabitDialog = false },
            title = { Text(stringResource(R.string.routine_add_habit_title)) },
            text = {
                OutlinedTextField(
                    value = newHabitLabel,
                    onValueChange = { newHabitLabel = it },
                    placeholder = { Text(stringResource(R.string.routine_add_habit_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_add_habit_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newHabitLabel.trim()
                        if (clean.isNotEmpty()) {
                            onAddCustomHabit(clean)
                            newHabitLabel = ""
                        }
                        showAddHabitDialog = false
                    },
                    enabled = newHabitLabel.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHabitDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val totalSteps = state.defaultProducts.size
    val activeSteps = state.defaultProducts.count { it.id !in state.skippedProductIds }
    val completionFraction = when {
        totalSteps > 0 -> activeSteps.toFloat() / totalSteps.toFloat()
        state.isCompletedToday -> 1f
        else -> 0f
    }
    val completionPercent = (completionFraction * 100f).toInt()

    val completeBtnTag = when (period) {
        RoutineRepository.PERIOD_MORNING -> "routine_complete_morning_btn"
        RoutineRepository.PERIOD_EVENING -> "routine_complete_evening_btn"
        else -> "routine_complete_hair_btn"
    }
    val completeBtnLabelRes = when (period) {
        RoutineRepository.PERIOD_MORNING -> R.string.routine_complete_morning
        RoutineRepository.PERIOD_EVENING -> R.string.routine_complete_evening
        else -> R.string.routine_complete_hair
    }

    val cardPulse = remember { Animatable(1f) }
    LaunchedEffect(state.isCompletedToday) {
        if (state.isCompletedToday) {
            cardPulse.snapTo(0.97f)
            cardPulse.animateTo(1.02f, ExpressiveMotion.bouncySpring())
            cardPulse.animateTo(1.0f, ExpressiveMotion.spatialSpring())
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Unified Compact Hero Card (Centered 100% Ring + Streak + 3-Period Today Status + Action)
        item {
            val heroBorderColor by animateColorAsState(
                targetValue = if (state.isCompletedToday) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                animationSpec = tween(260),
                label = "hero_border"
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveSpringSize()
                    .graphicsLayer {
                        scaleX = cardPulse.value
                        scaleY = cardPulse.value
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, heroBorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HeroScoreRing(
                            progress = completionFraction,
                            centerValue = "$completionPercent%",
                            centerSubtitle = if (totalSteps > 0) {
                                "$activeSteps/$totalSteps"
                            } else if (state.isCompletedToday) {
                                stringResource(R.string.hygiene_completed_label)
                            } else {
                                "0/0"
                            },
                            ringSize = 84.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.routine_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (state.streakDays > 0) {
                                    AnimatedStreakFlamePill(
                                        streakDays = state.streakDays,
                                        labelSuffix = stringResource(R.string.streak_days_suffix),
                                        modifier = Modifier.testTag("routine_streak_pill")
                                    )
                                }
                            }
                            if (state.isCompletedToday) {
                                val timeSuffix = if (state.completedAtHm.isNotBlank()) " · ${state.completedAtHm}" else ""
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("routine_completed_badge")
                                ) {
                                    Text(
                                        text = stringResource(R.string.routine_completed_today) + timeSuffix,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.routine_not_completed_today),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // 3-period today overview dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PeriodStatusChip(
                                    label = stringResource(R.string.routine_morning),
                                    done = state.morningCompletedToday
                                )
                                PeriodStatusChip(
                                    label = stringResource(R.string.routine_evening),
                                    done = state.eveningCompletedToday
                                )
                                PeriodStatusChip(
                                    label = stringResource(R.string.routine_hair),
                                    done = state.hairCompletedToday
                                )
                            }
                        }
                    }

                    // Primary bright CTA only when NOT yet completed; subtle OutlinedButton when already done
                    if (state.isCompletedToday) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCompleteRoutine()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(completeBtnTag)
                        ) {
                            Icon(
                                imageVector = AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.routine_update_completed_btn))
                        }
                    } else {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCompleteRoutine()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(completeBtnTag)
                        ) {
                            Icon(
                                imageVector = AppIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(completeBtnLabelRes))
                        }
                    }
                }
            }
        }

        // 2. Today Pulse Mini-Bar (Calories, Water +250ml 1-tap, Shave countdown)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveSpringSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${state.todayCaloriesKcal} / ${state.calorieTargetKcal} kcal  ·  ${state.todayWaterMl} / ${state.waterTargetMl} ml",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            state.shaveDaysUntilDue?.let { daysLeft ->
                                val shaveText = when {
                                    daysLeft <= 0 -> stringResource(R.string.routine_shave_due_now)
                                    else -> stringResource(R.string.routine_shave_in_days, daysLeft)
                                }
                                Text(
                                    text = shaveText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (daysLeft <= 0) NeonWarning else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onQuickAddWater250()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+250 ml",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if ((state.shaveDaysUntilDue ?: 1) <= 0) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLogShaveToday()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.routine_shave_done_btn),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Period Selector (Morning / Evening / Hair Care)
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = period == RoutineRepository.PERIOD_MORNING,
                    onClick = { onSelectPeriod(RoutineRepository.PERIOD_MORNING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = {
                        Icon(
                            imageVector = AppIcons.Sun,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("routine_period_morning")
                ) {
                    Text(
                        text = stringResource(R.string.routine_morning),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = period == RoutineRepository.PERIOD_EVENING,
                    onClick = { onSelectPeriod(RoutineRepository.PERIOD_EVENING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = {
                        Icon(
                            imageVector = AppIcons.Moon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("routine_period_evening")
                ) {
                    Text(
                        text = stringResource(R.string.routine_evening),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = period == RoutineRepository.PERIOD_HAIR,
                    onClick = { onSelectPeriod(RoutineRepository.PERIOD_HAIR) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = {
                        Icon(
                            imageVector = AppIcons.Haircut,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("routine_period_hair")
                ) {
                    Text(
                        text = stringResource(R.string.routine_hair),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4. INCI Conflict Warning Banner for the selected period
        if (state.periodConflicts.isNotEmpty()) {
            item {
                AnimatedVisibility(visible = true) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveSpringSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.routine_inci_conflict_header),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            state.periodConflicts.take(2).forEach { conflict ->
                                Text(
                                    text = "• ${conflict.title}: ${conflict.recommendation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Ordered Skincare & Vitamin Steps Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.routine_default_products),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.routine_override_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.defaultProducts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_empty_state"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.routine_empty_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.routine_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(state.defaultProducts, key = { _, p -> p.id }) { index, product ->
                val isSkipped = product.id in state.skippedProductIds
                val isIncluded = !isSkipped
                val isDoneStep = state.isCompletedToday && isIncluded
                RoutineProductRow(
                    stepNumber = index + 1,
                    product = product,
                    isIncluded = isIncluded,
                    isDoneToday = isDoneStep,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.defaultProducts.lastIndex,
                    onToggle = { onToggleProduct(product.id) },
                    onRenameClick = {
                        renameText = product.name
                        renamingProduct = product
                    },
                    onMoveUp = { onMoveProduct(product.id, -1) },
                    onMoveDown = { onMoveProduct(product.id, +1) },
                    modifier = Modifier.animateItem(placementSpec = ExpressiveMotion.spatialSpring())
                )
            }
        }

        // 6. Custom Daily Habits Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.routine_custom_habits_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = { showAddHabitDialog = true },
                    modifier = Modifier.testTag("routine_add_habit_btn")
                ) {
                    Icon(
                        imageVector = AppIcons.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.routine_add_habit_btn))
                }
            }
        }

        if (state.customHabits.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.routine_custom_habits_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            itemsIndexed(state.customHabits, key = { _, h -> "habit_${h.habitKey}" }) { _, habit ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (habit.completedToday) 0.65f else 1f)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleCustomHabit(habit.habitKey, habit.label)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (habit.completedToday) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (habit.completedToday) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = habit.label,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (habit.completedToday) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (habit.completedToday) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = habit.completedToday,
                            onCheckedChange = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleCustomHabit(habit.habitKey, habit.label)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodStatusChip(
    label: String,
    done: Boolean
) {
    val color = if (done) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal,
            color = color
        )
        if (done) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun RoutineProductRow(
    stepNumber: Int,
    product: ProductEntity,
    isIncluded: Boolean,
    isDoneToday: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onRenameClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val checkPop = remember { Animatable(1f) }
    LaunchedEffect(isIncluded, isDoneToday) {
        checkPop.snapTo(0.84f)
        checkPop.animateTo(1.1f, ExpressiveMotion.bouncySpring())
        checkPop.animateTo(1.0f, ExpressiveMotion.spatialSpring())
    }

    val isStruck = isDoneToday
    val cardAlpha by animateFloatAsState(
        targetValue = when {
            isDoneToday -> 0.65f
            else -> 1f
        },
        animationSpec = tween(220),
        label = "routine_item_alpha"
    )
    val rowBgColor by animateColorAsState(
        targetValue = when {
            isDoneToday -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(220),
        label = "routine_item_bg"
    )
    val rowBorderColor by animateColorAsState(
        targetValue = when {
            isDoneToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            isIncluded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        },
        animationSpec = tween(220),
        label = "routine_item_border"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .testTag("routine_product_item_${product.id}"),
        colors = CardDefaults.cardColors(containerColor = rowBgColor),
        border = BorderStroke(1.dp, rowBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowUp,
                        contentDescription = stringResource(R.string.routine_move_up),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "$stepNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDoneToday) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowDown,
                        contentDescription = stringResource(R.string.routine_move_down),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (isStruck) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isStruck) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusLabel = when {
                    !isIncluded -> stringResource(R.string.routine_todo_label)
                    isDoneToday -> stringResource(R.string.hygiene_completed_label)
                    else -> stringResource(R.string.routine_included_label)
                }
                val subtitle = if (product.brand.isNotBlank()) {
                    "${product.brand} · $statusLabel"
                } else {
                    statusLabel
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDoneToday) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRenameClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("routine_rename_btn_${product.id}")
            ) {
                Icon(
                    imageVector = AppIcons.Edit,
                    contentDescription = stringResource(R.string.routine_rename_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Checkbox(
                checked = isIncluded,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = checkPop.value
                        scaleY = checkPop.value
                    }
                    .testTag("routine_product_toggle_${product.id}")
            )
        }
    }
}

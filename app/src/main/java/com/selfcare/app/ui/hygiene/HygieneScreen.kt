package com.selfcare.app.ui.hygiene

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.repository.HygieneRepository
import com.selfcare.app.ui.components.AnimatedMetricGaugeBar
import com.selfcare.app.ui.icons.AppIcons

@Composable
fun HygieneScreen(
    viewModel: HygieneViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HygieneScreenContent(
        state = state,
        onToggleHabit = viewModel::toggleHabit,
        onCustomInputChange = viewModel::updateCustomInput,
        onAddCustomHabit = viewModel::addCustomHabit,
        onDeleteCustomHabit = viewModel::deleteCustomHabit,
        modifier = modifier
    )
}

@Composable
fun HygieneScreenContent(
    state: HygieneUiState,
    onToggleHabit: (habitKey: String, label: String, isCustom: Boolean) -> Unit,
    onCustomInputChange: (String) -> Unit,
    onAddCustomHabit: () -> Unit,
    onDeleteCustomHabit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val showerLabel = stringResource(R.string.hygiene_shower)
    val teethAmLabel = stringResource(R.string.hygiene_teeth_am)
    val teethPmLabel = stringResource(R.string.hygiene_teeth_pm)
    val lipBalmLabel = stringResource(R.string.hygiene_lip_balm)
    var pendingDeleteHabit by remember { mutableStateOf<Pair<String, String>?>(null) }

    pendingDeleteHabit?.let { (habitKey, habitLabel) ->
        AlertDialog(
            onDismissRequest = { pendingDeleteHabit = null },
            title = { Text(text = stringResource(R.string.confirm_delete_title)) },
            text = { Text(text = stringResource(R.string.confirm_delete_habit_body, habitLabel)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCustomHabit(habitKey)
                        pendingDeleteHabit = null
                    },
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteHabit = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val builtInItems = listOf(
        Triple(HygieneRepository.HABIT_SHOWER, showerLabel, AppIcons.WaterDrop),
        Triple(HygieneRepository.HABIT_TEETH_AM, teethAmLabel, AppIcons.Sparkle),
        Triple(HygieneRepository.HABIT_TEETH_PM, teethPmLabel, AppIcons.Moon),
        Triple(HygieneRepository.HABIT_LIP_BALM, lipBalmLabel, AppIcons.CheckCircle)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            val totalHabits = builtInItems.size + state.customHabits.size
            val doneBuiltIn = builtInItems.count { state.completedByKey[it.first] == true }
            val doneCustom = state.customHabits.count { it.completed }
            val doneTotal = doneBuiltIn + doneCustom
            val fraction = if (totalHabits > 0) doneTotal.toFloat() / totalHabits.toFloat() else 0f

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.hygiene_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$doneTotal / $totalHabits",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AnimatedMetricGaugeBar(
                        targetFraction = fraction.coerceIn(0.02f, 1f),
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        items(
            items = builtInItems,
            key = { it.first }
        ) { (key, label, icon) ->
            val isCompleted = state.completedByKey[key] == true
            HygieneRowCard(
                habitKey = key,
                label = label,
                icon = icon,
                completed = isCompleted,
                onToggle = { onToggleHabit(key, label, false) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.hygiene_custom_habits),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.customInputText,
                    onValueChange = onCustomInputChange,
                    placeholder = { Text(stringResource(R.string.hygiene_habit_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hygiene_add_custom_input")
                )
                Button(
                    onClick = onAddCustomHabit,
                    enabled = state.customInputText.isNotBlank(),
                    modifier = Modifier.testTag("hygiene_add_custom_btn")
                ) {
                    Icon(
                        imageVector = AppIcons.Add,
                        contentDescription = stringResource(R.string.hygiene_add_habit),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (state.customHabits.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.hygiene_no_custom_habits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(
                items = state.customHabits,
                key = { it.habitKey }
            ) { customItem ->
                HygieneRowCard(
                    habitKey = customItem.habitKey,
                    label = customItem.label,
                    icon = AppIcons.Habit,
                    completed = customItem.completed,
                    onToggle = { onToggleHabit(customItem.habitKey, customItem.label, true) },
                    onDelete = { pendingDeleteHabit = customItem.habitKey to customItem.label }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HygieneRowCard(
    habitKey: String,
    label: String,
    icon: ImageVector,
    completed: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hygiene_item_$habitKey")
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (completed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (completed) R.string.hygiene_completed_label else R.string.hygiene_pending_label
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Checkbox(
                checked = completed,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

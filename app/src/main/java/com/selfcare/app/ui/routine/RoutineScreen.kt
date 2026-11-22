package com.selfcare.app.ui.routine

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.ui.icons.AppIcons

@Composable
fun RoutineScreen(
    viewModel: RoutineViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RoutineScreenContent(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onCompleteRoutine = viewModel::completeCurrentRoutine,
        onToggleProduct = viewModel::toggleProductOverride,
        modifier = modifier
    )
}

@Composable
fun RoutineScreenContent(
    state: RoutineUiState,
    onSelectPeriod: (String) -> Unit,
    onCompleteRoutine: () -> Unit,
    onToggleProduct: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMorning = state.selectedPeriod == RoutineRepository.PERIOD_MORNING

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.routine_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = isMorning,
                onClick = { onSelectPeriod(RoutineRepository.PERIOD_MORNING) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        imageVector = AppIcons.Sun,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("routine_period_morning")
            ) {
                Text(stringResource(R.string.routine_morning))
            }
            SegmentedButton(
                selected = !isMorning,
                onClick = { onSelectPeriod(RoutineRepository.PERIOD_EVENING) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        imageVector = AppIcons.Moon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("routine_period_evening")
            ) {
                Text(stringResource(R.string.routine_evening))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (state.isCompletedToday) AppIcons.CheckCircle else {
                            if (isMorning) AppIcons.Sun else AppIcons.Moon
                        },
                        contentDescription = null,
                        tint = if (state.isCompletedToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    if (state.isCompletedToday) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.testTag("routine_completed_badge")
                        ) {
                            Text(
                                text = stringResource(R.string.routine_completed_today),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.routine_not_completed_today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onCompleteRoutine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            if (isMorning) {
                                "routine_complete_morning_btn"
                            } else {
                                "routine_complete_evening_btn"
                            }
                        )
                ) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(
                            if (isMorning) {
                                R.string.routine_complete_morning
                            } else {
                                R.string.routine_complete_evening
                            }
                        )
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.routine_default_products),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (state.defaultProducts.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_empty_state"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Bottle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.routine_empty),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.routine_no_products_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.routine_override_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.defaultProducts,
                    key = { it.id }
                ) { product ->
                    val isSkipped = product.id in state.skippedProductIds
                    RoutineProductRow(
                        product = product,
                        isIncluded = !isSkipped,
                        onToggle = { onToggleProduct(product.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun RoutineProductRow(
    product: ProductEntity,
    isIncluded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (isIncluded) TextDecoration.None else TextDecoration.LineThrough
                    ),
                    color = if (isIncluded) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                val subtitle = buildString {
                    if (product.brand.isNotBlank()) {
                        append(product.brand)
                        append(" - ")
                    }
                    append(
                        if (isIncluded) {
                            stringResource(R.string.routine_product_included)
                        } else {
                            stringResource(R.string.routine_product_skipped)
                        }
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = isIncluded,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("routine_product_toggle_${product.id}")
            )
        }
    }
}

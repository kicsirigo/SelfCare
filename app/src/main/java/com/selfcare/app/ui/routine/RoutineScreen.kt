package com.selfcare.app.ui.routine

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.local.entity.ProductEntity
import com.selfcare.app.data.repository.RoutineRepository
import com.selfcare.app.ui.components.CelebrationOverlay
import com.selfcare.app.ui.components.HeroScoreRing
import com.selfcare.app.ui.icons.AppIcons

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
    modifier: Modifier = Modifier
) {
    val period = state.selectedPeriod

    val totalSteps = state.defaultProducts.size
    val activeSteps = state.defaultProducts.count { it.id !in state.skippedProductIds }
    val completionFraction = when {
        state.isCompletedToday -> 1f
        totalSteps > 0 -> activeSteps.toFloat() / totalSteps.toFloat()
        else -> 0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Whoop/Oura-style Daily Hero Ring Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroScoreRing(
                    progress = completionFraction,
                    centerValue = if (state.isCompletedToday) "100%" else "$activeSteps/$totalSteps",
                    centerSubtitle = if (state.isCompletedToday) {
                        stringResource(R.string.hygiene_completed_label)
                    } else {
                        "${(completionFraction * 100).toInt()}%"
                    },
                    ringSize = 82.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.routine_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            if (state.isCompletedToday) R.string.routine_completed_today
                            else R.string.routine_override_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isCompletedToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

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

        val bannerContainerColor by animateColorAsState(
            targetValue = if (state.isCompletedToday) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            animationSpec = tween(durationMillis = 300),
            label = "routine_banner_bg"
        )
        val bannerBorderColor by animateColorAsState(
            targetValue = if (state.isCompletedToday) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            },
            animationSpec = tween(durationMillis = 300),
            label = "routine_banner_border"
        )

        val periodIcon = when (period) {
            RoutineRepository.PERIOD_MORNING -> AppIcons.Sun
            RoutineRepository.PERIOD_EVENING -> AppIcons.Moon
            else -> AppIcons.Haircut
        }
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = bannerContainerColor),
            border = BorderStroke(1.dp, bannerBorderColor)
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
                        imageVector = if (state.isCompletedToday) AppIcons.CheckCircle else periodIcon,
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
                        .testTag(completeBtnTag)
                ) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(completeBtnLabelRes))
                }
            }
        }

        Text(
            text = stringResource(R.string.routine_default_products),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        AnimatedContent(
            targetState = state.selectedPeriod to state.defaultProducts,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(150))
            },
            label = "routine_period_content"
        ) { (_, products) ->
            if (products.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_empty_state"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                            tint = MaterialTheme.colorScheme.primary,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            items = products,
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
    }
}

@Composable
private fun RoutineProductRow(
    product: ProductEntity,
    isIncluded: Boolean,
    onToggle: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isIncluded) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        },
        animationSpec = tween(220),
        label = "routine_row_border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, borderColor)
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

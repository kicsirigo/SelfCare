package com.selfcare.app.ui.trends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfcare.app.ui.theme.NeonGreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.repository.AdherenceCorrelation
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.MetricBaselineTrend
import com.selfcare.app.data.repository.TrendDirection
import com.selfcare.app.domain.cv.MetricAssessment
import com.selfcare.app.domain.cv.MetricInterpreter
import com.selfcare.app.domain.cv.MetricStatusLevel
import com.selfcare.app.ui.components.AnimatedMetricGaugeBar
import com.selfcare.app.ui.components.NeonSparklineChart
import com.selfcare.app.ui.components.StatusPillBadge
import com.selfcare.app.ui.components.statusLevelColor
import com.selfcare.app.ui.icons.AppIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("trends_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.trends_baseline_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.weightHistory.isNotEmpty() || uiState.avgDailyCalories7d != null || uiState.calorieChartBars.any { it.caloriesKcal > 0 }) {
            item {
                WeightAndNutritionTrendCard(
                    uiState = uiState,
                    onSelectCalorieRange = viewModel::selectCalorieRangeDays
                )
            }
        }

        if (summary.totalCheckIns == 0) {
            item {
                TrendsEmptyStateCard()
            }
        } else {
            item {
                val sparklinePoints = uiState.recentCheckIns
                    .take(14)
                    .reversed()
                    .map { item ->
                        val u = MetricInterpreter.interpretUnderEye(item.underEyeDeltaLab).scorePercent
                        val r = MetricInterpreter.interpretRedness(item.skinRednessIndex).scorePercent
                        val t = MetricInterpreter.interpretTexture(item.skinTextureVariance).scorePercent
                        val b = MetricInterpreter.interpretBlemish(item.blemishScore, item.blemishCount).scorePercent
                        ((u + r + t + b) / 4f)
                    }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
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
                                text = stringResource(R.string.trends_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${sparklinePoints.lastOrNull()?.toInt() ?: 0}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        NeonSparklineChart(values = sparklinePoints)
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BaselineWindowCard(
                        title = stringResource(R.string.trends_7d_label),
                        count = summary.checkInsLast7Days,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("trends_7d_baseline")
                    )
                    BaselineWindowCard(
                        title = stringResource(R.string.trends_30d_label),
                        count = summary.checkInsLast30Days,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("trends_30d_baseline")
                    )
                }
            }

            item {
                val eval = summary.underEyeTrend.latestValue?.let { MetricInterpreter.interpretUnderEye(it) }
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_under_eye),
                    trend = summary.underEyeTrend,
                    assessment = eval,
                    explanation = eval?.let {
                        stringResource(
                            when (it.level) {
                                MetricStatusLevel.OPTIMAL -> R.string.metric_under_eye_desc_optimal
                                MetricStatusLevel.GOOD -> R.string.metric_under_eye_desc_good
                                MetricStatusLevel.MODERATE -> R.string.metric_under_eye_desc_moderate
                                MetricStatusLevel.HIGH -> R.string.metric_under_eye_desc_high
                            }
                        )
                    },
                    formatPattern = "%.1f",
                    modifier = Modifier.testTag("trends_metric_card_under_eye")
                )
            }
            item {
                val eval = summary.rednessTrend.latestValue?.let { MetricInterpreter.interpretRedness(it) }
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_redness),
                    trend = summary.rednessTrend,
                    assessment = eval,
                    explanation = eval?.let {
                        stringResource(
                            when (it.level) {
                                MetricStatusLevel.OPTIMAL -> R.string.metric_redness_desc_optimal
                                MetricStatusLevel.GOOD -> R.string.metric_redness_desc_good
                                MetricStatusLevel.MODERATE -> R.string.metric_redness_desc_moderate
                                MetricStatusLevel.HIGH -> R.string.metric_redness_desc_high
                            }
                        )
                    },
                    formatPattern = "%.2f",
                    modifier = Modifier.testTag("trends_metric_card_redness")
                )
            }
            item {
                val eval = summary.textureTrend.latestValue?.let { MetricInterpreter.interpretTexture(it) }
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_texture),
                    trend = summary.textureTrend,
                    assessment = eval,
                    explanation = eval?.let {
                        stringResource(
                            when (it.level) {
                                MetricStatusLevel.OPTIMAL -> R.string.metric_texture_desc_optimal
                                MetricStatusLevel.GOOD -> R.string.metric_texture_desc_good
                                MetricStatusLevel.MODERATE -> R.string.metric_texture_desc_moderate
                                MetricStatusLevel.HIGH -> R.string.metric_texture_desc_high
                            }
                        )
                    },
                    formatPattern = "%.1f",
                    modifier = Modifier.testTag("trends_metric_card_texture")
                )
            }
            item {
                val eval = summary.lipDrynessTrend.latestValue?.let { MetricInterpreter.interpretLipDryness(it) }
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_lip),
                    trend = summary.lipDrynessTrend,
                    assessment = eval,
                    explanation = eval?.let {
                        stringResource(
                            when (it.level) {
                                MetricStatusLevel.OPTIMAL -> R.string.metric_lip_desc_optimal
                                MetricStatusLevel.GOOD -> R.string.metric_lip_desc_good
                                MetricStatusLevel.MODERATE -> R.string.metric_lip_desc_moderate
                                MetricStatusLevel.HIGH -> R.string.metric_lip_desc_high
                            }
                        )
                    },
                    formatPattern = "%.2f",
                    modifier = Modifier.testTag("trends_metric_card_lip")
                )
            }
            item {
                val hairRatio = summary.hairToFaceTrend.latestValue ?: 0f
                val hairEval = MetricInterpreter.interpretHairDetails(
                    crownVolumeScore = (hairRatio * 1.4f).coerceIn(0f, 1f),
                    shineScore = 0.78f,
                    foreheadCoverage = (hairRatio * 0.5f).coerceIn(0f, 1f),
                    templeSymmetryScore = 0.88f
                )
                val hairAssessment = MetricAssessment(
                    level = hairEval.crownVolumeLevel,
                    scorePercent = hairEval.crownVolumePercent,
                    progress = (hairEval.crownVolumePercent / 100f).coerceIn(0.08f, 1f)
                )
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_hair),
                    trend = summary.hairToFaceTrend,
                    assessment = hairAssessment,
                    explanation = stringResource(
                        R.string.trends_hair_detail_summary,
                        hairEval.crownVolumePercent,
                        hairEval.shinePercent,
                        hairEval.templeSymmetryPercent
                    ),
                    formatPattern = "%.2f",
                    modifier = Modifier.testTag("trends_metric_card_hair")
                )
            }

            item {
                val latestSpots = uiState.recentCheckIns.firstOrNull()?.blemishCount ?: 0
                val latestBlemishScore = summary.blemishTrend.latestValue ?: 0f
                val blemishEval = MetricInterpreter.interpretBlemish(latestBlemishScore, latestSpots)
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_blemish),
                    trend = summary.blemishTrend,
                    assessment = blemishEval,
                    explanation = stringResource(
                        when (blemishEval.level) {
                            MetricStatusLevel.OPTIMAL -> R.string.metric_blemish_desc_optimal
                            MetricStatusLevel.GOOD -> R.string.metric_blemish_desc_good
                            MetricStatusLevel.MODERATE -> R.string.metric_blemish_desc_moderate
                            MetricStatusLevel.HIGH -> R.string.metric_blemish_desc_high
                        },
                        latestSpots
                    ),
                    formatPattern = "%.2f",
                    modifier = Modifier.testTag("trends_metric_card_blemish")
                )
            }

            item {
                AdherenceCorrelationCard(correlation = summary.adherenceCorrelation)
            }

            item {
                var isManageMode by remember { mutableStateOf(false) }
                var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
                var showConfirmBulkDelete by remember { mutableStateOf(false) }

                if (showConfirmBulkDelete && selectedIds.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = { showConfirmBulkDelete = false },
                        title = { Text(stringResource(R.string.confirm_delete_title)) },
                        text = {
                            Text(
                                stringResource(
                                    R.string.trends_bulk_delete_confirm_body,
                                    selectedIds.size
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.deleteCheckIns(selectedIds)
                                    selectedIds = emptySet()
                                    isManageMode = false
                                    showConfirmBulkDelete = false
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmBulkDelete = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.trends_history_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (uiState.recentCheckIns.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    isManageMode = !isManageMode
                                    if (!isManageMode) selectedIds = emptySet()
                                },
                                modifier = Modifier.testTag("trends_history_manage_btn")
                            ) {
                                Text(
                                    text = stringResource(
                                        if (isManageMode) R.string.action_cancel else R.string.trends_history_manage
                                    )
                                )
                            }
                        }
                    }

                    if (isManageMode && uiState.recentCheckIns.isNotEmpty()) {
                        val allIds = uiState.recentCheckIns.map { it.id }.toSet()
                        val allSelected = selectedIds.size == allIds.size && allIds.isNotEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedIds = if (allSelected) emptySet() else allIds
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(
                                        if (allSelected) {
                                            R.string.trends_history_deselect_all
                                        } else {
                                            R.string.trends_history_select_all
                                        }
                                    ),
                                    maxLines = 1
                                )
                            }
                            Button(
                                onClick = { showConfirmBulkDelete = true },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.trends_history_delete_selected,
                                        selectedIds.size
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trends_history_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.recentCheckIns.forEach { item ->
                            val isSelected = item.id in selectedIds
                            CheckInHistoryItemCard(
                                item = item,
                                isManageMode = isManageMode,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    selectedIds = if (isSelected) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendsEmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trends_empty_state"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = AppIcons.Chart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = stringResource(R.string.trends_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.trends_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BaselineWindowCard(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.trends_checkin_count, count),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MetricTrendCard(
    title: String,
    trend: MetricBaselineTrend,
    assessment: MetricAssessment?,
    explanation: String?,
    formatPattern: String,
    modifier: Modifier = Modifier
) {
    val accent = assessment?.let { statusLevelColor(it.level) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (assessment != null) {
                        StatusPillBadge(level = assessment.level)
                    }
                    TrendDirectionBadge(direction = trend.direction)
                }
            }

            if (assessment != null) {
                AnimatedMetricGaugeBar(
                    targetFraction = (assessment.scorePercent / 100f).coerceIn(0.08f, 1f),
                    accentColor = accent
                )
            }

            if (!explanation.isNullOrBlank()) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val latestText = trend.latestValue?.let { String.format(Locale.US, formatPattern, it) } ?: "--"
            val avg7Text = trend.rolling7DayAvg?.let { String.format(Locale.US, formatPattern, it) } ?: "--"
            val avg30Text = trend.rolling30DayAvg?.let { String.format(Locale.US, formatPattern, it) } ?: "--"

            Text(
                text = stringResource(
                    R.string.trends_metric_values,
                    latestText,
                    avg7Text,
                    avg30Text
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendDirectionBadge(direction: TrendDirection) {
    val (labelRes, containerColor, contentColor, icon) = when (direction) {
        TrendDirection.BETTER -> Quad(
            R.string.trends_dir_better,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            AppIcons.ArrowDown
        )
        TrendDirection.WORSE -> Quad(
            R.string.trends_dir_worse,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            AppIcons.ArrowUp
        )
        TrendDirection.STEADY -> Quad(
            R.string.trends_dir_steady,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            AppIcons.Check
        )
        TrendDirection.INSUFFICIENT_DATA -> Quad(
            R.string.trends_dir_insufficient,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            null
        )
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun AdherenceCorrelationCard(correlation: AdherenceCorrelation) {
    val insightRes = when (correlation.insightCode) {
        FaceCheckInRepository.INSIGHT_BETTER_ON_ADHERENT_DAYS -> R.string.trends_insight_better
        FaceCheckInRepository.INSIGHT_STEADY_ACROSS_ADHERENCE -> R.string.trends_insight_steady
        FaceCheckInRepository.INSIGHT_NEED_MORE_VARIED_DAYS -> R.string.trends_insight_need_more
        else -> R.string.trends_insight_no_data
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trends_routine_correlation"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.trends_correlation_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(
                    R.string.trends_adherence_rates,
                    (correlation.routineAdherenceRate30d * 100f).toInt(),
                    (correlation.hygieneAdherenceRate30d * 100f).toInt(),
                    correlation.pairedDaysCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(insightRes),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CheckInHistoryItemCard(
    item: FaceCheckInEntity,
    isManageMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    val dateTimeText = runCatching {
        Instant.ofEpochMilli(item.timestampEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd · HH:mm:ss"))
    }.getOrElse {
        runCatching {
            LocalDate.ofEpochDay(item.dateEpochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrDefault(item.dateEpochDay.toString())
    }

    val underEyeStatus = MetricInterpreter.interpretUnderEye(item.underEyeDeltaLab)
    val rednessStatus = MetricInterpreter.interpretRedness(item.skinRednessIndex)

    Card(
        onClick = { if (isManageMode) onToggleSelect() },
        enabled = isManageMode,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
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
            if (isManageMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateTimeText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPillBadge(level = underEyeStatus.level)
                    StatusPillBadge(level = rednessStatus.level)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.trends_history_item_metrics,
                        String.format(Locale.US, "%.1f", item.underEyeDeltaLab),
                        String.format(Locale.US, "%.2f", item.skinRednessIndex),
                        String.format(Locale.US, "%.2f", item.lipDrynessScore),
                        (item.lightingQualityScore * 100f).toInt()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeightAndNutritionTrendCard(
    uiState: TrendsUiState,
    onSelectCalorieRange: (Int) -> Unit
) {
    val weights = uiState.weightHistory
    val latest = weights.lastOrNull()?.weightKg
    val first = weights.firstOrNull()?.weightKg
    val delta = if (latest != null && first != null && weights.size >= 2) latest - first else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trends_weight_nutrition_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
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
                    text = stringResource(R.string.trends_weight_nutrition_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (latest != null) {
                    Text(
                        text = String.format(Locale.US, "%.1f kg", latest),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (weights.size >= 2) {
                NeonSparklineChart(values = weights.map { it.weightKg })
            } else if (weights.isEmpty()) {
                Text(
                    text = stringResource(R.string.trends_weight_no_entries),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (delta != null) {
                    val sign = if (delta >= 0f) "+" else ""
                    Text(
                        text = stringResource(
                            R.string.trends_weight_delta,
                            "$sign${String.format(Locale.US, "%.1f", delta)}"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.targetWeightKg?.let { target ->
                    Text(
                        text = stringResource(
                            R.string.trends_weight_target,
                            String.format(Locale.US, "%.1f", target)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.avgDailyCalories7d != null && uiState.avgDailyProtein7d != null) {
                Text(
                    text = stringResource(
                        R.string.trends_nutrition_range_avg,
                        uiState.selectedCalorieRangeDays,
                        uiState.avgDailyCalories7d,
                        uiState.avgDailyProtein7d,
                        uiState.nutritionDaysLogged7d
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.calorieChartBars.any { it.caloriesKcal > 0 }) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.trends_calorie_chart_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.trends_calorie_chart_target, uiState.dailyCalorieTarget),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        14 to R.string.trends_calorie_range_14d,
                        30 to R.string.trends_calorie_range_30d,
                        90 to R.string.trends_calorie_range_90d,
                        365 to R.string.trends_calorie_range_365d
                    ).forEach { (days, labelRes) ->
                        FilterChip(
                            selected = uiState.selectedCalorieRangeDays == days,
                            onClick = { onSelectCalorieRange(days) },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.testTag("trends_calorie_range_${days}d")
                        )
                    }
                }
                CalorieHistoryBarChart(
                    bars = uiState.calorieChartBars,
                    targetKcal = uiState.dailyCalorieTarget
                )
            }
        }
    }
}

@Composable
private fun CalorieHistoryBarChart(
    bars: List<DailyCalorieBar>,
    targetKcal: Int
) {
    if (bars.isEmpty()) return
    val maxVal = maxOf(
        bars.maxOf { it.caloriesKcal },
        (targetKcal * 1.15f).toInt(),
        500
    ).toFloat()
    val errorColor = MaterialTheme.colorScheme.error
    val emptyBarColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    val blueColor = Color(0xFF38BDF8)
    val dateFmt = remember { DateTimeFormatter.ofPattern("MM.dd", Locale.US) }
    val firstDate = remember(bars) { LocalDate.ofEpochDay(bars.first().dateEpochDay).format(dateFmt) }
    val midDate = remember(bars) { LocalDate.ofEpochDay(bars[bars.size / 2].dateEpochDay).format(dateFmt) }
    val lastDate = remember(bars) { LocalDate.ofEpochDay(bars.last().dateEpochDay).format(dateFmt) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .testTag("trends_calorie_bar_chart")
        ) {
            val count = bars.size.coerceAtLeast(1)
            val gap = if (count <= 16) 4.dp.toPx() else if (count <= 32) 2.5.dp.toPx() else 1.5.dp.toPx()
            val totalGap = gap * (count - 1)
            val barWidth = ((size.width - totalGap) / count).coerceAtLeast(2f)
            val chartHeight = size.height - 6.dp.toPx()

            bars.forEachIndexed { idx, bar ->
                val x = idx * (barWidth + gap)
                if (bar.caloriesKcal <= 0) {
                    val stubH = 4.dp.toPx()
                    drawRoundRect(
                        color = emptyBarColor,
                        topLeft = Offset(x, size.height - stubH),
                        size = Size(barWidth, stubH),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                } else {
                    val ratio = (bar.caloriesKcal / maxVal).coerceIn(0.04f, 1f)
                    val h = chartHeight * ratio
                    val barColor = when {
                        bar.caloriesKcal > (targetKcal * 1.08f) -> errorColor
                        bar.caloriesKcal >= (targetKcal * 0.90f) -> NeonGreen
                        else -> blueColor
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            // Dashed daily calorie target line
            val targetY = size.height - (chartHeight * (targetKcal / maxVal).coerceIn(0.05f, 1f))
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(0f, targetY),
                end = Offset(size.width, targetY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = firstDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = midDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = lastDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

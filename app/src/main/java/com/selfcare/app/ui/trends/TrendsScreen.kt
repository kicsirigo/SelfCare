package com.selfcare.app.ui.trends

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfcare.app.R
import com.selfcare.app.data.local.entity.FaceCheckInEntity
import com.selfcare.app.data.repository.AdherenceCorrelation
import com.selfcare.app.data.repository.FaceCheckInRepository
import com.selfcare.app.data.repository.MetricBaselineTrend
import com.selfcare.app.data.repository.TrendDirection
import com.selfcare.app.ui.icons.AppIcons
import java.time.LocalDate
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.trends_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.trends_baseline_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (summary.totalCheckIns == 0) {
            item {
                TrendsEmptyStateCard()
            }
        } else {
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
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_under_eye),
                    trend = summary.underEyeTrend,
                    formatPattern = "%.1f",
                    modifier = Modifier.testTag("trends_metric_card_under_eye")
                )
            }
            item {
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_redness),
                    trend = summary.rednessTrend,
                    formatPattern = "%.2f",
                    modifier = Modifier.testTag("trends_metric_card_redness")
                )
            }
            item {
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_texture),
                    trend = summary.textureTrend,
                    formatPattern = "%.1f",
                    modifier = Modifier.testTag("trends_metric_card_texture")
                )
            }
            item {
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_lip),
                    trend = summary.lipDrynessTrend,
                    formatPattern = "%.2f",
                    modifier = Modifier.testTag("trends_metric_card_lip")
                )
            }
            item {
                MetricTrendCard(
                    title = stringResource(R.string.trends_metric_hair),
                    trend = summary.hairToFaceTrend,
                    formatPattern = "%.2f",
                    modifier = Modifier.testTag("trends_metric_card_hair")
                )
            }

            item {
                AdherenceCorrelationCard(correlation = summary.adherenceCorrelation)
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
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
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trends_history_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.recentCheckIns.forEach { item ->
                        CheckInHistoryItemCard(
                            item = item,
                            onDelete = { viewModel.deleteCheckIn(item.id) }
                        )
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
        )
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
    Card(modifier = modifier) {
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
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MetricTrendCard(
    title: String,
    trend: MetricBaselineTrend,
    formatPattern: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TrendDirectionBadge(direction = trend.direction)
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
                style = MaterialTheme.typography.bodySmall,
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
            .testTag("trends_routine_correlation")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.trends_correlation_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
    onDelete: () -> Unit
) {
    val dateText = runCatching {
        LocalDate.ofEpochDay(item.dateEpochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrDefault(item.dateEpochDay.toString())

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.trends_history_item_metrics,
                        String.format(Locale.US, "%.1f", item.underEyeDeltaLab),
                        String.format(Locale.US, "%.2f", item.skinRednessIndex),
                        String.format(Locale.US, "%.2f", item.lipDrynessScore),
                        (item.lightingQualityScore * 100f).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

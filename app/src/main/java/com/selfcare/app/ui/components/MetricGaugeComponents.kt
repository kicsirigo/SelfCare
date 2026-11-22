package com.selfcare.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.selfcare.app.R
import com.selfcare.app.domain.cv.HairQualitativeSummary
import com.selfcare.app.domain.cv.MetricAssessment
import com.selfcare.app.domain.cv.MetricStatusLevel
import com.selfcare.app.ui.icons.AppIcons
import com.selfcare.app.ui.theme.NeonGreen
import com.selfcare.app.ui.theme.NeonWarning
import java.util.Locale

@Composable
fun statusLevelColor(level: MetricStatusLevel): Color = when (level) {
    MetricStatusLevel.OPTIMAL -> NeonGreen
    MetricStatusLevel.GOOD -> MaterialTheme.colorScheme.primary
    MetricStatusLevel.MODERATE -> NeonWarning
    MetricStatusLevel.HIGH -> MaterialTheme.colorScheme.error
}

@Composable
fun statusLevelLabel(level: MetricStatusLevel): String = when (level) {
    MetricStatusLevel.OPTIMAL -> stringResource(R.string.metric_status_optimal)
    MetricStatusLevel.GOOD -> stringResource(R.string.metric_status_good)
    MetricStatusLevel.MODERATE -> stringResource(R.string.metric_status_moderate)
    MetricStatusLevel.HIGH -> stringResource(R.string.metric_status_high)
}

@Composable
fun StatusPillBadge(
    level: MetricStatusLevel,
    modifier: Modifier = Modifier
) {
    val accent = statusLevelColor(level)
    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Text(
            text = statusLevelLabel(level),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AnimatedMetricGaugeBar(
    targetFraction: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0.04f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "metric_gauge_fill"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor)
        )
    }
}

@Composable
fun MetricGaugeRow(
    title: String,
    assessment: MetricAssessment,
    explanation: String,
    technicalDetail: String? = null,
    invertBarForSeverity: Boolean = false
) {
    val accent = statusLevelColor(assessment.level)
    val barFraction = if (invertBarForSeverity) {
        assessment.progress
    } else {
        (assessment.scorePercent / 100f).coerceIn(0.08f, 1f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            StatusPillBadge(level = assessment.level)
        }
        AnimatedMetricGaugeBar(
            targetFraction = barFraction,
            accentColor = accent
        )
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!technicalDetail.isNullOrBlank()) {
            Text(
                text = technicalDetail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HairHealthAnalysisCard(
    summary: HairQualitativeSummary,
    growthPercent: Float,
    thresholdPercent: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("checkin_hair_health_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
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
                    imageVector = AppIcons.Haircut,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.hair_analysis_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 1. Crown Volume & Density
            HairDimensionRow(
                label = stringResource(R.string.hair_crown_volume_label),
                level = summary.crownVolumeLevel,
                percent = summary.crownVolumePercent
            )

            // 2. Hair Shine & Smoothness
            HairDimensionRow(
                label = stringResource(R.string.hair_shine_label),
                level = summary.shineLevel,
                percent = summary.shinePercent
            )

            // 3. Hairline & Forehead balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hair_hairline_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (summary.hairlineOpenForehead) {
                        stringResource(R.string.hair_hairline_open, summary.templeSymmetryPercent)
                    } else {
                        stringResource(R.string.hair_hairline_fringe, summary.templeSymmetryPercent)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 4. Growth since haircut
            val due = growthPercent >= thresholdPercent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hair_growth_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (due) {
                        stringResource(R.string.hair_growth_due, growthPercent.coerceAtLeast(0f))
                    } else {
                        stringResource(R.string.hair_growth_fresh, growthPercent.coerceAtLeast(0f))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (due) NeonWarning else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HairDimensionRow(
    label: String,
    level: MetricStatusLevel,
    percent: Int
) {
    val accent = statusLevelColor(level)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%d%%", percent),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                StatusPillBadge(level = level)
            }
        }
        AnimatedMetricGaugeBar(
            targetFraction = (percent / 100f).coerceIn(0.08f, 1f),
            accentColor = accent
        )
    }
}

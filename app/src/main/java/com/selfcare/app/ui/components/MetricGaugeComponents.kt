package com.selfcare.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
fun HeroScoreRing(
    progress: Float,
    centerValue: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier,
    ringSize: Dp = 104.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "hero_ring_progress"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 9.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (animatedProgress > 0.005f) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = centerValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (centerSubtitle.isNotBlank()) {
                Text(
                    text = centerSubtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BentoMetricTile(
    title: String,
    assessment: MetricAssessment,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val accent = statusLevelColor(assessment.level)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${assessment.scorePercent}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusPillBadge(level = assessment.level)
            }
            AnimatedMetricGaugeBar(
                targetFraction = (assessment.scorePercent / 100f).coerceIn(0.06f, 1f),
                accentColor = accent
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun NeonSparklineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = NeonGreen
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        val w = size.width
        val h = size.height
        val padY = 10.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(1f)

        // Subtle horizontal baseline grid
        drawLine(
            color = gridColor,
            start = Offset(0f, padY + usableH * 0.5f),
            end = Offset(w, padY + usableH * 0.5f),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, h - padY),
            end = Offset(w, h - padY),
            strokeWidth = 1.dp.toPx()
        )

        if (values.isEmpty()) return@Canvas
        val minVal = (values.minOrNull() ?: 0f).coerceAtMost(0f)
        val maxVal = (values.maxOrNull() ?: 100f).coerceAtLeast(minVal + 1f)
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val points = if (values.size == 1) {
            val y = padY + usableH * (1f - ((values[0] - minVal) / range).coerceIn(0f, 1f))
            listOf(Offset(0f, y), Offset(w, y))
        } else {
            val stepX = w / (values.size - 1).toFloat()
            values.mapIndexed { idx, v ->
                val norm = ((v - minVal) / range).coerceIn(0f, 1f)
                Offset(idx * stepX, padY + usableH * (1f - norm))
            }
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (prev.x + cur.x) / 2f
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                startY = padY,
                endY = h
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Glowing latest point
        val last = points.last()
        drawCircle(color = lineColor.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = last)
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = last)
    }
}

@Composable
fun statusLevelColor(level: MetricStatusLevel): Color = when (level) {
    MetricStatusLevel.OPTIMAL -> NeonGreen
    MetricStatusLevel.GOOD -> Color(0xFFA3E635) // Lime / yellow-green distinct from Optimal NeonGreen
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
    modifier: Modifier = Modifier,
    hairSupportProduct: String? = null
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

            if (!hairSupportProduct.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.hair_shelf_support_note, hairSupportProduct),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

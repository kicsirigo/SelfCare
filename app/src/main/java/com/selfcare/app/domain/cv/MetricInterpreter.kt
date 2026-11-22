package com.selfcare.app.domain.cv

/**
 * Qualitative status tier replacing opaque raw numbers with human-readable labels and gauges.
 */
enum class MetricStatusLevel {
    OPTIMAL,
    GOOD,
    MODERATE,
    HIGH
}

data class MetricAssessment(
    val level: MetricStatusLevel,
    val scorePercent: Int,
    val progress: Float
)

data class HairQualitativeSummary(
    val crownVolumeLevel: MetricStatusLevel,
    val crownVolumePercent: Int,
    val shineLevel: MetricStatusLevel,
    val shinePercent: Int,
    val hairlineOpenForehead: Boolean,
    val templeSymmetryPercent: Int
)

/**
 * Interprets raw CV metrics into clear qualitative statuses (Optimal / Good / Moderate / High)
 * and 0..100 visual gauge values. Never issues medical diagnoses.
 */
object MetricInterpreter {

    fun interpretUnderEye(deltaLab: Float): MetricAssessment {
        val level = when {
            deltaLab < 8f -> MetricStatusLevel.OPTIMAL
            deltaLab < 15f -> MetricStatusLevel.GOOD
            deltaLab < 24f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (deltaLab / 35f).coerceIn(0.08f, 1f)
        val healthScore = (100f - (deltaLab / 35f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, healthScore, progress)
    }

    fun interpretRedness(rednessIndex: Float): MetricAssessment {
        val level = when {
            rednessIndex < 0.22f -> MetricStatusLevel.OPTIMAL
            rednessIndex < 0.36f -> MetricStatusLevel.GOOD
            rednessIndex < 0.55f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (rednessIndex / 0.85f).coerceIn(0.08f, 1f)
        val calmScore = (100f - (rednessIndex / 0.85f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, calmScore, progress)
    }

    fun interpretTexture(textureVariance: Float): MetricAssessment {
        val level = when {
            textureVariance < 12f -> MetricStatusLevel.OPTIMAL
            textureVariance < 22f -> MetricStatusLevel.GOOD
            textureVariance < 36f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (textureVariance / 50f).coerceIn(0.08f, 1f)
        val smoothScore = (100f - (textureVariance / 50f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, smoothScore, progress)
    }

    fun interpretLipDryness(lipDrynessScore: Float): MetricAssessment {
        val level = when {
            lipDrynessScore < 0.24f -> MetricStatusLevel.OPTIMAL
            lipDrynessScore < 0.42f -> MetricStatusLevel.GOOD
            lipDrynessScore < 0.65f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = (lipDrynessScore / 0.90f).coerceIn(0.08f, 1f)
        val hydrationScore = (100f - (lipDrynessScore / 0.90f) * 100f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, hydrationScore, progress)
    }

    fun interpretHairDetails(
        crownVolumeScore: Float,
        shineScore: Float,
        foreheadCoverage: Float,
        templeSymmetryScore: Float
    ): HairQualitativeSummary {
        val volPct = (crownVolumeScore * 100f).toInt().coerceIn(0, 100)
        val volLevel = when {
            volPct >= 75 -> MetricStatusLevel.OPTIMAL
            volPct >= 50 -> MetricStatusLevel.GOOD
            volPct >= 30 -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val shinePct = (shineScore * 100f).toInt().coerceIn(0, 100)
        val shineLevel = when {
            shinePct >= 75 -> MetricStatusLevel.OPTIMAL
            shinePct >= 52 -> MetricStatusLevel.GOOD
            shinePct >= 32 -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val symPct = (templeSymmetryScore * 100f).toInt().coerceIn(0, 100)
        return HairQualitativeSummary(
            crownVolumeLevel = volLevel,
            crownVolumePercent = volPct,
            shineLevel = shineLevel,
            shinePercent = shinePct,
            hairlineOpenForehead = foreheadCoverage < 0.35f,
            templeSymmetryPercent = symPct
        )
    }
}

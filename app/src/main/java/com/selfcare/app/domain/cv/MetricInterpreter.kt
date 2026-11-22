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

enum class ImprovementTipCode {
    BLEMISHES,
    REDNESS,
    TEXTURE,
    UNDER_EYE,
    LIPS,
    SHAVE,
    MAINTAIN
}

data class ObjectiveRatingSummary(
    val scoreOutOfTen: Float,
    val level: MetricStatusLevel,
    val tips: List<ImprovementTipCode>
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

    fun interpretBlemish(blemishScore: Float, blemishCount: Int): MetricAssessment {
        val level = when {
            blemishCount <= 1 && blemishScore < 0.12f -> MetricStatusLevel.OPTIMAL
            blemishCount <= 4 && blemishScore < 0.30f -> MetricStatusLevel.GOOD
            blemishCount <= 9 && blemishScore < 0.60f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val progress = blemishScore.coerceIn(0.06f, 1f)
        val clarityScore = (100f - blemishScore.coerceIn(0f, 1f) * 95f).toInt().coerceIn(5, 99)
        return MetricAssessment(level, clarityScore, progress)
    }

    fun interpretStubble(stubbleScore: Float): MetricAssessment {
        val clamped = stubbleScore.coerceIn(0f, 1f)
        val level = when {
            clamped < 0.25f -> MetricStatusLevel.OPTIMAL
            clamped < 0.50f -> MetricStatusLevel.GOOD
            clamped < 0.72f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }
        val groomedScore = (100f - clamped * 90f).toInt().coerceIn(10, 99)
        return MetricAssessment(level, groomedScore, clamped.coerceIn(0.06f, 1f))
    }

    /**
     * Computes an objective 1.0..10.0 grooming & skin-care rating purely from on-device
     * CV metrics (zero subjective beauty bias) and ranks concrete improvement tips.
     */
    fun computeObjectiveRating(
        underEyeDelta: Float,
        redness: Float,
        texture: Float,
        lipDryness: Float,
        blemishScore: Float,
        blemishCount: Int,
        stubbleScore: Float,
        shaveDue: Boolean = false
    ): ObjectiveRatingSummary {
        val underEye = interpretUnderEye(underEyeDelta)
        val rednessEval = interpretRedness(redness)
        val textureEval = interpretTexture(texture)
        val lipEval = interpretLipDryness(lipDryness)
        val blemishEval = interpretBlemish(blemishScore, blemishCount)
        val stubbleEval = interpretStubble(stubbleScore)

        val weighted100 = (
            blemishEval.scorePercent * 0.26f +
                rednessEval.scorePercent * 0.20f +
                textureEval.scorePercent * 0.20f +
                underEye.scorePercent * 0.14f +
                lipEval.scorePercent * 0.10f +
                stubbleEval.scorePercent * 0.10f
            ).coerceIn(10f, 99f)

        val rating10 = ((weighted100 / 10f) * 10f).toInt() / 10f // 1 decimal place
        val level = when {
            rating10 >= 8.2f -> MetricStatusLevel.OPTIMAL
            rating10 >= 6.8f -> MetricStatusLevel.GOOD
            rating10 >= 5.2f -> MetricStatusLevel.MODERATE
            else -> MetricStatusLevel.HIGH
        }

        val candidates = mutableListOf<Pair<ImprovementTipCode, Int>>()
        if (blemishEval.scorePercent < 82) candidates.add(ImprovementTipCode.BLEMISHES to blemishEval.scorePercent)
        if (rednessEval.scorePercent < 82) candidates.add(ImprovementTipCode.REDNESS to rednessEval.scorePercent)
        if (textureEval.scorePercent < 82) candidates.add(ImprovementTipCode.TEXTURE to textureEval.scorePercent)
        if (underEye.scorePercent < 80) candidates.add(ImprovementTipCode.UNDER_EYE to underEye.scorePercent)
        if (lipEval.scorePercent < 80) candidates.add(ImprovementTipCode.LIPS to lipEval.scorePercent)
        if (shaveDue || stubbleScore >= 0.58f) candidates.add(ImprovementTipCode.SHAVE to stubbleEval.scorePercent)

        val topTips = candidates
            .sortedBy { it.second }
            .map { it.first }
            .take(3)
            .ifEmpty { listOf(ImprovementTipCode.MAINTAIN) }

        return ObjectiveRatingSummary(
            scoreOutOfTen = rating10.coerceIn(1.0f, 10.0f),
            level = level,
            tips = topTips
        )
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

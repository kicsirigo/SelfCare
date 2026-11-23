package com.selfcare.app.domain.cv

/**
 * Raw 0..1 skin health signal predictions from the 4-head EfficientNet-B0 ONNX model.
 * Higher values indicate healthier skin on each head (1.0 = optimal).
 */
data class SkinSignalsPrediction(
    val structureScore: Float,
    val hydrationScore: Float,
    val sunDamageScore: Float,
    val elasticityScore: Float
)

data class FusedSkinMetrics(
    val underEyeDeltaLab: Float,
    val skinRednessIndex: Float,
    val skinTextureVariance: Float,
    val lipDrynessScore: Float
)

/**
 * Pure-Kotlin decoder and hybrid fusion engine combining 1080px local ROI CIELAB/CV
 * measurements with global 224x224 EfficientNet-B0 skin signal predictions.
 */
object SkinSignalsHybridFusion {

    private const val TEXTURE_AI_WEIGHT = 0.35f
    private const val HYDRATION_AI_WEIGHT = 0.35f
    private const val REDNESS_AI_WEIGHT = 0.30f
    private const val UNDER_EYE_AI_WEIGHT = 0.30f

    fun decodeScores(rawScores: FloatArray): SkinSignalsPrediction? {
        if (rawScores.size < 4) return null
        for (i in 0 until 4) {
            if (!rawScores[i].isFinite()) return null
        }
        return SkinSignalsPrediction(
            structureScore = rawScores[0].coerceIn(0f, 1f),
            hydrationScore = rawScores[1].coerceIn(0f, 1f),
            sunDamageScore = rawScores[2].coerceIn(0f, 1f),
            elasticityScore = rawScores[3].coerceIn(0f, 1f)
        )
    }

    fun fuseSkinMetrics(
        cvUnderEyeDeltaLab: Float,
        cvRednessIndex: Float,
        cvTextureVariance: Float,
        cvLipDrynessScore: Float,
        aiSignals: SkinSignalsPrediction?
    ): FusedSkinMetrics {
        if (aiSignals == null) {
            return FusedSkinMetrics(
                underEyeDeltaLab = cvUnderEyeDeltaLab,
                skinRednessIndex = cvRednessIndex,
                skinTextureVariance = cvTextureVariance,
                lipDrynessScore = cvLipDrynessScore
            )
        }

        val aiTextureEquivalent = (1f - aiSignals.structureScore).coerceIn(0f, 1f) * 34f
        val aiDrynessEquivalent = (1f - aiSignals.hydrationScore).coerceIn(0f, 1f) * 0.72f
        val aiRednessEquivalent = (1f - aiSignals.sunDamageScore).coerceIn(0f, 1f) * 0.52f
        val aiUnderEyeEquivalent = (1f - aiSignals.elasticityScore).coerceIn(0f, 1f) * 24f

        return FusedSkinMetrics(
            underEyeDeltaLab = (
                cvUnderEyeDeltaLab * (1f - UNDER_EYE_AI_WEIGHT) +
                    aiUnderEyeEquivalent * UNDER_EYE_AI_WEIGHT
                ).coerceAtLeast(0f),
            skinRednessIndex = (
                cvRednessIndex * (1f - REDNESS_AI_WEIGHT) +
                    aiRednessEquivalent * REDNESS_AI_WEIGHT
                ).coerceIn(0f, 1f),
            skinTextureVariance = (
                cvTextureVariance * (1f - TEXTURE_AI_WEIGHT) +
                    aiTextureEquivalent * TEXTURE_AI_WEIGHT
                ).coerceAtLeast(0f),
            lipDrynessScore = (
                cvLipDrynessScore * (1f - HYDRATION_AI_WEIGHT) +
                    aiDrynessEquivalent * HYDRATION_AI_WEIGHT
                ).coerceIn(0f, 1f)
        )
    }
}

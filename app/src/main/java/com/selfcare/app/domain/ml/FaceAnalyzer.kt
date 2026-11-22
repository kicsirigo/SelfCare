package com.selfcare.app.domain.ml

import android.graphics.Bitmap

data class FaceAnalysisResult(
    val underEyeDeltaLab: Float,
    val skinRednessIndex: Float,
    val skinTextureVariance: Float,
    val lipDrynessScore: Float,
    val hairToFaceRatio: Float,
    val lightingQualityScore: Float,
    val isLightingAcceptable: Boolean,
    val lightingWarningReason: String? = null,
    val hairCrownVolumeScore: Float = (hairToFaceRatio * 1.4f).coerceIn(0f, 1f),
    val hairShineScore: Float = if (hairToFaceRatio > 0f) 0.72f else 0f,
    val hairForeheadCoverage: Float = (hairToFaceRatio * 0.6f).coerceIn(0f, 1f),
    val hairTempleSymmetryScore: Float = if (hairToFaceRatio > 0f) 0.88f else 0f
)

interface FaceAnalyzer {
    suspend fun analyze(bitmap: Bitmap): FaceAnalysisResult
}

class StubFaceAnalyzer : FaceAnalyzer {
    override suspend fun analyze(bitmap: Bitmap): FaceAnalysisResult = FaceAnalysisResult(
        underEyeDeltaLab = 0f,
        skinRednessIndex = 0f,
        skinTextureVariance = 0f,
        lipDrynessScore = 0f,
        hairToFaceRatio = 0f,
        lightingQualityScore = 0f,
        isLightingAcceptable = false,
        lightingWarningReason = "FaceAnalyzer not configured"
    )
}

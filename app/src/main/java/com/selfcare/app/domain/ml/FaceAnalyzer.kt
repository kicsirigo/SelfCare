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
    val lightingWarningReason: String? = null
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

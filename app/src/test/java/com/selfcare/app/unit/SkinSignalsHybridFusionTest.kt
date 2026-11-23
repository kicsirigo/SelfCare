package com.selfcare.app.unit

import com.selfcare.app.domain.cv.FaceMetricCalculator
import com.selfcare.app.domain.cv.PixelRect
import com.selfcare.app.domain.cv.SkinSignalsHybridFusion
import com.selfcare.app.domain.cv.SkinSignalsPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinSignalsHybridFusionTest {

    @Test
    fun `decodeScores rejects short or non-finite arrays and clamps valid scores`() {
        assertNull(SkinSignalsHybridFusion.decodeScores(floatArrayOf(0.5f, 0.5f, 0.5f)))
        assertNull(SkinSignalsHybridFusion.decodeScores(floatArrayOf(0.5f, Float.NaN, 0.5f, 0.5f)))
        assertNull(SkinSignalsHybridFusion.decodeScores(floatArrayOf(0.5f, Float.POSITIVE_INFINITY, 0.5f, 0.5f)))

        val decoded = SkinSignalsHybridFusion.decodeScores(floatArrayOf(-0.2f, 0.42f, 0.88f, 1.35f))
        assertNotNull(decoded)
        assertEquals(0f, decoded!!.structureScore, 1e-4f)
        assertEquals(0.42f, decoded.hydrationScore, 1e-4f)
        assertEquals(0.88f, decoded.sunDamageScore, 1e-4f)
        assertEquals(1f, decoded.elasticityScore, 1e-4f)
    }

    @Test
    fun `fuseSkinMetrics returns exact CV values when AI signals are null`() {
        val fused = SkinSignalsHybridFusion.fuseSkinMetrics(
            cvUnderEyeDeltaLab = 12.4f,
            cvRednessIndex = 0.27f,
            cvTextureVariance = 15.8f,
            cvLipDrynessScore = 0.34f,
            aiSignals = null
        )
        assertEquals(12.4f, fused.underEyeDeltaLab, 1e-4f)
        assertEquals(0.27f, fused.skinRednessIndex, 1e-4f)
        assertEquals(15.8f, fused.skinTextureVariance, 1e-4f)
        assertEquals(0.34f, fused.lipDrynessScore, 1e-4f)
    }

    @Test
    fun `fuseSkinMetrics blends 1080px CV and EfficientNet signals with bounded outputs`() {
        val optimalAi = SkinSignalsPrediction(
            structureScore = 0.90f,
            hydrationScore = 0.90f,
            sunDamageScore = 0.90f,
            elasticityScore = 0.90f
        )
        val fused = SkinSignalsHybridFusion.fuseSkinMetrics(
            cvUnderEyeDeltaLab = 20f,
            cvRednessIndex = 0.40f,
            cvTextureVariance = 25f,
            cvLipDrynessScore = 0.50f,
            aiSignals = optimalAi
        )
        assertTrue(fused.underEyeDeltaLab in 13f..16f)
        assertTrue(fused.skinRednessIndex in 0.25f..0.32f)
        assertTrue(fused.skinTextureVariance in 16f..19f)
        assertTrue(fused.lipDrynessScore in 0.32f..0.38f)
    }

    @Test
    fun `directional Sobel edge density distinguishes bristled jaw from smooth shadowed jaw`() {
        val width = 40
        val height = 40
        val smoothShadowPixels = IntArray(width * height)
        val bristlePixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val isLowerJaw = y >= 20
                val smoothVal = if (isLowerJaw) 130 else 160
                smoothShadowPixels[y * width + x] = (0xFF shl 24) or (smoothVal shl 16) or (smoothVal shl 8) or smoothVal

                val bristleVal = if (isLowerJaw) {
                    if ((x + y) % 2 == 0) 95 else 175
                } else {
                    160
                }
                bristlePixels[y * width + x] = (0xFF shl 24) or (bristleVal shl 16) or (bristleVal shl 8) or bristleVal
            }
        }

        val cheekRoi = listOf(PixelRect(4, 2, 36, 18))
        val jawRoi = listOf(PixelRect(4, 22, 36, 38))

        val smoothStubble = FaceMetricCalculator.computeStubbleScore(
            pixels = smoothShadowPixels,
            width = width,
            height = height,
            jawAndChinRois = jawRoi,
            referenceCheekRois = cheekRoi
        )
        val bristledStubble = FaceMetricCalculator.computeStubbleScore(
            pixels = bristlePixels,
            width = width,
            height = height,
            jawAndChinRois = jawRoi,
            referenceCheekRois = cheekRoi
        )

        assertTrue("Smooth shadowed jaw should stay <= 0.24, got $smoothStubble", smoothStubble <= 0.24f)
        assertTrue("Bristled jaw ($bristledStubble) must score significantly higher than smooth shadow ($smoothStubble)", bristledStubble > smoothStubble + 0.35f)
    }
}

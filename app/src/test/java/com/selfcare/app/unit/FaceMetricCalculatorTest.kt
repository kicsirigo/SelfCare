package com.selfcare.app.unit

import com.selfcare.app.domain.cv.FaceMetricCalculator
import com.selfcare.app.domain.cv.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMetricCalculatorTest {

    @Test
    fun rgbToLabMatchesD65ReferenceColors() {
        val black = FaceMetricCalculator.rgbToLab(0, 0, 0)
        assertEquals(0f, black.l, 0.05f)
        assertEquals(0f, black.a, 0.05f)
        assertEquals(0f, black.b, 0.05f)

        val white = FaceMetricCalculator.rgbToLab(255, 255, 255)
        assertEquals(100f, white.l, 0.05f)
        assertEquals(0f, white.a, 0.05f)
        assertEquals(0f, white.b, 0.05f)

        val red = FaceMetricCalculator.rgbToLab(255, 0, 0)
        assertEquals(53.24f, red.l, 0.3f)
        assertEquals(80.09f, red.a, 0.5f)
        assertEquals(67.20f, red.b, 0.5f)
    }

    @Test
    fun underEyeDeltaLabReflectsDarkCirclesRelativeToCheekBaseline() {
        val width = 20
        val height = 20
        val skinColor = argb(215, 180, 155)
        val uniformPixels = IntArray(width * height) { skinColor }

        val leftUnderEye = PixelRect(2, 2, 7, 5)
        val rightUnderEye = PixelRect(12, 2, 17, 5)
        val leftCheek = PixelRect(2, 8, 7, 13)
        val rightCheek = PixelRect(12, 8, 17, 13)

        val uniformDelta = FaceMetricCalculator.computeUnderEyeDeltaLab(
            pixels = uniformPixels,
            width = width,
            height = height,
            leftUnderEyeRoi = leftUnderEye,
            rightUnderEyeRoi = rightUnderEye,
            leftCheekRoi = leftCheek,
            rightCheekRoi = rightCheek
        )
        assertEquals(0f, uniformDelta, 0.01f)

        val darkCirclePixels = uniformPixels.copyOf()
        val darkenedColor = argb(150, 120, 115)
        fillRect(darkCirclePixels, width, leftUnderEye, darkenedColor)
        fillRect(darkCirclePixels, width, rightUnderEye, darkenedColor)

        val darkenedDelta = FaceMetricCalculator.computeUnderEyeDeltaLab(
            pixels = darkCirclePixels,
            width = width,
            height = height,
            leftUnderEyeRoi = leftUnderEye,
            rightUnderEyeRoi = rightUnderEye,
            leftCheekRoi = leftCheek,
            rightCheekRoi = rightCheek
        )
        assertTrue("Expected noticeable positive delta for darkened under-eye, got $darkenedDelta", darkenedDelta > 15f)
    }

    @Test
    fun skinRednessIndexIncreasesWithErythema() {
        val width = 16
        val height = 16
        val roi = listOf(PixelRect(2, 2, 14, 14))

        val neutralGray = IntArray(width * height) { argb(140, 140, 140) }
        val normalSkin = IntArray(width * height) { argb(215, 180, 155) }
        val flushedSkin = IntArray(width * height) { argb(225, 125, 115) }

        val grayRedness = FaceMetricCalculator.computeSkinRednessIndex(neutralGray, width, height, roi)
        val normalRedness = FaceMetricCalculator.computeSkinRednessIndex(normalSkin, width, height, roi)
        val flushedRedness = FaceMetricCalculator.computeSkinRednessIndex(flushedSkin, width, height, roi)

        assertEquals(0f, grayRedness, 0.01f)
        assertTrue(normalRedness in 0.05f..0.35f)
        assertTrue(flushedRedness > normalRedness + 0.25f)
        assertTrue(flushedRedness <= 1f)
    }

    @Test
    fun skinTextureVarianceIgnoresSmoothGradientAndDetectsLocalRoughness() {
        val width = 20
        val height = 20
        val roi = listOf(PixelRect(2, 2, 18, 18))

        val uniformPixels = IntArray(width * height) { argb(180, 150, 130) }
        val uniformVariance = FaceMetricCalculator.computeSkinTextureVariance(uniformPixels, width, height, roi)
        assertEquals(0f, uniformVariance, 0.001f)

        val gradientPixels = IntArray(width * height) { idx ->
            val x = idx % width
            val c = 120 + x * 4
            argb(c, c, c)
        }
        val gradientVariance = FaceMetricCalculator.computeSkinTextureVariance(gradientPixels, width, height, roi)
        assertEquals(0f, gradientVariance, 0.01f)

        val roughPixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            val c = if ((x + y) % 2 == 0) 190 else 130
            argb(c, c - 20, c - 30)
        }
        val roughVariance = FaceMetricCalculator.computeSkinTextureVariance(roughPixels, width, height, roi)
        assertTrue("Expected positive local texture variance, got $roughVariance", roughVariance > 10f)
    }

    @Test
    fun lipDrynessScoreDistinguishesSmoothFromRoughPatchyLips() {
        val width = 20
        val height = 12
        val lipRoi = PixelRect(2, 2, 18, 10)

        val smoothLip = IntArray(width * height) { argb(195, 115, 115) }
        val smoothScore = FaceMetricCalculator.computeLipDrynessScore(smoothLip, width, height, lipRoi)
        assertEquals(0f, smoothScore, 0.001f)

        val chappedLip = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x + y) % 2 == 0) argb(230, 165, 160) else argb(135, 65, 70)
        }
        val chappedScore = FaceMetricCalculator.computeLipDrynessScore(chappedLip, width, height, lipRoi)
        assertTrue("Expected high lip dryness score for patchy/rough ROI, got $chappedScore", chappedScore > 0.5f)
        assertTrue(chappedScore <= 1f)
    }

    private fun fillRect(pixels: IntArray, width: Int, rect: PixelRect, color: Int) {
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                pixels[y * width + x] = color
            }
        }
    }

    private fun argb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }
}

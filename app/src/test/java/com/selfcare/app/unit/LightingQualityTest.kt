package com.selfcare.app.unit

import com.selfcare.app.domain.cv.LightingQualityChecker
import com.selfcare.app.domain.cv.LightingWarningReason
import com.selfcare.app.domain.cv.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LightingQualityTest {

    @Test
    fun balancedIlluminationReturnsOkAndAcceptableScore() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { argb(140, 132, 126) }

        val assessment = LightingQualityChecker.assess(pixels, width, height)

        assertEquals(LightingWarningReason.OK, assessment.warningReason)
        assertTrue(assessment.isAcceptable)
        assertTrue(assessment.lightingQualityScore >= 0.8f)
        assertTrue(assessment.leftRightAsymmetry < 0.05f)
    }

    @Test
    fun darkBufferReturnsTooDarkWarning() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { argb(24, 22, 20) }

        val assessment = LightingQualityChecker.assess(pixels, width, height)

        assertEquals(LightingWarningReason.TOO_DARK, assessment.warningReason)
        assertFalse(assessment.isAcceptable)
        assertTrue(assessment.lightingQualityScore < 0.5f)
        assertTrue(assessment.underExposedRatio > 0.9f)
    }

    @Test
    fun overexposedBufferReturnsTooBrightWarning() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { argb(248, 246, 244) }

        val assessment = LightingQualityChecker.assess(pixels, width, height)

        assertEquals(LightingWarningReason.TOO_BRIGHT, assessment.warningReason)
        assertFalse(assessment.isAcceptable)
        assertTrue(assessment.lightingQualityScore < 0.5f)
        assertTrue(assessment.overExposedRatio > 0.9f)
    }

    @Test
    fun halfLitBufferReturnsUnevenWarning() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { idx ->
            val x = idx % width
            if (x < width / 2) argb(215, 210, 205) else argb(55, 50, 48)
        }

        val assessment = LightingQualityChecker.assess(pixels, width, height)

        assertEquals(LightingWarningReason.UNEVEN, assessment.warningReason)
        assertFalse(assessment.isAcceptable)
        assertTrue(assessment.lightingQualityScore < 0.5f)
        assertTrue(assessment.leftRightAsymmetry > 0.5f)
    }

    @Test
    fun roiEvaluatesOnlyTargetRegionAndRejectsInvalidBuffers() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { argb(15, 15, 15) }
        val brightRoi = PixelRect(5, 5, 15, 15)
        for (y in brightRoi.top until brightRoi.bottom) {
            for (x in brightRoi.left until brightRoi.right) {
                pixels[y * width + x] = argb(135, 135, 135)
            }
        }

        val roiAssessment = LightingQualityChecker.assess(pixels, width, height, roi = brightRoi)
        assertEquals(LightingWarningReason.OK, roiAssessment.warningReason)
        assertTrue(roiAssessment.isAcceptable)

        val emptyAssessment = LightingQualityChecker.assess(IntArray(0), 0, 0)
        assertEquals(LightingWarningReason.TOO_DARK, emptyAssessment.warningReason)
        assertFalse(emptyAssessment.isAcceptable)
    }

    private fun argb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }
}

package com.selfcare.app.domain.cv

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

enum class LightingWarningReason {
    OK,
    TOO_DARK,
    TOO_BRIGHT,
    UNEVEN
}

data class LightingAssessment(
    val lightingQualityScore: Float,
    val isAcceptable: Boolean,
    val warningReason: LightingWarningReason,
    val meanLuminance: Float = 0f,
    val underExposedRatio: Float = 0f,
    val overExposedRatio: Float = 0f,
    val leftRightAsymmetry: Float = 0f
)

/**
 * Pure-Kotlin lighting quality evaluator over ARGB pixel buffers.
 * Evaluates mean luminance, under/over-exposure ratio, and left-vs-right half illumination asymmetry.
 */
object LightingQualityChecker {

    private const val UNDER_EXPOSED_LUMA_THRESHOLD = 0.15f
    private const val OVER_EXPOSED_LUMA_THRESHOLD = 0.92f

    private const val TOO_DARK_MEAN_THRESHOLD = 0.22f
    private const val TOO_DARK_RATIO_THRESHOLD = 0.45f
    private const val TOO_DARK_RATIO_MEAN_CAP = 0.35f

    private const val TOO_BRIGHT_MEAN_THRESHOLD = 0.85f
    private const val TOO_BRIGHT_RATIO_THRESHOLD = 0.40f
    private const val TOO_BRIGHT_RATIO_MEAN_FLOOR = 0.70f

    private const val UNEVEN_ABS_DIFF_THRESHOLD = 0.18f
    private const val UNEVEN_REL_ASYMMETRY_THRESHOLD = 0.35f
    private const val UNEVEN_REL_MIN_ABS_DIFF = 0.10f

    private const val IDEAL_MEAN_LUMA = 0.52f

    fun assess(bitmap: Bitmap, roi: PixelRect? = null): LightingAssessment {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return invalidAssessment()
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return assess(pixels = pixels, width = width, height = height, roi = roi)
    }

    fun assess(
        pixels: IntArray,
        width: Int,
        height: Int,
        roi: PixelRect? = null
    ): LightingAssessment {
        if (width <= 0 || height <= 0 || pixels.size < width * height) {
            return invalidAssessment()
        }

        val bounds = (roi ?: PixelRect(0, 0, width, height)).clampTo(width, height)
        if (bounds.isEmpty) {
            return invalidAssessment()
        }

        val midX = bounds.left + bounds.width / 2
        var sumLuma = 0.0
        var leftSumLuma = 0.0
        var rightSumLuma = 0.0
        var leftCount = 0
        var rightCount = 0
        var underExposedCount = 0
        var overExposedCount = 0
        var totalCount = 0

        for (y in bounds.top until bounds.bottom) {
            val rowOffset = y * width
            for (x in bounds.left until bounds.right) {
                val argb = pixels[rowOffset + x]
                val luma = relativeLuminance(argb)
                sumLuma += luma
                totalCount++

                if (luma < UNDER_EXPOSED_LUMA_THRESHOLD) {
                    underExposedCount++
                } else if (luma > OVER_EXPOSED_LUMA_THRESHOLD) {
                    overExposedCount++
                }

                if (x < midX) {
                    leftSumLuma += luma
                    leftCount++
                } else {
                    rightSumLuma += luma
                    rightCount++
                }
            }
        }

        if (totalCount == 0) {
            return invalidAssessment()
        }

        val meanLuma = (sumLuma / totalCount).toFloat()
        val underRatio = underExposedCount.toFloat() / totalCount
        val overRatio = overExposedCount.toFloat() / totalCount
        val leftMean = if (leftCount > 0) (leftSumLuma / leftCount).toFloat() else meanLuma
        val rightMean = if (rightCount > 0) (rightSumLuma / rightCount).toFloat() else meanLuma
        val absHalfDiff = abs(leftMean - rightMean)
        val relAsymmetry = absHalfDiff / max(meanLuma, 0.05f)

        val reason = when {
            meanLuma < TOO_DARK_MEAN_THRESHOLD ||
                (underRatio > TOO_DARK_RATIO_THRESHOLD && meanLuma < TOO_DARK_RATIO_MEAN_CAP) ->
                LightingWarningReason.TOO_DARK

            meanLuma > TOO_BRIGHT_MEAN_THRESHOLD ||
                (overRatio > TOO_BRIGHT_RATIO_THRESHOLD && meanLuma > TOO_BRIGHT_RATIO_MEAN_FLOOR) ->
                LightingWarningReason.TOO_BRIGHT

            absHalfDiff > UNEVEN_ABS_DIFF_THRESHOLD ||
                (relAsymmetry > UNEVEN_REL_ASYMMETRY_THRESHOLD && absHalfDiff > UNEVEN_REL_MIN_ABS_DIFF) ->
                LightingWarningReason.UNEVEN

            else -> LightingWarningReason.OK
        }

        val exposurePenalty = (underRatio + overRatio).coerceIn(0f, 1f)
        val luminanceDeviationPenalty = (abs(meanLuma - IDEAL_MEAN_LUMA) / 0.52f).coerceIn(0f, 1f)
        val asymmetryPenalty = (absHalfDiff / 0.40f).coerceIn(0f, 1f)

        val rawScore = (1f - (0.45f * luminanceDeviationPenalty + 0.30f * exposurePenalty + 0.25f * asymmetryPenalty))
            .coerceIn(0f, 1f)

        val isAcceptable = reason == LightingWarningReason.OK
        val finalScore = if (isAcceptable) {
            rawScore.coerceAtLeast(0.55f)
        } else {
            rawScore.coerceAtMost(0.49f)
        }

        return LightingAssessment(
            lightingQualityScore = finalScore,
            isAcceptable = isAcceptable,
            warningReason = reason,
            meanLuminance = meanLuma,
            underExposedRatio = underRatio,
            overExposedRatio = overRatio,
            leftRightAsymmetry = absHalfDiff
        )
    }

    internal fun relativeLuminance(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    }

    private fun invalidAssessment(): LightingAssessment = LightingAssessment(
        lightingQualityScore = 0f,
        isAcceptable = false,
        warningReason = LightingWarningReason.TOO_DARK
    )
}

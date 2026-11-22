package com.selfcare.app.domain.cv

import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Axis-aligned pixel rectangle in image coordinates ([left, right) x [top, bottom)).
 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun clampTo(imageWidth: Int, imageHeight: Int): PixelRect {
        val clampedLeft = left.coerceIn(0, imageWidth)
        val clampedTop = top.coerceIn(0, imageHeight)
        val clampedRight = right.coerceIn(clampedLeft, imageWidth)
        val clampedBottom = bottom.coerceIn(clampedTop, imageHeight)
        return PixelRect(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }
}

/**
 * Perceptually uniform CIELAB (D65) color value.
 */
data class LabColor(
    val l: Float,
    val a: Float,
    val b: Float
)

/**
 * Pure-Kotlin computer vision metric calculators operating on ARGB pixel buffers,
 * ROI rectangles, and foreground masks so JVM unit tests run without native Bitmaps.
 */
object FaceMetricCalculator {

    private const val D65_XN = 0.95047
    private const val D65_YN = 1.00000
    private const val D65_ZN = 1.08883
    private const val LAB_EPSILON = 216.0 / 24389.0
    private const val LAB_KAPPA_DIV_116 = 841.0 / 108.0
    private const val LAB_OFFSET = 4.0 / 29.0

    private const val REDNESS_LAB_A_MAX = 60f
    private const val LIP_COLOR_NON_UNIFORMITY_SCALE = 25f
    private const val LIP_GRADIENT_ROUGHNESS_SCALE = 20f
    private const val FOREGROUND_MASK_THRESHOLD = 0.5f

    /**
     * Converts an 0xAARRGGBB pixel into CIELAB (D65 standard illuminant, 2-degree observer).
     */
    fun rgbToLab(argb: Int): LabColor {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return rgbToLab(r, g, b)
    }

    /**
     * Converts 8-bit sRGB channels (0..255) into CIELAB (D65).
     */
    fun rgbToLab(r: Int, g: Int, b: Int): LabColor {
        val sr = srgbToLinear(r.coerceIn(0, 255) / 255.0)
        val sg = srgbToLinear(g.coerceIn(0, 255) / 255.0)
        val sb = srgbToLinear(b.coerceIn(0, 255) / 255.0)

        val x = (0.4124564 * sr + 0.3575761 * sg + 0.1804375 * sb) / D65_XN
        val y = (0.2126729 * sr + 0.7151522 * sg + 0.0721750 * sb) / D65_YN
        val z = (0.0193339 * sr + 0.1191920 * sg + 0.9503041 * sb) / D65_ZN

        val fx = labTransfer(x)
        val fy = labTransfer(y)
        val fz = labTransfer(z)

        val lStar = (116.0 * fy - 16.0).toFloat()
        val aStar = (500.0 * (fx - fy)).toFloat()
        val bStar = (200.0 * (fy - fz)).toFloat()
        return LabColor(l = lStar, a = aStar, b = bStar)
    }

    /**
     * Standard CIE76 Delta E perceptual distance between two CIELAB colors.
     */
    fun deltaE(first: LabColor, second: LabColor): Float {
        val dl = first.l - second.l
        val da = first.a - second.a
        val db = first.b - second.b
        return sqrt(dl * dl + da * da + db * db)
    }

    /**
     * Computes mean CIELAB color inside [roi], or null when [roi] has no valid pixels.
     */
    fun meanLab(pixels: IntArray, width: Int, height: Int, roi: PixelRect): LabColor? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val clamped = roi.clampTo(width, height)
        if (clamped.isEmpty) return null

        var sumL = 0.0
        var sumA = 0.0
        var sumB = 0.0
        var count = 0

        for (y in clamped.top until clamped.bottom) {
            val rowOffset = y * width
            for (x in clamped.left until clamped.right) {
                val lab = rgbToLab(pixels[rowOffset + x])
                sumL += lab.l
                sumA += lab.a
                sumB += lab.b
                count++
            }
        }
        if (count == 0) return null
        return LabColor(
            l = (sumL / count).toFloat(),
            a = (sumA / count).toFloat(),
            b = (sumB / count).toFloat()
        )
    }

    /**
     * Under-eye dark circle intensity: CIELAB luminance drop and chromatic shift between
     * left/right under-eye ROIs and their adjacent cheek baseline ROIs.
     */
    fun computeUnderEyeDeltaLab(
        pixels: IntArray,
        width: Int,
        height: Int,
        leftUnderEyeRoi: PixelRect,
        rightUnderEyeRoi: PixelRect,
        leftCheekRoi: PixelRect,
        rightCheekRoi: PixelRect
    ): Float {
        val leftDelta = singleSideUnderEyeDelta(pixels, width, height, leftUnderEyeRoi, leftCheekRoi)
        val rightDelta = singleSideUnderEyeDelta(pixels, width, height, rightUnderEyeRoi, rightCheekRoi)
        val values = listOfNotNull(leftDelta, rightDelta)
        return if (values.isEmpty()) 0f else values.average().toFloat()
    }

    /**
     * Erythema / skin redness index (0f..1f) derived from positive CIELAB a* across cheek and forehead ROIs.
     */
    fun computeSkinRednessIndex(
        pixels: IntArray,
        width: Int,
        height: Int,
        skinRois: List<PixelRect>
    ): Float {
        if (width <= 0 || height <= 0 || pixels.size < width * height || skinRois.isEmpty()) return 0f

        var sumA = 0.0
        var count = 0
        for (roi in skinRois) {
            val clamped = roi.clampTo(width, height)
            if (clamped.isEmpty) continue
            for (y in clamped.top until clamped.bottom) {
                val rowOffset = y * width
                for (x in clamped.left until clamped.right) {
                    val lab = rgbToLab(pixels[rowOffset + x])
                    sumA += lab.a.toDouble()
                    count++
                }
            }
        }
        if (count == 0) return 0f
        val meanA = (sumA / count).toFloat()
        return (meanA.coerceAtLeast(0f) / REDNESS_LAB_A_MAX).coerceIn(0f, 1f)
    }

    /**
     * Local spatial skin texture variance across cheek and forehead ROIs.
     * Uses high-pass residual (center luminance minus 4-neighbor local mean) so smooth
     * illumination gradients do not inflate skin texture readings.
     */
    fun computeSkinTextureVariance(
        pixels: IntArray,
        width: Int,
        height: Int,
        skinRois: List<PixelRect>
    ): Float {
        if (width <= 0 || height <= 0 || pixels.size < width * height || skinRois.isEmpty()) return 0f

        var sumSqResidual = 0.0
        var residualCount = 0

        for (roi in skinRois) {
            val clamped = roi.clampTo(width, height)
            if (clamped.isEmpty) continue

            if (clamped.width >= 3 && clamped.height >= 3) {
                for (y in (clamped.top + 1) until (clamped.bottom - 1)) {
                    val rowOffset = y * width
                    for (x in (clamped.left + 1) until (clamped.right - 1)) {
                        val center = luminance100(pixels[rowOffset + x])
                        val localMean = (
                            luminance100(pixels[rowOffset + x - 1]) +
                                luminance100(pixels[rowOffset + x + 1]) +
                                luminance100(pixels[(y - 1) * width + x]) +
                                luminance100(pixels[(y + 1) * width + x])
                            ) * 0.25f
                        val diff = center - localMean
                        sumSqResidual += (diff * diff).toDouble()
                        residualCount++
                    }
                }
            } else {
                // Fallback for very small ROIs: variance around ROI mean luminance
                var sum = 0.0
                var n = 0
                for (y in clamped.top until clamped.bottom) {
                    val rowOffset = y * width
                    for (x in clamped.left until clamped.right) {
                        sum += luminance100(pixels[rowOffset + x])
                        n++
                    }
                }
                if (n > 1) {
                    val mean = (sum / n).toFloat()
                    for (y in clamped.top until clamped.bottom) {
                        val rowOffset = y * width
                        for (x in clamped.left until clamped.right) {
                            val diff = luminance100(pixels[rowOffset + x]) - mean
                            sumSqResidual += (diff * diff).toDouble()
                            residualCount++
                        }
                    }
                }
            }
        }

        if (residualCount == 0) return 0f
        return (sumSqResidual / residualCount).toFloat().coerceAtLeast(0f)
    }

    /**
     * Lip dryness score (0f..1f) combining lip ROI CIELAB color/luminance non-uniformity
     * and local spatial gradient roughness.
     */
    fun computeLipDrynessScore(
        pixels: IntArray,
        width: Int,
        height: Int,
        lipRoi: PixelRect
    ): Float {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return 0f
        val clamped = lipRoi.clampTo(width, height)
        if (clamped.isEmpty) return 0f

        val count = clamped.width * clamped.height
        val lValues = FloatArray(count)
        val aValues = FloatArray(count)
        val bValues = FloatArray(count)

        var sumL = 0.0
        var sumA = 0.0
        var sumB = 0.0
        var idx = 0

        for (y in clamped.top until clamped.bottom) {
            val rowOffset = y * width
            for (x in clamped.left until clamped.right) {
                val lab = rgbToLab(pixels[rowOffset + x])
                lValues[idx] = lab.l
                aValues[idx] = lab.a
                bValues[idx] = lab.b
                sumL += lab.l
                sumA += lab.a
                sumB += lab.b
                idx++
            }
        }

        val meanL = (sumL / count).toFloat()
        val meanA = (sumA / count).toFloat()
        val meanB = (sumB / count).toFloat()

        var varL = 0.0
        var varA = 0.0
        var varB = 0.0
        for (i in 0 until count) {
            val dl = lValues[i] - meanL
            val da = aValues[i] - meanA
            val db = bValues[i] - meanB
            varL += dl * dl
            varA += da * da
            varB += db * db
        }
        val colorDispersion = sqrt(
            (varL / count).toFloat() +
                0.5f * (varA / count).toFloat() +
                0.5f * (varB / count).toFloat()
        )
        val nonUniformityScore = (colorDispersion / LIP_COLOR_NON_UNIFORMITY_SCALE).coerceIn(0f, 1f)

        var gradSum = 0.0
        var gradCount = 0
        val w = clamped.width
        val h = clamped.height
        for (ry in 0 until h) {
            val rowBase = ry * w
            for (rx in 0 until w) {
                val curL = lValues[rowBase + rx]
                if (rx + 1 < w) {
                    gradSum += kotlin.math.abs(lValues[rowBase + rx + 1] - curL).toDouble()
                    gradCount++
                }
                if (ry + 1 < h) {
                    gradSum += kotlin.math.abs(lValues[(ry + 1) * w + rx] - curL).toDouble()
                    gradCount++
                }
            }
        }
        val meanGradient = if (gradCount > 0) (gradSum / gradCount).toFloat() else 0f
        val roughnessScore = (meanGradient / LIP_GRADIENT_ROUGHNESS_SCALE).coerceIn(0f, 1f)

        return (0.5f * nonUniformityScore + 0.5f * roughnessScore).coerceIn(0f, 1f)
    }

    /**
     * Computes hair-to-face ratio from a foreground segmentation confidence mask (0f..1f)
     * by measuring hair mask area and vertical extent above & beside the face box relative to face size.
     */
    fun computeHairToFaceRatio(
        foregroundMask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        faceBox: PixelRect
    ): Float {
        if (maskWidth <= 0 || maskHeight <= 0 ||
            imageWidth <= 0 || imageHeight <= 0 ||
            foregroundMask.size < maskWidth * maskHeight
        ) {
            return 0f
        }

        val clampedFace = faceBox.clampTo(imageWidth, imageHeight)
        if (clampedFace.isEmpty) return 0f

        val faceWidth = clampedFace.width
        val faceHeight = clampedFace.height
        val searchLeft = (clampedFace.left - faceWidth * 0.6f).toInt().coerceAtLeast(0)
        val searchRight = (clampedFace.right + faceWidth * 0.6f).toInt().coerceAtMost(imageWidth)
        val searchTop = 0
        val searchBottom = clampedFace.bottom.coerceIn(0, imageHeight)

        if (searchRight <= searchLeft || searchBottom <= searchTop) return 0f

        var hairPixelCount = 0
        var minHairY = clampedFace.top

        for (y in searchTop until searchBottom) {
            val maskY = ((y.toFloat() / imageHeight) * maskHeight).toInt().coerceIn(0, maskHeight - 1)
            val maskRowOffset = maskY * maskWidth
            for (x in searchLeft until searchRight) {
                val insideFaceBox =
                    x >= clampedFace.left && x < clampedFace.right && y >= clampedFace.top
                if (insideFaceBox) continue

                val maskX = ((x.toFloat() / imageWidth) * maskWidth).toInt().coerceIn(0, maskWidth - 1)
                if (foregroundMask[maskRowOffset + maskX] >= FOREGROUND_MASK_THRESHOLD) {
                    hairPixelCount++
                    if (y < minHairY) {
                        minHairY = y
                    }
                }
            }
        }

        val faceArea = (faceWidth * faceHeight).toFloat()
        val areaRatio = hairPixelCount.toFloat() / faceArea
        val aboveExtentRatio = (clampedFace.top - minHairY).coerceAtLeast(0).toFloat() / faceHeight.toFloat()
        return (0.6f * areaRatio + 0.4f * aboveExtentRatio).coerceAtLeast(0f)
    }

    private fun singleSideUnderEyeDelta(
        pixels: IntArray,
        width: Int,
        height: Int,
        underEyeRoi: PixelRect,
        cheekRoi: PixelRect
    ): Float? {
        val underEyeLab = meanLab(pixels, width, height, underEyeRoi) ?: return null
        val cheekLab = meanLab(pixels, width, height, cheekRoi) ?: return null
        val lDrop = (cheekLab.l - underEyeLab.l).coerceAtLeast(0f)
        val chromaShift = hypot(cheekLab.a - underEyeLab.a, cheekLab.b - underEyeLab.b)
        // If under-eye is brighter or identical in luminance and color, keep delta focused on darkening/tint
        return if (lDrop == 0f && chromaShift < 1e-3f) {
            0f
        } else if (cheekLab.l >= underEyeLab.l) {
            sqrt(lDrop * lDrop + chromaShift * chromaShift)
        } else {
            // Under-eye is brighter than cheek (e.g. highlight); reflect only chromatic difference lightly
            chromaShift * 0.5f
        }
    }

    private fun srgbToLinear(channel: Double): Double {
        return if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun labTransfer(t: Double): Double {
        return if (t > LAB_EPSILON) {
            cbrt(t)
        } else {
            LAB_KAPPA_DIV_116 * t + LAB_OFFSET
        }
    }

    private fun luminance100(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return ((0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f) * 100f
    }
}

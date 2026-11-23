package com.selfcare.app.data.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.selfcare.app.domain.cv.FaceMetricCalculator
import com.selfcare.app.domain.cv.LightingQualityChecker
import com.selfcare.app.domain.cv.LightingWarningReason
import com.selfcare.app.domain.cv.PixelRect
import com.selfcare.app.domain.ml.FaceAnalysisResult
import com.selfcare.app.domain.ml.FaceAnalyzer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bundled offline ML Kit FaceAnalyzer combining FaceMeshDetection for facial ROIs,
 * SelfieSegmentation (SINGLE_IMAGE_MODE) for hair/foreground mask extraction,
 * LightingQualityChecker, and pure-Kotlin FaceMetricCalculator.
 */
class MlKitFaceAnalyzer(
    private val acneDetector: OnnxAcneDetector? = null
) : FaceAnalyzer {

    private val faceMeshDetector: FaceMeshDetector by lazy {
        val options = FaceMeshDetectorOptions.Builder()
            .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
            .build()
        FaceMeshDetection.getClient(options)
    }

    private val segmenter: Segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    override suspend fun isFacePresent(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false
        val work = downscaleIfNeeded(bitmap, maxDim = 480)
        return try {
            detectPrimaryFaceMultiRotation(work) != null
        } catch (_: Throwable) {
            false
        } finally {
            if (work !== bitmap) work.recycle()
        }
    }

    private suspend fun detectPrimaryFaceMultiRotation(bitmap: Bitmap): FaceMesh? {
        for (rotation in intArrayOf(0, 270, 90, 180)) {
            val meshes = runCatching {
                faceMeshDetector.process(InputImage.fromBitmap(bitmap, rotation)).awaitResult()
            }.getOrNull()
            val primary = meshes?.maxByOrNull {
                it.boundingBox.width().coerceAtLeast(0) * it.boundingBox.height().coerceAtLeast(0)
            }
            if (primary != null && primary.boundingBox.width() > 20 && primary.boundingBox.height() > 20) {
                return primary
            }
        }
        return null
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim || largest <= 0) return bitmap
        val scale = maxDim.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    override suspend fun analyze(bitmap: Bitmap): FaceAnalysisResult {
        val startMs = System.currentTimeMillis()
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return FaceAnalysisResult(
                underEyeDeltaLab = 0f,
                skinRednessIndex = 0f,
                skinTextureVariance = 0f,
                lipDrynessScore = 0f,
                hairToFaceRatio = 0f,
                lightingQualityScore = 0f,
                isLightingAcceptable = false,
                lightingWarningReason = LightingWarningReason.TOO_DARK.name
            )
        }

        // Downscale huge camera captures (e.g. 12MP+) to 1080px to preserve pores, blemishes & stubble while avoiding OOM
        val workBitmap = downscaleIfNeeded(bitmap, maxDim = 1080)
        val width = workBitmap.width
        val height = workBitmap.height

        try {
            val pixels = IntArray(width * height)
            workBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val inputImage = InputImage.fromBitmap(workBitmap, 0)

            val (primaryFace, maskResult) = coroutineScope {
                val meshDeferred = async { detectPrimaryFaceMultiRotation(workBitmap) }
                val segDeferred = async { runCatching { segmenter.process(inputImage).awaitResult() } }
                meshDeferred.await() to segDeferred.await()
            }

            // Use detected face mesh box, or fall back to the guided center oval ROI so Trends always receives real metrics
            val detectedRect = primaryFace?.boundingBox?.toPixelRect()?.clampTo(width, height)?.takeIf { !it.isEmpty }
            val facePixelRect = detectedRect ?: PixelRect(
                left = (width * 0.20f).roundToInt(),
                top = (height * 0.18f).roundToInt(),
                right = (width * 0.80f).roundToInt(),
                bottom = (height * 0.82f).roundToInt()
            ).clampTo(width, height)

            val lightingAssessment = LightingQualityChecker.assess(
                pixels = pixels,
                width = width,
                height = height,
                roi = facePixelRect.takeIf { !it.isEmpty }
            )

            val rois = if (primaryFace != null && detectedRect != null) {
                extractFacialRois(primaryFace, facePixelRect, width, height)
            } else {
                extractFallbackOvalRois(facePixelRect, width, height)
            }

            val underEyeDeltaLab = FaceMetricCalculator.computeUnderEyeDeltaLab(
                pixels = pixels,
                width = width,
                height = height,
                leftUnderEyeRoi = rois.leftUnderEye,
                rightUnderEyeRoi = rois.rightUnderEye,
                leftCheekRoi = rois.leftCheek,
                rightCheekRoi = rois.rightCheek
            )

            val skinRois = listOf(rois.leftCheek, rois.rightCheek, rois.forehead)
            val chinRoi = PixelRect(
                left = facePixelRect.left + (facePixelRect.width * 0.28f).roundToInt(),
                top = (rois.lip.bottom + 2).coerceAtMost(facePixelRect.bottom - 2),
                right = facePixelRect.left + (facePixelRect.width * 0.72f).roundToInt(),
                bottom = facePixelRect.bottom
            ).clampTo(width, height)
            val blemishRois = if (!chinRoi.isEmpty) skinRois + chinRoi else skinRois

            val skinRednessIndex = FaceMetricCalculator.computeSkinRednessIndex(
                pixels = pixels,
                width = width,
                height = height,
                skinRois = skinRois
            )
            val skinTextureVariance = FaceMetricCalculator.computeSkinTextureVariance(
                pixels = pixels,
                width = width,
                height = height,
                skinRois = skinRois
            )
            val onnxBlemishes = acneDetector?.detectOnFace(workBitmap, facePixelRect)
            val blemishEngine = if (onnxBlemishes != null) "ONNX_YOLOV8S" else "CIELAB_CV"
            val blemishMetrics = onnxBlemishes ?: FaceMetricCalculator.computeBlemishMetrics(
                pixels = pixels,
                width = width,
                height = height,
                skinRois = blemishRois
            )
            val leftJawRoi = PixelRect(
                left = facePixelRect.left + (facePixelRect.width * 0.14f).roundToInt(),
                top = (rois.lip.top).coerceAtMost(facePixelRect.bottom - 2),
                right = facePixelRect.left + (facePixelRect.width * 0.34f).roundToInt(),
                bottom = facePixelRect.bottom
            ).clampTo(width, height)
            val rightJawRoi = PixelRect(
                left = facePixelRect.left + (facePixelRect.width * 0.66f).roundToInt(),
                top = (rois.lip.top).coerceAtMost(facePixelRect.bottom - 2),
                right = facePixelRect.left + (facePixelRect.width * 0.86f).roundToInt(),
                bottom = facePixelRect.bottom
            ).clampTo(width, height)
            val jawAndChinRois = listOf(chinRoi, leftJawRoi, rightJawRoi).filter { !it.isEmpty }
            val stubbleScore = FaceMetricCalculator.computeStubbleScore(
                pixels = pixels,
                width = width,
                height = height,
                jawAndChinRois = jawAndChinRois,
                referenceCheekRois = listOf(rois.leftCheek, rois.rightCheek)
            )
            val lipDrynessScore = FaceMetricCalculator.computeLipDrynessScore(
                pixels = pixels,
                width = width,
                height = height,
                lipRoi = rois.lip
            )

            val hairDetails = maskResult.getOrNull()?.let { mask ->
                val buffer = mask.buffer
                buffer.rewind()
                val floatBuffer = buffer.asFloatBuffer()
                val maskValues = FloatArray(mask.width * mask.height)
                floatBuffer.get(maskValues)
                FaceMetricCalculator.computeDetailedHairMetrics(
                    pixels = pixels,
                    foregroundMask = maskValues,
                    maskWidth = mask.width,
                    maskHeight = mask.height,
                    imageWidth = width,
                    imageHeight = height,
                    faceBox = facePixelRect
                )
            }

            return FaceAnalysisResult(
                underEyeDeltaLab = underEyeDeltaLab,
                skinRednessIndex = skinRednessIndex,
                skinTextureVariance = skinTextureVariance,
                lipDrynessScore = lipDrynessScore,
                hairToFaceRatio = hairDetails?.hairToFaceRatio ?: 0f,
                lightingQualityScore = lightingAssessment.lightingQualityScore,
                isLightingAcceptable = true,
                lightingWarningReason = null,
                hairCrownVolumeScore = hairDetails?.crownVolumeScore ?: 0f,
                hairShineScore = hairDetails?.shineSmoothnessScore ?: 0f,
                hairForeheadCoverage = hairDetails?.foreheadCoverageRatio ?: 0f,
                hairTempleSymmetryScore = hairDetails?.templeSymmetryScore ?: 0f,
                blemishScore = blemishMetrics.blemishScore,
                blemishCount = blemishMetrics.estimatedSpotCount,
                stubbleScore = stubbleScore,
                blemishEngineUsed = blemishEngine,
                faceMeshUsed = (primaryFace != null && detectedRect != null),
                hairSegmentationUsed = (hairDetails != null),
                analysisDurationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            )
        } finally {
            if (workBitmap !== bitmap) {
                workBitmap.recycle()
            }
        }
    }

    private fun extractFallbackOvalRois(
        faceRect: PixelRect,
        imageWidth: Int,
        imageHeight: Int
    ): FacialRois {
        val faceW = faceRect.width
        val faceH = faceRect.height
        val leftEyeBox = PixelRect(
            left = faceRect.left + (faceW * 0.56f).roundToInt(),
            top = faceRect.top + (faceH * 0.32f).roundToInt(),
            right = faceRect.left + (faceW * 0.80f).roundToInt(),
            bottom = faceRect.top + (faceH * 0.42f).roundToInt()
        ).clampTo(imageWidth, imageHeight)
        val rightEyeBox = PixelRect(
            left = faceRect.left + (faceW * 0.20f).roundToInt(),
            top = faceRect.top + (faceH * 0.32f).roundToInt(),
            right = faceRect.left + (faceW * 0.44f).roundToInt(),
            bottom = faceRect.top + (faceH * 0.42f).roundToInt()
        ).clampTo(imageWidth, imageHeight)
        val leftUnderEye = buildUnderEyeRoi(leftEyeBox, imageWidth, imageHeight)
        val rightUnderEye = buildUnderEyeRoi(rightEyeBox, imageWidth, imageHeight)
        val leftCheek = buildCheekRoi(leftUnderEye, leftEyeBox, faceH, imageWidth, imageHeight)
        val rightCheek = buildCheekRoi(rightUnderEye, rightEyeBox, faceH, imageWidth, imageHeight)
        val forehead = PixelRect(
            left = faceRect.left + (faceW * 0.26f).roundToInt(),
            top = faceRect.top + (faceH * 0.10f).roundToInt(),
            right = faceRect.left + (faceW * 0.74f).roundToInt(),
            bottom = faceRect.top + (faceH * 0.26f).roundToInt()
        ).clampTo(imageWidth, imageHeight)
        val lip = PixelRect(
            left = faceRect.left + (faceW * 0.32f).roundToInt(),
            top = faceRect.top + (faceH * 0.68f).roundToInt(),
            right = faceRect.left + (faceW * 0.68f).roundToInt(),
            bottom = faceRect.top + (faceH * 0.80f).roundToInt()
        ).clampTo(imageWidth, imageHeight)
        return FacialRois(
            leftUnderEye = leftUnderEye,
            rightUnderEye = rightUnderEye,
            leftCheek = leftCheek,
            rightCheek = rightCheek,
            forehead = forehead,
            lip = lip
        )
    }

    private fun extractFacialRois(
        face: FaceMesh,
        faceRect: PixelRect,
        imageWidth: Int,
        imageHeight: Int
    ): FacialRois {
        val faceW = faceRect.width
        val faceH = faceRect.height

        val leftEyeBox = pointsBoundingBox(face.getPoints(FaceMesh.LEFT_EYE))
            ?.clampTo(imageWidth, imageHeight)
            ?.takeIf { !it.isEmpty }
            ?: PixelRect(
                left = faceRect.left + (faceW * 0.58f).roundToInt(),
                top = faceRect.top + (faceH * 0.30f).roundToInt(),
                right = faceRect.left + (faceW * 0.82f).roundToInt(),
                bottom = faceRect.top + (faceH * 0.40f).roundToInt()
            )

        val rightEyeBox = pointsBoundingBox(face.getPoints(FaceMesh.RIGHT_EYE))
            ?.clampTo(imageWidth, imageHeight)
            ?.takeIf { !it.isEmpty }
            ?: PixelRect(
                left = faceRect.left + (faceW * 0.18f).roundToInt(),
                top = faceRect.top + (faceH * 0.30f).roundToInt(),
                right = faceRect.left + (faceW * 0.42f).roundToInt(),
                bottom = faceRect.top + (faceH * 0.40f).roundToInt()
            )

        val leftUnderEye = buildUnderEyeRoi(leftEyeBox, imageWidth, imageHeight)
        val rightUnderEye = buildUnderEyeRoi(rightEyeBox, imageWidth, imageHeight)
        val leftCheek = buildCheekRoi(leftUnderEye, leftEyeBox, faceH, imageWidth, imageHeight)
        val rightCheek = buildCheekRoi(rightUnderEye, rightEyeBox, faceH, imageWidth, imageHeight)

        val ovalBox = pointsBoundingBox(face.getPoints(FaceMesh.FACE_OVAL))
        val leftBrowBox = pointsBoundingBox(face.getPoints(FaceMesh.LEFT_EYEBROW_TOP))
        val rightBrowBox = pointsBoundingBox(face.getPoints(FaceMesh.RIGHT_EYEBROW_TOP))

        val ovalTop = ovalBox?.top ?: faceRect.top
        val browTop = listOfNotNull(leftBrowBox?.top, rightBrowBox?.top).minOrNull()
            ?: (faceRect.top + (faceH * 0.24f).roundToInt())
        val foreheadTop = (ovalTop + ((browTop - ovalTop) * 0.25f)).roundToInt()
        val foreheadBottom = (browTop - max(2, faceH / 30)).coerceAtLeast(foreheadTop + 3)

        val leftCenterX = (leftEyeBox.left + leftEyeBox.right) / 2
        val rightCenterX = (rightEyeBox.left + rightEyeBox.right) / 2
        val foreheadLeft = min(leftCenterX, rightCenterX)
        val foreheadRight = max(leftCenterX, rightCenterX).coerceAtLeast(foreheadLeft + 4)

        val forehead = PixelRect(
            left = foreheadLeft,
            top = foreheadTop,
            right = foreheadRight,
            bottom = foreheadBottom
        ).clampTo(imageWidth, imageHeight)

        val lipPoints = buildList {
            addAll(face.getPoints(FaceMesh.UPPER_LIP_TOP))
            addAll(face.getPoints(FaceMesh.UPPER_LIP_BOTTOM))
            addAll(face.getPoints(FaceMesh.LOWER_LIP_TOP))
            addAll(face.getPoints(FaceMesh.LOWER_LIP_BOTTOM))
        }
        val lip = pointsBoundingBox(lipPoints)
            ?.clampTo(imageWidth, imageHeight)
            ?.takeIf { !it.isEmpty }
            ?: PixelRect(
                left = faceRect.left + (faceW * 0.32f).roundToInt(),
                top = faceRect.top + (faceH * 0.72f).roundToInt(),
                right = faceRect.left + (faceW * 0.68f).roundToInt(),
                bottom = faceRect.top + (faceH * 0.85f).roundToInt()
            ).clampTo(imageWidth, imageHeight)

        return FacialRois(
            leftUnderEye = leftUnderEye,
            rightUnderEye = rightUnderEye,
            leftCheek = leftCheek,
            rightCheek = rightCheek,
            forehead = forehead,
            lip = lip
        )
    }

    private fun buildUnderEyeRoi(
        eyeBox: PixelRect,
        imageWidth: Int,
        imageHeight: Int
    ): PixelRect {
        val eyeH = max(4, eyeBox.height)
        val top = eyeBox.bottom + max(1, eyeH / 4)
        val bottom = eyeBox.bottom + max(3, (eyeH * 1.4f).roundToInt())
        return PixelRect(
            left = eyeBox.left,
            top = top,
            right = eyeBox.right,
            bottom = bottom
        ).clampTo(imageWidth, imageHeight)
    }

    private fun buildCheekRoi(
        underEyeBox: PixelRect,
        eyeBox: PixelRect,
        faceHeight: Int,
        imageWidth: Int,
        imageHeight: Int
    ): PixelRect {
        val eyeH = max(4, eyeBox.height)
        val top = underEyeBox.bottom + max(1, eyeH / 3)
        val bottom = top + max(4, (faceHeight * 0.16f).roundToInt())
        return PixelRect(
            left = eyeBox.left,
            top = top,
            right = eyeBox.right,
            bottom = bottom
        ).clampTo(imageWidth, imageHeight)
    }

    private fun pointsBoundingBox(points: List<FaceMeshPoint>): PixelRect? {
        if (points.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (pt in points) {
            val pos = pt.position
            if (pos.x < minX) minX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.x > maxX) maxX = pos.x
            if (pos.y > maxY) maxY = pos.y
        }
        return PixelRect(
            left = minX.roundToInt(),
            top = minY.roundToInt(),
            right = maxX.roundToInt().coerceAtLeast(minX.roundToInt() + 1),
            bottom = maxY.roundToInt().coerceAtLeast(minY.roundToInt() + 1)
        )
    }

    private fun Rect.toPixelRect(): PixelRect = PixelRect(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )

    private data class FacialRois(
        val leftUnderEye: PixelRect,
        val rightUnderEye: PixelRect,
        val leftCheek: PixelRect,
        val rightCheek: PixelRect,
        val forehead: PixelRect,
        val lip: PixelRect
    )

    companion object {
        const val NO_FACE_DETECTED_REASON = "NO_FACE_DETECTED"
    }
}

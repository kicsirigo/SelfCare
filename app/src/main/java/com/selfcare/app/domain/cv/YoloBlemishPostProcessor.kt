package com.selfcare.app.domain.cv

import kotlin.math.max
import kotlin.math.min

data class BlemishBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

/**
 * Pure-Kotlin post-processor for single-class YOLOv8 ONNX output tensor of shape [1, 5, N]
 * (channels: cx, cy, w, h, confidence). Applies confidence thresholding and greedy IoU NMS.
 */
object YoloBlemishPostProcessor {

    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.26f
    const val DEFAULT_IOU_THRESHOLD = 0.45f

    fun decode(
        output: Array<FloatArray>,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
        iouThreshold: Float = DEFAULT_IOU_THRESHOLD
    ): BlemishMetrics {
        if (output.size < 5) return BlemishMetrics(0f, 0)
        val cxRow = output[0]
        val cyRow = output[1]
        val wRow = output[2]
        val hRow = output[3]
        val confRow = output[4]
        val count = minOf(cxRow.size, cyRow.size, wRow.size, hRow.size, confRow.size)
        if (count == 0) return BlemishMetrics(0f, 0)

        val candidates = ArrayList<BlemishBox>()
        for (i in 0 until count) {
            val conf = confRow[i]
            if (conf < confidenceThreshold) continue
            val w = wRow[i]
            val h = hRow[i]
            if (w <= 1f || h <= 1f) continue
            val cx = cxRow[i]
            val cy = cyRow[i]
            candidates.add(
                BlemishBox(
                    left = cx - w * 0.5f,
                    top = cy - h * 0.5f,
                    right = cx + w * 0.5f,
                    bottom = cy + h * 0.5f,
                    confidence = conf.coerceIn(0f, 1f)
                )
            )
        }

        if (candidates.isEmpty()) return BlemishMetrics(0f, 0)

        candidates.sortByDescending { it.confidence }
        val kept = ArrayList<BlemishBox>()
        for (box in candidates) {
            var suppressed = false
            for (accepted in kept) {
                if (iou(box, accepted) > iouThreshold) {
                    suppressed = true
                    break
                }
            }
            if (!suppressed) {
                kept.add(box)
            }
        }

        var severitySum = 0f
        for (box in kept) {
            // Higher-confidence lesions contribute slightly more severity
            severitySum += (0.55f + box.confidence * 0.75f)
        }
        val normalizedScore = (severitySum / 10f).coerceIn(0f, 1f)
        return BlemishMetrics(
            blemishScore = normalizedScore,
            estimatedSpotCount = kept.size
        )
    }

    internal fun iou(a: BlemishBox, b: BlemishBox): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = (interRight - interLeft).coerceAtLeast(0f)
        val interH = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interW * interH
        if (interArea <= 0f) return 0f
        val areaA = ((a.right - a.left).coerceAtLeast(0f)) * ((a.bottom - a.top).coerceAtLeast(0f))
        val areaB = ((b.right - b.left).coerceAtLeast(0f)) * ((b.bottom - b.top).coerceAtLeast(0f))
        val union = areaA + areaB - interArea
        return if (union > 0f) interArea / union else 0f
    }
}

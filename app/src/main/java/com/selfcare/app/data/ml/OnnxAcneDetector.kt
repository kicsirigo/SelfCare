package com.selfcare.app.data.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.selfcare.app.BuildConfig
import com.selfcare.app.domain.cv.BlemishMetrics
import com.selfcare.app.domain.cv.PixelRect
import com.selfcare.app.domain.cv.YoloBlemishPostProcessor
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * 100% offline on-device YOLOv8s INT8 acne/blemish lesion detector running via ONNX Runtime.
 * Model: mufasabrownie/glowlytics-skin-models acne_detector (MIT license), dynamically INT8-quantized.
 * Gracefully returns null if native ONNX Runtime or asset is unavailable (allowing CIELAB fallback).
 */
class OnnxAcneDetector(private val context: Context) {

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var initAttempted: Boolean = false

    private fun ensureSession(): OrtSession? {
        session?.let { return it }
        if (initAttempted) return null
        synchronized(this) {
            session?.let { return it }
            if (initAttempted) return null
            initAttempted = true
            return runCatching {
                val versionedName = "acne_detector_v${BuildConfig.VERSION_CODE}.onnx"
                val modelFile = File(context.filesDir, versionedName)
                if (!modelFile.exists() || modelFile.length() == 0L) {
                    // Clean up any older extracted ONNX model versions so stale weights are never retained
                    context.filesDir.listFiles { _, name ->
                        name.startsWith("acne_detector") &&
                            name.endsWith(".onnx") &&
                            name != versionedName
                    }?.forEach { oldFile ->
                        runCatching { oldFile.delete() }
                    }
                    val tempFile = File(context.filesDir, "$versionedName.tmp")
                    try {
                        context.assets.open(ASSET_NAME).use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (!tempFile.renameTo(modelFile)) {
                            tempFile.copyTo(modelFile, overwrite = true)
                            tempFile.delete()
                        }
                    } catch (e: Throwable) {
                        runCatching { tempFile.delete() }
                        runCatching { modelFile.delete() }
                        throw e
                    }
                }
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                }
                env.createSession(modelFile.absolutePath, opts).also { created ->
                    session = created
                }
            }.getOrNull()
        }
    }

    fun detectOnFace(bitmap: Bitmap, faceRect: PixelRect): BlemishMetrics? {
        val ortSession = ensureSession() ?: return null
        return runCatching {
            val padX = (faceRect.width * 0.08f).roundToInt()
            val padY = (faceRect.height * 0.08f).roundToInt()
            val cropRect = PixelRect(
                left = faceRect.left - padX,
                top = faceRect.top - padY,
                right = faceRect.right + padX,
                bottom = faceRect.bottom + padY
            ).clampTo(bitmap.width, bitmap.height)
            if (cropRect.width < 16 || cropRect.height < 16) return null

            val cropped = Bitmap.createBitmap(
                bitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width,
                cropRect.height
            )
            val scaled = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
            if (cropped !== bitmap) cropped.recycle()

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            scaled.recycle()

            val planeSize = INPUT_SIZE * INPUT_SIZE
            val floatArray = FloatArray(3 * planeSize)
            val inv255 = 1f / 255f
            for (i in 0 until planeSize) {
                val px = pixels[i]
                floatArray[i] = ((px ushr 16) and 0xFF) * inv255
                floatArray[planeSize + i] = ((px ushr 8) and 0xFF) * inv255
                floatArray[2 * planeSize + i] = (px and 0xFF) * inv255
            }

            val env = OrtEnvironment.getEnvironment()
            val inputName = ortSession.inputNames.firstOrNull() ?: "images"
            val shape = longArrayOf(1L, 3L, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape).use { tensor ->
                ortSession.run(mapOf(inputName to tensor)).use { results ->
                    val raw = results[0].value
                    @Suppress("UNCHECKED_CAST")
                    val batch = raw as? Array<Array<FloatArray>> ?: return null
                    if (batch.isEmpty()) return null
                    YoloBlemishPostProcessor.decode(batch[0])
                }
            }
        }.getOrNull()
    }

    companion object {
        const val ASSET_NAME = "acne_detector_int8.onnx"
        const val INPUT_SIZE = 640
    }
}

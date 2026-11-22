package com.selfcare.app.ui.shelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

data class PendingScanCapture(
    val file: File,
    val uri: Uri
)

/**
 * Creates app-private temporary JPEG files in cacheDir for full-resolution
 * TakePicture captures (fixing low-res thumbnail blur for barcodes/OCR) and
 * immediately deletes them after decoding so product photos never persist.
 */
object ScanPhotoHelper {

    private const val SCAN_TMP_DIR = "scan_tmp"

    fun createTempScanCapture(context: Context): PendingScanCapture {
        val dir = File(context.cacheDir, SCAN_TMP_DIR).apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return PendingScanCapture(file = file, uri = uri)
    }

    fun decodeAndDeleteTempPhoto(file: File?, maxDimensionPx: Int = 1600): Bitmap? {
        if (file == null) return null
        return try {
            if (!file.exists() || file.length() <= 0L) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null

            var sampleSize = 1
            while ((width / sampleSize) > maxDimensionPx || (height / sampleSize) > maxDimensionPx) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            applyExifRotation(decoded, file.absolutePath)
        } catch (_: Exception) {
            null
        } finally {
            cleanupTempFile(file)
        }
    }

    fun cleanupTempFile(file: File?) {
        if (file == null) return
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, path: String): Bitmap {
        val degrees = runCatching {
            val exif = ExifInterface(path)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}

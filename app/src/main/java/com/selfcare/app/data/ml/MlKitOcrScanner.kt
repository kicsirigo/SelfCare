package com.selfcare.app.data.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.selfcare.app.domain.ml.OcrScanner

class MlKitOcrScanner : OcrScanner {

    private val client by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(bitmap: Bitmap): String {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = client.process(image).awaitResult()
            visionText.text.trim()
        }.getOrDefault("")
    }
}

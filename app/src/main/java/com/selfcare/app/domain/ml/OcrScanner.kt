package com.selfcare.app.domain.ml

import android.graphics.Bitmap

interface OcrScanner {
    suspend fun recognizeText(bitmap: Bitmap): String
}

class StubOcrScanner : OcrScanner {
    override suspend fun recognizeText(bitmap: Bitmap): String = ""
}

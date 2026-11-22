package com.selfcare.app.domain.ml

import android.graphics.Bitmap

interface BarcodeScanner {
    suspend fun scanBarcode(bitmap: Bitmap): String?
}

class StubBarcodeScanner : BarcodeScanner {
    override suspend fun scanBarcode(bitmap: Bitmap): String? = null
}

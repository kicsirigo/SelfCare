package com.selfcare.app.data.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.selfcare.app.domain.ml.BarcodeScanner

class MlKitBarcodeScanner : BarcodeScanner {

    private val client by lazy { BarcodeScanning.getClient() }

    override suspend fun scanBarcode(bitmap: Bitmap): String? {
        return runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = client.process(image).awaitResult()
            barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.trim()
        }.getOrNull()
    }
}

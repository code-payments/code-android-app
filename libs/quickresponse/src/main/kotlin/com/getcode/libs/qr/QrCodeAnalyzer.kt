package com.getcode.libs.qr

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.getcode.libs.code.detection.CodeDetector
import com.getcode.libs.code.detection.CodeScanResult
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

class QrCodeAnalyzer @Inject constructor() : CodeDetector<List<String?>> {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )


    private val recentScans = mutableMapOf<String, Long>()
    private val debouncePeriod = 3000L

    @OptIn(ExperimentalGetImage::class)
    override suspend fun detect(
        image: ImageProxy,
    ): CodeScanResult? {
        val imageBytes = image.toNv21Bytes()
        val inputImage = InputImage.fromByteArray(
            imageBytes, image.width, image.height, image.imageInfo.rotationDegrees,
            InputImage.IMAGE_FORMAT_NV21,
        )

        return suspendCancellableCoroutine { cont ->
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val now = System.currentTimeMillis()

                    val texts = barcodes
                        .mapNotNull { it.rawValue }
                        .filter { text ->
                            val lastSeen = recentScans[text]
                            val isNew = lastSeen == null || now - lastSeen > debouncePeriod
                            if (isNew) recentScans[text] = now
                            isNew
                        }

                    recentScans.entries.removeAll { now - it.value > debouncePeriod }

                    cont.resume(texts.takeIf { it.isNotEmpty() }?.let(CodeScanResult::QrCode))
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }
}

private fun ImageProxy.toNv21Bytes(): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val ySize = width * height
    val uvSize = width * height / 2
    val nv21 = ByteArray(ySize + uvSize)

    // Y plane — handle row stride padding
    val yBuffer = yPlane.buffer
    val yRowStride = yPlane.rowStride
    if (yRowStride == width) {
        yBuffer.get(nv21, 0, ySize)
    } else {
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, row * width, width)
        }
    }

    // Interleave V and U for NV21
    val vBuffer = vPlane.buffer
    val uBuffer = uPlane.buffer
    val uvRowStride = vPlane.rowStride
    val uvPixelStride = vPlane.pixelStride

    var offset = ySize
    for (row in 0 until height / 2) {
        for (col in 0 until width / 2) {
            val bufferIndex = row * uvRowStride + col * uvPixelStride
            nv21[offset++] = vBuffer.get(bufferIndex)
            nv21[offset++] = uBuffer.get(bufferIndex)
        }
    }

    return nv21
}
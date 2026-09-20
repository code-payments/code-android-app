package com.getcode.libs.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * QR detection over a picked image, the still-image counterpart to [QrCodeAnalyzer].
 *
 * No crop ladder here: ML Kit scales internally and finds a small QR in a large photo on its own,
 * which is why the still-image search only ever pays the ladder's cost for Kik codes.
 *
 * Client options match [QrCodeAnalyzer]'s. A still image is a worse case for ML Kit than a frame,
 * so there is no reason to widen the format set here.
 */
class StaticQrAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    /** Every QR payload in the image, in ML Kit's order. */
    suspend fun detect(uri: Uri): List<String> {
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri)
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull() ?: return emptyList()

        return try {
            detect(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun detect(bitmap: Bitmap): List<String> =
        suspendCancellableCoroutine { continuation ->
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { barcodes ->
                    continuation.resume(barcodes.mapNotNull { it.rawValue })
                }
                .addOnFailureListener {
                    continuation.resume(emptyList())
                }
        }
}

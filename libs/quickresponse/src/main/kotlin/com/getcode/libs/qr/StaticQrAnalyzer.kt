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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    /**
     * Every QR payload in the image, in ML Kit's order.
     *
     * Off the caller's dispatcher: the only caller reaches this from `rememberCoroutineScope`,
     * which is the main thread, and decoding an arbitrary photo there would freeze the UI for as
     * long as the decode takes. It is also the slow path -- it runs after the Kik ladder has
     * already spent its budget -- so this is the worst place to block.
     */
    suspend fun detect(uri: Uri): List<String> = withContext(Dispatchers.Default) {
        val bitmap = decode(uri) ?: return@withContext emptyList()
        scan(bitmap)
    }

    /** Takes ownership of [bitmap] and recycles it once ML Kit is finished reading it. */
    private suspend fun scan(bitmap: Bitmap): List<String> =
        suspendCancellableCoroutine { continuation ->
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { barcodes ->
                    // Recycled here rather than in a `finally` around the await: cancelling the
                    // search resumes the coroutine immediately while ML Kit's worker is still
                    // reading the bitmap this image wraps. A task always settles, so the bitmap is
                    // still freed -- just not before its reader is done. `resume` after
                    // cancellation is a no-op.
                    bitmap.recycle()
                    continuation.resume(barcodes.mapNotNull { it.rawValue })
                }
                .addOnFailureListener {
                    bitmap.recycle()
                    continuation.resume(emptyList())
                }
        }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
        ) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false

            // The same cap the Kik search uses. ML Kit wants a few hundred pixels across the code,
            // not the whole 12-megapixel frame, and an ARGB_8888 decode of one is 48MB.
            val longestSide = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longestSide / sample > MAX_SOURCE_SIDE) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
    }.getOrNull()

    private companion object {
        const val MAX_SOURCE_SIDE = 2400
    }
}

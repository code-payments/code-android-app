package com.getcode.libs.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import javax.inject.Inject

class QRCodeGenerator @Inject constructor() {
    fun generate(url: String, size: Int): Bitmap? {
        return generateQr(
            url = url,
            size = size,
            padding = 0,
            contentColor = Color.WHITE,
            spaceColor = Color.TRANSPARENT
        )
    }
}

internal fun generateQr(
    url: String,
    size: Int,
    padding: Int,
    contentColor: Int = Color.BLACK,
    spaceColor: Int = Color.WHITE,
): Bitmap {
    val qrCodeWriter = QRCodeWriter()

    val encodeHints = mutableMapOf<EncodeHintType, Any?>()
        .apply {
            this[EncodeHintType.MARGIN] = padding
        }

    val bitmapMatrix = try {
        qrCodeWriter.encode(
            url, BarcodeFormat.QR_CODE,
            size, size, encodeHints
        )
    } catch (ex: WriterException) {
        null
    }

    // Guard against a zero/negative dimension reaching Bitmap.createBitmap,
    // which throws IllegalArgumentException("width and height must be > 0").
    val matrixWidth = (bitmapMatrix?.width ?: size).coerceAtLeast(1)
    val matrixHeight = (bitmapMatrix?.height ?: size).coerceAtLeast(1)

    val newBitmap = createBitmap(matrixWidth, matrixHeight)

    val pixels = IntArray(matrixWidth * matrixHeight)

    for (x in 0 until matrixWidth) {
        for (y in 0 until matrixHeight) {
            val shouldColorPixel = bitmapMatrix?.get(x, y) ?: false
            val pixelColor = if (shouldColorPixel) contentColor else spaceColor

            pixels[y * matrixWidth + x] = pixelColor
        }
    }

    newBitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)

    return newBitmap
}

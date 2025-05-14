package com.getcode.libs.qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.graphics.createBitmap

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

    val matrixWidth = bitmapMatrix?.width ?: size
    val matrixHeight = bitmapMatrix?.height ?: size

    val newBitmap = createBitmap(bitmapMatrix?.width ?: size, bitmapMatrix?.height ?: size)

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

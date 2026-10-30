package com.flipcash.shared.common.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.asImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

internal actual fun argbToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    // Skia's N32 is BGRA little-endian, which is the in-memory order of an ARGB Int.
    val bytes = ByteArray(pixels.size * 4)
    pixels.forEachIndexed { i, argb ->
        bytes[i * 4] = (argb and 0xFF).toByte()
        bytes[i * 4 + 1] = (argb shr 8 and 0xFF).toByte()
        bytes[i * 4 + 2] = (argb shr 16 and 0xFF).toByte()
        bytes[i * 4 + 3] = (argb ushr 24).toByte()
    }
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.OPAQUE))
    bitmap.installPixels(bytes)
    return bitmap.asComposeImageBitmap()
}

internal actual fun ImageBitmap.asCoilImage(): coil3.Image = asSkiaBitmap().asImage()

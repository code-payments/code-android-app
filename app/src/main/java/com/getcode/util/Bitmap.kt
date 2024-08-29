package com.getcode.util

import android.graphics.Bitmap

fun Bitmap.toByteArray(): ByteArray = getLuminanceData()

private fun Bitmap.getLuminanceData(): ByteArray {
    val width = this.width
    val height = this.height
    val pixelData = IntArray(width * height)
    this.getPixels(pixelData, 0, width, 0, 0, width, height)

    val luminanceData = ByteArray(width * height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixel = pixelData[y * width + x]

            // Extract RGB values from the pixel
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Calculate luminance (grayscale) using a common formula
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            luminanceData[y * width + x] = luminance.toByte()
        }
    }

    return luminanceData
}
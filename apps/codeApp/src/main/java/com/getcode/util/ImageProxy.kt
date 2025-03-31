package com.getcode.util

import androidx.camera.core.ImageProxy

fun ImageProxy.toByteArray(): ByteArray {
    // Remove padding from Y plane data before passing it to ZXing
    // @see https://github.com/beemdevelopment/Aegis/commit/fb58c877d1b305b1c66db497880da5651dda78d7
   return getLuminancePlaneData()
}

private fun ImageProxy.getLuminancePlaneData(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    buffer.rewind()

    val width = width
    val height = height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride

    if (width != rowStride || pixelStride != -1) {
        // remove padding from the Y plane data
        val cleanData = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                cleanData[y * width + x] = data[y * rowStride + x * pixelStride]
            }
        }

        return cleanData
    }

    return data
}
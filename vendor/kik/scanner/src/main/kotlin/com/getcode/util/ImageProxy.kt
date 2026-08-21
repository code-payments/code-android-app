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

    return unpadLuminancePlane(
        data = data,
        width = width,
        height = height,
        rowStride = plane.rowStride,
        pixelStride = plane.pixelStride,
    )
}

/**
 * Strips row padding (and any pixel interleaving) from a YUV_420_888 Y plane so the result is a
 * tightly packed `width * height` luminance matrix.
 *
 * When the plane is already tightly packed — `rowStride == width` and `pixelStride == 1`, which is
 * the common case for analysis resolutions whose width is a multiple of the hardware alignment —
 * [data] is returned as-is, skipping the O(width * height) per-pixel copy.
 *
 * Note that YUV_420_888 guarantees a Y-plane pixel stride of 1, so in practice only
 * the row-stride check can send us down the copying path; the pixel-stride term is kept to match
 * the upstream implementation and to stay correct if that guarantee ever loosens.
 */
internal fun unpadLuminancePlane(
    data: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
): ByteArray {
    if (width != rowStride || pixelStride != 1) {
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

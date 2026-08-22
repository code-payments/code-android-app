package com.getcode.util

import androidx.camera.core.ImageProxy
import com.getcode.codes.kikcode.LuminancePlane

fun ImageProxy.toByteArray(): ByteArray {
    // Remove padding from Y plane data before passing it to the scanner
    // @see https://github.com/beemdevelopment/Aegis/commit/fb58c877d1b305b1c66db497880da5651dda78d7
    return getLuminancePlaneData()
}

/**
 * Reads the Y plane of an analyzed frame into the tightly packed `width * height` buffer the native
 * scanner expects.
 *
 * The packing rule lives in [LuminancePlane] because iOS has to apply the same one — see that file
 * for why the shared piece is the decision rather than the bytes.
 */
private fun ImageProxy.getLuminancePlaneData(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    buffer.rewind()

    return LuminancePlane.unpad(
        data = data,
        width = width,
        height = height,
        rowStride = plane.rowStride,
        pixelStride = plane.pixelStride,
    )
}

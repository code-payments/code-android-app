package com.getcode.util

import android.graphics.Bitmap
import com.getcode.utils.ErrorUtils
import java.io.File
import java.io.FileOutputStream

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

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            luminanceData[y * width + x] = luminance.toByte()
        }
    }

    return luminanceData
}

internal fun Bitmap.save(destination: File, name: () -> String): Boolean {
    val filename = name()
    if (!destination.exists()) {
        destination.mkdirs()
    }
    val dest = File(destination, filename)

    try {
        val out = FileOutputStream(dest)
        compress(Bitmap.CompressFormat.PNG, 90, out)
        out.flush()
        out.close()
    } catch (e: Exception) {
        e.printStackTrace()
        ErrorUtils.handleError(e)
        return false
    }
    return true
}
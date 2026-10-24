package com.getcode.ui.utils

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.getcode.utils.sha512
import kotlin.experimental.and
import kotlin.math.roundToInt


private val resultMap: MutableMap<Pair<List<Byte>, Size>, ImageBitmap> = mutableMapOf()

fun generateEightBitAvatar(data: List<Byte>, size: Size): ImageBitmap? {
    return resultMap.getOrPutIfNonNull(data to size) {
        generateAvatar(data, size)
    }
}

private fun generateAvatar(data: List<Byte>, size: Size): ImageBitmap? {
    val hash = runCatching { data.toByteArray().sha512() }.getOrNull() ?: return null
    val foregroundColor = rgbFromHash(hash.copyOfRange(0, 3))

    val bitmap = Bitmap.createBitmap(
        size.width.roundToInt(),
        size.height.roundToInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint()

    val length = 10
    val rCount = length
    val cCount = length / 2
    val cellSize = size.width / length.toFloat()
    val inset = cellSize * 0.6f

    val bounds = RectF(0f, 0f, size.width, size.height)
    val fullPath = Path().apply { addOval(bounds, Path.Direction.CCW) }
    val maskPath = Path().apply { addOval(bounds.apply { inset(inset, inset) }, Path.Direction.CW) }

    val delta = Path().apply {
        op(fullPath, maskPath, Path.Op.DIFFERENCE)
    }

    val paths = mutableListOf<Path>()

    for (r in 0 until rCount) {
        for (c in 0 until cCount) {
            val i = r * cCount + c
            val isEven = (hash[i] and 1) == 0.toByte()
            if (isEven) {
                val leftPath = createPath(r, c, cellSize)
                if (!delta.intersects(leftPath)) {
                    paths.add(leftPath)
                }

                val rightPath = createPath(r, length - c - 1, cellSize)
                if (!delta.intersects(rightPath)) {
                    paths.add(rightPath)
                }
            }
        }
    }

    paint.color = foregroundColor.toAGColor()
    paths.forEach { canvas.drawPath(it, paint) }

    return bitmap.asImageBitmap()
}

private fun createPath(row: Int, col: Int, size: Float): Path {
    return Path().apply {
        addRect(col * size, row * size, (col + 1) * size, (row + 1) * size, Path.Direction.CW)
    }
}

private fun Path.intersects(other: Path): Boolean {
    val result = Path()
    result.op(this, other, Path.Op.INTERSECT)
    return !result.isEmpty
}

private fun rgbFromHash(hash: ByteArray): Color {
    return Color(hash[0].toInt() and 0xFF, hash[1].toInt() and 0xFF, hash[2].toInt() and 0xFF)
}
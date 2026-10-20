package com.flipcash.app.core.share.groupcard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset

private fun List<PathOp>.toComposePath(): Path = Path().also { path ->
    path.fillType = PathFillType.EvenOdd
    for (op in this) when (op) {
        is PathOp.Move -> path.moveTo(op.x, op.y)
        is PathOp.Line -> path.lineTo(op.x, op.y)
        is PathOp.Cubic -> path.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
        PathOp.Close -> path.close()
    }
}

private fun List<PathOp>.toAndroidPath(): android.graphics.Path = android.graphics.Path().also { path ->
    path.fillType = android.graphics.Path.FillType.EVEN_ODD
    for (op in this) when (op) {
        is PathOp.Move -> path.moveTo(op.x, op.y)
        is PathOp.Line -> path.lineTo(op.x, op.y)
        is PathOp.Cubic -> path.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
        PathOp.Close -> path.close()
    }
}

/** The group card's QR: the styled tile, square, filling its [modifier]. */
@Composable
fun GroupCardQr(matrix: QrMatrix, modifier: Modifier = Modifier) {
    val path = remember(matrix) { StyledQrGeometry.build(matrix).toComposePath() }
    val units = StyledQrGeometry.tileUnits(matrix)
    ComposeCanvas(modifier = modifier) {
        drawRoundRect(
            color = Color(StyledQrGeometry.TileColorArgb),
            cornerRadius = CornerRadius(size.width * StyledQrGeometry.TileCornerFraction),
        )
        val unit = size.width / units
        scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            drawPath(path = path, color = Color.White)
        }
    }
}

/** Rasterises the styled QR for export. */
object StyledQrBitmap {
    /**
     * A [sizePx] square: the tile fills the image on a transparent ground, and the QR, quiet zone
     * included, is drawn at the largest whole number of pixels per module that fits, centred.
     */
    fun render(matrix: QrMatrix, sizePx: Int = 1024): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = StyledQrGeometry.TileColorArgb
        val corner = sizePx * StyledQrGeometry.TileCornerFraction
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), corner, corner, paint)

        val units = StyledQrGeometry.tileUnits(matrix)
        val pxPerModule = maxOf(1, sizePx / units)
        val offset = (sizePx - units * pxPerModule) / 2f
        canvas.translate(offset, offset)
        canvas.scale(pxPerModule.toFloat(), pxPerModule.toFloat())
        paint.color = android.graphics.Color.WHITE
        canvas.drawPath(StyledQrGeometry.build(matrix).toAndroidPath(), paint)
        return bitmap
    }
}

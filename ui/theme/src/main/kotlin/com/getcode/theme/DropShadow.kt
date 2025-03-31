package com.getcode.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DropShadow(
    val color: Color = Color.Black,
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val blurRadius: Dp = 0.dp,
    val spread: Dp = 0.dp
)

fun Paint.makeBlur(shadowBlurRadius: Float) {
    this.asFrameworkPaint().apply {
        maskFilter =
            android.graphics.BlurMaskFilter(
                shadowBlurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
}

fun Modifier.dropShadow(shadow: DropShadow): Modifier {
    return this.then(
        with(shadow) { Modifier.dropShadow(color, offsetX, offsetY, blurRadius, spread) })
}
 fun Modifier.dropShadow(
    color: Color = Color.Black,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    blurRadius: Dp = 0.dp,
    spread: Dp = 0.dp
): Modifier {
    return this.then(
        Modifier.drawBehind {
            val shadowOffsetX = offsetX.toPx()
            val shadowOffsetY = offsetY.toPx()
            val shadowBlurRadius = blurRadius.toPx()
            val shadowSpread = spread.toPx()
            val rectLeft = shadowOffsetX - shadowSpread
            val rectTop = shadowOffsetY - shadowSpread
            val rectRight = size.width + shadowOffsetX + shadowSpread
            val rectBottom = size.height + shadowOffsetY + shadowSpread
            val cornerRadius = spread.toPx()

            drawIntoCanvas { canvas ->
                val paint =
                    Paint().apply {
                        this.color = color
                        this.makeBlur(shadowBlurRadius)
                    }
                translate(left = shadowOffsetX, top = shadowOffsetY) {
                    canvas.drawRoundRect(
                        left = rectLeft,
                        top = rectTop,
                        right = rectRight,
                        bottom = rectBottom,
                        radiusX = cornerRadius,
                        radiusY = cornerRadius,
                        paint)
                }
            }
        })
}
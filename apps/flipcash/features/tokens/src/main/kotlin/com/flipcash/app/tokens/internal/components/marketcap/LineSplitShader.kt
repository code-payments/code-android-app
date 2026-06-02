package com.flipcash.app.tokens.internal.components.marketcap

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

class SplitState {
    var canvasX: Float = Float.MAX_VALUE
}

@Composable
internal fun rememberLineSplitState(): SplitState = remember { SplitState() }

fun LineCartesianLayer.LineFill.Companion.double(
    leftFill: Fill,
    rightFill: Fill,
    splitX: (ExtraStore) -> Float,
): LineCartesianLayer.LineFill = HorizontalSplitLineFill(leftFill, rightFill, splitX)

class HorizontalSplitLineFill(
    val leftFill: Fill,
    val rightFill: Fill,
    val splitX: (ExtraStore) -> Float,
) : LineCartesianLayer.LineFill {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(
        context: CartesianDrawingContext,
        halfLineThickness: Float,
        verticalAxisPosition: Axis.Position.Vertical?,
    ) {
        with(context) {
            val canvasSplitX = splitX(extraStore).coerceIn(layerBounds.left, layerBounds.right)
            val height = layerBounds.bottom + halfLineThickness - (layerBounds.top - halfLineThickness)

            // Left portion
            paint.color = leftFill.color.toArgb()
            paint.shader = (leftFill.brush as? ShaderBrush)?.createShader(
                androidx.compose.ui.geometry.Size(canvasSplitX - layerBounds.left, height)
            )
            canvas.nativeCanvas.drawRect(
                layerBounds.left,
                layerBounds.top - halfLineThickness,
                canvasSplitX,
                layerBounds.bottom + halfLineThickness,
                paint,
            )

            // Right portion
            paint.color = rightFill.color.toArgb()
            paint.shader = (rightFill.brush as? ShaderBrush)?.createShader(
                androidx.compose.ui.geometry.Size(layerBounds.right - canvasSplitX, height)
            )
            canvas.nativeCanvas.drawRect(
                canvasSplitX,
                layerBounds.top - halfLineThickness,
                layerBounds.right,
                layerBounds.bottom + halfLineThickness,
                paint,
            )
        }
    }
}
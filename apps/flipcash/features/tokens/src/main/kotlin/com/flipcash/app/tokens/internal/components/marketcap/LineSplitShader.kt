package com.flipcash.app.tokens.internal.components.marketcap

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.data.ExtraStore

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
            val canvasSplitX = splitX(model.extraStore).coerceIn(layerBounds.left, layerBounds.right)

            // Left portion
            paint.color = leftFill.color
            paint.shader = leftFill.shaderProvider?.getShader(
                this,
                layerBounds.left,
                layerBounds.top - halfLineThickness,
                canvasSplitX,
                layerBounds.bottom + halfLineThickness,
            )
            canvas.drawRect(
                layerBounds.left,
                layerBounds.top - halfLineThickness,
                canvasSplitX,
                layerBounds.bottom + halfLineThickness,
                paint,
            )

            // Right portion
            paint.color = rightFill.color
            paint.shader = rightFill.shaderProvider?.getShader(
                this,
                canvasSplitX,
                layerBounds.top - halfLineThickness,
                layerBounds.right,
                layerBounds.bottom + halfLineThickness,
            )
            canvas.drawRect(
                canvasSplitX,
                layerBounds.top - halfLineThickness,
                layerBounds.right,
                layerBounds.bottom + halfLineThickness,
                paint,
            )
        }
    }
}
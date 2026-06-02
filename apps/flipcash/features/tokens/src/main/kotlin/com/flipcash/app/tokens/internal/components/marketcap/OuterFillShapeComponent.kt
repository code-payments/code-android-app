package com.flipcash.app.tokens.internal.components.marketcap

import android.graphics.Paint
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.DrawingContext
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent

class OuterFillShapeComponent(
    private val outerFill: Fill,
    private val outerInsets: Insets,
    private val inner: Component,
) : Component {

    private val outerPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun draw(
        context: DrawingContext,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2

        with(context) {
            val outerLeft = left - outerInsets.start.pixels
            val outerTop = top - outerInsets.top.pixels
            val outerRight = right + outerInsets.end.pixels
            val outerBottom = bottom + outerInsets.bottom.pixels
            val outerRadius = minOf(outerRight - outerLeft, outerBottom - outerTop) / 2

            // Draw outer fill
            outerPaint.color = outerFill.color.toArgb()
            canvas.nativeCanvas.drawCircle(centerX, centerY, outerRadius, outerPaint)

            // Draw inner component
            inner.draw(context, left, top, right, bottom)
        }
    }
}

fun outerFillShapeComponent(
    outerFill: Fill,
    margins: Insets = Insets(4.dp),
    innerFill: Fill,
    strokeFill: Fill,
    shape: Shape = CircleShape,
    strokeThickness: Dp = 2.dp,
): Component {
    val inner = ShapeComponent(
        fill = innerFill,
        shape = shape,
        strokeFill = strokeFill,
        strokeThickness = strokeThickness,
    )

    return OuterFillShapeComponent(
        outerFill = outerFill,
        inner = inner,
        outerInsets = margins,
    )
}
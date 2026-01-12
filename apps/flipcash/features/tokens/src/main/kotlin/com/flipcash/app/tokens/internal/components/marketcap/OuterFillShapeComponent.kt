package com.flipcash.app.tokens.internal.components.marketcap

import android.graphics.Paint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.component.shapeComponent
import com.patrykandpatrick.vico.compose.common.shape.markerCorneredShape
import com.patrykandpatrick.vico.core.common.DrawingContext
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.Component
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.Shape

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
            val outerLeft = left - outerInsets.startDp.pixels
            val outerTop = top - outerInsets.topDp.pixels
            val outerRight = right + outerInsets.endDp.pixels
            val outerBottom = bottom + outerInsets.bottomDp.pixels
            val outerRadius = minOf(outerRight - outerLeft, outerBottom - outerTop) / 2

            // Draw outer fill
            outerPaint.color = outerFill.color
            canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)

            // Draw inner component
            inner.draw(context, left, top, right, bottom)
        }
    }
}

fun outerFillShapeComponent(
    outerFill: Fill,
    margins: Insets = Insets(4f),
    innerFill: Fill,
    strokeFill: Fill,
    shape: Shape = markerCorneredShape(CorneredShape.Corner.Rounded),
    strokeThickness: Dp = 2.dp,
): Component {
    val inner = shapeComponent(
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
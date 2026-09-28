package com.flipcash.app.tokens.internal.components.marketcap

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.DrawingContext
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

class SplitState {
    var canvasX: Float = Float.MAX_VALUE
}

@Composable
internal fun rememberLineSplitState(): SplitState = remember { SplitState() }

/**
 * Colors are read on every draw, so they can come from animated state without rebuilding the line (and
 * with it the chart) on each frame.
 */
fun LineCartesianLayer.LineFill.Companion.double(
    leftColor: () -> Color,
    rightColor: () -> Color,
    splitX: (ExtraStore) -> Float,
): LineCartesianLayer.LineFill = HorizontalSplitLineFill(leftColor, rightColor, splitX)

class HorizontalSplitLineFill(
    val leftColor: () -> Color,
    val rightColor: () -> Color,
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

            // Left portion
            paint.color = leftColor().toArgb()
            canvas.nativeCanvas.drawRect(
                layerBounds.left,
                layerBounds.top - halfLineThickness,
                canvasSplitX,
                layerBounds.bottom + halfLineThickness,
                paint,
            )

            // Right portion
            paint.color = rightColor().toArgb()
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
/**
 * Fills the area between the line and the bottom of the layer with a vertical gradient from [color] at
 * [topAlpha] to transparent. Like [HorizontalSplitLineFill], it reads both on every draw.
 */
internal class VerticalGradientAreaFill(
    private val color: () -> Color,
    private val topAlpha: () -> Float,
) : LineCartesianLayer.AreaFill {
    private val areaPath = Path()
    private val paint = ComposePaint()

    override fun draw(
        context: CartesianDrawingContext,
        linePath: Path,
        halfLineThickness: Float,
        verticalAxisPosition: Axis.Position.Vertical?,
    ) {
        with(context) {
            val lineBounds = linePath.getBounds()
            val bottom = layerBounds.bottom
            with(areaPath) {
                rewind()
                addPath(linePath)
                lineTo(if (isLtr) lineBounds.right else lineBounds.left, bottom)
                lineTo(if (isLtr) lineBounds.left else lineBounds.right, bottom)
                close()
            }

            val top = color()
            Brush.verticalGradient(
                colors = listOf(top.copy(alpha = topAlpha()), top.copy(alpha = 0f)),
                startY = layerBounds.top,
                endY = bottom,
            ).applyTo(layerBounds.size, paint, alpha = 1f)

            canvas.save()
            canvas.clipRect(layerBounds)
            canvas.drawPath(areaPath, paint)
            canvas.restore()
        }
    }
}

/**
 * Vico caches marker indicators by the point's color, so a component built from an animated color would
 * stay on whichever frame it was first drawn with. This reads [color] on every draw and rebuilds the
 * delegate only when it changes.
 */
internal class ColorProviderComponent(
    private val color: () -> Color,
    private val build: (Color) -> Component,
) : Component {
    private var builtFor: Color? = null
    private var delegate: Component? = null

    override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
        val current = color()
        val component = delegate?.takeIf { builtFor == current }
            ?: build(current).also {
                delegate = it
                builtFor = current
            }
        component.draw(context, left, top, right, bottom)
    }
}

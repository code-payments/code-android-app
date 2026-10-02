package com.flipcash.app.tokens.internal.explainer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import kotlin.math.abs
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flipcash.app.tokens.bondingcurve.ExplainerChart
import com.getcode.theme.CodeTheme

/**
 * The bonding curve (price against tokens purchased), both axes linear. Solid with a fade-out fill up
 * to the selected supply, dashed beyond it; a dashed line marks Today's price and a marker with a
 * vertical scrub line sits at the selection.
 */
@Composable
internal fun MarketCapExplainerChart(
    chart: ExplainerChart,
    scrubLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val accent = CodeTheme.colors.success
    val faded = accent.copy(alpha = 0.35f)
    val guide = CodeTheme.colors.textSecondary.copy(alpha = 0.5f)
    val todayDot = CodeTheme.colors.textSecondary
    val density = LocalDensity.current
    val labelAreaHeight = 24.dp
    val padPx = with(density) { 12.dp.toPx() }
    val topPx = with(density) { labelAreaHeight.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .semantics { contentDescription = description },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val plotW = widthPx - padPx * 2
        val plotH = heightPx - topPx - padPx
        fun x(tokens: Double) = padPx + chart.xFraction(tokens) * plotW
        fun y(price: Double): Float {
            val span = (chart.yMax - chart.yMin).coerceAtLeast(1e-9)
            val t = ((price - chart.yMin) / span).coerceIn(0.0, 1.0).toFloat()
            return topPx + plotH * (1f - t)
        }

        Canvas(Modifier.fillMaxWidth().height(maxHeight)) {
            val solid = Path()
            val dashed = Path()
            var started = false
            var dashedStarted = false
            chart.points.forEach { point ->
                val px = x(point.tokens)
                val py = y(point.price)
                if (point.tokens <= chart.selectedTokens) {
                    if (!started) solid.moveTo(px, py).also { started = true } else solid.lineTo(px, py)
                }
                if (point.tokens >= chart.selectedTokens) {
                    if (!dashedStarted) dashed.moveTo(px, py).also { dashedStarted = true } else dashed.lineTo(px, py)
                }
            }

            val selX = x(chart.selectedTokens)
            val selY = y(chart.selectedPrice)
            val baseline = topPx + plotH

            if (started) {
                val fill = Path().apply {
                    addPath(solid)
                    lineTo(selX, baseline)
                    lineTo(x(chart.points.first().tokens), baseline)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0f)),
                        startY = selY,
                        endY = baseline,
                    ),
                )
                drawPath(solid, accent, style = Stroke(width = 2.5.dp.toPx()))
            }
            if (dashedStarted) {
                drawPath(
                    dashed,
                    faded,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
                )
            }

            // Today's price, as a dashed horizontal reference.
            val todayY = y(chart.todayPrice)
            drawLine(
                guide,
                Offset(padPx, todayY),
                Offset(padPx + plotW, todayY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )

            // Scrub line, then Today's small grey dot (only once the selection has left Today), then the
            // solid selection dot - no halo, matching iOS.
            drawLine(guide, Offset(selX, topPx - 4.dp.toPx()), Offset(selX, baseline), strokeWidth = 1.dp.toPx())
            if (abs(selX - x(chart.todayTokens)) > 1.dp.toPx()) {
                drawCircle(todayDot, radius = 3.dp.toPx(), center = Offset(x(chart.todayTokens), todayY))
            }
            drawCircle(accent, radius = 4.5.dp.toPx(), center = Offset(selX, selY))
        }

        Box(Modifier.fillMaxWidth().height(labelAreaHeight)) {
            Text(
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, placeable.height) {
                        val px = x(chart.selectedTokens) - placeable.width / 2f
                        placeable.place(
                            px.coerceIn(0f, (constraints.maxWidth - placeable.width).toFloat()).toInt(),
                            0,
                        )
                    }
                },
                text = scrubLabel,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}

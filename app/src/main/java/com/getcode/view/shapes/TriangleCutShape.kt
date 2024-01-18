package com.getcode.view.shapes

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import kotlin.random.Random

class TriangleCutShape(private val cutStep: Dp = 10.dp) : androidx.compose.ui.graphics.Shape {

    private fun makeCutPath(size: Size, stepX: Float, height: Float, topClosed: Boolean): Path {
        val localHeight = if (topClosed) height else -height;
        val startPt = PointF(0f, if (topClosed) 0f else height)
        val seq = generateSequence(startPt) {
            PointF(it.x.plus(stepX), if (it.y == startPt.y) startPt.y + localHeight else startPt.y)
        }.takeWhile { it.x <= (size.width + stepX) }
        return Path().apply {
            moveTo(startPt.x, startPt.y)
            seq.forEach { lineTo(it.x, it.y) }
            if (seq.last().y != startPt.y)
                lineTo(seq.last().x.plus(stepX), startPt.y)
            close()
        }
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val basePath = Path().apply {
            addRect(Rect(Offset.Zero, size))
        }
        val stepPx = with(density) { cutStep.toPx() } / 2
        val triangleHeight: Float = stepPx * sqrt(3f) / 2f

        val topCutPath = makeCutPath(size, stepPx, triangleHeight, true)
        var horizontalShift = -Random.nextFloat(stepPx)
        topCutPath.translate(Offset(horizontalShift, 0f))
        basePath.op(basePath, topCutPath, PathOperation.Difference)
        val bottomCutPath = makeCutPath(size, stepPx, triangleHeight, false)
        horizontalShift = -Random.nextFloat(stepPx)
        bottomCutPath.translate(Offset(horizontalShift, size.height - triangleHeight))
        basePath.op(basePath, bottomCutPath, PathOperation.Difference)
        return Outline.Generic(basePath)
    }

    private fun Random.nextFloat(until: Float) = Random.nextDouble(until.toDouble()).toFloat()
}


@Preview(showBackground = true)
@Composable
fun TestShape() {
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(color = Color.LightGray)
    ) {
        val tShape = TriangleCutShape()
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(100.dp)
                .shadow(4.dp, shape = tShape)
                .background(color = Color.White)

        ) {
            Text(
                text = "It's your ticket",
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
    }
}
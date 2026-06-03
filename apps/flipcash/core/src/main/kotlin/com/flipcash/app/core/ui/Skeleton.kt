package com.flipcash.app.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.getcode.theme.CodeTheme

@Composable
fun Modifier.shimmer(
    shape: Shape = CodeTheme.shapes.medium
): Modifier {
    val alpha = rememberShimmerAlpha()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, density)
        onDrawBehind {
            drawOutline(
                outline = outline,
                color = Color.White.copy(alpha = alpha),
            )
        }
    }
}

@Composable
fun rememberShimmerAlpha(): Float {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    return alpha
}
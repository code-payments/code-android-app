package com.getcode.view.main.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OUTLINE_WIDTH: Dp
    @Composable get() = CodeTheme.dimens.border

private const val BACKGROUND_ALPHA = 0.40f
private const val OUTLINE_INITIAL_SCALE = 1.33f
private const val SHOW_DURATION = 1_500L
private const val RADIUS_PX = 100f

@Composable
fun FocusIndicator(position: Offset, onTimeout: () -> Unit) {
    var center by remember { mutableStateOf(Offset.Unspecified) }
    val circleAlpha = remember { Animatable(0f) }
    val outlineScale = remember { Animatable(OUTLINE_INITIAL_SCALE) }
    val circleScale = remember { Animatable(0f) }

    LaunchedEffect(position) {
        center = position
        launch {
            circleAlpha.snapTo(BACKGROUND_ALPHA)
            circleAlpha.animateTo(targetValue = 0f, animationSpec = tween(1_000))
        }

        launch {
            outlineScale.snapTo(OUTLINE_INITIAL_SCALE)
            outlineScale.animateTo(targetValue = 1f, animationSpec = tween(500))
        }

        launch {
            circleScale.snapTo(0f)
            circleScale.animateTo(1f)
        }

        delay(SHOW_DURATION)
        onTimeout()
        center = Offset.Unspecified
        circleAlpha.snapTo(BACKGROUND_ALPHA)
        circleScale.snapTo(0f)
        outlineScale.snapTo(OUTLINE_INITIAL_SCALE)
    }

    val outlineWidth = with (LocalDensity.current) { OUTLINE_WIDTH.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (center.isSpecified) {
            drawCircle(
                color = Color.White,
                radius = RADIUS_PX * outlineScale.value,
                style = Stroke(outlineWidth),
                center = center
            )

            drawCircle(
                color = Color.White.copy(alpha = circleAlpha.value),
                radius = RADIUS_PX * circleScale.value,
                center = center
            )
        }
    }
}
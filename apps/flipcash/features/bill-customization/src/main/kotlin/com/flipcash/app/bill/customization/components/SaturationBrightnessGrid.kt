package com.flipcash.app.bill.customization.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.hsv
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun SaturationBrightnessGrid(
    modifier: Modifier = Modifier,
    hsv: Hsv,
    onHsvChanged: (Hsv, isDragging: Boolean) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val large = CodeTheme.shapes.large

    val currentHsv by rememberUpdatedState(hsv)

    Box(modifier = modifier) {
        ControlPanelItem(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            if (!isDragging) {
                                isDragging = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            }
                        },
                        onDrag = { change, _ ->
                            val offset = change.position
                            updatePosition(
                                offsetX = offset.x,
                                offsetY = offset.y,
                                width = componentSize.width.toFloat(),
                                height = componentSize.height.toFloat(),
                                onChange = { saturation, brightness ->
                                    val updatedHsv = currentHsv.copy(s = saturation, v = brightness)
                                    onHsvChanged(updatedHsv, true)
                                },
                            )
                        },
                        onDragEnd = {
                            isDragging = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            onHsvChanged(currentHsv, false)
                        }
                    )
                },
            onMeasured = {
                if (componentSize == IntSize.Zero) {
                    componentSize = it
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(large)
                    .drawBehind {
                        drawGradientsAndDots(
                            hue = currentHsv.h,
                            dotColor = Color.White.copy(alpha = 0.09f),
                            shape = large
                        )
                    }
            )
        }

        // not drawn inside panel item content so this can overlay properly
        Thumb(
            componentSize = componentSize,
            hsv = currentHsv,
            isDragging = isDragging,
        )
    }
}

@Composable
private fun BoxScope.Thumb(
    componentSize: IntSize,
    hsv: Hsv,
    isDragging: Boolean,
) {
    val thumbSize = CodeTheme.dimens.grid.x5
    val x = componentSize.width * hsv.s
    val y = componentSize.height * (1 - hsv.v)

    // Responsive animation: instant during drag for real-time following, spring otherwise
    val animationSpec = if (isDragging) {
        tween(durationMillis = 0, easing = LinearEasing)
    } else {
        spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.1f  // Lower threshold for snappier updates
        )
    }

    val animatedX by animateFloatAsState(
        targetValue = x,
        animationSpec = animationSpec
    )

    val animatedY by animateFloatAsState(
        targetValue = y,
        animationSpec = animationSpec
    )

    val oneThirdThumb = thumbSize * 0.33f
    val twoThirdThumb = thumbSize * 0.67f

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1.0f,
        label = "thumbScale"
    )
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (animatedX - (twoThirdThumb.toPx())).roundToInt()
                        .coerceAtLeast(-(oneThirdThumb).toPx().roundToInt()),
                    (animatedY - twoThirdThumb.toPx()).roundToInt()
                        .coerceAtLeast(-(oneThirdThumb).toPx().roundToInt()),
                )
            }
            .size(thumbSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .align(Alignment.TopStart)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color= Color.hsv(hue = hsv.h, saturation = hsv.s, value = hsv.v),
                    shape = CircleShape,
                )
                .border(
                    width = CodeTheme.dimens.thickBorder,
                    color = Color.White,
                    shape = CircleShape
                )
        )
    }
}

private fun DrawScope.drawGradientsAndDots(
    hue: Float,
    dotSize: Dp = 4.dp,
    inset: Dp = 16.dp,
    dotColor: Color,
    shape: CornerBasedShape,
) {
    val corner = shape.bottomEnd
    val cornerRadius = corner.toPx(size, this)

    // Base horizontal gradient (hue to saturated)
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.hsv(hue = hue, saturation = 0f, value = 1f),
                Color.hsv(hue = hue, saturation = 1f, value = 1f)
            )
        ),
        cornerRadius = CornerRadius(
            x = cornerRadius,
            y = cornerRadius
        )
    )

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.05f),
                Color.Black.copy(alpha = 1f)
            )
        ),
        blendMode = BlendMode.Multiply,
        cornerRadius = CornerRadius(
            x = cornerRadius,
            y = cornerRadius
        )
    )

    val insetPx = inset.toPx()
    val dotRadiusPx = dotSize.toPx() / 2
    val spacing = 8.dp.toPx()
    val availableWidth = size.width - (2 * insetPx)
    val availableHeight = size.height - (2 * insetPx)

    val calculatedRows = (((availableHeight / (spacing)).roundToInt()) / 2)
    val calculatedCols = (((availableWidth / (spacing)).roundToInt()) / 2)
    for (row in 0..calculatedRows) {
        for (col in 0..calculatedCols) {
            val x = insetPx + col * (availableWidth / calculatedCols)
            val y = insetPx + row * (availableHeight / calculatedRows)
            if (x in 0f..size.width && y in 0f..size.height) {
                drawCircle(
                    color = dotColor, radius = dotRadiusPx, center = Offset(x, y)
                )
            }
        }
    }
}

private fun updatePosition(
    offsetX: Float,
    offsetY: Float,
    width: Float,
    height: Float,
    onChange: (Float, Float) -> Unit,
) {
    val x = max(0f, min(width, offsetX))
    val y = max(0f, min(height, offsetY))
    val newSaturation = if (width == 0f) 0f else (x / width)
    val newBrightness = if (height == 0f) 0f else 1f - (y / height)
    onChange(newSaturation, newBrightness)
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun HueControlPreview() {
    FlipcashPreview {
        val hsv = Color(0xFF3B66AD).hsv
        var selectedHsv by remember {
            mutableStateOf(hsv)
        }
        SaturationBrightnessGrid(
            modifier = Modifier
                .padding(CodeTheme.dimens.inset)
                .aspectRatio(16 / 9f),
            hsv = selectedHsv,
            onHsvChanged = { hsv, _ -> selectedHsv = hsv }
        )
    }
}
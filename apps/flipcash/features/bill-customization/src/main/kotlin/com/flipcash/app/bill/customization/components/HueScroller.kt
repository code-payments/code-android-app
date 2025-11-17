package com.flipcash.app.bill.customization.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashDesignSystem
import com.getcode.theme.CodeTheme
import com.getcode.ui.utils.Hsv
import com.getcode.ui.utils.color
import com.getcode.ui.utils.hsv
import com.getcode.ui.utils.toAGColor
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

@OptIn(ExperimentalStdlibApi::class)
@Composable
internal fun HueScroller(
    hsv: Hsv,
    modifier: Modifier = Modifier,
    hueWindow: Float = 120f,
    onChange: (Hsv, isDragging: Boolean) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1.0f,
        label = "thumbScale"
    )

    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val large = CodeTheme.shapes.large

    val thumbWidth = with(density) { componentSize.width.toDp() * 0.08f }

    val currentHsv by rememberUpdatedState(hsv)

    ControlPanelItem(
        modifier = modifier
            .clip(large)
            .drawWithContent {
                drawColorStrip({ currentHsv }, window = hueWindow)
                drawContent()
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDragEnd = {
                        isDragging = false
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        onChange(currentHsv, false)
                    },
                ) { change, dragAmount ->
                    val deltaHue = dragAmount * (180f / componentSize.width)
                    val newHue = (currentHsv.h + deltaHue).let { h ->
                        var wrapped = h % 360f
                        if (wrapped < 0f) wrapped += 360f
                        wrapped
                    }
                    onChange(currentHsv.copy(h = newHue), true)
                }
            },
        onMeasured = { componentSize = it },
        shape = large,
    ) {
        Thumb(
            modifier = Modifier
                .align(Alignment.Center)
                .width(thumbWidth)
                .scale(scale),
            hsv = currentHsv,
        )
    }
}

@Composable
private fun Thumb(hsv: Hsv, modifier: Modifier = Modifier) {
    val thumbColor by animateColorAsState(hsv.color)

    Box(
        modifier = modifier
            .fillMaxHeight(0.7f)
            .background(
                color = thumbColor,
                shape = CodeTheme.shapes.small
            )
            .border(3.dp, Color.White, CodeTheme.shapes.small)
    )
}

private fun DrawScope.drawColorStrip(hsv: () -> Hsv, window: Float) {
    val w = size.width
    val h = size.height
    if (w == 0f || h == 0f) return

    val tickWidth = 2.dp

    val tickWidthPx = tickWidth.toPx()
    val pixelsPerHue = w / max(0.25f, 0.0001f)

    val (hue, saturation, brightness) = hsv()

    val halfWindow = window / 2f
    var startHue = hue - halfWindow
    val endHue = hue + halfWindow

    // Handle wrap-around for continuity (offset entire window if crossing 0/360)
    val offsetAmount = when {
        startHue < 0f -> 360f
        endHue > 360f -> -360f
        else -> 0f
    }
    startHue += offsetAmount

    var x = 0f
    while (x < w) {
        val progress = x / w
        val hueAtX = startHue + window * progress
        var wrapped = hueAtX % 360f
        if (wrapped < 0f) wrapped += 360f
        val color = Color.hsv(wrapped, saturation, brightness)

        // Draw a 1-pixel-wide vertical strip
        drawRect(
            color = color,
            topLeft = Offset(x, 0f),
            size = Size(1f, h)
        )

        x += 1f
    }

    // Draw tick marks
    val spacing = w * 0.10f
    val tickCount = 12
    val tickHeight = h * 0.50f
    val phase = (hue * pixelsPerHue) % spacing
    val center = (w / 2f)
    for (i in 0..tickCount) {
        val relativeTx = i * spacing - phase - center
        val tx = center + relativeTx - spacing / 2 + tickWidthPx * 4

        val distanceToCenter = abs(relativeTx)
        val maxDistance = w * 0.75f
        val alphaForTick = 0.5f - (distanceToCenter / maxDistance)

        if (tx <= w) {
            val tickTop = (h - tickHeight) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = alphaForTick),
                topLeft = Offset(tx - tickWidthPx / 2f, tickTop),
                size = Size(tickWidthPx, tickHeight),
                CornerRadius(x = 1f, y = 1f),
            )
        }
    }
}

@Composable
@Preview
private fun ColorWheelPreview() {
    FlipcashDesignSystem {
        val hsv = Color(0xFF2D36FA).hsv
        var selectedHsv by remember {
            mutableStateOf(hsv)
        }

        HueScroller(
            modifier = Modifier
                .padding(CodeTheme.dimens.inset)
                .aspectRatio(16 / 9f),
            hsv = selectedHsv,
            onChange = { hsv, _ -> selectedHsv = hsv }
        )
    }
}
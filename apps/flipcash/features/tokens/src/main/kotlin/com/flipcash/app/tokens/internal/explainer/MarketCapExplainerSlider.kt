package com.flipcash.app.tokens.internal.explainer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import com.flipcash.app.tokens.marketcap.ExplainerLabelBox
import com.flipcash.app.tokens.marketcap.layoutExplainerLabels
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flipcash.app.tokens.marketcap.ExplainerTick
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.util.vibration.LocalVibrator
import androidx.compose.ui.tooling.preview.Preview

/**
 * A continuous slider whose track is log(reserve). The [ticks] are labelled marks, not snap points:
 * the thumb moves freely, and a light haptic fires each time it crosses one.
 *
 * @param position 0..1 along the track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketCapExplainerSlider(
    position: Float,
    ticks: List<ExplainerTick>,
    tickLabel: (ExplainerTick) -> String,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragEnd: () -> Unit = {},
) {
    val vibrator = LocalVibrator.current
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentPosition by rememberUpdatedState(position)
    val currentTicks by rememberUpdatedState(ticks)
    val change by rememberUpdatedState { next: Float ->
        val previous = currentPosition
        val crossed = currentTicks.any { tick ->
            val p = tick.position.toFloat()
            (previous < p && next >= p) || (previous > p && next <= p)
        }
        if (crossed) vibrator.tick()
        onPositionChange(next.coerceIn(0f, 1f))
    }

    val tickColor = CodeTheme.colors.textSecondary.copy(alpha = 0.3f)
    val todayTickColor = CodeTheme.colors.textMain
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(
        thumbColor = CodeTheme.colors.success,
        activeTrackColor = CodeTheme.colors.success,
        inactiveTrackColor = CodeTheme.colors.surfaceVariant,
    )
    // The thumb is a 26dp disc and its centre travels from half a thumb in to half a thumb short of
    // the far end; ticks and labels use the same geometry so they line up with it.
    val thumbWidth = 38.dp
    val thumbHeight = 24.dp
    val sideInsetPx = with(density) { (thumbWidth / 2).toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        // Slider supplies the drag, tap and progress semantics; the thumb is the stock M3 handle.
        Slider(
            value = position.coerceIn(0f, 1f),
            onValueChange = { change(it) },
            onValueChangeFinished = { currentOnDragEnd() },
            interactionSource = interactionSource,
            colors = colors,
            thumb = {
                // iOS: an opaque white capsule at rest; held, it grows ~1.5x and turns translucent glass
                // (the track shows through). Android has no Liquid Glass, so the glass is a dark
                // frosted fill with a light rim, sprung between the two states.
                val pressed by interactionSource.collectIsPressedAsState()
                val dragged by interactionSource.collectIsDraggedAsState()
                val held = pressed || dragged
                val glass by animateFloatAsState(
                    if (held) 1f else 0f,
                    spring(dampingRatio = 0.7f, stiffness = 500f),
                    label = "thumbGlass",
                )
                Box(
                    Modifier
                        .size(thumbWidth, thumbHeight)
                        .graphicsLayer {
                            val s = 1f + 0.5f * glass
                            scaleX = s
                            scaleY = s
                        }
                        .shadow((4f * (1f - glass)).dp, CircleShape)
                        .background(
                            lerp(Color.White, CodeTheme.colors.toggleUncheckedTrackColor.copy(alpha = 0.45f), glass),
                            CircleShape,
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.28f * glass), CircleShape),
                )
            },
            track = { state ->
                Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    SliderDefaults.Track(
                        sliderState = state,
                        modifier = Modifier.height(10.dp),
                        colors = colors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 0.dp,
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val travel = size.width - sideInsetPx * 2
                        val thumbCentre = sideInsetPx + travel * state.value.coerceIn(0f, 1f)
                        ticks.forEach { tick ->
                            val tx = sideInsetPx + travel * tick.position.toFloat()
                            // The disc covers the track here; skip ticks that would peek out below it.
                            if (kotlin.math.abs(tx - thumbCentre) < sideInsetPx + 2.dp.toPx()) return@forEach
                            val (h, color) = if (tick.isToday) 10.dp.toPx() to todayTickColor
                            else 6.dp.toPx() to tickColor
                            val top = size.height / 2 + 9.dp.toPx()
                            drawRoundRect(
                                color,
                                Offset(tx - 1.dp.toPx(), top),
                                Size(2.dp.toPx(), h),
                                CornerRadius(1.dp.toPx()),
                            )
                        }
                    }
                }
            },
        )

        // Labels sit under the ticks, centred on them, clamped inside the track's bounds. Today is
        // always drawn; a fixed label is dropped when its measured bounds come within 6dp of
        // Today's or of one already drawn (labels are localized currency, so widths vary).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val textMeasurer = rememberTextMeasurer()
            val todayText = stringResource(R.string.label_marketCapTickToday)
            val regular = CodeTheme.typography.textSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
            val bold = regular.copy(fontWeight = FontWeight.Bold)
            val shown = ticks.filter { it.isToday || it.labelVisible }
            val texts = shown.map { if (it.isToday) todayText else tickLabel(it) }
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val travelPx = totalWidthPx - sideInsetPx * 2
            val gapPx = with(density) { 6.dp.toPx() }
            val boxes = shown.mapIndexed { i, tick ->
                val width = textMeasurer.measure(texts[i], if (tick.isToday) bold else regular).size.width
                ExplainerLabelBox(
                    centre = sideInsetPx + travelPx * tick.position.toFloat(),
                    width = width.toFloat(),
                    isToday = tick.isToday,
                )
            }
            val lefts = layoutExplainerLabels(boxes, totalWidthPx, gapPx)
            shown.forEachIndexed { i, tick ->
                val left = lefts[i] ?: return@forEachIndexed
                Text(
                    modifier = Modifier.offset { IntOffset(left.roundToInt(), 0) },
                    text = texts[i],
                    maxLines = 1,
                    softWrap = false,
                    style = if (tick.isToday) bold else regular,
                    color = if (tick.isToday) CodeTheme.colors.textMain else CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}


@Preview
@Composable
private fun Preview_Slider() {
    FlipcashPreview(showBackground = true) {
        MarketCapExplainerSlider(
            position = 0.4f,
            ticks = listOf(
                ExplainerTick(java.math.BigDecimal(5_000), 0.0, false, true),
                ExplainerTick(java.math.BigDecimal(22_700), 0.4, true, true),
                ExplainerTick(java.math.BigDecimal(100_000), 0.58, false, true),
                ExplainerTick(java.math.BigDecimal(1_000_000), 0.8, false, true),
                ExplainerTick(java.math.BigDecimal(10_000_000), 1.0, false, true),
            ),
            tickLabel = { "$" + it.reserve.toPlainString() },
            onPositionChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

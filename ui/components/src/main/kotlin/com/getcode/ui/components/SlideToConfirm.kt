package com.getcode.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import com.getcode.theme.White20
import com.getcode.theme.White50
import com.getcode.ui.theme.CodeCircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

object SlideToConfirmDefaults {
    @Composable
    fun Hint(
        text: String,
        swipeFraction: Float,
        modifier: Modifier = Modifier,
    ) {
        val hintTextColor by remember(swipeFraction) {
            derivedStateOf { calculateHintTextColor(swipeFraction) }
        }

        Text(
            text = text,
            color = hintTextColor,
            style = CodeTheme.typography.textMedium,
            modifier = modifier,
        )
    }


    const val SnapThreshold = 0.7f
}

private object Thumb {
    val Size: Dp
        @Composable get() = CodeTheme.dimens.grid.x12
    val Color = androidx.compose.ui.graphics.Color.White
    val Shape: Shape
        @Composable get() = CodeTheme.shapes.small
}

private object Track {
    val Shape: Shape
        @Composable get() = CodeTheme.shapes.small

    val Color = White20
}


@Stable
class SlideHintState(private val scope: CoroutineScope) {

    var shouldBump by mutableStateOf(false)
        private set

    private var timer: TimerTask? = null

    private suspend fun tick() {
        shouldBump = true
        delay(150)
        shouldBump = false
    }

    fun cancelTimer() {
        timer?.cancel()
        timer = null
    }

    fun startTimer() {
        timer = Timer().schedule(
            3.seconds.inWholeMilliseconds,
            4.seconds.inWholeMilliseconds
        ) {
            scope.launch { tick() }
        }
    }
}

@Composable
fun SlideToConfirm(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    trackShape: Shape = Track.Shape,
    trackColor: Color = Track.Color,
    thumbShape: Shape = Thumb.Shape,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    label: String = stringResource(R.string.action_swipeToPay),
    hint: @Composable BoxScope.(Float, PaddingValues, String) -> Unit = { swipe, padding, text ->
        SlideToConfirmDefaults.Hint(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(padding),
            swipeFraction = swipe,
            text = text,
        )
    },
) {
    val currentIsLoading by rememberUpdatedState(isLoading)
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbSize = Thumb.Size
    val horizontalPadding = CodeTheme.dimens.grid.x1
    val overhead = with(density) { (2 * horizontalPadding + thumbSize).toPx() }

    val swipeState = remember {
        AnchoredDraggableState(
            initialValue = Anchor.Start,
            positionalThreshold = { totalDistance ->
                SlideToConfirmDefaults.SnapThreshold * (totalDistance - overhead).coerceAtLeast(0f)
            },
            velocityThreshold = { with(density) { 1250.dp.toPx() } },
            snapAnimationSpec = spring(),
            decayAnimationSpec = splineBasedDecay(density),
        )
    }

    val composeScope = rememberCoroutineScope()
    val hintState = remember { SlideHintState(composeScope) }

    val swipeFraction by remember {
        derivedStateOf {
            val offset = swipeState.offset
            val end = swipeState.anchors.positionOf(Anchor.End)
            if (offset.isNaN() || end.isNaN() || end <= 0f) 0f
            else (offset / end).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(swipeFraction, enabled) {
        when {
            !enabled -> hintState.cancelTimer()
            swipeFraction == 0f -> hintState.startTimer()
            swipeFraction in 0.1f .. 0.99f -> hintState.cancelTimer()
        }
    }
    LaunchedEffect(swipeFraction) {
        if (swipeFraction == 1f) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onConfirm()
            // Give the caller a moment to set isLoading = true.
            // If they don't, the confirmation was rejected — reset.
            // Launch in composeScope so the animation isn't cancelled when
            // swipeFraction changes (which would cancel this LaunchedEffect).
            delay(200)
            if (!currentIsLoading) {
                composeScope.launch { swipeState.animateTo(Anchor.Start) }
            }
        }
    }

    // Handle the loading → idle transition (e.g. after a network call completes)
    LaunchedEffect(isLoading) {
        if (!isLoading && swipeState.currentValue == Anchor.End) {
            swipeState.animateTo(Anchor.Start)
        }
    }

    val disabledAlpha by animateFloatAsState(
        targetValue = if (enabled || isLoading || isSuccess) 1f else 0.38f,
        label = "disabled alpha"
    )

    Track(
        swipeState = swipeState,
        enabled = enabled && !isLoading,
        modifier = modifier.alpha(disabledAlpha),
        shape = trackShape,
        color = trackColor,
    ) {
        if (!isSuccess && !isLoading) {
            hint(swipeFraction, PaddingValues(horizontal = Thumb.Size + CodeTheme.dimens.grid.x2), label)
        }

        if (isSuccess) {
            Image(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = "",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .size(CodeTheme.dimens.grid.x4)
                    .align(Alignment.Center),
            )
        } else {
            val loadingColor by animateColorAsState(
                targetValue = if (isLoading) Color.White else Color.Transparent
            )

            CodeCircularProgressIndicator(
                strokeWidth = CodeTheme.dimens.thickBorder,
                color = loadingColor,
                modifier = Modifier
                    .size(CodeTheme.dimens.grid.x4)
                    .align(Alignment.Center),
            )
        }

        val thumbAlpha by animateFloatAsState(
            targetValue = if (isLoading || isSuccess) 0f else 1f,
            label = "thumb alpha"
        )

        val bumpFactor by animateDpAsState(
            targetValue = if (hintState.shouldBump) 20.dp else 0.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "hint bump peek offset",
        )

        Thumb(
            shape = thumbShape,
            modifier = Modifier
                .alpha(thumbAlpha)
                .offset {
                    val rawOffset = swipeState.offset
                    val x = if (rawOffset.isNaN()) 0
                        else rawOffset.roundToInt() + bumpFactor.roundToPx()
                    IntOffset(x = x, y = 0)
                },
        )
    }
}

enum class Anchor { Start, End }

@Composable
private fun Track(
    swipeState: AnchoredDraggableState<Anchor>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = Track.Shape,
    color: Color = Track.Color,
    content: @Composable (BoxScope.() -> Unit),
) {
    var fullWidth by remember { mutableIntStateOf(0) }

    val horizontalPadding = CodeTheme.dimens.grid.x1
    val thumbSize = Thumb.Size

    LaunchedEffect(fullWidth) {
        if (fullWidth > 0) {
            swipeState.updateAnchors(
                DraggableAnchors {
                    Anchor.Start at 0f
                    Anchor.End at fullWidth.toFloat()
                }
            )
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { fullWidth = it.width }
            .height(thumbSize + CodeTheme.dimens.grid.x1)
            .fillMaxWidth()
            .anchoredDraggable(
                state = swipeState,
                enabled = enabled,
                orientation = Orientation.Horizontal,
            )
            .background(
                color = color,
                shape = shape,
            )
            .clip(shape)
            .padding(
                PaddingValues(
                    horizontal = horizontalPadding,
                    vertical = CodeTheme.dimens.grid.x1,
                )
            ),
        content = content,
    )
}

@Composable
private fun Thumb(
    modifier: Modifier = Modifier,
    size: Dp = Thumb.Size,
    color: Color = Thumb.Color,
    shape: Shape = Thumb.Shape,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = color, shape = shape)
            .padding(CodeTheme.dimens.grid.x2),
        contentAlignment = Alignment.Center
    ) {
        Image(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
        )
    }
}

private fun calculateHintTextColor(swipeFraction: Float): Color {
    val endOfFadeFraction = 0.35f
    val fraction = (swipeFraction / endOfFadeFraction).coerceIn(0f..1f)
    return lerp(Color.White, Color.White.copy(alpha = 0f), fraction)
}


@Preview
@Composable
private fun InteractivePreview() {
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    DesignSystem {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(
                    horizontal = CodeTheme.dimens.inset,
                    vertical = CodeTheme.dimens.grid.x6
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            SlideToConfirm(
                isLoading = isLoading,
                isSuccess = isSuccess,
                onConfirm = { isLoading = true },
            )

            LaunchedEffect(isLoading) {
                if (isLoading) {
                    delay(1500)
                    isSuccess = true
                }
            }

            LaunchedEffect(isSuccess) {
                if (isSuccess) {
                    delay(1500)
                    isLoading = false
                    isSuccess = false
                }
            }
        }
    }
}

@Preview
@Composable
private fun StatesPreview() {
    DesignSystem {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(
                    horizontal = CodeTheme.dimens.inset,
                    vertical = CodeTheme.dimens.grid.x6
                ),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            Text(
                text = "Enabled",
                color = White50,
                style = CodeTheme.typography.caption,
            )
            SlideToConfirm(
                onConfirm = {},
            )
            Text(
                text = "Disabled",
                color = White50,
                style = CodeTheme.typography.caption,
            )
            SlideToConfirm(
                onConfirm = {},
                enabled = false,
            )
            Text(
                text = "Loading",
                color = White50,
                style = CodeTheme.typography.caption,
            )
            SlideToConfirm(
                onConfirm = {},
                isLoading = true,
            )
            Text(
                text = "Success",
                color = White50,
                style = CodeTheme.typography.caption,
            )
            SlideToConfirm(
                onConfirm = {},
                isSuccess = true,
            )
        }
    }
}

@file:OptIn(ExperimentalMaterialApi::class)

package com.getcode.view.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SwipeProgress
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.SwipeableState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.getcode.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White50
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
            style = MaterialTheme.typography.body1,
            modifier = modifier,
        )
    }
}

private object Thumb {
    val Size = 60.dp
    val Color = androidx.compose.ui.graphics.Color.White
    val Shape = RoundedCornerShape(8.dp)
}

private object Track {
    val VelocityThreshold = SwipeableDefaults.VelocityThreshold * 10
    val Shape = RoundedCornerShape(8.dp)
    val Color = Color(0xFF201D1D)
}


@Composable
fun SlideToConfirm(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    trackShape: Shape = Track.Shape,
    thumbShape: Shape = Thumb.Shape,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    hint: @Composable BoxScope.(Float, PaddingValues) -> Unit = { swipe, padding ->
        SlideToConfirmDefaults.Hint(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(padding),
            swipeFraction = swipe,
            text = "Swipe to Pay"
        )
    },
) {
    var loading by remember(isLoading) {
        mutableStateOf(isLoading)
    }
    val hapticFeedback = LocalHapticFeedback.current
    val swipeState = rememberSwipeableState(
        initialValue = if (loading) Anchor.End else Anchor.Start,
        confirmStateChange = { anchor ->
            if (anchor == Anchor.End) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                loading = true
                onConfirm()
            }
            true
        }
    )

    val swipeFraction by remember {
        derivedStateOf { calculateSwipeFraction(swipeState.progress) }
    }

    LaunchedEffect(loading) {
        swipeState.animateTo(if (loading) Anchor.End else Anchor.Start)
    }

    Track(
        swipeState = swipeState,
        enabled = !loading,
        modifier = modifier,
        shape = trackShape,
    ) {
        if (!isSuccess) {
            hint(swipeFraction, PaddingValues(horizontal = Thumb.Size + 8.dp))
        }

        when {
            isSuccess -> {
                Image(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "",
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center),
                )
            }
            loading -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = White,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center),
                )
            }
        }

        val thumbAlpha by animateFloatAsState(
            targetValue = if (loading || isSuccess) 0f else 1f,
            label = "thumb alpha"
        )

        Thumb(
            shape = thumbShape,
            modifier = Modifier
                .alpha(thumbAlpha)
                .offset {
                    IntOffset(swipeState.offset.value.roundToInt(), 0)
                },
        )
    }
}

private fun calculateSwipeFraction(progress: SwipeProgress<Anchor>): Float {
    val atAnchor = progress.from == progress.to
    val fromStart = progress.from == Anchor.Start
    return if (atAnchor) {
        if (fromStart) 0f else 1f
    } else {
        if (fromStart) progress.fraction else 1f - progress.fraction
    }
}

enum class Anchor { Start, End }

@Composable
private fun Track(
    swipeState: SwipeableState<Anchor>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = Track.Shape,
    content: @Composable (BoxScope.() -> Unit),
) {
    val density = LocalDensity.current
    var fullWidth by remember { mutableIntStateOf(0) }

    val horizontalPadding = 4.dp

    val startOfTrackPx = 0f
    val endOfTrackPx = remember(fullWidth) {
        with(density) { fullWidth - (2 * horizontalPadding + Thumb.Size).toPx() }
    }

    val snapThreshold = 0.8f
    val thresholds = { from: Anchor, _: Anchor ->
        if (from == Anchor.Start) {
            FractionalThreshold(snapThreshold)
        } else {
            FractionalThreshold(1f - snapThreshold)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { fullWidth = it.width }
            .height(52.dp)
            .fillMaxWidth()
            .swipeable(
                enabled = enabled,
                state = swipeState,
                orientation = Orientation.Horizontal,
                anchors = mapOf(
                    startOfTrackPx to Anchor.Start,
                    endOfTrackPx to Anchor.End,
                ),
                thresholds = thresholds,
                velocityThreshold = Track.VelocityThreshold,
            )
            .background(
                color = Track.Color,
                shape = shape,
            )
            .padding(
                PaddingValues(
                    horizontal = horizontalPadding,
                    vertical = 4.dp,
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
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            Icons.Rounded.ArrowForward,
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
private fun Preview() {
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    CodeTheme {
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .background(Color.White)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black)
                    .padding(horizontal = 20.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                SlideToConfirm(
                    isLoading = isLoading,
                    isSuccess = isSuccess,
                    onConfirm = { isLoading = true },
                )
                AnimatedContent(
                    targetState = !isLoading,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { show ->
                    if (show) {
                        TextButton(
                            shape = RoundedCornerShape(percent = 50),
                            onClick = { isLoading = false }) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.caption,
                                color = White50
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.minimumInteractiveComponentSize())
                    }
                }

                LaunchedEffect(isLoading) {
                    if (isLoading) {
                        delay(1500)
                        isSuccess = true
                    }
                }
            }
        }
    }
}
package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.getcode.util.vibration.LocalVibrator
import kotlin.math.roundToInt

internal enum class ReplyDragAnchor { Rest, Reply }

/**
 * A trailing-ward drag on a message row that dispatches a reply.
 *
 * Ported from the legacy `MessageNode`, including its numbers. The row never settles open —
 * `confirmValueChange` always refuses — so the gesture is a pull that springs back: the haptic fires
 * the moment the threshold is crossed, and the action fires as the row returns, which is what makes
 * an abandoned drag cost nothing.
 */
@Composable
internal fun rememberSwipeToReply(
    enabled: Boolean,
    onReply: () -> Unit,
): Modifier {
    if (!enabled) return Modifier

    val density = LocalDensity.current
    val vibrator = LocalVibrator.current
    // The screen width, not the row's own. A row spans the transcript's full width, and reading it
    // from BoxWithConstraints would mean building this modifier inside the constraints scope —
    // which is not where the row's outer Box applies its modifiers.
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = widthPx * SWIPE_THRESHOLD_FRACTION
    var crossed by remember { mutableStateOf(false) }

    val anchors = remember(threshold) {
        DraggableAnchors {
            ReplyDragAnchor.Rest at 0f
            ReplyDragAnchor.Reply at threshold
        }
    }

    // The deprecated constructor, deliberately. Its replacement drops confirmValueChange, and that
    // veto is the whole gesture: without it a fling past the threshold settles the row open, which
    // is a state this interaction has no way back out of.
    @Suppress("DEPRECATION")
    val dragState = remember(anchors) {
        AnchoredDraggableState(
            initialValue = ReplyDragAnchor.Rest,
            anchors = anchors,
            positionalThreshold = { it * 0.9f },
            velocityThreshold = { Float.POSITIVE_INFINITY },
            confirmValueChange = { target ->
                if (target == ReplyDragAnchor.Reply && !crossed) {
                    crossed = true
                    vibrator.tick()
                }
                false
            },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            decayAnimationSpec = splineBasedDecay(density),
        )
    }

    LaunchedEffect(crossed, dragState.targetValue) {
        if (crossed &&
            dragState.targetValue == ReplyDragAnchor.Rest &&
            dragState.isAnimationRunning
        ) {
            onReply()
            crossed = false
        }
    }

    return Modifier
        .anchoredDraggable(
            state = dragState,
            orientation = Orientation.Horizontal,
        )
        .offset {
            IntOffset(
                x = dragState.offset.coerceIn(0f, widthPx * MAX_OFFSET_FRACTION).roundToInt(),
                y = 0,
            )
        }
}

private const val SWIPE_THRESHOLD_FRACTION = 0.40f
private const val MAX_OFFSET_FRACTION = 0.30f

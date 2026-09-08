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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.getcode.util.vibration.LocalVibrator
import kotlin.math.roundToInt

internal enum class ReplyDragAnchor { Rest, Reply }

/**
 * A trailing-ward drag on a message row that dispatches a reply.
 *
 * The row never settles open — `confirmValueChange` always refuses — so the gesture is a pull that
 * springs back: the haptic fires the moment the threshold is crossed, and the action fires as the
 * row returns, which is what makes an abandoned drag cost nothing.
 *
 * The distances are iOS's, in absolute units rather than the fractions of screen width this was
 * ported with. They have to be: [progress] is the fraction of the trigger distance travelled, and it
 * is what draws the affordance, so a threshold that moves with the screen would put the icon at a
 * different point of its reveal on every device — and on a 411dp screen the old 0.40 fraction put
 * the trigger at roughly three times iOS's.
 */
@Composable
internal fun rememberSwipeToReply(
    enabled: Boolean,
    onReply: () -> Unit,
): SwipeToReplyState {
    val density = LocalDensity.current
    val vibrator = LocalVibrator.current
    val maxPx = with(density) { MAX_TRANSLATION.toPx() }
    val triggerPx = with(density) { TRIGGER_THRESHOLD.toPx() }
    var crossed by remember { mutableStateOf(false) }

    val anchors = remember(maxPx) {
        DraggableAnchors {
            ReplyDragAnchor.Rest at 0f
            ReplyDragAnchor.Reply at maxPx
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
            // A fraction of the distance between the anchors, so the trigger lands at
            // TRIGGER_THRESHOLD rather than at the full travel.
            positionalThreshold = { it * (triggerPx / maxPx) },
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

    // Nothing to drag and nothing to draw, but the hooks above still have to be called in the same
    // order on every composition, so the disabled case is decided here rather than at the top.
    if (!enabled) return SwipeToReplyState.Disabled

    return remember(dragState, maxPx, triggerPx) {
        SwipeToReplyState(
            modifier = Modifier
                .anchoredDraggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                )
                .offset {
                    IntOffset(x = dragState.clampedOffset(maxPx).roundToInt(), y = 0)
                },
            // Read through a lambda, not captured: this is sampled inside a graphicsLayer block, so
            // the affordance redraws on every frame of the drag without recomposing the row.
            offsetPx = { dragState.clampedOffset(maxPx) },
            triggerPx = triggerPx,
        )
    }
}

/**
 * What a row needs from the gesture: the modifier that carries it, and how far it has travelled, so
 * the row can draw the affordance the drag is uncovering.
 */
internal class SwipeToReplyState(
    val modifier: Modifier,
    private val offsetPx: () -> Float,
    private val triggerPx: Float,
) {
    /** How far the drag has come as a fraction of the distance that fires the reply, capped at 1. */
    fun progress(): Float = (offsetPx() / triggerPx).coerceIn(0f, 1f)

    companion object {
        val Disabled = SwipeToReplyState(Modifier, { 0f }, 1f)
    }
}

/**
 * The drag's travel, capped at the full translation and never NaN — `offset` has no value until the
 * anchors have been applied, and a NaN reaching `IntOffset` throws.
 */
@Suppress("DEPRECATION")
private fun AnchoredDraggableState<ReplyDragAnchor>.clampedOffset(maxPx: Float): Float =
    offset.takeIf { !it.isNaN() }?.coerceIn(0f, maxPx) ?: 0f

/** iOS's `maxTranslation`: how far the row itself can move. */
private val MAX_TRANSLATION = 64.dp

/** iOS's `triggerThreshold`: the travel that arms the reply and fires the haptic. */
private val TRIGGER_THRESHOLD = 48.dp

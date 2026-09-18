package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.getcode.util.vibration.LocalVibrator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A trailing-ward drag on a message row that dispatches a reply.
 *
 * The row never settles open: the gesture is a pull that springs back. The haptic fires the moment
 * the threshold is crossed, and the reply fires on release, which is what makes an abandoned drag
 * cost nothing — a drag that passes the threshold and comes back sends nothing.
 *
 * Past [MAX_TRANSLATION] the row keeps following the finger with diminishing returns rather than
 * stopping dead, so a hard swipe still feels connected. The affordance does not follow it there —
 * it holds at the end of its own travel, because it marks where the gesture fires and there is
 * nothing further along to mark.
 *
 * [leadingGutter] is the strip at the row's leading edge that the bubble does not reach — see
 * [replyGutterFor]. A drag that starts inside it is inert: a row that opened from a point that is
 * not the message would promise a reply it is not going to send.
 *
 * The distances are iOS's, in absolute units rather than the fractions of screen width this was
 * ported with. They have to be: [SwipeToReplyState.progress] is the fraction of the trigger
 * distance travelled, and it is what draws the affordance, so a threshold that moves with the
 * screen would put the icon at a different point of its reveal on every device — and on a 411dp
 * screen the old 0.40 fraction put the trigger at roughly three times iOS's.
 */
@Composable
internal fun rememberSwipeToReply(
    enabled: Boolean,
    leadingGutter: Dp,
    onReply: () -> Unit,
): SwipeToReplyState {
    val density = LocalDensity.current
    val vibrator = LocalVibrator.current
    val scope = rememberCoroutineScope()

    val maxPx = with(density) { MAX_TRANSLATION.toPx() }
    val triggerPx = with(density) { TRIGGER_THRESHOLD.toPx() }
    val gutterPx = with(density) { leadingGutter.toPx() }

    val reply by rememberUpdatedState(onReply)

    // The finger's raw travel, before resistance; the row's own offset is derived from it on every
    // read. Keeping the raw value is what lets the rubber band be a pure function: a drag 200dp
    // along is at 200dp of travel whatever the row is showing, so dragging back out of the band
    // retraces the same curve instead of starting a second one.
    val travel = remember { mutableFloatStateOf(0f) }
    val latch = remember { DragLatch() }

    val dragState = rememberDraggableState { delta ->
        // A drag off the message moves nothing: the row opening under a finger that cannot reply
        // would promise an action it is not going to take.
        if (!latch.armed) return@rememberDraggableState
        travel.floatValue = (travel.floatValue + delta).coerceAtLeast(0f)
        if (!latch.crossed && resist(travel.floatValue, maxPx) > triggerPx) {
            latch.crossed = true
            vibrator.tick()
        }
    }

    // A row that loses the gesture mid-drag — the backdrop coming up is the way that happens —
    // drops its modifier with it, so the travel has to be cleared here or the next enabled frame
    // would draw the row still held open.
    LaunchedEffect(enabled) { if (!enabled) travel.floatValue = 0f }

    // Nothing to drag and nothing to draw, but the hooks above still have to be called in the same
    // order on every composition, so the disabled case is decided here rather than at the top.
    if (!enabled) return SwipeToReplyState.Disabled

    return remember(dragState, maxPx, triggerPx, gutterPx) {
        SwipeToReplyState(
            modifier = Modifier
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { start ->
                        latch.settle?.cancel()
                        latch.armed = start.x >= gutterPx
                        latch.crossed = false
                    },
                    onDragStopped = {
                        val fires = latch.armed && resist(travel.floatValue, maxPx) > triggerPx
                        latch.armed = false
                        latch.crossed = false
                        // Sent before the row is back: the reply is the answer to the gesture, and
                        // waiting out the spring reads as lag.
                        if (fires) reply()
                        // Launched on the composition's scope, not the gesture's, which ends with
                        // the finger — the spring outlives it.
                        latch.settle = scope.launch {
                            animate(travel.floatValue, 0f, animationSpec = SpringBack) { value, _ ->
                                travel.floatValue = value
                            }
                        }
                    },
                )
                .offset {
                    IntOffset(x = resist(travel.floatValue, maxPx).roundToInt(), y = 0)
                },
            // Read through a lambda, not captured: this is sampled inside a graphicsLayer block, so
            // the affordance redraws on every frame of the drag without recomposing the row.
            offsetPx = { resist(travel.floatValue, maxPx) },
            triggerPx = triggerPx,
            affordanceEndPx = maxPx,
        )
    }
}

/**
 * The strip at a row's leading edge that a reply drag may not start in: everything ahead of the
 * bubble, and nothing of the bubble itself.
 *
 * Only one kind of row has its bubble hard against the leading edge — an incoming message in a DM,
 * which reserves no avatar column and is not pushed inward by one. Every other row leads with
 * something that is not the message: the avatar column on an incoming row of a group, which is the
 * target for the person rather than for what they said, and empty transcript on an outgoing row,
 * where the bubble is aligned to the far side.
 *
 * [width] is that one avatar column's width, reused as the outgoing strip so both read the same. It
 * cannot eat into an outgoing bubble: a bubble stops at 0.78 of the row, which leaves far more than
 * this to its leading side at every width the app runs at. The one content that fills the row is a
 * system notice, and those carry no capabilities at all, so no drag on one was ever going to reply.
 */
internal fun replyGutterFor(width: Dp, isFromSelf: Boolean, showsSenderGutter: Boolean): Dp =
    if (!isFromSelf && !showsSenderGutter) 0.dp else width

/**
 * What a row needs from the gesture: the modifier that carries it, and how far it has travelled, so
 * the row can draw the affordance the drag is uncovering.
 */
internal class SwipeToReplyState(
    val modifier: Modifier,
    private val offsetPx: () -> Float,
    private val triggerPx: Float,
    private val affordanceEndPx: Float,
) {
    /** How far the drag has come as a fraction of the distance that fires the reply, capped at 1. */
    fun progress(): Float = (offsetPx() / triggerPx).coerceIn(0f, 1f)

    /**
     * How far the affordance has to be pulled back against its row to stay put once the drag is
     * past [affordanceEndPx]. The affordance is a child of the row, so it is already carried by the
     * row's offset; holding it still means cancelling whatever of that offset runs past the end.
     */
    fun affordanceTranslationPx(): Float = -(offsetPx() - affordanceEndPx).coerceAtLeast(0f)

    companion object {
        val Disabled = SwipeToReplyState(Modifier, { 0f }, 1f, 0f)
    }
}

/**
 * Per-drag bookkeeping, written from the gesture's own callbacks.
 *
 * Plain fields rather than snapshot state on purpose: only the gesture reads them, nothing drawn
 * depends on them, and a write mid-drag that recomposed the row would cost a frame of the transcript
 * for no visible change.
 */
private class DragLatch {
    /** Whether this drag may reply at all — false for one that started in the leading gutter. */
    var armed = false

    /** Whether the threshold haptic has fired. Latched for the rest of the drag, so a finger held
     *  just past the threshold ticks once and not on every frame. */
    var crossed = false

    /** The spring-back, kept so a new drag can interrupt it and carry on from where the row is. */
    var settle: Job? = null
}

/**
 * How far the row moves for a raw travel: itself up to [maxPx], resisted past it.
 *
 * The rubber band approaches one more [maxPx] of travel and never reaches it, so the row stays
 * connected to a hard swipe without running out from under the transcript.
 */
internal fun resist(travel: Float, maxPx: Float): Float = when {
    travel <= 0f -> 0f
    travel <= maxPx -> travel
    else -> {
        val overshoot = travel - maxPx
        maxPx + maxPx * overshoot / (overshoot + maxPx)
    }
}

private val SpringBack = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** iOS's `maxTranslation`: how far the row travels before the drag starts to resist. */
private val MAX_TRANSLATION = 64.dp

/** iOS's `triggerThreshold`: the travel that arms the reply and fires the haptic. */
private val TRIGGER_THRESHOLD = 48.dp

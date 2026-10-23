package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * A leading-ward drag anywhere on the transcript that slides the viewer's own bubbles aside and
 * uncovers the time each message was sent.
 *
 * One offset for the whole list, not one per row: every visible row moves together, so the state
 * lives here and the rows only read it, inside layer blocks, so a drag redraws them without
 * recomposing any. Like the reply swipe it is a pull that springs back — nothing fires on release,
 * and there is no threshold and no haptic.
 *
 * The offset follows the reply swipe's [resist] curve at half the column's width and stops at the
 * full width: 1:1 for the first half, resisting after, never past [REVEAL_SHIFT]. iOS uses the same
 * curve, so the two apps open the column at the same rate.
 */
@Stable
internal class TimestampRevealState(
    private val columnPx: Float,
    private val scope: CoroutineScope,
) {
    // The finger's raw travel toward the leading edge, before resistance; see rememberSwipeToReply
    // for why the raw value is what is kept.
    private val travel = mutableFloatStateOf(0f)
    private var settle: Job? = null

    /**
     * How far the column has opened, in pixels: the viewer's own bubbles move left by this, and the
     * times slide in from the trailing edge by it.
     */
    fun offsetPx(): Float = revealOffset(travel.floatValue, columnPx)

    /** How open the column is, 0 to 1; the times fade in with it. */
    fun progress(): Float = offsetPx() / columnPx

    val shiftPx: Float get() = columnPx

    internal fun dragStarted() {
        settle?.cancel()
    }

    internal fun dragBy(leadingDelta: Float) {
        travel.floatValue = (travel.floatValue + leadingDelta).coerceAtLeast(0f)
    }

    internal fun dragStopped() {
        // On the list's scope, not the gesture's: the spring outlives the finger, and the row the
        // drag started on can scroll out of composition before it lands.
        settle = scope.launch {
            animate(travel.floatValue, 0f, animationSpec = SpringBack) { value, _ ->
                travel.floatValue = value
            }
        }
    }

    internal fun reset() {
        settle?.cancel()
        travel.floatValue = 0f
    }
}

@Composable
internal fun rememberTimestampReveal(enabled: Boolean): TimestampRevealState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val columnPx = with(density) { REVEAL_SHIFT.toPx() }
    val state = remember(columnPx, scope) { TimestampRevealState(columnPx, scope) }
    // The backdrop coming up takes the gesture away mid-drag; the column must not stay open behind it.
    LaunchedEffect(enabled) { if (!enabled) state.reset() }
    return state
}

/**
 * The transcript's side of the drag. Installed on the list itself and run on the initial pointer pass, so
 * it hears a drag before any row does: a row's reply swipe is a horizontal `draggable` that would
 * otherwise take a leading-ward drag too and do nothing with it.
 *
 * Each gesture is decided once, by the same orientation-locked slop the rows and the list use — see
 * [classifyTranscriptDrag]. Only a reveal is consumed; a reply or a scroll is left untouched for the
 * row or the list to take, which is what keeps the two directions exclusive.
 *
 * A drag that starts in the right edge's system gesture strip is left alone: that strip is the
 * predictive back gesture's, and with three-button navigation it is empty anyway.
 */
@Composable
internal fun Modifier.timestampRevealGesture(
    state: TimestampRevealState,
    enabled: Boolean,
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val backEdgePx = WindowInsets.systemGestures.getRight(density, layoutDirection)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    if (!enabled) return this

    return this
        .onPlaced { coordinates[0] = it }
        .pointerInput(state, backEdgePx, windowWidth) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val windowX = coordinates[0]?.takeIf { it.isAttached }?.positionInWindow()?.x ?: 0f
                if (startsInBackEdge(windowX + down.position.x, windowWidth.toFloat(), backEdgePx.toFloat())) {
                    return@awaitEachGesture
                }

                var dx = 0f
                var dy = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed || change.isConsumed) return@awaitEachGesture
                    val delta = change.positionChange()
                    dx += delta.x
                    dy += delta.y
                    when (classifyTranscriptDrag(dx, dy, viewConfiguration.touchSlop)) {
                        TranscriptDrag.Undecided -> continue
                        TranscriptDrag.Scroll, TranscriptDrag.Reply -> return@awaitEachGesture
                        TranscriptDrag.Reveal -> {
                            change.consume()
                            break
                        }
                    }
                }

                // The slop itself is not applied, as the reply swipe's draggable does not apply it:
                // the bubbles start moving from where they are rather than jumping by the slop.
                state.dragStarted()
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        // Consumed every frame, or the list's own scroll would start under a drag
                        // that is already the reveal's.
                        state.dragBy(-change.positionChange().x)
                        change.consume()
                    }
                } finally {
                    state.dragStopped()
                }
            }
        }
}

/** What a drag on the transcript turns out to be, once it has moved far enough to tell. */
internal enum class TranscriptDrag { Undecided, Scroll, Reply, Reveal }

/**
 * Decides a drag from its travel so far ([dx], [dy] from the down) against [slopPx].
 *
 * Orientation-locked, the way the rows' `draggable` and the list's scroll each measure their own
 * slop, rather than by the distance travelled: a verdict that differed from theirs would leave
 * drags that neither reveal nor reply. Horizontal is checked first because a row is a child of the
 * list and hears the drag first — on a diagonal that crosses both at once, the row's swipe wins.
 */
internal fun classifyTranscriptDrag(dx: Float, dy: Float, slopPx: Float): TranscriptDrag = when {
    dx.absoluteValue > slopPx -> if (dx < 0f) TranscriptDrag.Reveal else TranscriptDrag.Reply
    dy.absoluteValue > slopPx -> TranscriptDrag.Scroll
    else -> TranscriptDrag.Undecided
}

/** Whether a drag that went down at [windowX] started in the right edge's back-gesture strip. */
internal fun startsInBackEdge(windowX: Float, windowWidthPx: Float, backEdgePx: Float): Boolean =
    backEdgePx > 0f && windowX >= windowWidthPx - backEdgePx

/** How far the column opens for a raw [travel]: [resist] at half of [shiftPx], held at [shiftPx]. */
internal fun revealOffset(travel: Float, shiftPx: Float): Float =
    resist(travel, maxPx = shiftPx / 2f).coerceAtMost(shiftPx)

private val SpringBack = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** The time column: its label's width, and the margin it keeps from the screen's trailing edge. */
internal val REVEAL_COLUMN_WIDTH = 64.dp
internal val REVEAL_COLUMN_MARGIN = 12.dp

/** How far the viewer's own bubbles move at full reveal: the column and its margin. */
internal val REVEAL_SHIFT = REVEAL_COLUMN_WIDTH + REVEAL_COLUMN_MARGIN

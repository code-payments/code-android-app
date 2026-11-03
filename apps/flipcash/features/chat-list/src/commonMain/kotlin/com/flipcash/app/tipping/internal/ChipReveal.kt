package com.flipcash.app.tipping.internal

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.max
import kotlin.math.min

/** What a released drag does to a chip row that has not been revealed yet. */
internal enum class ChipSettle {
    /** Leave the release to the list's own fling. */
    None,

    /** Settle fully on the chips, which then stay. */
    Reveal,

    /** Settle just past the chips, hiding them again. */
    Park,

    /** Fling toward the top, but stop where the chips begin. */
    ClampedFling,
}

/**
 * How far the list projects a release, in seconds of its velocity. iOS's value: a quick flick from
 * the parked position reveals the chips even if the finger barely moved.
 */
private const val ProjectionSeconds = 0.1f

/**
 * Decides how a release settles while the chips are hidden.
 *
 * [pastPark] is how far the list sits beyond the parked position (item 1 at the top): negative
 * while the chips are partly on screen, zero when parked, positive once scrolled into the list.
 * [velocity] is the finger's velocity in px/s, positive when moving down (toward the top of the
 * list). [pullStartedAtTop] is whether the gesture began at the parked position; only such a pull
 * may reveal.
 */
internal fun chipSettle(
    pastPark: Float,
    chipHeight: Float,
    velocity: Float,
    pullStartedAtTop: Boolean,
): ChipSettle {
    if (chipHeight <= 0f) return if (velocity > 0f) ChipSettle.ClampedFling else ChipSettle.None
    val projected = pastPark - velocity * ProjectionSeconds
    return when {
        // More than half the chip row is (or would be) on screen.
        pullStartedAtTop && projected < -chipHeight / 2 -> ChipSettle.Reveal
        pastPark < 0f -> when {
            // Flung back into the list: its own fling carries the chips away.
            velocity < 0f && projected > 0f -> ChipSettle.None
            else -> ChipSettle.Park
        }
        velocity > 0f -> ChipSettle.ClampedFling
        else -> ChipSettle.None
    }
}

/**
 * Keeps the chip row (item 0 of [listState]) out of view until a pull from the top reveals it.
 *
 * Every scroll toward the top that did not begin at the parked position stops there, in
 * [onPreScroll] for drags and in a clamped fling for releases. The release of a pull settles in
 * [onPreFling] with one animation that starts at the finger's velocity and then reports all of it
 * consumed, so the list never runs a second fling after the settle.
 */
internal class ChipRevealConnection(
    private val listState: LazyListState,
    private val flingBehavior: FlingBehavior,
    private val isShown: () -> Boolean,
    private val canReveal: () -> Boolean,
    private val onRevealed: () -> Unit,
) : NestedScrollConnection {

    /** Set on pointer down; cleared when the gesture's release is handled. */
    private var pullStartedAtTop = false

    fun onPointerDown() {
        pullStartedAtTop = !isShown() && canReveal() && listState.pastPark() <= 1f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (isShown()) return Offset.Zero
        val dy = available.y
        // Scrolling into the list, or a pull that began at the top: the list takes it all.
        if (dy <= 0f) return Offset.Zero
        if (source == NestedScrollSource.UserInput && pullStartedAtTop) return Offset.Zero
        val room = max(listState.pastPark(), 0f)
        return if (dy > room) Offset(0f, dy - room) else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val fromTop = pullStartedAtTop
        pullStartedAtTop = false
        if (isShown()) return Velocity.Zero
        val pastPark = listState.pastPark()
        val chipHeight = listState.chipHeight()
        return when (chipSettle(pastPark, chipHeight, available.y, fromTop && canReveal())) {
            ChipSettle.None -> Velocity.Zero
            ChipSettle.Reveal -> {
                var marked = false
                val markRevealed = {
                    if (!marked) {
                        marked = true
                        onRevealed()
                    }
                }
                try {
                    // Revealed the moment the chips are fully on, not when the spring comes to
                    // rest: a touch in the spring's tail cancels it, and an unmarked row would be
                    // parked again by the next scroll toward the top.
                    settleBy(-(pastPark + chipHeight), velocity = -available.y, onReached = markRevealed)
                } finally {
                    // Cancelled before getting there, but already mostly on: keep them.
                    if (listState.pastPark() <= -chipHeight / 2) markRevealed()
                }
                available
            }
            ChipSettle.Park -> {
                settleBy(-pastPark, velocity = -available.y)
                available
            }
            ChipSettle.ClampedFling -> {
                clampedFling(velocity = -available.y)
                available
            }
        }
    }

    /**
     * Scrolls by [delta] on a spring that starts at [velocity] (in scroll direction), so the
     * release carries straight into the settle. Overshoot is held at the target rather than
     * scrolled past and back.
     */
    private suspend fun settleBy(delta: Float, velocity: Float, onReached: () -> Unit = {}) {
        if (delta == 0f) {
            onReached()
            return
        }
        val low = min(0f, delta)
        val high = max(0f, delta)
        listState.scroll {
            var applied = 0f
            animate(
                initialValue = 0f,
                targetValue = delta,
                initialVelocity = velocity,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ ->
                applied += scrollBy(value.coerceIn(low, high) - applied)
                if (applied == delta) onReached()
            }
        }
    }

    /** The list's own fling, stopped where the chips begin. */
    private suspend fun clampedFling(velocity: Float) {
        listState.scroll {
            val scope = this
            val clamped = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    if (pixels >= 0f) return scope.scrollBy(pixels)
                    // Toward the top: never past the parked position. Steps, because far from the
                    // top [pastPark] is only a lower bound; each step lands closer and re-measures.
                    var consumed = 0f
                    while (consumed > pixels) {
                        val step = max(pixels - consumed, -max(listState.pastPark(), 0f))
                        if (step == 0f) break
                        val moved = scope.scrollBy(step)
                        consumed += moved
                        if (moved != step) break
                    }
                    // A delta not fully consumed ends the fling.
                    return consumed
                }
            }
            with(flingBehavior) { clamped.performFling(velocity) }
        }
    }
}

/**
 * How far the list sits beyond the parked position (item 1 at the top of the content area):
 * negative while the chip row is partly on screen. Item offsets are relative to the content start,
 * so a chip row lying in the top padding (under the bar) still counts as hidden.
 *
 * Exact while item 0 or 1 is laid out, which includes the parked position: the chip row then sits
 * in the top padding, where the list still composes it. Further down it is a lower bound, the top
 * padding: item 1 is dropped only once it has scrolled past all of it.
 */
internal fun LazyListState.pastPark(): Float {
    val items = layoutInfo.visibleItemsInfo
    items.firstOrNull { it.index == 0 }?.let { return -(it.offset + it.size).toFloat() }
    items.firstOrNull { it.index == 1 }?.let { return -it.offset.toFloat() }
    return layoutInfo.beforeContentPadding.toFloat()
}

/**
 * Lets no touch through to the chip row while [blocked]: hidden, it lies in the list's top padding
 * under the title bar, and a tap on the bar's lower edge would otherwise land on an invisible chip.
 * Only the pointer is stopped; the row's semantics stay, so a screen reader can still reach it.
 */
internal fun Modifier.blockTouchesWhile(blocked: () -> Boolean): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // A consumed down never starts a click below. The list's own drag still starts from it.
        if (blocked()) down.consume()
    }
}

/**
 * Wires [connection] to the list it is placed on: records where each gesture starts, and takes
 * part in the list's nested scroll. Placed before the list's own scrolling.
 */
internal fun Modifier.chipReveal(connection: ChipRevealConnection): Modifier = this
    .pointerInput(connection) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            connection.onPointerDown()
        }
    }
    .nestedScroll(connection)

/** The chip row's height, or 0 while it is not laid out. */
internal fun LazyListState.chipHeight(): Float =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat() ?: 0f

/** How much of the chip row is on screen, 0 to 1. */
internal fun LazyListState.chipVisibleFraction(): Float {
    val height = chipHeight()
    if (height <= 0f) return 0f
    return (-pastPark() / height).coerceIn(0f, 1f)
}

/**
 * Keeps the list long enough to park. Parked means item 1 at the top, which a list can only hold
 * if everything from item 1 on is at least a viewport tall: shorter, and the list scrolls back to
 * index 0 and the chips show, on the first frame included. iOS sets the scroll distance outright;
 * here a last item makes up the difference, and nothing more, so the list never scrolls further
 * past its last row than the chip row's height.
 *
 * Each item after the chips reports its height with [tracked] as it is measured. The list measures
 * forward from item 1, so in a short list every row has reported by the time [filler] is measured
 * in the same pass, and the first frame is already parked.
 */
@Stable
internal class ParkFiller {
    private val heights = mutableStateMapOf<Any, Int>()

    /** Reports the height of the item under [key]. On the root of each item between chips and filler. */
    fun tracked(key: Any): Modifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val known = Snapshot.withoutReadObservation { heights[key] }
        if (known != placeable.height) heights[key] = placeable.height
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    /**
     * The last item's modifier, from a [LazyItemScope]: as tall as the viewport less the items
     * under [keys]. While any of them has not been measured yet the list is long enough already
     * (it skipped them), so it takes no height.
     */
    fun LazyItemScope.filler(keys: List<Any>): Modifier = Modifier
        .layout { measurable, constraints ->
            // Measured only to learn the viewport (less the content padding).
            val viewport = measurable.measure(constraints).height
            var taken = 0
            var known = true
            for (key in keys) {
                val height = heights[key]
                if (height == null) {
                    known = false
                    break
                }
                taken += height
            }
            val height = if (known) (viewport - taken).coerceAtLeast(0) else 0
            layout(constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: 0, height) {}
        }
        .fillParentMaxHeight()

    /** Forgets items no longer in the list. */
    fun retain(keys: List<Any>) {
        heights.keys.retainAll(keys.toSet())
    }
}

@Composable
internal fun rememberParkFiller(): ParkFiller = remember { ParkFiller() }

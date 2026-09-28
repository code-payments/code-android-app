package com.flipcash.shared.chat.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.core.R
import com.flipcash.shared.chat.reactions.ReactionPill
import com.flipcash.shared.chat.reactions.ReactionSettler
import com.getcode.theme.CodeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The N-more collapse the pill row uses under a bubble (decision 2): pack pills onto up to
 * [maxLines] lines of [containerWidth], and when they don't all fit, show as many as fit
 * alongside a trailing "N more" pill (itself sized [moreWidth], resolved by the caller from the
 * real count) rather than clipping a pill mid-row.
 *
 * A pure measure function so the N-more decision is testable without standing up Compose: given
 * the same child widths and container width, it always returns the same split. [ReactionPillRow]
 * is the only caller — it measures real pill widths, asks this, and lays out accordingly.
 */
object ReactionPillRowLayout {

    data class Result(
        /** How many of [pillWidths], from the front, are drawn. */
        val visibleCount: Int,
        /** Whether a trailing "N more" pill is needed. */
        val showsMore: Boolean,
        /** How many pills the "N more" pill stands in for. Zero when [showsMore] is false. */
        val moreCount: Int,
    )

    /**
     * @param pillWidths each reaction pill's measured width, in the same order the row draws them.
     * @param moreWidth the "N more" pill's width for the count this call would show. The caller
     *   only knows that count once this returns, so it re-measures the real chip only when
     *   [Result.showsMore] comes back true — everything up to then uses an upper-bound estimate
     *   (a chip sized for the worst case, `pillWidths.size` digits), which never under-reserves.
     * @param plusWidth the trailing "+" button's width, or `null` when `canReact` hides it.
     * @param expanded true once "N more" has been tapped: every pill draws, unbounded lines, and
     *   nothing collapses again this composition.
     */
    fun compute(
        pillWidths: List<Int>,
        moreWidth: Int,
        plusWidth: Int?,
        containerWidth: Int,
        spacing: Int,
        maxLines: Int,
        expanded: Boolean,
    ): Result {
        if (containerWidth <= 0 || pillWidths.isEmpty()) {
            return Result(visibleCount = pillWidths.size, showsMore = false, moreCount = 0)
        }

        val effectiveMaxLines = if (expanded) Int.MAX_VALUE else maxLines

        if (fits(pillWidths, plusWidth, containerWidth, spacing, effectiveMaxLines)) {
            return Result(visibleCount = pillWidths.size, showsMore = false, moreCount = 0)
        }

        // Collapsed and everything didn't fit: back off from the full set until what's left, plus
        // a trailing "N more" pill (and the "+" beyond it), fits within the line budget.
        for (visible in pillWidths.size - 1 downTo 0) {
            val widths = pillWidths.subList(0, visible) + moreWidth
            if (fits(widths, plusWidth, containerWidth, spacing, effectiveMaxLines)) {
                return Result(
                    visibleCount = visible,
                    showsMore = true,
                    moreCount = pillWidths.size - visible,
                )
            }
        }
        return Result(visibleCount = 0, showsMore = true, moreCount = pillWidths.size)
    }

    /**
     * Whether [widths], followed by [trailingWidth] (the "+" button, when present), packs into
     * [maxLines] lines of [containerWidth] with [spacing] between adjacent items on a line — the
     * same greedy left-to-right wrap `FlowRow` performs, just run ahead of time on known widths.
     */
    private fun fits(
        widths: List<Int>,
        trailingWidth: Int?,
        containerWidth: Int,
        spacing: Int,
        maxLines: Int,
    ): Boolean {
        var line = 0
        var remaining = containerWidth
        var lineHasItem = false

        fun place(width: Int): Boolean {
            val needed = width + if (lineHasItem) spacing else 0
            if (needed <= remaining) {
                remaining -= needed
                lineHasItem = true
                return true
            }
            line++
            if (line >= maxLines) return false
            remaining = containerWidth - width
            lineHasItem = true
            return true
        }

        for (width in widths) {
            if (!place(width)) return false
        }
        if (trailingWidth != null && !place(trailingWidth)) return false
        return true
    }
}


/**
 * The pill row drawn under a bubble (decision 2): up to two lines of [pills], collapsing into an
 * "N more" pill before the trailing "+" when they don't fit, aligned to [alignEnd] (the sender's
 * side — end for the viewer's own messages).
 *
 * A pill tap toggles that reaction; a pill long-press opens the reactors sheet and must not also
 * select the message — its own `combinedClickable` handles the long-press so it never reaches the
 * bubble's. "N more" expands the row in place — every pill then draws, unbounded lines — and
 * stays expanded for the rest of this composition. "+" (hidden when `canReact` is false) opens
 * the reaction picker. With no [pills] the row takes no space: the long-press strip is how a
 * first reaction gets added, as on iOS.
 *
 * Changes animate as iOS's `ReactionPillRowView` does: a new pill grows in from 40% on a bouncy
 * spring, a removed one shrinks to 60% and fades where it stood while the rest slide over, a count
 * rolls up or down, and the row's height follows. The pills showing when the row first composes
 * appear without animation, unless [animateInitialPills] says they're new — the caller passes it
 * for a row that composes because its message just got its first reaction.
 *
 * While the row is on screen a [ReactionSettler] decides what it shows: counts change live, but
 * positions, joins, leaves and pill widths change on a once-a-second beat, a touch holds the row
 * still, and for [ReactionSettler.HIT_GRACE_MS] after a beat changes the layout a tap lands on
 * whatever was under it before. A row coming on screen shows [pills] in their sorted order.
 */
@Composable
fun ReactionPillRow(
    pills: List<ReactionPill>,
    canReact: Boolean,
    onToggle: (emoji: String) -> Unit,
    onPillLongClick: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    maxLines: Int = 2,
    animateInitialPills: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    val clock = LocalPillRowClock.current
    val settler = remember { ReactionSettler(pills, clock) }
    var beat by remember { mutableIntStateOf(0) }
    val settled = remember(pills, beat) {
        settler.update(pills)
        settler.shown
    }
    LaunchedEffect(settler, pills) {
        while (settler.needsBeat) {
            delay((settler.nextBeatAt - clock()).coerceAtLeast(0))
            if (settler.settle()) beat++
        }
    }
    val shown = settled.map { it.pill }
    val countDigits = settled.associate { it.pill.emoji to it.countDigits }
    val grace = remember { TapGrace(clock) }
    val layoutBeat = beat

    val scope = rememberCoroutineScope()
    val motion = remember { PillRowMotion(if (animateInitialPills) emptyList() else shown, canReact) }
    val plusTarget = canReact && shown.isNotEmpty()
    val rendered = remember(shown, plusTarget, motion.exitsFinished) { motion.update(shown, plusTarget) }

    val currentCanReact by rememberUpdatedState(canReact)
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnPillLongClick by rememberUpdatedState(onPillLongClick)
    val currentOnOpenPicker by rememberUpdatedState(onOpenPicker)
    // Watches every touch on the row before the pills see it: each one holds the beat, and one
    // that lands during the tap grace on something the beat moved goes to what was there before.
    val touches = Modifier.pointerInput(settler, grace) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            settler.touch()
            val target = grace.redirect(down.position) ?: return@awaitEachGesture
            down.consume()
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial)
            }
            if (up == null) {
                // A long-press, or a cancel: only a pill has anything to do on a long-press.
                if (target.key != null && target.key != PLUS_KEY && target.key != MORE_KEY) currentOnPillLongClick()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                return@awaitEachGesture
            }
            up.consume()
            when (target.key) {
                null -> Unit
                PLUS_KEY -> if (currentCanReact) currentOnOpenPicker()
                MORE_KEY -> expanded = true
                else -> if (currentCanReact) currentOnToggle(target.key)
            }
        }
    }

    val pillHeight = 28.dp
    val pillPadding = 10.dp
    val spacingDp = 6.dp
    val plusSize = 28.dp
    val topGap = 4.dp

    SubcomposeLayout(modifier = modifier.animateHeightUnclipped().then(touches)) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val spacingPx = spacingDp.roundToPx()
        val topGapPx = topGap.roundToPx()
        val containerWidth = constraints.maxWidth

        val pillPlaceables = subcompose("pills") {
            rendered.pills.forEach { item ->
                key(item.pill.emoji) {
                    EnterExit(
                        entering = item.entering,
                        exiting = item.exiting,
                        onExited = { motion.exited(item.pill.emoji) },
                    ) { motionModifier ->
                        ReactionPillChip(
                            pill = item.pill,
                            countDigits = countDigits[item.pill.emoji] ?: ReactionSettler.MIN_COUNT_DIGITS,
                            height = pillHeight,
                            horizontalPadding = pillPadding,
                            // A previewer (canReact == false) can still open the reactors sheet
                            // from a long-press, but a tap must not toggle a reaction they're not
                            // allowed to make. A leaving pill takes neither.
                            onClick = if (canReact && !item.exiting) ({ onToggle(item.pill.emoji) }) else null,
                            onLongClick = if (item.exiting) ({}) else onPillLongClick,
                            modifier = motionModifier,
                        )
                    }
                }
            }
        }.map { it.measure(loose) }

        val plusPlaceable = rendered.plus?.let { plus ->
            subcompose("plus") {
                EnterExit(
                    entering = plus.entering,
                    exiting = plus.exiting,
                    onExited = { motion.exited(PLUS_KEY) },
                ) { motionModifier ->
                    PlusChip(
                        size = plusSize,
                        onClick = if (plus.exiting) ({}) else onOpenPicker,
                        modifier = motionModifier,
                    )
                }
            }.first().measure(loose)
        }

        // Leaving items keep the spot they held and take no room, so the rest reflow around them.
        val flowIndices = rendered.pills.indices.filter { !rendered.pills[it].exiting }
        val flowPlus = plusPlaceable?.takeIf { rendered.plus?.exiting == false }

        // Upper-bound estimate: a "more" chip sized for showing every pill, never narrower than
        // the real chip this could resolve to.
        val moreEstimate = subcompose("more-estimate") {
            MorePillChip(count = flowIndices.size, height = pillHeight, horizontalPadding = pillPadding)
        }.first().measure(loose)

        val result = ReactionPillRowLayout.compute(
            pillWidths = flowIndices.map { pillPlaceables[it].width },
            moreWidth = moreEstimate.width,
            plusWidth = flowPlus?.width,
            containerWidth = containerWidth,
            spacing = spacingPx,
            maxLines = maxLines,
            expanded = expanded,
        )

        val morePlaceable = if (result.showsMore) {
            subcompose("more") {
                MorePillChip(
                    count = result.moreCount,
                    height = pillHeight,
                    horizontalPadding = pillPadding,
                    onClick = { expanded = true },
                )
            }.first().measure(loose)
        } else null

        // Greedy wrap identical to ReactionPillRowLayout.fits, now placing real placeables: first
        // assign each item an (x, line) within its line, tracking each line's total width and
        // height, then place with a per-line offset so an end-aligned row hugs the container's
        // trailing edge rather than its leading one.
        class Item(val key: String, val placeable: Placeable)
        class Placement(val item: Item, val x: Int, val line: Int)

        val items = flowIndices.take(result.visibleCount).map { Item(rendered.pills[it].pill.emoji, pillPlaceables[it]) } +
            listOfNotNull(morePlaceable?.let { Item(MORE_KEY, it) }, flowPlus?.let { Item(PLUS_KEY, it) })

        val placements = ArrayList<Placement>(items.size)
        val lineWidths = ArrayList<Int>()
        val lineHeights = ArrayList<Int>()
        var x = 0
        var line = 0
        var lineHasItem = false
        var lineHeight = 0
        for (item in items) {
            val needed = item.placeable.width + if (lineHasItem) spacingPx else 0
            if (lineHasItem && x + needed > containerWidth) {
                lineWidths.add(x)
                lineHeights.add(lineHeight)
                line++
                x = 0
                lineHeight = 0
                lineHasItem = false
            }
            if (lineHasItem) x += spacingPx
            placements.add(Placement(item, x, line))
            x += item.placeable.width
            lineHeight = maxOf(lineHeight, item.placeable.height)
            lineHasItem = true
        }
        lineWidths.add(x)
        lineHeights.add(lineHeight)

        val lineY = IntArray(lineHeights.size)
        for (i in 1 until lineHeights.size) lineY[i] = lineY[i - 1] + lineHeights[i - 1] + spacingPx
        // No pills in the flow means no row: not even the gap above it.
        val totalHeight = if (items.isEmpty()) 0 else topGapPx + lineY.last() + lineHeights.last()

        val leaving = rendered.pills.mapIndexedNotNull { index, item ->
            if (item.exiting) Item(item.pill.emoji, pillPlaceables[index]) else null
        } + listOfNotNull(plusPlaceable?.takeIf { rendered.plus?.exiting == true }?.let { Item(PLUS_KEY, it) })

        val targets = placements.map { placement ->
            val lineOffset = if (alignEnd) containerWidth - lineWidths[placement.line] else 0
            IntOffset(placement.x + lineOffset, topGapPx + lineY[placement.line])
        }
        grace.laidOut(
            placements.mapIndexed { index, placement ->
                val at = targets[index]
                // placeRelative mirrors in RTL, so the hit rects mirror with it.
                val x = if (layoutDirection == LayoutDirection.Rtl) {
                    containerWidth - at.x - placement.item.placeable.width
                } else {
                    at.x
                }
                placement.item.key to IntRect(x, at.y, x + placement.item.placeable.width, at.y + placement.item.placeable.height)
            },
            beat = layoutBeat,
        )

        layout(containerWidth, totalHeight) {
            val placed = HashSet<String>()
            placements.forEachIndexed { index, placement ->
                placement.item.placeable.placeRelative(motion.slide(placement.item.key, targets[index], scope))
                placed += placement.item.key
            }
            for (item in leaving) {
                val at = motion.lastPosition(item.key) ?: continue
                item.placeable.placeRelative(at)
                placed += item.key
            }
            motion.placed(placed)
        }
    }
}

private const val MORE_KEY = "\u0000more"
private const val PLUS_KEY = "\u0000plus"

/** The row's clock, in monotonic milliseconds; tests swap it for the compose test clock. */
internal val LocalPillRowClock = staticCompositionLocalOf<() -> Long> { { SystemClock.uptimeMillis() } }

/**
 * The tap grace: for [ReactionSettler.HIT_GRACE_MS] after a beat changes where anything sits (a
 * move, a resize, a pill joining into the "+" slot, one leaving), a tap resolves against the
 * layout that was on screen before, since a thumb already on its way down aimed at that.
 */
private class TapGrace(private val clock: () -> Long) {
    /** Where a tap should go instead: [key] is a pill's emoji, [PLUS_KEY], [MORE_KEY], or null for empty space. */
    class Redirect(val key: String?)

    private var current: List<Pair<String, IntRect>> = emptyList()
    private var currentBeat: Int = 0
    private var previous: List<Pair<String, IntRect>> = emptyList()
    private var previousUntil: Long? = null

    /** Records a layout pass's hit rects; a change a beat made opens the grace. */
    fun laidOut(rects: List<Pair<String, IntRect>>, beat: Int) {
        if (rects == current) return
        if (beat != currentBeat && current.isNotEmpty()) {
            previous = current
            previousUntil = clock() + ReactionSettler.HIT_GRACE_MS
        }
        current = rects
        currentBeat = beat
    }

    /** The redirect for a touch-down at [position], or null when it lands where it would anyway. */
    fun redirect(position: Offset): Redirect? {
        val until = previousUntil ?: return null
        if (clock() >= until) return null
        val before = keyAt(previous, position)
        return if (before != keyAt(current, position)) Redirect(before) else null
    }

    private fun keyAt(rects: List<Pair<String, IntRect>>, position: Offset): String? =
        rects.firstOrNull { (_, rect) ->
            position.x >= rect.left && position.x < rect.right && position.y >= rect.top && position.y < rect.bottom
        }?.first
}

private class RenderedPill(val pill: ReactionPill, val entering: Boolean, val exiting: Boolean)
private class RenderedPlus(val entering: Boolean, val exiting: Boolean)
private class RenderedRow(val pills: List<RenderedPill>, val plus: RenderedPlus?)

/**
 * What the row draws across updates: the current pills plus any still animating out, each at the
 * index it last held, and where every item was last placed so a leaving one can fade in place and
 * the rest can slide from where they were. Plain fields, not state — they're written as the row
 * composes and places, and only [exitsFinished] needs to trigger anything.
 */
private class PillRowMotion(initialPills: List<ReactionPill>, canReact: Boolean) {
    private var shown: List<RenderedPill> = initialPills.map { RenderedPill(it, entering = false, exiting = false) }
    private var plusShown: Boolean = canReact && initialPills.isNotEmpty()
    private var plusLeaving: Boolean = false
    private val finished = HashSet<String>()
    private var placedLastPass: Set<String> = emptySet()
    private val offsets = HashMap<String, Animatable<IntOffset, AnimationVector2D>>()

    var exitsFinished by mutableIntStateOf(0)
        private set

    fun update(pills: List<ReactionPill>, plusTarget: Boolean): RenderedRow {
        val current = pills.mapTo(HashSet()) { it.emoji }
        val before = shown.filter { !it.exiting }.mapTo(HashSet()) { it.pill.emoji }
        val next = pills.mapTo(ArrayList()) { RenderedPill(it, entering = it.emoji !in before, exiting = false) }
        shown.forEachIndexed { index, old ->
            val emoji = old.pill.emoji
            // Only a pill that was actually on screen has somewhere to fade out from; one folded
            // into "N more" just goes.
            if (emoji in current || emoji in finished || emoji !in placedLastPass) return@forEachIndexed
            next.add(index.coerceAtMost(next.size), RenderedPill(old.pill, entering = false, exiting = true))
        }
        finished.removeAll(current)
        shown = next

        val plus = when {
            plusTarget -> RenderedPlus(entering = !plusShown || plusLeaving, exiting = false)
                .also { plusShown = true; plusLeaving = false }
            plusShown && PLUS_KEY !in finished && PLUS_KEY in placedLastPass -> RenderedPlus(entering = false, exiting = true)
                .also { plusLeaving = true }
            else -> null.also { plusShown = false; plusLeaving = false; finished.remove(PLUS_KEY) }
        }
        return RenderedRow(next, plus)
    }

    fun exited(key: String) {
        finished += key
        offsets.remove(key)
        exitsFinished++
    }

    /** Where [key] should draw this frame on its way to [target]; a new key starts there. */
    fun slide(key: String, target: IntOffset, scope: CoroutineScope): IntOffset {
        val offset = offsets.getOrPut(key) { Animatable(target, IntOffset.VectorConverter) }
        if (offset.targetValue != target) scope.launch { offset.animateTo(target, ChatAnimations.reactionReflowOffset) }
        return offset.value
    }

    fun lastPosition(key: String): IntOffset? = offsets[key]?.value

    fun placed(keys: Set<String>) {
        placedLastPass = keys
        offsets.keys.retainAll(keys)
    }
}

/**
 * Scales and fades [content] in when [entering] (from 40%, overshooting on the arrival spring) and
 * out when [exiting] (to 60%), calling [onExited] once it's gone. A return mid-exit springs back.
 */
@Composable
private fun EnterExit(
    entering: Boolean,
    exiting: Boolean,
    onExited: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val progress = remember { Animatable(if (entering) 0f else 1f) }
    val currentOnExited by rememberUpdatedState(onExited)
    LaunchedEffect(exiting) {
        if (exiting) {
            progress.animateTo(0f, ChatAnimations.reactionExit)
            currentOnExited()
        } else {
            progress.animateTo(1f, ChatAnimations.reactionEnter)
        }
    }
    content(
        Modifier.graphicsLayer {
            val p = progress.value
            val from = if (exiting) ChatAnimations.reactionExitScale else ChatAnimations.reactionEnterScale
            val scale = from + (1f - from) * p
            scaleX = scale
            scaleY = scale
            alpha = p.coerceIn(0f, 1f)
        },
    )
}

/**
 * Animates the height this reports on the reflow spring, without the clip `animateContentSize`
 * adds — a pill overshooting on arrival, or fading out below a shrinking row, draws past the
 * bounds. The first measure (and the first after lazy-list reuse) takes its height as is.
 */
private fun Modifier.animateHeightUnclipped(): Modifier = this then AnimateHeightElement

private data object AnimateHeightElement : ModifierNodeElement<AnimateHeightNode>() {
    override fun create() = AnimateHeightNode()
    override fun update(node: AnimateHeightNode) = Unit
}

private class AnimateHeightNode : Modifier.Node(), LayoutModifierNode {
    private var height: Animatable<Int, AnimationVector1D>? = null

    override fun onReset() {
        height = null
    }

    override fun onDetach() {
        height = null
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val target = placeable.height
        val animated = height ?: Animatable(target, Int.VectorConverter).also { height = it }
        if (animated.targetValue != target) coroutineScope.launch { animated.animateTo(target, ChatAnimations.reactionReflowHeight) }
        // The spring overshoots, and a row collapsing to nothing would dip below zero.
        return layout(placeable.width, animated.value.coerceAtLeast(0)) { placeable.place(0, 0) }
    }
}

@Composable
private fun ReactionPillChip(
    pill: ReactionPill,
    countDigits: Int,
    height: Dp,
    horizontalPadding: Dp,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val fill by animateColorAsState(
        targetValue = Color.White.copy(alpha = if (pill.selfReacted) 0.12f else 0.06f),
        animationSpec = ChatAnimations.reactionChangeColor,
        label = "pill fill",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (pill.selfReacted) CodeTheme.dimens.thickBorder else 0.dp,
        animationSpec = ChatAnimations.reactionChangeDp,
        label = "pill border",
    )
    Box(
        modifier = modifier
            .testTag("reaction_pill_${pill.emoji}")
            .semantics { selected = pill.selfReacted }
            // Clipped first so the press ripple stays inside the capsule.
            .clip(shape)
            .background(color = fill, shape = shape)
            .let { base ->
                // A zero-width border would draw as a hairline, so none at all until it grows.
                if (borderWidth > 0.dp) {
                    base.border(width = borderWidth, color = Color.White.copy(alpha = 0.24f), shape = shape)
                } else {
                    base
                }
            }
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            .height(height)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        // iOS's pill: a 15pt emoji and a 13pt medium count in textMain, 4pt apart.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = pill.emoji, fontSize = 15.sp)
            // Room for [countDigits] tabular digits, the settler's reservation, so the pill's width
            // changes only on a beat and never with the count inside it.
            val countStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val countWidth = remember(countDigits, measurer, density) {
                with(density) { measurer.measure("0".repeat(countDigits), countStyle).size.width.toDp() }
            }
            // The count rolls to its next value like a counter turning: up for a rise, down for a
            // fall.
            AnimatedContent(
                targetState = pill.count,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInVertically(ChatAnimations.reactionChangeOffset) { it * direction } + fadeIn(ChatAnimations.reactionChange))
                        .togetherWith(slideOutVertically(ChatAnimations.reactionChangeOffset) { -it * direction } + fadeOut(ChatAnimations.reactionChange))
                        .using(SizeTransform(clip = true) { _, _ -> ChatAnimations.reactionChangeSize })
                },
                label = "pill count",
                contentAlignment = Alignment.Center,
                modifier = Modifier.widthIn(min = countWidth),
            ) { count ->
                Text(
                    text = count.toString(),
                    style = countStyle,
                    color = CodeTheme.colors.textMain,
                )
            }
        }
    }
}

@Composable
private fun MorePillChip(
    count: Int,
    height: Dp,
    horizontalPadding: Dp,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            // Only the real "more" chip is tagged: the width-estimate slot SubcomposeLayout keeps
            // composed (to size the collapse ahead of the real decision) would otherwise carry the
            // same tag into the semantics tree even though it's never placed.
            .let { base -> if (onClick != null) base.testTag("reaction_pill_more") else base }
            .clip(shape)
            .background(color = Color.White.copy(alpha = 0.06f), shape = shape)
            .let { base -> onClick?.let { base.combinedClickable(onClick = it, onLongClick = {}, hapticFeedbackEnabled = false) } ?: base }
            .height(height)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$count more",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = CodeTheme.colors.textMain,
        )
    }
}

@Composable
private fun PlusChip(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag("reaction_pill_plus")
            .size(size)
            .clip(CircleShape)
            .background(color = Color.White.copy(alpha = 0.18f), shape = CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = {}, hapticFeedbackEnabled = false),
        contentAlignment = Alignment.Center,
    ) {
        // The strip's add-reaction icon, as iOS reuses its strip asset here.
        Icon(
            imageVector = Icons.Outlined.AddReaction,
            contentDescription = stringResource(R.string.action_addReaction),
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(20.dp),
        )
    }
}

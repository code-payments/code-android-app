package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.shared.chat.reactions.ReactionPill
import com.flipcash.shared.chat.reactions.ReactionStrip
import com.getcode.theme.CodeTheme

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
 * the reaction picker.
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
) {
    if (pills.isEmpty() && !canReact) return

    var expanded by remember(pills) { mutableStateOf(false) }

    val pillHeight = 28.dp
    val pillPadding = 10.dp
    val spacingDp = 6.dp
    val plusSize = 28.dp
    val topGap = 4.dp

    SubcomposeLayout(modifier = modifier.padding(top = topGap)) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val spacingPx = spacingDp.roundToPx()
        val containerWidth = constraints.maxWidth

        val pillPlaceables = subcompose("pills") {
            pills.forEach { pill ->
                ReactionPillChip(
                    pill = pill,
                    height = pillHeight,
                    horizontalPadding = pillPadding,
                    // A previewer (canReact == false) can still open the reactors sheet from a
                    // long-press, but a tap must not toggle a reaction they're not allowed to make.
                    onClick = if (canReact) ({ onToggle(pill.emoji) }) else null,
                    onLongClick = onPillLongClick,
                )
            }
        }.map { it.measure(loose) }

        val plusPlaceable = if (canReact) {
            subcompose("plus") { PlusChip(size = plusSize, onClick = onOpenPicker) }
                .first().measure(loose)
        } else null

        // Upper-bound estimate: a "more" chip sized for showing every pill, never narrower than
        // the real chip this could resolve to.
        val moreEstimate = subcompose("more-estimate") {
            MorePillChip(count = pills.size, height = pillHeight, horizontalPadding = pillPadding)
        }.first().measure(loose)

        val result = ReactionPillRowLayout.compute(
            pillWidths = pillPlaceables.map { it.width },
            moreWidth = moreEstimate.width,
            plusWidth = plusPlaceable?.width,
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

        val visible = pillPlaceables.take(result.visibleCount)
        val items = visible + listOfNotNull(morePlaceable, plusPlaceable)

        // Greedy wrap identical to ReactionPillRowLayout.fits, now placing real placeables: first
        // assign each item an (x, line) within its line, tracking each line's total width and
        // height, then place with a per-line offset so an end-aligned row hugs the container's
        // trailing edge rather than its leading one.
        data class Placement(val item: androidx.compose.ui.layout.Placeable, val x: Int, val line: Int)

        val placements = ArrayList<Placement>(items.size)
        val lineWidths = ArrayList<Int>()
        val lineHeights = ArrayList<Int>()
        var x = 0
        var line = 0
        var lineHasItem = false
        var lineHeight = 0
        for (item in items) {
            val needed = item.width + if (lineHasItem) spacingPx else 0
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
            x += item.width
            lineHeight = maxOf(lineHeight, item.height)
            lineHasItem = true
        }
        lineWidths.add(x)
        lineHeights.add(lineHeight)

        val lineY = IntArray(lineHeights.size)
        for (i in 1 until lineHeights.size) lineY[i] = lineY[i - 1] + lineHeights[i - 1] + spacingPx
        val totalHeight = lineY.last() + lineHeights.last()

        layout(containerWidth, totalHeight) {
            for (placement in placements) {
                val lineOffset = if (alignEnd) containerWidth - lineWidths[placement.line] else 0
                placement.item.placeRelative(placement.x + lineOffset, lineY[placement.line])
            }
        }
    }
}

/**
 * The strip a long-press on a reactable, selected bubble shows above it (decision 5): up to the 12
 * entries [ReactionStripComposer][com.flipcash.shared.chat.reactions.ReactionStripComposer]
 * composed, self-reacted ones highlighted, plus a trailing "+" that opens the full picker. A tap
 * on an entry both toggles the reaction and clears the selection — [onToggle] is the one signal
 * for both, same as the pill row's tap is a plain toggle without one.
 *
 * Horizontally scrolling rather than wrapping: it draws above a single bubble, where wrapping to a
 * second line would push it further from the message it's reacting to.
 */
@Composable
fun QuickReactionStrip(
    entries: List<ReactionStrip.Entry>,
    onToggle: (emoji: String) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    Row(
        modifier = modifier
            .background(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(percent = 50))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            Box(
                modifier = Modifier
                    .testTag("quick_reaction_${entry.emoji}")
                    .semantics { selected = entry.highlighted }
                    .size(32.dp)
                    .let { base ->
                        if (entry.highlighted) {
                            base.background(Color.White.copy(alpha = 0.16f), CircleShape)
                        } else {
                            base
                        }
                    }
                    .combinedClickable(onClick = { onToggle(entry.emoji) }, onLongClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = entry.emoji, fontSize = 20.sp)
            }
        }
        Box(
            modifier = Modifier
                .testTag("quick_reaction_plus")
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
                .combinedClickable(onClick = onOpenPicker, onLongClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ReactionPillChip(
    pill: ReactionPill,
    height: Dp,
    horizontalPadding: Dp,
    onClick: (() -> Unit)?,
    onLongClick: () -> Unit,
) {
    val fill = if (pill.selfReacted) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.06f)
    }
    Box(
        modifier = Modifier
            .testTag("reaction_pill_${pill.emoji}")
            .semantics { selected = pill.selfReacted }
            .background(color = fill, shape = RoundedCornerShape(percent = 50))
            .let { base ->
                if (pill.selfReacted) {
                    base.border(
                        width = CodeTheme.dimens.thickBorder,
                        color = Color.White.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(percent = 50),
                    )
                } else {
                    base
                }
            }
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "${pill.emoji} ${pill.count}", fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MorePillChip(
    count: Int,
    height: Dp,
    horizontalPadding: Dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            // Only the real "more" chip is tagged: the width-estimate slot SubcomposeLayout keeps
            // composed (to size the collapse ahead of the real decision) would otherwise carry the
            // same tag into the semantics tree even though it's never placed.
            .let { base -> if (onClick != null) base.testTag("reaction_pill_more") else base }
            .background(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(percent = 50),
            )
            .let { base -> onClick?.let { base.combinedClickable(onClick = it, onLongClick = {}) } ?: base }
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "$count more", fontSize = 13.sp)
    }
}

@Composable
private fun PlusChip(size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag("reaction_pill_plus")
            .background(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(percent = 50),
            )
            .combinedClickable(onClick = onClick, onLongClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "+", fontSize = 15.sp)
    }
}

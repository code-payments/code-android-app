package com.getcode.ui.core

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset

/**
 * Answers taps that land up to [outset] outside this element with [onTap], without changing its
 * layout.
 *
 * For a control drawn smaller than the area a thumb aims at, such as a 40dp circle with dead space
 * around it. Put it at the front of the control's modifier chain, ahead of any clip, because a clip
 * also clips hit testing for everything after it. The control's own click still handles taps
 * inside its bounds: it sees the up event first and consumes it, so a tap there fires once. It
 * adds no semantics, because the control already announces the action.
 *
 * The outset only wins where nothing drawn above it takes the tap. Size it to stop where a
 * neighbouring target starts: a neighbour that isn't clickable lets taps fall through to this.
 * A clickable smaller than 48dp also takes taps up to that minimum itself, and those near hits
 * win over the outset, which is harmless when both do the same thing.
 */
fun Modifier.expandedTouchTarget(outset: PaddingValues, onTap: () -> Unit): Modifier =
    // Compose hit-tests a pointer handler against the size of the layout it wraps. The inner layout
    // grows by the outset, so the tap handler covers it; the outer one reports the original size
    // and pulls the grown box back by the outset, so the parent lays this out as before.
    // Compose's own touchBoundsExpansion can't do this: it applies to stylus pointers only.
    layout { measurable, constraints ->
        val start = outset.calculateStartPadding(layoutDirection).roundToPx()
        val top = outset.calculateTopPadding().roundToPx()
        val horizontal = start + outset.calculateEndPadding(layoutDirection).roundToPx()
        val vertical = top + outset.calculateBottomPadding().roundToPx()
        val grown = measurable.measure(constraints.offset(horizontal, vertical))
        layout(
            constraints.constrainWidth(grown.width - horizontal),
            constraints.constrainHeight(grown.height - vertical),
        ) {
            grown.placeRelative(-start, -top)
        }
    }
        .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }
        .layout { measurable, constraints ->
            val start = outset.calculateStartPadding(layoutDirection).roundToPx()
            val top = outset.calculateTopPadding().roundToPx()
            val horizontal = start + outset.calculateEndPadding(layoutDirection).roundToPx()
            val vertical = top + outset.calculateBottomPadding().roundToPx()
            val control = measurable.measure(constraints.offset(-horizontal, -vertical))
            layout(control.width + horizontal, control.height + vertical) {
                control.placeRelative(start, top)
            }
        }

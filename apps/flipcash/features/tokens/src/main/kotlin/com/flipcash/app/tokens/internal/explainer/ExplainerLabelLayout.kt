package com.flipcash.app.tokens.internal.explainer

import kotlin.math.max

/** A tick label measured in pixels: its [centre] on the track and its text [width]. */
internal data class ExplainerLabelBox(val centre: Float, val width: Float, val isToday: Boolean)

/**
 * Decides which slider labels to draw and where. Today is always drawn; every other label is
 * clamped inside `[0, totalWidth]` and dropped when its bounds come within [minGap] of Today's or of
 * a label already drawn. Returns the left edge of each drawn label, or null for a dropped one.
 */
internal fun layoutExplainerLabels(labels: List<ExplainerLabelBox>, totalWidth: Float, minGap: Float): List<Float?> {
    val lefts = labels.map { (it.centre - it.width / 2f).coerceIn(0f, max(0f, totalWidth - it.width)) }
    val drawn = mutableListOf<Int>()
    val result = MutableList<Float?>(labels.size) { null }
    val order = labels.indices.sortedBy { if (labels[it].isToday) 0 else 1 }
    for (i in order) {
        val clashes = drawn.any { j ->
            lefts[i] < lefts[j] + labels[j].width + minGap && lefts[j] < lefts[i] + labels[i].width + minGap
        }
        if (labels[i].isToday || !clashes) {
            drawn += i
            result[i] = lefts[i]
        }
    }
    return result
}

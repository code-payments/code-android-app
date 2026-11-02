package com.flipcash.shared.chat.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil

/**
 * Narrows wrapped text to its widest line.
 *
 * Text that wraps reports the whole width it was offered, not the width of its lines, so a bubble
 * around it stretches to its ceiling and leaves the slack of the wrapped word on the right. A long
 * word or a mention's pill wrapping onto its own line makes that slack most of the bubble. SwiftUI
 * sizes text to its widest line, which is what the iOS bubble shows.
 *
 * The text is measured once here at the offered width to find that line, then handed a ceiling of
 * exactly its width. [style] and [inlineContent] have to be what the text itself is drawn with, or
 * the two measurements break lines differently.
 */
internal fun Modifier.widestLine(
    measurer: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    inlineContent: Map<String, InlineTextContent>,
): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val probe = measurer.measure(
        text = text,
        style = style,
        placeholders = text.placeholders(inlineContent),
        constraints = Constraints(maxWidth = constraints.maxWidth),
        layoutDirection = layoutDirection,
        density = this,
    )
    val narrowed = if (probe.lineCount > 1) {
        constraints.copy(maxWidth = probe.widestLine().coerceIn(constraints.minWidth, constraints.maxWidth))
    } else {
        constraints
    }
    val placeable = measurable.measure(narrowed)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

internal fun TextLayoutResult.widestLine(): Int =
    ceil((0 until lineCount).maxOf { getLineRight(it) - getLineLeft(it) }).toInt()

// The tag `appendInlineContent` files its slots under. Foundation keeps the constant internal.
private const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

private fun AnnotatedString.placeholders(
    inlineContent: Map<String, InlineTextContent>,
): List<AnnotatedString.Range<Placeholder>> =
    getStringAnnotations(INLINE_CONTENT_TAG, 0, length).mapNotNull { slot ->
        inlineContent[slot.item]?.let { AnnotatedString.Range(it.placeholder, slot.start, slot.end) }
    }

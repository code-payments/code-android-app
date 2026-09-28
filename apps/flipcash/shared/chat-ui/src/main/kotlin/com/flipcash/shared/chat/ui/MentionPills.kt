package com.flipcash.shared.chat.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How a mention is drawn: a rounded pill behind the handle, so it reads as a person rather than a
 * web link. iOS's `MentionPill`, value for value.
 */
internal object MentionPill {
    val fill = Color.White.copy(alpha = 0.14f)

    /** How far the pill reaches past the handle's letters, each side. */
    val padding = 6.dp

    /** The space between the pill and the neighbouring words. */
    val gap = 3.dp

    val cornerRadius = 6.dp

    /** The room held open either side of the handle: the pill's padding, then the gap. */
    val spacing = padding + gap
}

/**
 * A text bubble's body with its links and mentions in place, and where each handle's letters sit
 * in it.
 *
 * The body is not [text] verbatim: each mention is bracketed by a placeholder [MentionPill.spacing]
 * wide, which is what makes room for the pill without drawing over the words beside it. iOS kerns
 * the neighbouring characters for the same room; Compose has no per-character kern, and a
 * placeholder of known width is the nearest thing. Its stand-in character is a no-break space, so
 * the line can still break before the leading one but never between it and the `@`, nor between
 * the handle and the trailing one.
 */
internal class MentionedText(
    val text: AnnotatedString,
    /** Each handle's letters, `@` included, in [text]'s offsets. The pills are drawn from these. */
    val handles: List<TextRange>,
)

internal const val MENTION_SPACING_SLOT = "mentionSpacing"

private const val MENTION_SPACING_STAND_IN = " "

internal fun buildMentionedText(
    text: String,
    mentions: List<DetectedMention>,
    linkStyle: SpanStyle,
    onMentionClick: ((String) -> Unit)?,
): MentionedText {
    val ordered = mentions.sortedBy { it.start }
    val handles = mutableListOf<TextRange>()
    val body = buildAnnotatedString {
        var cursor = 0
        ordered.forEach { mention ->
            // Out of order or overlapping only if the caller handed over ranges from other text.
            if (mention.start < cursor || mention.end > text.length) return@forEach
            append(text, cursor, mention.start)
            val tapStart = length
            appendInlineContent(MENTION_SPACING_SLOT, MENTION_SPACING_STAND_IN)
            val handleStart = length
            append(text, mention.start, mention.end)
            handles += TextRange(handleStart, length)
            appendInlineContent(MENTION_SPACING_SLOT, MENTION_SPACING_STAND_IN)
            // Over the spacing as well as the letters, so the whole pill takes the tap.
            if (onMentionClick != null) {
                addLink(
                    LinkAnnotation.Clickable(tag = mention.username) { onMentionClick(mention.username) },
                    start = tapStart,
                    end = length,
                )
            }
            cursor = mention.end
        }
        append(text, cursor, text.length)

        // A link never overlaps a mention -- detection drops the mention -- so each link moves by
        // the two placeholders of every mention before it.
        detectUrls(text).forEach { link ->
            val shift = 2 * ordered.count { it.start < link.start }
            addLink(
                LinkAnnotation.Url(url = link.url, styles = TextLinkStyles(style = linkStyle)),
                start = link.start + shift,
                end = link.end + shift,
            )
        }
    }
    return MentionedText(body, handles)
}

/** The placeholder either side of a handle, for the body's `inlineContent`. */
internal fun mentionSpacingContent(density: Density): Pair<String, InlineTextContent> =
    MENTION_SPACING_SLOT to InlineTextContent(
        Placeholder(
            width = with(density) { MentionPill.spacing.toSp() },
            height = 1.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
        ),
    ) { }

/**
 * The pill behind each line [handle] covers: its letters plus [MentionPill.padding] each side, the
 * line's full height. A handle that wraps gets a pill per line, as on iOS.
 */
internal fun TextLayoutResult.mentionPillRects(handle: TextRange, padding: Float): List<Pair<Offset, Size>> {
    if (handle.collapsed || handle.end > layoutInput.text.length) return emptyList()
    val firstLine = getLineForOffset(handle.start)
    val lastLine = getLineForOffset(handle.end - 1)
    return (firstLine..lastLine).mapNotNull { line ->
        val from = maxOf(handle.start, getLineStart(line))
        val until = minOf(handle.end, getLineEnd(line, visibleEnd = true))
        if (from >= until) return@mapNotNull null
        val left = getBoundingBox(from).left - padding
        val right = getBoundingBox(until - 1).right + padding
        val top = getLineTop(line)
        Offset(left, top) to Size(right - left, getLineBottom(line) - top)
    }
}

internal fun DrawScope.drawMentionPills(layout: TextLayoutResult, handles: List<TextRange>) {
    val padding = MentionPill.padding.toPx()
    val radius = CornerRadius(MentionPill.cornerRadius.toPx())
    handles.forEach { handle ->
        layout.mentionPillRects(handle, padding).forEach { (topLeft, size) ->
            drawRoundRect(color = MentionPill.fill, topLeft = topLeft, size = size, cornerRadius = radius)
        }
    }
}

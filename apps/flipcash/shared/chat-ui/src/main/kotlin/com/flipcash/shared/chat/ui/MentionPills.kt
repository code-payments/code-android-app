package com.flipcash.shared.chat.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import com.flipcash.libs.textformat.FormatStyle
import com.flipcash.libs.textformat.RangeKind
import com.flipcash.libs.textformat.StyledSpan
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
 * A text bubble's body with its formatting, links and mentions in place, and where each handle's
 * letters sit in it.
 *
 * The body is not the message text verbatim. The parser's consumed markers are gone, and each
 * mention is bracketed by a placeholder [MentionPill.spacing] wide, which is what makes room for
 * the pill without drawing over the words beside it. iOS kerns the neighbouring characters for the
 * same room; Compose has no per-character kern, and a placeholder of known width is the nearest
 * thing. Its stand-in character is a no-break space, so the line can still break before the
 * leading one but never between it and the `@`, nor between the handle and the trailing one.
 */
internal class MentionedText(
    val text: AnnotatedString,
    /** Each handle's letters, `@` included, in [text]'s offsets. The pills are drawn from these. */
    val handles: List<TextRange>,
    /**
     * One range per run of consecutive quoted lines, in [text]'s offsets. The bar is drawn down
     * each, in the gutter [FormatStyles.quoteIndent] leaves.
     */
    val quoteBars: List<TextRange> = emptyList(),
)

/** How the parser's styles are drawn. Everything else the bubble owns: weight, colour, size. */
internal class FormatStyles(
    val code: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = 0.12f)),
    val codeBlock: SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = Color.White.copy(alpha = 0.12f)),
    /** The gutter a quoted line is indented by, which holds the bar. */
    val quoteIndent: TextUnit = 12.sp,
    /** Where the wrapped lines of a bulleted item start, past the `- `. */
    val bulletIndent: TextUnit = 12.sp,
    /** The same for a numbered item, past `1. `. */
    val numberedIndent: TextUnit = 20.sp,
)

internal const val MENTION_SPACING_SLOT = "mentionSpacing"

private const val MENTION_SPACING_STAND_IN = " "

internal fun buildMentionedText(
    text: String,
    mentions: List<DetectedMention>,
    linkStyle: SpanStyle,
    mentionStyle: SpanStyle,
    onMentionClick: ((String) -> Unit)?,
    formatStyles: FormatStyles = FormatStyles(),
): MentionedText {
    // Out of order or overlapping only if the caller handed over ranges from other text.
    val ordered = mentions.sortedBy { it.start }.fold(emptyList<DetectedMention>()) { kept, mention ->
        if (mention.start < (kept.lastOrNull()?.end ?: 0) || mention.end > text.length) kept else kept + mention
    }
    val parsed = parseChatText(text, detectUrls(text), ordered)
    val display = parsed.display

    // The parser hands back every mention it was given, moved to display offsets and in order.
    val mentionRanges = parsed.displayRanges.filter { it.kind == RangeKind.Mention }
    val placed = if (mentionRanges.size == ordered.size) mentionRanges.zip(ordered) else emptyList()

    // Each placed mention adds a placeholder before and after its handle, so a display offset moves
    // by two for every mention wholly before it and by one inside a handle. A boundary on a
    // mention's own edge stays outside the leading placeholder and past the trailing one, which
    // lets a style wrap a whole pill (`*@jeff*`).
    fun laidOut(offset: Int): Int = offset + placed.sumOf { (range, _) ->
        when {
            range.end <= offset -> 2
            range.start < offset -> 1
            else -> 0
        }.toInt()
    }

    val handles = mutableListOf<TextRange>()
    val body = buildAnnotatedString {
        var cursor = 0
        placed.forEach { (range, mention) ->
            append(display, cursor, range.start)
            val tapStart = length
            appendInlineContent(MENTION_SPACING_SLOT, MENTION_SPACING_STAND_IN)
            val handleStart = length
            append(display, range.start, range.end)
            handles += TextRange(handleStart, length)
            appendInlineContent(MENTION_SPACING_SLOT, MENTION_SPACING_STAND_IN)
            // Over the spacing as well as the letters, so the whole pill takes the tap.
            if (onMentionClick == null) {
                addStyle(mentionStyle, handleStart, length)
            } else {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = mention.username,
                        styles = TextLinkStyles(style = mentionStyle),
                    ) { onMentionClick(mention.username) },
                    start = tapStart,
                    end = length,
                )
            }
            cursor = range.end
        }
        append(display, cursor, display.length)

        parsed.spans.forEach { span ->
            val style = when (span.style) {
                FormatStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                FormatStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                FormatStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                FormatStyle.Code -> formatStyles.code
                FormatStyle.CodeBlock -> formatStyles.codeBlock
                // Whole lines, drawn below.
                FormatStyle.Quote, FormatStyle.Bullet, FormatStyle.Numbered -> null
            } ?: return@forEach
            addStyle(style, laidOut(span.start), laidOut(span.end))
        }

        parsed.displayRanges.filter { it.kind == RangeKind.Link }.forEach { link ->
            val url = link.target?.let { target -> detectUrls(target).singleOrNull()?.url ?: target }
                ?: display.substring(link.start, link.end).let { shown ->
                    detectUrls(shown).firstOrNull()?.url ?: shown
                }
            addLink(
                LinkAnnotation.Url(url = url, styles = TextLinkStyles(style = linkStyle)),
                start = laidOut(link.start),
                end = laidOut(link.end),
            )
        }
    }

    val lines = display.lineRanges()
    val quoted = lines.map { line -> parsed.spans.any { it.style == FormatStyle.Quote && it.covers(line) } }
    val paragraphs = AnnotatedString.Builder(body)
    lines.forEachIndexed { index, line ->
        val listIndent = when {
            parsed.spans.any { it.style == FormatStyle.Bullet && it.covers(line) } -> formatStyles.bulletIndent
            parsed.spans.any { it.style == FormatStyle.Numbered && it.covers(line) } -> formatStyles.numberedIndent
            else -> TextUnit.Unspecified
        }
        val quoteIndent = if (quoted[index]) formatStyles.quoteIndent else TextUnit.Unspecified
        if (listIndent.isUnspecified && quoteIndent.isUnspecified) return@forEachIndexed
        val lead = if (quoteIndent.isSpecified) quoteIndent else 0.sp
        val rest = (lead.value + if (listIndent.isSpecified) listIndent.value else 0f).sp
        // Through the newline, so the paragraphs tile the text and no stray one is left holding it.
        val end = if (line.last < display.length) line.last + 1 else line.last
        paragraphs.addStyle(
            ParagraphStyle(textIndent = TextIndent(firstLine = lead, restLine = rest)),
            laidOut(line.first),
            laidOut(end),
        )
    }

    val bars = mutableListOf<TextRange>()
    var run: IntRange? = null
    lines.forEachIndexed { index, line ->
        if (quoted[index]) {
            run = run?.let { it.first..line.last } ?: line
        } else {
            run?.let { bars += TextRange(laidOut(it.first), laidOut(it.last)) }
            run = null
        }
    }
    run?.let { bars += TextRange(laidOut(it.first), laidOut(it.last)) }

    return MentionedText(paragraphs.toAnnotatedString(), handles, bars)
}

/** Each line's span, end exclusive of the newline; `last` is the offset of the line break. */
private fun String.lineRanges(): List<IntRange> {
    val out = mutableListOf<IntRange>()
    var start = 0
    while (true) {
        val newline = indexOf('\n', start)
        if (newline < 0) {
            out += start..length
            return out
        }
        out += start..newline
        start = newline + 1
    }
}

private fun StyledSpan.covers(line: IntRange): Boolean = start < line.last && end > line.first

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

/** The bar down the leading edge of each run of quoted lines, from its first line's top to its last line's bottom. */
internal fun DrawScope.drawQuoteBars(layout: TextLayoutResult, bars: List<TextRange>, color: Color) {
    val width = QUOTE_BAR_WIDTH.toPx()
    val radius = CornerRadius(width / 2)
    bars.forEach { bar ->
        if (bar.end > layout.layoutInput.text.length) return@forEach
        val top = layout.getLineTop(layout.getLineForOffset(bar.start))
        val bottom = layout.getLineBottom(layout.getLineForOffset(maxOf(bar.start, bar.end - 1)))
        drawRoundRect(color, Offset(0f, top), Size(width, bottom - top), radius)
    }
}

private val QUOTE_BAR_WIDTH = 3.dp
